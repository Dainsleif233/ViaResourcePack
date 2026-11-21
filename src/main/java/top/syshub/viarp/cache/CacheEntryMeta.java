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
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import top.syshub.viarp.io.PathIo;
import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;
import top.syshub.viarp.pack.PackFormat;

/** 一条缓存记录的元数据（{@code cache/<key>/meta.json}）。 */
public final class CacheEntryMeta {

    private static final String FILE_NAME = "meta.json";

    /** 缓存格式版本；结构变更时递增，旧目录会被视为不可用。 */
    public static final int FORMAT_VERSION = 2;

    public final String sourcePath;
    public final String sourceSha1;
    public final long sourceSize;
    public final int fromMajor;
    public final int fromMinor;
    public final int toMajor;
    public final int toMinor;
    public final long createdAt;
    public final long payloadSize;
    public final boolean zip;
    public final String rulesVersion;

    public CacheEntryMeta(String sourcePath, String sourceSha1, long sourceSize,
                          int fromMajor, int fromMinor, int toMajor, int toMinor,
                          long createdAt, long payloadSize,
                          boolean zip, String rulesVersion) {
        this.sourcePath = sourcePath;
        this.sourceSha1 = sourceSha1;
        this.sourceSize = sourceSize;
        this.fromMajor = fromMajor;
        this.fromMinor = fromMinor;
        this.toMajor = toMajor;
        this.toMinor = toMinor;
        this.createdAt = createdAt;
        this.payloadSize = payloadSize;
        this.zip = zip;
        this.rulesVersion = rulesVersion;
    }

    public static CacheEntryMeta read(Path dir) throws IOException {
        Path file = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing " + FILE_NAME + " in " + dir);
        }
        Map<String, Object> root = Json.parseObject(PathIo.readString(file));
        long version = readLong(root, "formatVersion");
        if (version != FORMAT_VERSION) {
            throw new IOException("Cache entry " + dir + " has format version " + version
                    + ", expected " + FORMAT_VERSION);
        }
        String kind = JsonAccess.string(root, "inputKind", "");
        if (!"zip".equals(kind) && !"dir".equals(kind)) {
            throw new IOException("Invalid cache input kind in " + dir);
        }
        return new CacheEntryMeta(
                JsonAccess.string(root, "sourcePath", ""),
                JsonAccess.string(root, "sourceSha1", ""),
                readLong(root, "sourceSize"),
                readFormatNumber(root, "fromMajor"),
                readFormatNumber(root, "fromMinor"),
                readFormatNumber(root, "toMajor"),
                readFormatNumber(root, "toMinor"),
                readLong(root, "createdAt"),
                readLong(root, "payloadSize"),
                "zip".equals(kind),
                JsonAccess.string(root, "rulesVersion", null));
    }

    /** 从元数据还原完整键；读到缺失或不合法的关键字段时不能复用缓存。 */
    String entryKey() throws IOException {
        if (fromMajor < 0 || fromMinor < 0) {
            throw new IOException("Missing source format in cache metadata");
        }
        try {
            return CacheKey.create(sourceSha1, zip, PackFormat.of(toMajor, toMinor), rulesVersion);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid conversion identity in cache metadata", e);
        }
    }

    public void write(Path dir) throws IOException {
        String key = entryKey();
        if (dir.getFileName() == null || !key.equals(dir.getFileName().toString())) {
            throw new IOException("Cache metadata does not match entry directory " + dir);
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("formatVersion", Integer.valueOf(FORMAT_VERSION));
        root.put("sourcePath", sourcePath);
        root.put("sourceSha1", CacheKey.normalizeSha1(sourceSha1));
        root.put("sourceSize", Long.valueOf(sourceSize));
        root.put("inputKind", zip ? "zip" : "dir");
        root.put("fromMajor", Integer.valueOf(fromMajor));
        root.put("fromMinor", Integer.valueOf(fromMinor));
        root.put("toMajor", Integer.valueOf(toMajor));
        root.put("toMinor", Integer.valueOf(toMinor));
        root.put("rulesVersion", rulesVersion);
        root.put("createdAt", Long.valueOf(createdAt));
        root.put("payloadSize", Long.valueOf(payloadSize));

        Files.createDirectories(dir);
        Path file = dir.resolve(FILE_NAME);
        Path temp = Files.createTempFile(dir, ".meta-", ".tmp");
        try {
            Files.write(temp, Json.write(root).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static long readLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Long ? ((Long) value).longValue() : -1L;
    }

    private static int readFormatNumber(Map<String, Object> map, String key) throws IOException {
        long value = readLong(map, key);
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IOException("Invalid " + key + " in cache metadata");
        }
        return (int) value;
    }
}
