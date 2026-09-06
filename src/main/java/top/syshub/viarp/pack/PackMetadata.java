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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;
import top.syshub.viarp.json.JsonException;

/**
 * 读写 {@code pack.mcmeta} 的 {@code pack} 段。
 *
 * <p>横跨三代 schema（键名与形态都从 26.2 的
 * {@code PackFormat$IntermediaryFormat} 编解码器里逐条核对过）：
 *
 * <ol>
 *   <li>1.6 – 1.20.1：只有 {@code pack_format}（int）。</li>
 *   <li>1.20.2 – 1.21.8：额外支持 {@code supported_formats}，可以是 int、
 *       {@code [min, max]} 数组、或 {@code {min_inclusive, max_inclusive}} 对象。</li>
 *   <li>1.21.9+：新增 {@code min_format} / {@code max_format}（int 或
 *       {@code [major, minor]}）与 {@code formats}（int / 数组 / 对象）；
 *       {@code pack_format} 与 {@code supported_formats} 仍被接受。</li>
 * </ol>
 *
 * <p>解析时按「新键优先」合并出一个 {@link PackFormatRange}；写回时只写
 * {@code pack_format} 一个键——它在 1.6 – 26.2 全区间都被接受，而转换产物只针对
 * 当前这一个 major，声明区间反而会让缓存产物被误用到别的版本上。
 */
public final class PackMetadata {

    private static final String KEY_PACK = "pack";
    private static final String KEY_PACK_FORMAT = "pack_format";
    private static final String KEY_SUPPORTED_FORMATS = "supported_formats";
    private static final String KEY_MIN_FORMAT = "min_format";
    private static final String KEY_MAX_FORMAT = "max_format";
    private static final String KEY_FORMATS = "formats";
    private static final String KEY_MIN_INCLUSIVE = "min_inclusive";
    private static final String KEY_MAX_INCLUSIVE = "max_inclusive";
    private static final String KEY_DESCRIPTION = "description";

    private final Map<String, Object> root;
    private final PackFormatRange supported;
    private final boolean hasLegacyPackFormat;

    private PackMetadata(Map<String, Object> root, PackFormatRange supported, boolean hasLegacyPackFormat) {
        this.root = root;
        this.supported = supported;
        this.hasLegacyPackFormat = hasLegacyPackFormat;
    }

    /** 解析一份 {@code pack.mcmeta} 文本。 */
    public static PackMetadata parse(String text) throws JsonException {
        Map<String, Object> root = Json.parseObject(text);
        Map<String, Object> pack = JsonAccess.object(root, KEY_PACK);
        boolean legacy = pack != null && readFormat(pack.get(KEY_PACK_FORMAT)).isKnown();
        return new PackMetadata(root, readRange(pack), legacy);
    }

    /** 源包声明的受支持格式区间；未声明任何格式时 {@code min().isKnown()} 为 false。 */
    public PackFormatRange supportedFormats() {
        return supported;
    }

    /** 源包的「主」格式——转换时作为 {@code from}。 */
    public PackFormat primaryFormat() {
        return supported.min();
    }

    /** 源包是否声明了任何可用的格式。 */
    public boolean hasFormat() {
        return supported.min().isKnown();
    }

    /** 目标 major 是否已被源包声明支持（支持则无需转换）。 */
    public boolean supports(int major) {
        return hasFormat() && supported.containsMajor(major);
    }

    /**
     * 源包是否写了 {@code pack_format}（int）这个老键。
     *
     * <p>用途见 {@code PackPlaceholder}：1.20.1 及更早的 {@code PackMetadataSectionSerializer}
     * 用 {@code GsonHelper.getAsInt(json, "pack_format")} 读格式，缺键直接抛
     * {@code JsonParseException}，原版会记一句「Couldn't load pack metadata」并把整个包
     * **从列表里丢掉**。而 1.21.9+ 的资源包只写 {@code min_format} / {@code max_format}，
     * 于是这类包在老版本上连「不兼容」都显示不出来——它压根不出现。
     */
    public boolean hasLegacyPackFormat() {
        return hasLegacyPackFormat;
    }

    /** {@code pack.description}，没有则返回 [fallback]。 */
    public String description(String fallback) {
        Map<String, Object> pack = JsonAccess.object(root, KEY_PACK);
        String description = JsonAccess.string(pack, KEY_DESCRIPTION, null);
        return description != null ? description : fallback;
    }
    /**
     * 把 {@code pack} 段改写成 [target] 单一格式，返回新的 {@code pack.mcmeta} 文本。
     *
     * <p>其余键（{@code description}、{@code overlays}、语言/滤镜段等）原样保留。
     */
    public String withFormat(PackFormat target) {
        Map<String, Object> newRoot = new LinkedHashMap<String, Object>(root);
        Map<String, Object> pack = JsonAccess.object(root, KEY_PACK);
        Map<String, Object> newPack = pack == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(pack);

        // 先清掉所有格式相关的键，再写目标格式，避免新旧键互相矛盾。
        newPack.remove(KEY_PACK_FORMAT);
        newPack.remove(KEY_SUPPORTED_FORMATS);
        newPack.remove(KEY_MIN_FORMAT);
        newPack.remove(KEY_MAX_FORMAT);
        newPack.remove(KEY_FORMATS);
        newPack.put(KEY_PACK_FORMAT, Integer.valueOf(target.major()));

        newRoot.put(KEY_PACK, newPack);
        return Json.write(newRoot);
    }

    /** 生成一份最小可用的 {@code pack.mcmeta}（源包缺失该文件时兜底）。 */
    public static String minimal(PackFormat target, String description) {
        Map<String, Object> pack = new LinkedHashMap<String, Object>();
        pack.put(KEY_PACK_FORMAT, Integer.valueOf(target.major()));
        pack.put(KEY_DESCRIPTION, description);
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put(KEY_PACK, pack);
        return Json.write(root);
    }

    /**
     * 生成占位包的 {@code pack.mcmeta}：只有 {@code pack_format} 与原样搬过来的
     * {@code description}。
     *
     * <p>{@code description} 必须**原样**复制而不是转成字符串：它可以是纯文本，也可以是
     * 一整个文本组件（{@code {"text":...,"color":...}}）甚至组件数组，而各版本的
     * {@code ComponentSerialization} 对这三种形态都认。源包没写时用 [fallback]。
     *
     * <p>刻意不复制 {@code overlays} 之类的其他键：占位包里没有对应目录，声明了反而会让
     * 原版去找不存在的东西。
     */
    public String stub(PackFormat target, String fallback) {
        Map<String, Object> pack = new LinkedHashMap<String, Object>();
        pack.put(KEY_PACK_FORMAT, Integer.valueOf(target.major()));
        Object description = descriptionValue();
        pack.put(KEY_DESCRIPTION, description != null ? description : fallback);
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put(KEY_PACK, pack);
        return Json.write(root);
    }

    /** {@code pack.description} 的原始 JSON 值（字符串 / 对象 / 数组）；缺失时 {@code null}。 */
    private Object descriptionValue() {
        Map<String, Object> pack = JsonAccess.object(root, KEY_PACK);
        return pack == null ? null : pack.get(KEY_DESCRIPTION);
    }

    // ────────────────────────────── 解析细节 ──────────────────────────────

    private static PackFormatRange readRange(Map<String, Object> pack) {
        if (pack == null) {
            return PackFormatRange.single(PackFormat.UNKNOWN);
        }

        PackFormat min = readFormat(pack.get(KEY_MIN_FORMAT));
        PackFormat max = readFormat(pack.get(KEY_MAX_FORMAT));

        // formats（1.21.9+）与 supported_formats（1.20.2+）语义相同，都是闭区间。
        PackFormatRange declared = readRangeValue(pack.get(KEY_FORMATS));
        if (declared == null) {
            declared = readRangeValue(pack.get(KEY_SUPPORTED_FORMATS));
        }

        PackFormat single = readFormat(pack.get(KEY_PACK_FORMAT));

        PackFormat lo = firstKnown(min, declared == null ? null : declared.min(), single);
        PackFormat hi = firstKnown(max, declared == null ? null : declared.max(), single);

        if (!lo.isKnown() && !hi.isKnown()) {
            return PackFormatRange.single(PackFormat.UNKNOWN);
        }
        if (!lo.isKnown()) {
            lo = hi;
        }
        if (!hi.isKnown()) {
            hi = lo;
        }
        if (lo.compareTo(hi) > 0) {
            PackFormat swap = lo;
            lo = hi;
            hi = swap;
        }
        return new PackFormatRange(lo, hi);
    }

    private static PackFormat firstKnown(PackFormat... candidates) {
        for (PackFormat candidate : candidates) {
            if (candidate != null && candidate.isKnown()) {
                return candidate;
            }
        }
        return PackFormat.UNKNOWN;
    }

    /** 单个格式值：int 或 {@code [major, minor]}。 */
    private static PackFormat readFormat(Object value) {
        if (value instanceof Number || value instanceof String) {
            int major = JsonAccess.integer(value, -1);
            return major < 0 ? PackFormat.UNKNOWN : PackFormat.of(major);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return PackFormat.UNKNOWN;
            }
            int major = JsonAccess.integer(list.get(0), -1);
            if (major < 0) {
                return PackFormat.UNKNOWN;
            }
            int minor = list.size() > 1 ? JsonAccess.integer(list.get(1), 0) : 0;
            return PackFormat.of(major, Math.max(0, minor));
        }
        return PackFormat.UNKNOWN;
    }

    /** 区间值：int（退化为单点）、{@code [min, max]}、或 {@code {min_inclusive, max_inclusive}}。 */
    @SuppressWarnings("unchecked")
    private static PackFormatRange readRangeValue(Object value) {
        if (value instanceof Number || value instanceof String) {
            PackFormat single = readFormat(value);
            return single.isKnown() ? PackFormatRange.single(single) : null;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.size() < 2) {
                PackFormat single = list.isEmpty() ? PackFormat.UNKNOWN : readFormat(list.get(0));
                return single.isKnown() ? PackFormatRange.single(single) : null;
            }
            // 这里的两元素是 [min, max]（区间），而不是 min_format 的 [major, minor]，
            // 由键名区分——同样的 JSON 形状在两处含义不同。
            PackFormat lo = PackFormat.of(JsonAccess.integer(list.get(0), -1));
            PackFormat hi = PackFormat.of(JsonAccess.integer(list.get(1), -1));
            if (!lo.isKnown() || !hi.isKnown()) {
                return null;
            }
            return new PackFormatRange(lo, hi);
        }
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            PackFormat lo = readFormat(map.get(KEY_MIN_INCLUSIVE));
            PackFormat hi = readFormat(map.get(KEY_MAX_INCLUSIVE));
            if (!lo.isKnown() || !hi.isKnown()) {
                return null;
            }
            return new PackFormatRange(lo, hi);
        }
        return null;
    }
}
