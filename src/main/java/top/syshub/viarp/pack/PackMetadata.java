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
package top.syshub.viarp.pack;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;
import top.syshub.viarp.json.JsonException;

/** 资源包格式声明：按原版读者的代际解释兼容范围，另提供安全的升版边界。 */
public final class PackMetadata {

    // 23w31a (16) 开始读取 supported_formats；25w31a (65.0) 切到 major/minor。
    private static final int FIRST_LEGACY_RANGE = 16;
    private static final int LAST_LEGACY_FORMAT = 64;
    private static final int MIN_RANGE_FALLBACK = 15;
    private static final PackFormatRange UNKNOWN = PackFormatRange.single(PackFormat.UNKNOWN);

    private static final String KEY_PACK = "pack";
    private static final String KEY_PACK_FORMAT = "pack_format";
    private static final String KEY_SUPPORTED_FORMATS = "supported_formats";
    private static final String KEY_MIN_FORMAT = "min_format";
    private static final String KEY_MAX_FORMAT = "max_format";
    private static final String KEY_MIN_INCLUSIVE = "min_inclusive";
    private static final String KEY_MAX_INCLUSIVE = "max_inclusive";
    private static final String KEY_DESCRIPTION = "description";

    private final Map<String, Object> root;
    private final PackFormat legacyFormat;
    private final PackFormatRange legacyRange;
    private final PackFormatRange supported;

    private PackMetadata(Map<String, Object> root, PackFormat legacyFormat,
                         PackFormatRange legacyRange, PackFormatRange supported) {
        this.root = root;
        this.legacyFormat = legacyFormat;
        this.legacyRange = legacyRange;
        this.supported = supported;
    }

    /** 解析一份 {@code pack.mcmeta}；错误或自相矛盾的格式声明不构成升级依据。 */
    public static PackMetadata parse(String text) throws JsonException {
        Map<String, Object> root = Json.parseObject(text);
        Map<String, Object> pack = JsonAccess.object(root, KEY_PACK);
        if (pack == null) {
            return new PackMetadata(root, PackFormat.UNKNOWN, UNKNOWN, UNKNOWN);
        }
        PackFormat legacy = PackFormat.of(nonNegativeInt(pack.get(KEY_PACK_FORMAT)));
        PackFormatRange range = readLegacyRange(pack.get(KEY_SUPPORTED_FORMATS));
        PackFormatRange legacyRange = range.contains(legacy) ? range : PackFormatRange.single(legacy);
        return new PackMetadata(root, legacy, legacyRange, readDeclaredRange(pack, legacy, range));
    }

    /** 源包声明的格式闭区间；无可信声明时两端均为 UNKNOWN。 */
    public PackFormatRange supportedFormats() {
        return supported;
    }

    /** 旧主声明或现代区间下界，作为迁移基准；不能拿它代替整个兼容区间。 */
    public PackFormat primaryFormat() {
        return supported.contains(legacyFormat) ? legacyFormat : supported.min();
    }

    public boolean hasFormat() {
        return supported.min().isKnown();
    }

    /** 按目标客户端实际使用的格式声明判断兼容；不把元数据兼容等同于素材兼容。 */
    public boolean supports(PackFormat target) {
        if (!target.isKnown()) {
            return false;
        }
        if (target.major() < FIRST_LEGACY_RANGE) {
            return target.equals(legacyFormat);
        }
        return (target.major() <= LAST_LEGACY_FORMAT ? legacyRange : supported).contains(target);
    }

    /** 只允许目标严格超过源包声明上界；不能用降版来补偿旧客户端无法读取新声明。 */
    public boolean canUpgradeTo(PackFormat target) {
        return target.isKnown() && hasFormat() && target.compareTo(supported.max()) > 0 && !supports(target);
    }

    /** 是否有旧式、可读的单个整型 {@code pack_format} 字段。 */
    public boolean hasLegacyPackFormat() {
        return legacyFormat.isKnown();
    }

    public String description(String fallback) {
        Map<String, Object> pack = JsonAccess.object(root, KEY_PACK);
        return JsonAccess.string(pack, KEY_DESCRIPTION, fallback);
    }

    /** 只改写格式声明；迁移 overlays、filter 或素材是调用者的职责。 */
    public String withFormat(PackFormat target) {
        Map<String, Object> newRoot = new LinkedHashMap<String, Object>(root);
        Map<String, Object> pack = JsonAccess.object(root, KEY_PACK);
        Map<String, Object> newPack = pack == null
                ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(pack);
        newPack.remove(KEY_PACK_FORMAT);
        newPack.remove(KEY_SUPPORTED_FORMATS);
        newPack.remove(KEY_MIN_FORMAT);
        newPack.remove(KEY_MAX_FORMAT);
        writeSingleFormat(newPack, target);
        if (newPack.get(KEY_DESCRIPTION) == null) {
            newPack.put(KEY_DESCRIPTION, "Converted by ViaResourcePack");
        }
        newRoot.put(KEY_PACK, newPack);
        return Json.write(newRoot);
    }

    /** 生成一份最小可用的目标格式声明。 */
    public static String minimal(PackFormat target, String description) {
        return minimalDescription(target, description);
    }

    /** 占位包只含目标格式、原始文本组件；不能引用不存在的 overlays。 */
    public String stub(PackFormat target, String fallback) {
        Map<String, Object> pack = JsonAccess.object(root, KEY_PACK);
        Object description = pack == null ? null : pack.get(KEY_DESCRIPTION);
        return minimalDescription(target, description != null ? description : fallback);
    }

    private static String minimalDescription(PackFormat target, Object description) {
        Map<String, Object> pack = new LinkedHashMap<String, Object>();
        writeSingleFormat(pack, target);
        pack.put(KEY_DESCRIPTION, description);
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put(KEY_PACK, pack);
        return Json.write(root);
    }

    private static void writeSingleFormat(Map<String, Object> pack, PackFormat target) {
        if (!target.isKnown()) {
            throw new IllegalArgumentException("Unknown resource pack format");
        }
        if (target.major() <= LAST_LEGACY_FORMAT) {
            if (target.minor() != 0) {
                throw new IllegalArgumentException("Legacy pack formats have no minor: " + target);
            }
            pack.put(KEY_PACK_FORMAT, Integer.valueOf(target.major()));
        } else {
            List<Integer> exact = Arrays.asList(Integer.valueOf(target.major()), Integer.valueOf(target.minor()));
            pack.put(KEY_MIN_FORMAT, exact);
            pack.put(KEY_MAX_FORMAT, exact);
        }
    }

    private static PackFormatRange readDeclaredRange(Map<String, Object> pack, PackFormat legacy,
                                                     PackFormatRange legacyRange) {
        boolean hasLegacy = pack.containsKey(KEY_PACK_FORMAT);
        boolean hasLegacyRange = pack.containsKey(KEY_SUPPORTED_FORMATS);
        if ((hasLegacy && !legacy.isKnown()) || (hasLegacyRange && !legacyRange.min().isKnown())) {
            return UNKNOWN;
        }
        if (!pack.containsKey(KEY_MIN_FORMAT) && !pack.containsKey(KEY_MAX_FORMAT)) {
            if (!legacy.isKnown() || legacy.major() > LAST_LEGACY_FORMAT) {
                return UNKNOWN;
            }
            if (!hasLegacyRange) {
                return PackFormatRange.single(legacy);
            }
            if (legacyRange.max().major() > LAST_LEGACY_FORMAT
                    || !legacyRange.contains(legacy) || legacy.major() < MIN_RANGE_FALLBACK) {
                return UNKNOWN;
            }
            return legacyRange;
        }

        PackFormat min = readBound(pack.get(KEY_MIN_FORMAT), 0);
        PackFormat max = readBound(pack.get(KEY_MAX_FORMAT), PackFormat.MAX_MINOR);
        PackFormatRange modern = orderedRange(min, max);
        if (!modern.min().isKnown()) {
            return UNKNOWN;
        }
        if (hasLegacy && (legacy.major() < MIN_RANGE_FALLBACK
                || legacy.major() < min.major() || legacy.major() > max.major())) {
            return UNKNOWN;
        }
        if (min.major() > LAST_LEGACY_FORMAT) {
            return hasLegacyRange ? UNKNOWN : modern;
        }
        if (!hasLegacy || !hasLegacyRange || legacyRange.min().major() != min.major()
                || (legacyRange.max().major() != max.major() && legacyRange.max().major() != LAST_LEGACY_FORMAT)) {
            return UNKNOWN;
        }
        return modern;
    }

    static PackFormat readBound(Object value, int defaultMinor) {
        if (value instanceof List) {
            List<?> values = (List<?>) value;
            if (values.isEmpty() || values.size() > 256) {
                return PackFormat.UNKNOWN;
            }
            for (Object part : values) {
                if (nonNegativeInt(part) < 0) {
                    return PackFormat.UNKNOWN;
                }
            }
            return PackFormat.of(nonNegativeInt(values.get(0)),
                    values.size() > 1 ? nonNegativeInt(values.get(1)) : defaultMinor);
        }
        int major = nonNegativeInt(value);
        return major < 0 ? PackFormat.UNKNOWN : PackFormat.of(major, defaultMinor);
    }

    static PackFormatRange readLegacyRange(Object value) {
        if (value instanceof List) {
            List<?> bounds = (List<?>) value;
            if (bounds.size() != 2) {
                return UNKNOWN;
            }
            return orderedRange(PackFormat.of(nonNegativeInt(bounds.get(0))),
                    PackFormat.of(nonNegativeInt(bounds.get(1))));
        }
        if (value instanceof Map) {
            Map<?, ?> bounds = (Map<?, ?>) value;
            return orderedRange(PackFormat.of(nonNegativeInt(bounds.get(KEY_MIN_INCLUSIVE))),
                    PackFormat.of(nonNegativeInt(bounds.get(KEY_MAX_INCLUSIVE))));
        }
        return PackFormatRange.single(PackFormat.of(nonNegativeInt(value)));
    }

    static PackFormatRange orderedRange(PackFormat min, PackFormat max) {
        return min.isKnown() && max.isKnown() && min.compareTo(max) <= 0
                ? new PackFormatRange(min, max) : UNKNOWN;
    }

    private static int nonNegativeInt(Object value) {
        if (!(value instanceof Number)) {
            return -1;
        }
        double number = ((Number) value).doubleValue();
        int integer = (int) number;
        return integer >= 0 && number == integer ? integer : -1;
    }
}
