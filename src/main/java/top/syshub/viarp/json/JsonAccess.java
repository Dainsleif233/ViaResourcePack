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
package top.syshub.viarp.json;

import java.util.List;
import java.util.Map;

/** 从 {@link Json} 解析结果里安全取值的小工具。缺键、类型不符统一返回默认值。 */
public final class JsonAccess {

    private JsonAccess() {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> parent, String key) {
        if (parent == null) {
            return null;
        }
        Object value = parent.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Map<String, Object> parent, String key) {
        if (parent == null) {
            return null;
        }
        Object value = parent.get(key);
        return value instanceof List ? (List<Object>) value : null;
    }

    public static String string(Map<String, Object> parent, String key, String fallback) {
        if (parent == null) {
            return fallback;
        }
        Object value = parent.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    /** 取整数。{@code null}、非数字、超出 int 范围都返回 [fallback]。 */
    public static int integer(Map<String, Object> parent, String key, int fallback) {
        if (parent == null) {
            return fallback;
        }
        return integer(parent.get(key), fallback);
    }

    /** 把任意已解析的 JSON 值当整数看。 */
    public static int integer(Object value, int fallback) {
        if (value instanceof Number) {
            long l = ((Number) value).longValue();
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return (int) l;
            }
            return l < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean hasKey(Map<String, Object> parent, String key) {
        return parent != null && parent.containsKey(key);
    }
}
