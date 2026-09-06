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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.io.PathIo;

/**
 * 转换产物缓存目录。
 *
 * <p>布局：
 * <pre>
 *   cache/&lt;sha1-of-source&gt;-&lt;targetMajor&gt;/
 *       pack.zip | pack/        转换产物（源是 zip 则为 zip，源是目录则为目录）
 *       meta.json              见 {@link CacheEntryMeta}
 * </pre>
 *
 * <p>缓存键里带目标 major：同一个源包在不同游戏版本下的产物互不覆盖，玩家在
 * 1.20 与 1.21 之间来回切换时两份产物都留着。
 */
public final class ConversionCache {

    private static final String ZIP_PAYLOAD = "pack.zip";
    private static final String DIR_PAYLOAD = "pack";

    private final Path cacheDir;
    private final CacheIndex index;
    private final long maxBytes;

    public ConversionCache(Path cacheDir, CacheIndex index, long maxBytes) {
        this.cacheDir = cacheDir;
        this.index = index;
        this.maxBytes = maxBytes;
    }

    public CacheIndex index() {
        return index;
    }

    public static String cacheKey(String sourceSha1, int targetMajor) {
        return sourceSha1 + "-" + targetMajor;
    }

    public Path entryDir(String cacheKey) {
        return cacheDir.resolve(cacheKey);
    }

    /**
     * 缓存里可用的产物路径；不存在或元数据不匹配则返回 {@code null}。
     *
     * @param wantZip 期望的产物形态（源是 zip 就要 zip，源是目录就要目录）
     */
    public Path lookup(String cacheKey, boolean wantZip) {
        Path dir = entryDir(cacheKey);
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try {
            // 元数据读不动（版本不符、损坏）就当缓存不可用，重新转换。
            CacheEntryMeta.read(dir);
        } catch (IOException e) {
            ViaRpLog.debug("Discarding unusable cache entry {}", dir, e);
            PathIo.deleteQuietly(dir);
            return null;
        }
        Path payload = dir.resolve(wantZip ? ZIP_PAYLOAD : DIR_PAYLOAD);
        if (wantZip ? Files.isRegularFile(payload) : Files.isDirectory(payload)) {
            return payload;
        }
        return null;
    }

    /** 目标产物应该写到的位置（zip 或目录）。 */
    public Path payloadPath(String cacheKey, boolean zip) {
        return entryDir(cacheKey).resolve(zip ? ZIP_PAYLOAD : DIR_PAYLOAD);
    }

    /** 把 [meta] 写进缓存记录目录。 */
    public void writeMeta(String cacheKey, CacheEntryMeta meta) throws IOException {
        Path dir = entryDir(cacheKey);
        Files.createDirectories(dir);
        meta.write(dir);
    }

    /**
     * 把缓存裁剪到 {@link #maxBytes} 以内，按 {@code createdAt} 从旧到新淘汰。
     *
     * <p>刻意用 createdAt 而不是文件访问时间：很多文件系统默认关闭 atime 更新，
     * 拿不到可靠的「最后使用」时间；而索引里的 usedAt 只覆盖被索引到的源包。
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
                if (Files.isDirectory(path)) {
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
                createdAt = CacheEntryMeta.read(dir).createdAt;
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
            index.forgetKey(candidate.dir.getFileName().toString());
            freed += candidate.size;
            ViaRpLog.info("Pruned cached conversion {} ({} MiB)",
                    candidate.dir.getFileName(), candidate.size / (1024 * 1024));
        }
        index.saveIfDirty();
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
