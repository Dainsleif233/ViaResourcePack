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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 格式 44 边界上，原版弓和钓竿默认物品入口的旧模型选择规则。 */
final class ClassicItemPredicates {
    private static final String MINECRAFT = "minecraft:";
    private static final int MAX_PULL_TICK = 20;
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private ClassicItemPredicates() {
    }

    static boolean supports(String item) {
        return "bow".equals(item) || "fishing_rod".equals(item);
    }

    /** 构造根对象的 model 字段；不支持的行令整个列表失败。无 overrides 字段时传空列表。 */
    static Map<String, Object> convert(String item, Object overrides) {
        if (!supports(item) || !(overrides instanceof List)) {
            return null;
        }
        boolean bow = "bow".equals(item);
        String booleanKey = bow ? "pulling" : "cast";
        String base = MINECRAFT + "item/" + item;
        List<LegacyOverride> rows = new ArrayList<LegacyOverride>();
        boolean hasPredicates = false;
        for (Object value : (List<?>) overrides) {
            if (!(value instanceof Map)) {
                return null;
            }
            Map<?, ?> row = (Map<?, ?>) value;
            String model = canonicalModelId(row.get("model"));
            Object rawPredicates = row.get("predicate");
            if (row.size() != 2 || model == null || !(rawPredicates instanceof Map)) {
                return null;
            }
            Map<?, ?> predicates = (Map<?, ?>) rawPredicates;
            int firstState = 0;
            int firstTick = 0;
            boolean seenState = false;
            boolean seenPull = false;
            for (Map.Entry<?, ?> predicate : predicates.entrySet()) {
                if (!(predicate.getKey() instanceof String) || !(predicate.getValue() instanceof Number)) {
                    return null;
                }
                String key = (String) predicate.getKey();
                if (key.startsWith(MINECRAFT)) {
                    key = key.substring(MINECRAFT.length());
                }
                // Gson 从 JSON 十进制数直接读 float；先舍入到 double 会在部分边界选错模型。
                float threshold = Float.parseFloat(predicate.getValue().toString());
                if (!Float.isFinite(threshold)) {
                    return null;
                }
                if (booleanKey.equals(key) && !seenState) {
                    firstState = threshold > 1.0f ? 2 : (threshold > 0.0f ? 1 : 0);
                    seenState = true;
                } else if (bow && "pull".equals(key) && !seenPull) {
                    firstTick = firstPullTick(threshold);
                    seenPull = true;
                } else {
                    // 不混合不同的属性域；同一行也不接受两个拼法指向同一属性 ID。
                    return null;
                }
            }
            hasPredicates |= !predicates.isEmpty();
            rows.add(new LegacyOverride(model, firstState, firstTick));
        }
        // 旧 BakedOverrides 没有任何属性时不遍历覆盖表；混合表的空谓词行则可匹配一切。
        if (!hasPredicates) {
            return model(base);
        }
        Map<String, Object> onFalse = branch(base, rows, 0, bow);
        Map<String, Object> onTrue = branch(base, rows, 1, bow);
        if (onFalse.equals(onTrue)) {
            return onFalse;
        }
        Map<String, Object> result = node("condition", bow ? "using_item" : "fishing_rod/cast");
        result.put("on_false", onFalse);
        result.put("on_true", onTrue);
        return result;
    }

    private static int firstPullTick(float threshold) {
        if (threshold <= 0.0f) {
            return 0;
        }
        for (int tick = 1; tick <= MAX_PULL_TICK; tick++) {
            if ((float) tick / 20.0f >= threshold) {
                return tick;
            }
        }
        return MAX_PULL_TICK + 1;
    }

    private static Map<String, Object> branch(String base, List<LegacyOverride> rows, int state, boolean bow) {
        String fallback = select(base, rows, state, 0);
        if (!bow) {
            return model(fallback);
        }
        List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
        String previous = fallback;
        // 只对正的整数 tick 写阈值：负 elapsed 与 0 的旧 pull 都为 0，>=20 的旧 pull 都为 1。
        for (int tick = 1; tick <= MAX_PULL_TICK; tick++) {
            String selected = select(base, rows, state, tick);
            if (!selected.equals(previous)) {
                Map<String, Object> entry = new LinkedHashMap<String, Object>();
                entry.put("threshold", Integer.valueOf(tick));
                entry.put("model", model(selected));
                entries.add(entry);
                previous = selected;
            }
        }
        if (entries.isEmpty()) {
            return model(fallback);
        }
        Map<String, Object> result = node("range_dispatch", "use_duration");
        result.put("fallback", model(fallback));
        result.put("entries", entries);
        return result;
    }

    private static String select(String base, List<LegacyOverride> rows, int state, int tick) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            LegacyOverride row = rows.get(i);
            if (state >= row.firstState && tick >= row.firstTick) {
                return row.model;
            }
        }
        return base;
    }

    private static String canonicalModelId(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String id = (String) value;
        int separator = id.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : id.substring(0, separator);
        String path = id.substring(separator + 1);
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            return null;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return namespace + ":" + path;
    }

    private static Map<String, Object> model(String id) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("type", MINECRAFT + "model");
        result.put("model", id);
        return result;
    }

    private static Map<String, Object> node(String type, String property) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("type", MINECRAFT + type);
        result.put("property", MINECRAFT + property);
        return result;
    }

    private static final class LegacyOverride {
        final String model;
        final int firstState;
        final int firstTick;

        LegacyOverride(String model, int firstState, int firstTick) {
            this.model = model;
            this.firstState = firstState;
            this.firstTick = firstTick;
        }
    }
}
