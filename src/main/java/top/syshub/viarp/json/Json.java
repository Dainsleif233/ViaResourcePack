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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 读写器。
 *
 * <p>为什么不用 Gson：core 要在 MC 1.14.4（gson 2.8.0）到 26.3（gson 2.14.0）之间
 * 通用，而这段区间里 Gson 的静态入口有过增删（{@code JsonParser.parseString} 直到
 * 2.8.6 才有，实例方法 {@code parse} 长期处于 deprecated 状态）。资源包元数据的
 * JSON 读写不依赖不同客户端所携带的 Gson 版本。
 *
 * <p>解析是**宽松**的：允许 UTF-8 BOM、{@code //} 与 {@code /* *\/} 注释、以及
 * 对象/数组末尾多余的逗号——现实里的资源包 {@code pack.mcmeta} 常有这些毛病，而
 * 原版自己也用 lenient 模式读它。
 *
 * <p>值的 Java 映射：对象 → {@link LinkedHashMap}，数组 → {@link ArrayList}，
 * 字符串 → {@link String}，数字 → {@link Long}、{@link Double} 或 {@link BigDecimal}，
 * 布尔 → {@link Boolean}，null → {@code null}。
 */
public final class Json {

    private static final int MAX_CONTAINER_DEPTH = 96;
    private static final int MAX_EXACT_NUMBER_LENGTH = 512;

    private final String text;
    private final boolean rejectDuplicateKeys;
    private int pos;

    private Json(String text, boolean rejectDuplicateKeys) {
        this.text = text;
        this.rejectDuplicateKeys = rejectDuplicateKeys;
    }

    /** 解析一段 JSON 文本。 */
    public static Object parse(String raw) throws JsonException {
        return parse(raw, false);
    }

    private static Object parse(String raw, boolean rejectDuplicateKeys) throws JsonException {
        if (raw == null) {
            throw new JsonException("input is null");
        }
        String text = raw;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        Json json = new Json(text, rejectDuplicateKeys);
        json.skipWhitespace();
        Object value = json.readValue(0);
        json.skipWhitespace();
        if (json.pos < json.text.length()) {
            throw json.error("trailing content");
        }
        return value;
    }

    /** 解析并要求顶层是对象。 */
    public static Map<String, Object> parseObject(String raw) throws JsonException {
        return requireObject(parse(raw));
    }

    /** 内容改写要求唯一键和精确数值，但仍接受注释和尾逗号。 */
    public static Map<String, Object> parseObjectRejectingDuplicateKeys(String raw) throws JsonException {
        return requireObject(parse(raw, true));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireObject(Object value) throws JsonException {
        if (!(value instanceof Map)) {
            throw new JsonException("expected a JSON object at top level, got "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        return (Map<String, Object>) value;
    }

    // ────────────────────────────── 读取 ──────────────────────────────

    private Object readValue(int depth) throws JsonException {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                checkContainerDepth(depth);
                return readObject(depth + 1);
            case '[':
                checkContainerDepth(depth);
                return readArray(depth + 1);
            case '"':
                return readString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return readNumber();
        }
    }

    private void checkContainerDepth(int depth) throws JsonException {
        if (depth >= MAX_CONTAINER_DEPTH) {
            throw error("JSON nesting exceeds " + MAX_CONTAINER_DEPTH + " containers");
        }
    }

    private Map<String, Object> readObject(int depth) throws JsonException {
        pos++; // '{'
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() == '}') { // 容忍末尾逗号
                pos++;
                return map;
            }
            if (peek() != '"') {
                throw error("expected a quoted object key");
            }
            String key = readString();
            if (rejectDuplicateKeys && map.containsKey(key)) {
                throw error("duplicate object key: " + key);
            }
            skipWhitespace();
            if (peek() != ':') {
                throw error("expected ':' after object key");
            }
            pos++;
            skipWhitespace();
            map.put(key, readValue(depth));
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                return map;
            } else {
                throw error("expected ',' or '}' in object");
            }
        }
    }

    private List<Object> readArray(int depth) throws JsonException {
        pos++; // '['
        List<Object> list = new ArrayList<Object>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            if (peek() == ']') { // 容忍末尾逗号
                pos++;
                return list;
            }
            list.add(readValue(depth));
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                return list;
            } else {
                throw error("expected ',' or ']' in array");
            }
        }
    }

    private String readString() throws JsonException {
        pos++; // opening quote
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("unterminated escape sequence");
            }
            char esc = text.charAt(pos++);
            switch (esc) {
                case '"':
                    out.append('"');
                    break;
                case '\\':
                    out.append('\\');
                    break;
                case '/':
                    out.append('/');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    if (pos + 4 > text.length()) {
                        throw error("truncated \\u escape");
                    }
                    String hex = text.substring(pos, pos + 4);
                    pos += 4;
                    try {
                        out.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw error("invalid \\u escape: " + hex);
                    }
                    break;
                default:
                    throw error("invalid escape: \\" + esc);
            }
        }
    }

    private Object readNumber() throws JsonException {
        int start = pos;
        if (peek() == '-' || peek() == '+') {
            pos++;
        }
        boolean floating = false;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                floating = floating || c == '.' || c == 'e' || c == 'E';
                pos++;
            } else {
                break;
            }
        }
        if (rejectDuplicateKeys && pos - start > MAX_EXACT_NUMBER_LENGTH) {
            throw error("number exceeds " + MAX_EXACT_NUMBER_LENGTH + " characters");
        }
        String token = text.substring(start, pos);
        if (token.isEmpty() || "-".equals(token) || "+".equals(token)) {
            throw error("expected a value");
        }
        if (rejectDuplicateKeys) {
            return exactNumber(token, floating);
        }
        try {
            if (floating) {
                return Double.valueOf(token);
            }
            return Long.valueOf(token);
        } catch (NumberFormatException e) {
            try {
                return Double.valueOf(token);
            } catch (NumberFormatException e2) {
                throw error("invalid number: " + token);
            }
        }
    }

    private Number exactNumber(String token, boolean floating) throws JsonException {
        if (!floating && !"-0".equals(token)) {
            try {
                return Long.valueOf(token);
            } catch (NumberFormatException ignored) {
                // 大整数也要保留精度，不能通过 double 受舍入影响。
            }
        }
        try {
            BigDecimal number = new BigDecimal(token);
            if (number.signum() == 0 && token.charAt(0) == '-') {
                return Double.valueOf(-0.0d);
            }
            return number;
        } catch (NumberFormatException e) {
            throw error("invalid exact number: " + token);
        }
    }

    private void expect(String literal) throws JsonException {
        if (!text.startsWith(literal, pos)) {
            throw error("expected '" + literal + "'");
        }
        pos += literal.length();
    }

    private char peek() throws JsonException {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                pos += 2;
                while (pos < text.length() && text.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                pos += 2;
                while (pos + 1 < text.length() && !(text.charAt(pos) == '*' && text.charAt(pos + 1) == '/')) {
                    pos++;
                }
                pos = Math.min(pos + 2, text.length());
            } else {
                break;
            }
        }
    }

    private JsonException error(String reason) {
        int line = 1;
        int col = 1;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new JsonException(reason + " (line " + line + ", column " + col + ")");
    }

    // ────────────────────────────── 写出 ──────────────────────────────

    /** 把 {@link #parse} 支持的值结构写回 JSON 文本（两空格缩进）。 */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value, 0);
        out.append('\n');
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value, int indent) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map) {
            writeObject(out, (Map<?, ?>) value, indent);
        } else if (value instanceof List) {
            writeArray(out, (List<?>) value, indent);
        } else if (value instanceof String) {
            writeString(out, (String) value);
        } else if (value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof Number) {
            writeNumber(out, (Number) value);
        } else {
            writeString(out, value.toString());
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map, int indent) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(",\n");
            }
            first = false;
            indent(out, indent + 1);
            writeString(out, String.valueOf(entry.getKey()));
            out.append(": ");
            writeValue(out, entry.getValue(), indent + 1);
        }
        out.append('\n');
        indent(out, indent);
        out.append('}');
    }

    private static void writeArray(StringBuilder out, List<?> list, int indent) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                out.append(",\n");
            }
            indent(out, indent + 1);
            writeValue(out, list.get(i), indent + 1);
        }
        out.append('\n');
        indent(out, indent);
        out.append(']');
    }

    private static void writeNumber(StringBuilder out, Number number) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            // (double) Long.MAX_VALUE 会舍入到 2^63；用上界开区间避免饱和截断，也不要把负零写成整数。
            if (d >= -0x1.0p63 && d < 0x1.0p63 && d == Math.rint(d)
                    && Double.doubleToRawLongBits(d) != Long.MIN_VALUE) {
                out.append((long) d);
                return;
            }
        }
        out.append(number.toString());
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    private static void indent(StringBuilder out, int level) {
        for (int i = 0; i < level; i++) {
            out.append("  ");
        }
    }
}
