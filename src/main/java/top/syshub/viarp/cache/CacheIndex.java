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

import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.io.PathIo;
import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;

/**
 * 「源包 → 缓存键」索引。
 *
 * <p>存在的意义是**免哈希**：绝对路径 + 大小 + mtime 全部匹配时直接复用缓存，
 * 不必为每个资源包重新算一遍 SHA-1（大包动辄几百 MB，每次启动都算会明显拖慢加载）。
 * 任何一项不匹配就退化到算哈希，因此索引只是加速器，不是正确性来源。
 */
public final class CacheIndex {

    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
    private boolean dirty;

    public CacheIndex(Path file) {
        this.file = file;
    }

    /** 一条索引记录。 */
    public static final class Entry {
        public final String key;
        public final long size;
        public final long modified;
        public final long usedAt;

        Entry(String key, long size, long modified, long usedAt) {
            this.key = key;
            this.size = size;
            this.modified = modified;
            this.usedAt = usedAt;
        }
    }

    public synchronized void load() {
        entries.clear();
        dirty = false;
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Map<String, Object> root = Json.parseObject(PathIo.readString(file));
            Map<String, Object> packs = JsonAccess.object(root, "packs");
            if (packs == null) {
                return;
            }
            for (Map.Entry<String, Object> raw : packs.entrySet()) {
                if (!(raw.getValue() instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> value = (Map<String, Object>) raw.getValue();
                String key = JsonAccess.string(value, "key", null);
                if (key == null) {
                    continue;
                }
                entries.put(raw.getKey(), new Entry(
                        key,
                        readLong(value, "size"),
                        readLong(value, "modified"),
                        readLong(value, "usedAt")));
            }
        } catch (IOException e) {
            // 索引损坏不是致命问题：清空后重建，代价只是下次要重新算哈希。
            ViaRpLog.warn("Cache index {} is unreadable; rebuilding it", file, e);
            entries.clear();
        }
    }

    /** 查 [source] 对应的缓存键；大小/mtime 不匹配视为未命中。 */
    public synchronized String lookup(Path source, long size, long modified) {
        Entry entry = entries.get(keyOf(source));
        if (entry == null) {
            return null;
        }
        if (entry.size != size || entry.modified != modified) {
            return null;
        }
        return entry.key;
    }

    public synchronized void put(Path source, long size, long modified, String cacheKey) {
        entries.put(keyOf(source), new Entry(cacheKey, size, modified, System.currentTimeMillis()));
        dirty = true;
    }

    /** 刷新某条记录的访问时间（供 LRU 裁剪用）。 */
    public synchronized void touch(Path source) {
        String id = keyOf(source);
        Entry entry = entries.get(id);
        if (entry != null) {
            entries.put(id, new Entry(entry.key, entry.size, entry.modified, System.currentTimeMillis()));
            dirty = true;
        }
    }

    /** 删除所有指向 [cacheKey] 的记录（缓存目录被裁剪后调用）。 */
    public synchronized void forgetKey(String cacheKey) {
        boolean removed = entries.values().removeIf(new java.util.function.Predicate<Entry>() {
            @Override
            public boolean test(Entry entry) {
                return entry.key.equals(cacheKey);
            }
        });
        dirty = dirty || removed;
    }

    public synchronized void saveIfDirty() {
        if (!dirty) {
            return;
        }
        Map<String, Object> packs = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("key", entry.getValue().key);
            value.put("size", Long.valueOf(entry.getValue().size));
            value.put("modified", Long.valueOf(entry.getValue().modified));
            value.put("usedAt", Long.valueOf(entry.getValue().usedAt));
            packs.put(entry.getKey(), value);
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("version", Integer.valueOf(1));
        root.put("packs", packs);
        try {
            PathIo.writeString(file, Json.write(root));
            dirty = false;
        } catch (IOException e) {
            ViaRpLog.warn("Could not write cache index {}", file, e);
        }
    }

    private static String keyOf(Path source) {
        return source.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static long readLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).longValue() : -1L;
    }
}
