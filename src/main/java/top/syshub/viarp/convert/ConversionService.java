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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * 缓存命中、失败降级全在这里决定，因此七个适配器族共享同一套行为。
 *
 * <p>线程安全：{@code resolve} 会被资源重载线程与主线程调用。转换本身用
 * {@code synchronized} 串行化——同时转换两个大包只会互相抢 IO，没有收益，
 * 而串行让缓存写入不需要额外的文件锁。
 */
public final class ConversionService {

    private static final int MAX_LOGGED_WARNINGS = 8;

    private static volatile ConversionService instance;

    private final ViaRpPaths paths;
    private final ViaRpConfig config;
    private final ConversionCache cache;
    private final PackSelection selection;
    private final PackPlaceholder placeholder;
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
                            new ConversionCache(paths.cacheDir(), config.maxCacheBytes()),
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
        try {
            return resolveChecked(source, kind);
        } catch (IOException | RuntimeException e) {
            // 拦截点在原版调用栈里，转换失败时回退原路径。
            reportFailureOnce(source, e);
            return source;
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
            if (isEnabled(source)) {
                return resolveChecked(source, PackKind.LOCAL);
            }
            // 无 pack.mcmeta 的旧纹理包必须先能出现在界面，玩家才有机会启用它。
            PackFormat target = GameVersion.current().packFormat();
            PackInput pack = inspect(source);
            return pack != null && pack.isLegacyTexturePack() && ResourcePackUpgrader.canUpgrade(pack.format(), target)
                    ? placeholder.create(source, target) : source;
        } catch (IOException | RuntimeException e) {
            reportFailureOnce(source, e);
            return source;
        }
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
        ViaRpLog.debug("Not converting {} yet: it is not enabled in options.txt", name);
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

    private Path resolveChecked(Path source, PackKind kind) throws IOException {
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
        return convertCached(source, isZip, pack, target, kind);
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

    private synchronized Path convertCached(Path source, boolean zip, PackInput pack, PackFormat target,
                                            PackKind kind) throws IOException {
        prepareOnce();
        // 即使路径、大小和 mtime 相同，内容也可能被启动器或同步工具覆盖。
        String sha1 = PathIo.sha1(source);
        String key = ConversionCache.cacheKey(sha1, zip, target, ResourcePackUpgrader.RULES_VERSION);
        Path payload = cache.lookup(key, zip);
        return payload != null ? payload
                : convert(source, zip, pack, target, key, sha1, PathIo.sizeOrMinusOne(source), kind);
    }

    private Path convert(Path source, boolean zip, PackInput pack, PackFormat target, String key,
                         String sha1, long size, PackKind kind) throws IOException {
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
            List<String> warnings = ResourcePackUpgrader.upgrade(unpacked, snapshot, target, config.verbose());
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
