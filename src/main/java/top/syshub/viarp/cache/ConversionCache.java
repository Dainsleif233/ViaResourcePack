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
package top.syshub.viarp.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.io.PathIo;
import top.syshub.viarp.pack.PackFormat;

/**
 * 转换产物缓存目录。
 *
 * <p>布局：
 * <pre>
 *   cache/v2-&lt;sourceSha1&gt;-&lt;zip|dir&gt;-&lt;targetMajor&gt;-&lt;targetMinor&gt;-&lt;rulesFingerprint&gt;/
 *       pack.zip | pack/        转换产物（源是 zip 则为 zip，源是目录则为目录）
 *       meta.json              见 {@link CacheEntryMeta}
 * </pre>
 *
 * <p>源文件继续使用 SHA-1，以支持旧版服务器包下载去重；目标格式和规则版本隔离不同转换产物。
 */
public final class ConversionCache {

    private static final String ZIP_PAYLOAD = "pack.zip";
    private static final String DIR_PAYLOAD = "pack";

    private final Path cacheDir;
    private final long maxBytes;

    public ConversionCache(Path cacheDir, long maxBytes) {
        this.cacheDir = cacheDir;
        this.maxBytes = maxBytes;
    }

    /** rulesVersion 必须随转换引擎或其他影响产物的规则变化。 */
    public static String cacheKey(String sourceSha1, boolean zip, PackFormat target, String rulesVersion) {
        return CacheKey.create(sourceSha1, zip, target, rulesVersion);
    }

    public Path entryDir(String cacheKey) {
        if (CacheKey.parse(cacheKey) == null) {
            throw new IllegalArgumentException("Invalid conversion cache key");
        }
        return cacheDir.resolve(cacheKey);
    }

    /** 用外部提供的源 SHA-1 查完整缓存键；非法哈希视为未命中。 */
    public Path lookup(String sourceSha1, boolean zip, PackFormat target, String rulesVersion) {
        String cacheKey;
        try {
            cacheKey = cacheKey(sourceSha1, zip, target, rulesVersion);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return lookup(cacheKey, zip);
    }

    /** 缓存里可用的产物路径；键、元数据或产物形态不匹配则返回 {@code null}。 */
    public Path lookup(String cacheKey, boolean wantZip) {
        CacheKey parsed = CacheKey.parse(cacheKey);
        if (parsed == null || parsed.zip != wantZip) {
            return null;
        }
        Path dir = cacheDir.resolve(cacheKey);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            if (!isDirectChild(cacheDir, dir) || !cacheKey.equals(CacheEntryMeta.read(dir).entryKey())) {
                return null;
            }
            Path payload = dir.resolve(wantZip ? ZIP_PAYLOAD : DIR_PAYLOAD);
            boolean rightShape = wantZip ? Files.isRegularFile(payload, LinkOption.NOFOLLOW_LINKS)
                    : Files.isDirectory(payload, LinkOption.NOFOLLOW_LINKS);
            if (rightShape && isDirectChild(dir, payload)) {
                return payload;
            }
        } catch (IOException e) {
            // 这里只报告未命中，不碰可能正由别的线程发布的产物。
            ViaRpLog.debug("Ignoring unusable cache entry {}", dir, e);
        }
        return null;
    }

    /** 目标产物应该写到的位置（zip 或目录）。 */
    public Path payloadPath(String cacheKey, boolean zip) throws IOException {
        CacheKey parsed = CacheKey.parse(cacheKey);
        if (parsed == null || parsed.zip != zip) {
            throw new IllegalArgumentException("Invalid conversion cache key or payload kind");
        }
        Path dir = cacheDir.resolve(cacheKey);
        checkEntryDirectory(dir);
        Path payload = dir.resolve(zip ? ZIP_PAYLOAD : DIR_PAYLOAD);
        if (Files.exists(payload, LinkOption.NOFOLLOW_LINKS) && !isDirectChild(dir, payload)) {
            throw new IOException("Cache payload is not a direct child of " + dir);
        }
        return payload;
    }

    /** 把 [meta] 写进缓存记录目录。 */
    public void writeMeta(String cacheKey, CacheEntryMeta meta) throws IOException {
        Path dir = entryDir(cacheKey);
        checkEntryDirectory(dir);
        meta.write(dir);
    }

    private void checkEntryDirectory(Path dir) throws IOException {
        if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || !isDirectChild(cacheDir, dir))) {
            throw new IOException("Cache entry is not a direct directory under " + cacheDir);
        }
    }

    private static boolean isDirectChild(Path parent, Path child) throws IOException {
        return child.toRealPath().equals(parent.toRealPath().resolve(child.getFileName()));
    }

    /**
     * 把缓存裁剪到 {@link #maxBytes} 以内，按 {@code createdAt} 从旧到新淘汰。
     *
     * <p>刻意用 createdAt 而不是文件访问时间：很多文件系统默认关闭 atime 更新，
     * 拿不到可靠的「最后使用」时间。
     */
    public void prune() {
        if (maxBytes <= 0 || !Files.isDirectory(cacheDir)) {
            return;
        }
        List<Candidate> candidates = new ArrayList<Candidate>();
        long total = 0;
        List<Path> dirs = new ArrayList<Path>();
        try (java.util.stream.Stream<Path> stream = Files.list(cacheDir)) {
            for (Path path : stream.collect(java.util.stream.Collectors.toList())) {
                String key = path.getFileName().toString();
                if ((CacheKey.parse(key) != null || CacheKey.isLegacy(key))
                        && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        && isDirectChild(cacheDir, path)) {
                    dirs.add(path);
                }
            }
        } catch (IOException e) {
            ViaRpLog.warn("Could not enumerate cache directory {}", cacheDir, e);
            return;
        }
        for (Path dir : dirs) {
            long size = directorySize(dir);
            long createdAt;
            try {
                CacheEntryMeta meta = CacheEntryMeta.read(dir);
                createdAt = dir.getFileName().toString().equals(meta.entryKey()) ? meta.createdAt : 0L;
            } catch (IOException e) {
                // 元数据坏掉的目录优先淘汰。
                createdAt = 0L;
            }
            candidates.add(new Candidate(dir, size, createdAt));
            total += size;
        }

        if (total <= maxBytes) {
            return;
        }

        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Long.compare(a.createdAt, b.createdAt);
            }
        });

        long freed = 0;
        for (Candidate candidate : candidates) {
            if (total - freed <= maxBytes) {
                break;
            }
            PathIo.deleteQuietly(candidate.dir);
            freed += candidate.size;
            ViaRpLog.info("Pruned cached conversion {} ({} MiB)",
                    candidate.dir.getFileName(), candidate.size / (1024 * 1024));
        }
    }

    private static long directorySize(Path dir) {
        final long[] total = {0L};
        try {
            Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(
                        Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    total[0] += attrs.size();
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // 统计不到就按已累加的算，裁剪本来就是尽力而为。
        }
        return total[0];
    }

    private static final class Candidate {
        final Path dir;
        final long size;
        final long createdAt;

        Candidate(Path dir, long size, long createdAt) {
            this.dir = dir;
            this.size = size;
            this.createdAt = createdAt;
        }
    }
}
