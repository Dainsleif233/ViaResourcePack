/*
 * ViaResourcePack — automatic resource pack version conversion for Fabric clients.
 * Copyright (C) 2026 Dainsleif
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package top.syshub.viarp.convert;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.zip.ZipException;

import top.syshub.viarp.ViaRpConfig;
import top.syshub.viarp.ViaRpPaths;
import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.cache.CacheEntryMeta;
import top.syshub.viarp.cache.ConversionCache;
import top.syshub.viarp.io.PathIo;
import top.syshub.viarp.io.ZipTool;
import top.syshub.viarp.pack.GameVersion;
import top.syshub.viarp.pack.PackFormat;
import top.syshub.viarp.pack.PackMetadata;
import top.syshub.viarp.pack.PackSelection;

/**
 * 转换服务：所有拦截点的唯一入口。
 *
 * <p>各版本适配器的 mixin 只做一件事——把拿到的资源包路径交给
 * {@link #resolve(Path, PackKind)}，然后用返回的路径替换原参数。是否需要转换、
 * 缓存命中、失败降级全在这里决定，因此所有适配器共用同一套行为。
 *
 * <p>一批资源在有界工作池中处理；相同完整缓存键仅有一个发布者。
 * 原版读取路径的回调仍在原来的线程，且按原请求顺序使用整批结果。
 */
public final class ConversionService {

    private static final int MAX_LOGGED_WARNINGS = 8;
    private static final int WORKERS = Math.max(1, Math.min(4, Math.min(
            Runtime.getRuntime().availableProcessors() - 1,
            (int) (Runtime.getRuntime().maxMemory() / (512L * 1024L * 1024L)))));
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ThreadPoolExecutor WORK_POOL = new ThreadPoolExecutor(WORKERS, WORKERS,
            30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(32), runnable -> {
                Thread worker = new Thread(runnable, "ViaResourcePack-Worker-" + THREAD_IDS.incrementAndGet());
                worker.setDaemon(true);
                return worker;
            });

    static {
        WORK_POOL.allowCoreThreadTimeOut(true);
    }

    private static volatile ConversionService instance;

    private final ViaRpPaths paths;
    private final ViaRpConfig config;
    private final ConversionCache cache;
    private final PackSelection selection;
    private final PackPlaceholder placeholder;
    private final Map<String, CompletableFuture<Path>> inFlight = new ConcurrentHashMap<String, CompletableFuture<Path>>();
    private final ThreadLocal<Map<Path, Path>> preparedLocal = new ThreadLocal<Map<Path, Path>>();
    private final Set<String> reportedSkips = new HashSet<String>();

    /** 本次运行已经报告过失败的源包，避免每次重载都刷同一条错误日志。 */
    private final Set<String> reportedFailures = new HashSet<String>();

    private boolean prepared;

    private ConversionService(ViaRpPaths paths, ViaRpConfig config, ConversionCache cache,
                              PackSelection selection) {
        this.paths = paths;
        this.config = config;
        this.cache = cache;
        this.selection = selection;
        this.placeholder = new PackPlaceholder(paths);
    }

    public static ConversionService get() {
        ConversionService existing = instance;
        if (existing == null) {
            synchronized (ConversionService.class) {
                existing = instance;
                if (existing == null) {
                    ViaRpPaths paths = ViaRpPaths.get();
                    ViaRpConfig config = ViaRpConfig.get();
                    existing = new ConversionService(paths, config,
                            new ConversionCache(paths.cacheDir()),
                            new PackSelection(paths.optionsFile()));
                    instance = existing;
                }
            }
        }
        return existing;
    }

    /** 资源包的来源，只用于日志与配置开关。 */
    public enum PackKind {
        /** {@code resourcepacks/} 目录下的本地包。 */
        LOCAL,
        /** 服务器下发或世界捆绑的包。 */
        SERVER
    }

    /**
     * 仅在 [source] 的声明上界低于当前格式时尝试升级；否则原样返回 [source]。
     *
     * <p>任何失败都返回 [source]——宁可让原版按老规矩报「资源包不兼容」，也不能因为
     * 我们的转换出错就让游戏加载不了任何包。
     */
    public Path resolve(Path source, PackKind kind) {
        if (source == null) {
            return null;
        }
        return resolveForBatch(source, kind, null);
    }

    private Path resolveForBatch(Path source, PackKind kind, ConversionBatch.Entry progress) {
        if (progress != null) {
            progress.stage(ConversionBatch.Stage.CHECKING);
        }
        try {
            Path resolved = source == null ? null : resolveChecked(source, kind, progress);
            if (progress != null && !progress.stage().finished()) {
                progress.finish(resolved, ConversionBatch.Stage.UNCHANGED);
            }
            return resolved;
        } catch (IOException | RuntimeException e) {
            reportFailureOnce(source, e);
            if (progress != null) {
                progress.finish(source, ConversionBatch.Stage.FAILED);
            }
            return source;
        }
    }

    /** 已下载的整组地址并行准备，结果保持服务器的原顺序。 */
    public List<Path> resolveServerPacks(List<Path> sources) {
        if (sources.isEmpty() || !config.enabled() || !config.convertServerPacks()) {
            return sources;
        }
        return startBatch(sources, PackKind.SERVER).resolvedPaths();
    }

    /** 原版保存选项之后、实际资源重载之前，为本地包安排后台准备。 */
    public ConversionBatch prepareLocalReload() {
        Set<Path> sources = new LinkedHashSet<Path>();
        if (config.enabled() && config.convertLocalPacks()) {
            Path root = paths.resourcePacksDir();
            if (config.onlyConvertEnabledPacks()) {
                for (String id : selection.selectedIds()) {
                    String name = id.startsWith("file/") ? id.substring(5) : id;
                    if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf((char) 92) >= 0
                            || ".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    try {
                        Path child = root.resolve(name);
                        if (paths.isInResourcePacksDir(child) && !paths.isOwnOutput(child)
                                && Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
                            sources.add(child);
                        }
                    } catch (InvalidPathException ignored) {
                        // 损坏的选项 ID 不应使其他条目失去转换机会。
                    }
                }
            } else if (Files.isDirectory(root)) {
                try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
                    for (Path child : children) {
                        if (!paths.isOwnOutput(child)) {
                            sources.add(child);
                        }
                    }
                } catch (IOException e) {
                    ViaRpLog.warn("Could not enumerate local resource packs for conversion", e);
                }
            }
        }
        return startBatch(new ArrayList<Path>(sources), PackKind.LOCAL);
    }

    private ConversionBatch startBatch(List<Path> sources, PackKind kind) {
        return ConversionBatch.start(sources, WORK_POOL, WORKERS,
                (source, entry) -> resolveForBatch(source, kind, entry));
    }

    /** 仅在原版同步发现资源包的调用栈中使用已准备路径；失败项也不在主线程重试。 */
    public <T> T withPreparedLocalPacks(ConversionBatch batch, Supplier<T> reload) {
        Map<Path, Path> previous = preparedLocal.get();
        preparedLocal.set(batch.resolvedMap());
        try {
            return reload.get();
        } finally {
            if (previous == null) {
                preparedLocal.remove();
            } else {
                preparedLocal.set(previous);
            }
        }
    }

    /** {@link #resolve(Path, PackKind)} 的 {@link File} 版本，供 1.19.2 及更早的 hook 用。 */
    public File resolve(File source, PackKind kind) {
        if (source == null) {
            return null;
        }
        Path resolved = resolve(source.toPath(), kind);
        return resolved == null ? source : resolved.toFile();
    }

    /**
     * 本地包仅处理客户端 resourcepacks 的直接子项，且默认等到用户启用时才转换。
     * 只给可升版的旧纹理包提供占位；绝不伪装现代资源包的兼容性。
     */
    public Path resolveLocal(Path source) {
        if (source == null || !config.enabled() || !config.convertLocalPacks()
                || !paths.isInResourcePacksDir(source) || paths.isOwnOutput(source)) {
            return source;
        }
        try {
            Map<Path, Path> batch = preparedLocal.get();
            if (batch != null) {
                Path preparedPath = batch.get(source.toAbsolutePath().normalize());
                if (preparedPath != null) {
                    return preparedPath;
                }
            } else if (isEnabled(source)) {
                return resolveChecked(source, PackKind.LOCAL, null);
            }
            PackFormat target = GameVersion.current().packFormat();
            PackInput pack = inspect(source);
            if (pack == null || !pack.canUpgradeTo(target) || !ResourcePackUpgrader.canUpgrade(pack.format(), target)) {
                return source;
            }
            // 只复用已有产物，不在浏览时启动转换。原版用同一份产物决定兼容性并保留原条目 ID。
            Path cached = lookupLocalCache(source, target);
            if (cached != null) {
                return cached;
            }
            // 没有缓存的旧纹理包仍用占位通过原版发现，等玩家启用才转换。
            return pack.isLegacyTexturePack() ? placeholder.create(source, target) : source;
        } catch (IOException | RuntimeException e) {
            reportFailureOnce(source, e);
            return source;
        }
    }

    /** 启动选包时证明本地包确实有当前兼容的升级产物；不转换、不创建占位。 */
    public boolean hasConvertedLocalPack(Path vanillaPackDirectory, String packId) {
        if (!config.enabled() || !config.convertLocalPacks() || vanillaPackDirectory == null
                || packId == null || !packId.startsWith("file/")) {
            return false;
        }
        String name = packId.substring(5);
        if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf((char) 92) >= 0
                || ".".equals(name) || "..".equals(name)) {
            return false;
        }
        try {
            Path configuredDirectory = paths.resourcePacksDir();
            if (!configuredDirectory.equals(vanillaPackDirectory.toAbsolutePath().normalize())) {
                return false;
            }
            Path source = configuredDirectory.resolve(name);
            if (!paths.isInResourcePacksDir(source) || paths.isOwnOutput(source)) {
                return false;
            }
            PackFormat target = GameVersion.current().packFormat();
            PackInput pack = inspect(source);
            return pack != null && pack.canUpgradeTo(target) && ResourcePackUpgrader.canUpgrade(pack.format(), target)
                    && lookupLocalCache(source, target) != null;
        } catch (IOException | RuntimeException e) {
            // 无法验证就保留原版标记和选包行为。
            ViaRpLog.debug("Could not verify the converted resource pack {} at startup", packId, e);
            return false;
        }
    }

    private Path lookupLocalCache(Path source, PackFormat target) throws IOException {
        boolean zip = Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS);
        if (!cache.hasCandidate(zip, zip ? Files.size(source) : -1L, target, ResourcePackUpgrader.RULES_VERSION)) {
            return null;
        }
        prepareOnce();
        return cache.lookup(PathIo.sha1(source), zip, target, ResourcePackUpgrader.RULES_VERSION);
    }

    /**
     * 旧 File 适配器借占位目录通过原版的预筛选。不在这里转包，也不替换列表中的原目录。
     */
    public File resolveLegacyFolderForListing(File source) {
        if (source == null || !config.enabled() || !config.convertLocalPacks()) {
            return source;
        }
        Path folder = source.toPath();
        if (!paths.isInResourcePacksDir(folder) || paths.isOwnOutput(folder)
                || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(folder.resolve("pack.mcmeta"), LinkOption.NOFOLLOW_LINKS)) {
            return source;
        }
        try {
            PackFormat target = GameVersion.current().packFormat();
            PackInput pack = inspect(folder);
            return pack != null && pack.isLegacyTexturePack() && ResourcePackUpgrader.canUpgrade(pack.format(), target)
                    ? placeholder.create(folder, target).toFile() : source;
        } catch (IOException | RuntimeException e) {
            reportFailureOnce(folder, e);
            return source;
        }
    }

    /** [source] 是否出现在 {@code options.txt} 的已启用列表里。 */
    private boolean isEnabled(Path source) {
        if (!config.onlyConvertEnabledPacks()) {
            return true;
        }
        Path name = source.getFileName();
        if (name == null) {
            return false;
        }
        if (selection.isEnabled(name.toString())) {
            return true;
        }
        return false;
    }

    /** {@link #resolveLocal(Path)} 的 {@link File} 版本。 */
    public File resolveLocal(File source) {
        if (source == null) {
            return null;
        }
        Path resolved = resolveLocal(source.toPath());
        return resolved == null ? source : resolved.toFile();
    }

    private Path resolveChecked(Path source, PackKind kind, ConversionBatch.Entry progress) throws IOException {
        if (!config.enabled()) {
            return source;
        }
        if (kind == PackKind.LOCAL && !config.convertLocalPacks()) {
            return source;
        }
        if (kind == PackKind.SERVER && !config.convertServerPacks()) {
            return source;
        }
        // 缓存产物本身也会流过拦截点（原版重新打开我们返回的路径），必须放行，
        // 否则会无限递归转换。
        if (paths.isOwnOutput(source)) {
            return source;
        }

        PackFormat target = GameVersion.current().packFormat();
        if (!target.isKnown()) {
            return source;
        }

        PackInput pack = inspect(source);
        if (pack == null) {
            return source;
        }
        if (!pack.canUpgradeTo(target) || !ResourcePackUpgrader.canUpgrade(pack.format(), target)) {
            if (pack.metadata() != null && !pack.metadata().supports(target)) {
                reportSkipOnce(source, pack.metadata(), target);
            }
            return source;
        }
        boolean isZip = Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS);
        return convertCached(source, isZip, pack, target, kind, progress);
    }

    private static PackInput inspect(Path source) throws IOException {
        boolean zip = Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS);
        if (!zip && !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            PackInput pack = PackInput.inspect(source, zip);
            return pack.metadata() != null || pack.isLegacyTexturePack() ? pack : null;
        } catch (ZipException e) {
            // 资源包目录也可能只是普通文件；非 ZIP 交由原版处理。
            return null;
        }
    }

    /**
     * 服务器声明的内容 SHA-1 是否已经有对应的转换产物。
     *
     * <p>给 {@link ServerPackShortcut} 用：MC 1.14.4 – 1.20.2 的下载去重键是 **URL** 而不是
     * 内容哈希，URL 带 token 的服务器会让同一份包被反复下载。这里让它在下载之前就能问一句
     * 「这份内容我早转好了吗」。
     *
     * <p>只查 zip 形态：服务器包一定是单个文件。
     *
     * @return 转换产物路径；没有则 {@code null}
     */
    Path cachedForSourceSha1(String sourceSha1) {
        if (sourceSha1 == null || sourceSha1.isEmpty() || !config.enabled() || !config.convertServerPacks()) {
            return null;
        }
        PackFormat target = GameVersion.current().packFormat();
        if (!target.isKnown()) {
            return null;
        }
        try {
            prepareOnce();
            return cache.lookup(sourceSha1, true, target, ResourcePackUpgrader.RULES_VERSION);
        } catch (IOException e) {
            ViaRpLog.debug("Unable to prepare the resource pack cache", e);
            return null;
        }
    }

    private Path convertCached(Path source, boolean zip, PackInput pack, PackFormat target, PackKind kind,
                               ConversionBatch.Entry progress) throws IOException {
        prepareOnce();
        // 即使路径、大小和 mtime 相同，内容也可能被启动器或同步工具覆盖。
        String sha1 = PathIo.sha1(source);
        String key = ConversionCache.cacheKey(sha1, zip, target, ResourcePackUpgrader.RULES_VERSION);
        Path payload = cache.lookup(key, zip);
        if (payload != null) {
            return finish(progress, payload, ConversionBatch.Stage.CACHED);
        }
        CompletableFuture<Path> pending = new CompletableFuture<Path>();
        CompletableFuture<Path> other = inFlight.putIfAbsent(key, pending);
        if (other != null) {
            try {
                return finish(progress, other.join(), ConversionBatch.Stage.CACHED);
            } catch (CompletionException e) {
                Throwable failure = e.getCause();
                if (failure instanceof Error) {
                    throw (Error) failure;
                }
                throw new IOException("Equivalent resource pack conversion failed", failure);
            }
        }
        try {
            // 上次发布可能恰好在首次查缓存与取得写权限之间完成。
            payload = cache.lookup(key, zip);
            if (payload != null) {
                pending.complete(payload);
                return finish(progress, payload, ConversionBatch.Stage.CACHED);
            }
            payload = convert(source, zip, pack, target, key, sha1, PathIo.sizeOrMinusOne(source), kind, progress);
            pending.complete(payload);
            return finish(progress, payload, ConversionBatch.Stage.CONVERTED);
        } catch (IOException | RuntimeException | Error failure) {
            pending.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(key, pending);
        }
    }

    private static Path finish(ConversionBatch.Entry entry, Path result, ConversionBatch.Stage stage) {
        if (entry != null) {
            entry.finish(result, stage);
        }
        return result;
    }

    private Path convert(Path source, boolean zip, PackInput pack, PackFormat target, String key,
                         String sha1, long size, PackKind kind, ConversionBatch.Entry progress) throws IOException {
        if (progress != null) {
            progress.stage(ConversionBatch.Stage.READING);
        }
        Path work = Files.createTempDirectory(paths.workDir(), "job-");
        Path unpacked = work.resolve("pack");
        PackFormat from = pack.format();
        long started = System.currentTimeMillis();
        ViaRpLog.info("Upgrading {} pack {} from format {} to {}",
                kind == PackKind.LOCAL ? "local" : "server", source.getFileName(), from, target);
        try {
            if (zip) {
                ZipTool.extract(source, unpacked);
            } else {
                PathIo.copyDirectory(source, unpacked);
                if (!sha1.equals(PathIo.sha1(unpacked))) {
                    throw new IOException("Resource pack changed during the directory copy");
                }
            }
            PackInput snapshot = PackInput.inspect(unpacked, false);
            if (!from.equals(snapshot.format()) || !snapshot.canUpgradeTo(target)) {
                throw new IOException("Resource pack changed while preparing the upgrade");
            }
            if (progress != null) {
                progress.stage(ConversionBatch.Stage.MIGRATING);
            }
            List<String> warnings = ResourcePackUpgrader.upgrade(unpacked, snapshot, target, config.verbose());
            if (progress != null) {
                progress.stage(ConversionBatch.Stage.SAVING);
            }
            Path destination = cache.payloadPath(key, zip);
            if (zip) {
                Path packed = work.resolve("packed.zip");
                ZipTool.pack(unpacked, packed);
                if (!sha1.equals(PathIo.sha1(source))) {
                    throw new IOException("Resource pack changed during ZIP extraction");
                }
                PathIo.move(packed, destination);
            } else {
                PathIo.move(unpacked, destination);
            }
            Path diagnostics = destination.resolveSibling("warnings.txt");
            if (!warnings.isEmpty()) {
                Path report = work.resolve("warnings.txt");
                PathIo.writeString(report, String.join("\n", warnings) + "\n");
                PathIo.move(report, diagnostics);
            }
            cache.writeMeta(key, new CacheEntryMeta(
                    source.toAbsolutePath().normalize().toString(), sha1, size,
                    from.major(), from.minor(), target.major(), target.minor(),
                    System.currentTimeMillis(), zip ? PathIo.sizeOrMinusOne(destination) : -1L,
                    zip, ResourcePackUpgrader.RULES_VERSION));

            if (!warnings.isEmpty()) {
                for (int i = 0; i < Math.min(MAX_LOGGED_WARNINGS, warnings.size()); i++) {
                    ViaRpLog.warn("Resource pack {}: {}", source.getFileName(), warnings.get(i));
                }
                ViaRpLog.warn("Resource pack {}: recorded migration diagnostics in {}", source.getFileName(), diagnostics);
            }
            ViaRpLog.info("Upgraded {} in {} ms -> {}", source.getFileName(),
                    System.currentTimeMillis() - started, destination);
            return destination;
        } finally {
            PathIo.deleteQuietly(work);
        }
    }

    private void reportSkipOnce(Path source, PackMetadata metadata, PackFormat target) {
        String id = source.toAbsolutePath().normalize() + "|" + metadata.supportedFormats() + "|" + target;
        synchronized (reportedSkips) {
            if (!reportedSkips.add(id)) {
                return;
            }
        }
        ViaRpLog.warn("Not upgrading {}: declared formats {}, current format {}; only known upward routes are supported",
                source.getFileName(), metadata.supportedFormats(), target);
    }

    private synchronized void prepareOnce() throws IOException {
        if (!prepared) {
            // 只建目录，不清理：占位包此刻可能正被原版持有（见 ViaRpPaths.prepare 的注释），
            // 启动时的那次清理由 ViaRpClient 负责。
            paths.ensureDirectories();
            // 仅在本次运行尚无装载产物时淘汰；不能删除原版可能正持有的文件。
            cache.prune();
            prepared = true;
        }
    }

    private void reportFailureOnce(Path source, Throwable failure) {
        String id = source.toAbsolutePath().normalize().toString();
        boolean fresh;
        synchronized (reportedFailures) {
            fresh = reportedFailures.add(id);
        }
        if (fresh) {
            ViaRpLog.error("Failed to convert {}; loading it unchanged", source, failure);
        }
    }
}
