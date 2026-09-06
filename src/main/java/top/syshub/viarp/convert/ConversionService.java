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
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import top.syshub.viarp.ViaRpConfig;
import top.syshub.viarp.ViaRpPaths;
import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.cache.CacheEntryMeta;
import top.syshub.viarp.cache.CacheIndex;
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

    private static volatile ConversionService instance;

    private final ViaRpPaths paths;
    private final ViaRpConfig config;
    private final ConversionCache cache;
    private final PackSelection selection;
    private final PackPlaceholder placeholder;
    private final AtomicLong workCounter = new AtomicLong();

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
                    CacheIndex index = new CacheIndex(paths.indexFile());
                    index.load();
                    existing = new ConversionService(paths, config,
                            new ConversionCache(paths.cacheDir(), index, config.maxCacheBytes()),
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
     * 若 [source] 与当前游戏版本不匹配，返回转换后的资源包路径；否则原样返回 [source]。
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
        } catch (RuntimeException e) {
            // 拦截点在原版调用栈里，抛异常会直接崩加载流程，所以这里兜住一切。
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
     * 本地包专用入口：{@code resourcepacks/} 目录下的直接子项。
     *
     * <p>三种结果：
     *
     * <ol>
     *   <li><b>不是资源包</b>（父目录不对、读不出元数据、已兼容）→ 原样放行。
     *       1.20.2+ 的拦截点 {@code PackDetector.detectPackResources} 是所有本地包
     *       （资源包、数据包、世界内置包）的公共漏斗，它本身不知道自己在探测哪一类；
     *       靠「父目录必须等于客户端 resourcepacks 目录」把数据包排除掉，比读调用栈或猜
     *       {@code PackType} 都稳。</li>
     *   <li><b>版本不匹配、已在 {@code options.txt} 里启用</b> → 完整转换。</li>
     *   <li><b>版本不匹配、尚未启用</b> → 换成占位包（见 {@link PackPlaceholder}）。</li>
     * </ol>
     *
     * <p>第 3 条同时解决两件事。其一是**时机**：这个拦截点在「打开资源包界面」时也会对
     * 目录里每一项跑一遍（{@code PackSelectionModel.findNewPacks} →
     * {@code PackRepository.reload}），无条件转换会让玩家只是进去看一眼就把目录里所有旧包
     * 全转一遍。其二是**可见性**：1.21.9+ 的包可能只写 {@code min_format} / {@code max_format}，
     * 老客户端读不出 {@code pack_format} 会直接把整个包从列表里丢掉，玩家根本没机会勾选它。
     *
     * <p>启用状态来自 {@link PackSelection}（读 {@code options.txt}）：玩家在界面里点「完成」
     * 后原版先 {@code save()} 再 {@code reloadResourcePacks()}，所以真正要加载时这个列表已经
     * 是新的。
     */
    public Path resolveLocal(Path source) {
        if (source == null || !paths.isInResourcePacksDir(source)) {
            return source;
        }
        try {
            return resolveLocalChecked(source);
        } catch (RuntimeException e) {
            reportFailureOnce(source, e);
            return source;
        }
    }

    private Path resolveLocalChecked(Path source) {
        if (!config.enabled() || !config.convertLocalPacks() || paths.isOwnOutput(source)) {
            return source;
        }

        PackFormat target = GameVersion.current().packFormat();
        if (!target.isKnown()) {
            return source;
        }

        boolean isZip = Files.isRegularFile(source);
        if (!isZip && !Files.isDirectory(source)) {
            return source;
        }

        PackMetadata metadata = readMetadata(source, isZip);
        if (metadata == null || metadata.supports(target.major())) {
            // 读不到 pack.mcmeta 的不是资源包；已兼容的不需要我们插手。
            return source;
        }

        if (isEnabled(source)) {
            return resolve(source, PackKind.LOCAL);
        }
        if (metadata.hasLegacyPackFormat()) {
            // 本版本能读出它的 pack_format，原版会正常列出（标记为不兼容），不必伪装。
            return source;
        }
        return placeholder.create(source, isZip, metadata, target);
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

    private Path resolveChecked(Path source, PackKind kind) {
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

        boolean isZip = Files.isRegularFile(source);
        if (!isZip && !Files.isDirectory(source)) {
            return source;
        }

        PackMetadata metadata = readMetadata(source, isZip);
        if (metadata == null) {
            // 读不到 pack.mcmeta 的东西不是资源包（或已损坏），交给原版处理。
            return source;
        }
        if (metadata.supports(target.major())) {
            return source;
        }

        PackFormat from = metadata.primaryFormat();
        if (!from.isKnown()) {
            ViaRpLog.warn("{} declares no usable pack_format; leaving it untouched", source);
            return source;
        }

        ConverterEngine engine = ConverterEngines.engine();
        if (engine == null) {
            return source;
        }

        try {
            return convertCached(source, isZip, from, target, engine, kind);
        } catch (IOException e) {
            reportFailureOnce(source, e);
            return source;
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
        return cache.lookup(ConversionCache.cacheKey(sourceSha1.toLowerCase(java.util.Locale.ROOT),
                target.major()), true);
    }

    private Path convertCached(Path source, boolean isZip, PackFormat from, PackFormat target,
                               ConverterEngine engine, PackKind kind) throws IOException {        long size = PathIo.sizeOrMinusOne(source);
        long modified = PathIo.lastModifiedOrMinusOne(source);
        CacheIndex index = cache.index();

        // 1. 路径 + 大小 + mtime 命中：完全不必算哈希。
        String cachedKey = index.lookup(source, size, modified);
        if (cachedKey != null) {
            Path payload = cache.lookup(cachedKey, isZip);
            if (payload != null) {
                index.touch(source);
                index.saveIfDirty();
                return payload;
            }
        }

        // 2. 算哈希后再查一次：同一个包换了路径 / 换了 mtime 也能命中。
        String sha1 = PathIo.sha1(source);
        String key = ConversionCache.cacheKey(sha1, target.major());
        Path payload = cache.lookup(key, isZip);
        if (payload != null) {
            index.put(source, size, modified, key);
            index.saveIfDirty();
            return payload;
        }

        // 3. 真正转换。
        synchronized (this) {
            // 拿到锁后再查一次：可能有别的线程刚转完同一个包。
            payload = cache.lookup(key, isZip);
            if (payload != null) {
                index.put(source, size, modified, key);
                index.saveIfDirty();
                return payload;
            }
            payload = convert(source, isZip, from, target, engine, key, sha1, size, kind);
        }
        index.put(source, size, modified, key);
        index.saveIfDirty();
        cache.prune();
        return payload;
    }

    private Path convert(Path source, boolean isZip, PackFormat from, PackFormat target,
                         ConverterEngine engine, String cacheKey, String sha1, long size,
                         PackKind kind) throws IOException {
        prepareOnce();

        Path work = paths.workDir().resolve("job-" + workCounter.incrementAndGet());
        PathIo.deleteQuietly(work);
        Files.createDirectories(work);
        Path unpacked = work.resolve("pack");

        long started = System.currentTimeMillis();
        ViaRpLog.info("Converting {} pack {} from format {} to {}",
                kind == PackKind.LOCAL ? "local" : "server", source.getFileName(), from, target);

        try {
            if (isZip) {
                ZipTool.extract(source, unpacked);
            } else {
                PathIo.copyDirectory(source, unpacked);
            }

            // RPC 覆盖不到的目标版本（>maxSupportedMajor）：转到它能转到的最高版本，
            // 剩下的差距只靠改写 pack.mcmeta 声明——见 docs/DESIGN.md §3.3。
            int clampedMajor = Math.min(target.major(), engine.maxSupportedMajor());
            int effectiveFromMajor = Math.max(from.major(), engine.minSupportedMajor());
            boolean metaOnly = clampedMajor == effectiveFromMajor;

            if (clampedMajor != target.major()) {
                ViaRpLog.info("Format {} is beyond the converter's range; converting {} to format {} "
                                + "(the highest it supports) and declaring {}",
                        target, source.getFileName(), Integer.valueOf(clampedMajor), target);
            }

            if (!metaOnly) {
                engine.convert(unpacked, PackFormat.of(effectiveFromMajor), PackFormat.of(clampedMajor),
                        config.verbose());
            } else {
                ViaRpLog.info("No converter step applies for {} ({} -> {}); "
                        + "only pack.mcmeta will be rewritten", source.getFileName(), from, target);
            }

            rewriteMetadata(unpacked, target);

            Path destination = cache.payloadPath(cacheKey, isZip);
            PathIo.deleteQuietly(destination);
            if (isZip) {
                Path packed = work.resolve("packed.zip");
                ZipTool.pack(unpacked, packed);
                PathIo.move(packed, destination);
            } else {
                PathIo.move(unpacked, destination);
            }

            long payloadSize = isZip
                    ? PathIo.sizeOrMinusOne(destination)
                    : -1L;
            cache.writeMeta(cacheKey, new CacheEntryMeta(
                    source.toAbsolutePath().normalize().toString(),
                    sha1,
                    size,
                    from.major(),
                    target.major(),
                    target.minor(),
                    engine.describe(),
                    metaOnly,
                    System.currentTimeMillis(),
                    payloadSize));

            ViaRpLog.info("Converted {} in {} ms -> {}",
                    source.getFileName(), System.currentTimeMillis() - started, destination);
            return destination;
        } finally {
            PathIo.deleteQuietly(work);
        }
    }

    /** 把转换产物的 {@code pack.mcmeta} 改写成目标格式（源包没有则补一份）。 */
    private void rewriteMetadata(Path unpacked, PackFormat target) throws IOException {
        Path file = unpacked.resolve("pack.mcmeta");
        if (!Files.isRegularFile(file)) {
            PathIo.writeString(file, PackMetadata.minimal(target, "Converted by ViaResourcePack"));
            return;
        }
        String text = PathIo.readString(file);
        try {
            PackMetadata metadata = PackMetadata.parse(text);
            PathIo.writeString(file, metadata.withFormat(target));
        } catch (IOException e) {
            // 转换器有可能写出我们读不回来的元数据；此时至少保证 pack_format 正确。
            ViaRpLog.warn("Rewriting pack.mcmeta from scratch for {}", unpacked, e);
            PathIo.writeString(file, PackMetadata.minimal(target, "Converted by ViaResourcePack"));
        }
    }

    private PackMetadata readMetadata(Path source, boolean isZip) {
        try {
            String text = isZip ? readZipEntry(source, "pack.mcmeta") : readDirEntry(source, "pack.mcmeta");
            if (text == null) {
                return null;
            }
            return PackMetadata.parse(text);
        } catch (IOException e) {
            ViaRpLog.debug("Could not read pack.mcmeta from {}", source, e);
            return null;
        }
    }

    private static String readDirEntry(Path dir, String name) throws IOException {
        Path file = dir.resolve(name);
        return Files.isRegularFile(file) ? PathIo.readString(file) : null;
    }

    private static String readZipEntry(Path zip, String name) throws IOException {
        java.util.zip.ZipFile file = new java.util.zip.ZipFile(zip.toFile());
        try {
            java.util.zip.ZipEntry entry = file.getEntry(name);
            if (entry == null) {
                return null;
            }
            java.io.InputStream in = file.getInputStream(entry);
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                PathIo.copyStream(in, out);
                return PathIo.stripBom(new String(out.toByteArray(), java.nio.charset.Charset.forName("UTF-8")));
            } finally {
                in.close();
            }
        } catch (java.util.zip.ZipException e) {
            // 不是有效 zip（或加密）：交给原版去报错。
            return null;
        } finally {
            file.close();
        }
    }

    private synchronized void prepareOnce() throws IOException {
        if (!prepared) {
            // 只建目录，不清理：占位包此刻可能正被原版持有（见 ViaRpPaths.prepare 的注释），
            // 启动时的那次清理由 ViaRpClient 负责。
            paths.ensureDirectories();
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
