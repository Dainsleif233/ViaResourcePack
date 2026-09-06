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
import java.util.LinkedHashMap;
import java.util.Map;

import top.syshub.viarp.io.PathIo;
import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;

/** 一条缓存记录的元数据（{@code cache/<key>/meta.json}）。 */
public final class CacheEntryMeta {

    private static final String FILE_NAME = "meta.json";

    /** 缓存格式版本；结构变更时递增，旧目录会被视为不可用。 */
    public static final int FORMAT_VERSION = 1;

    public final String sourcePath;
    public final String sourceSha1;
    public final long sourceSize;
    public final int fromMajor;
    public final int toMajor;
    public final int toMinor;
    public final String engine;
    public final boolean metaOnly;
    public final long createdAt;
    public final long payloadSize;

    public CacheEntryMeta(String sourcePath, String sourceSha1, long sourceSize,
                          int fromMajor, int toMajor, int toMinor,
                          String engine, boolean metaOnly, long createdAt, long payloadSize) {
        this.sourcePath = sourcePath;
        this.sourceSha1 = sourceSha1;
        this.sourceSize = sourceSize;
        this.fromMajor = fromMajor;
        this.toMajor = toMajor;
        this.toMinor = toMinor;
        this.engine = engine;
        this.metaOnly = metaOnly;
        this.createdAt = createdAt;
        this.payloadSize = payloadSize;
    }

    public static CacheEntryMeta read(Path dir) throws IOException {
        Path file = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Missing " + FILE_NAME + " in " + dir);
        }
        Map<String, Object> root = Json.parseObject(PathIo.readString(file));
        int version = JsonAccess.integer(root, "formatVersion", -1);
        if (version != FORMAT_VERSION) {
            throw new IOException("Cache entry " + dir + " has format version " + version
                    + ", expected " + FORMAT_VERSION);
        }
        return new CacheEntryMeta(
                JsonAccess.string(root, "sourcePath", ""),
                JsonAccess.string(root, "sourceSha1", ""),
                readLong(root, "sourceSize"),
                JsonAccess.integer(root, "fromMajor", -1),
                JsonAccess.integer(root, "toMajor", -1),
                JsonAccess.integer(root, "toMinor", 0),
                JsonAccess.string(root, "engine", "unknown"),
                Boolean.TRUE.equals(root.get("metaOnly")),
                readLong(root, "createdAt"),
                readLong(root, "payloadSize"));
    }

    public void write(Path dir) throws IOException {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("formatVersion", Integer.valueOf(FORMAT_VERSION));
        root.put("sourcePath", sourcePath);
        root.put("sourceSha1", sourceSha1);
        root.put("sourceSize", Long.valueOf(sourceSize));
        root.put("fromMajor", Integer.valueOf(fromMajor));
        root.put("toMajor", Integer.valueOf(toMajor));
        root.put("toMinor", Integer.valueOf(toMinor));
        root.put("engine", engine);
        root.put("metaOnly", Boolean.valueOf(metaOnly));
        root.put("createdAt", Long.valueOf(createdAt));
        root.put("payloadSize", Long.valueOf(payloadSize));
        PathIo.writeString(dir.resolve(FILE_NAME), Json.write(root));
    }

    private static long readLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).longValue() : -1L;
    }
}
