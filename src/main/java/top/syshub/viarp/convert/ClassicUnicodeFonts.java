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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import top.syshub.viarp.json.Json;

/** 格式 15：仅迁移能从本包字形和宽度完整还原的单色旧字体。 */
final class ClassicUnicodeFonts {
    private static final String VANILLA = "assets/minecraft/";
    private static final String VANILLA_SIZES = VANILLA + "font/glyph_sizes.bin";
    private static final String VANILLA_TEMPLATE = "minecraft:font/unicode_page_%s.png";
    private static final String FALLBACK = VANILLA + "font/include/unifont.json";
    private static final String OUTPUT = "viarp:font/migration/legacy_unicode/";
    private static final int SIZE_TABLE_BYTES = 65536;
    private static final int MAX_FONT_JSON = 1024 * 1024;
    private static final int MAX_GLYPHS = SIZE_TABLE_BYTES * 2;
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private ClassicUnicodeFonts() {
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        List<String> files = workspace.files();
        Set<String> names = new HashSet<String>(files);
        Set<String> occupied = new HashSet<String>();
        for (String file : files) {
            occupied.add(fold(file));
        }
        Budget budget = new Budget();
        int nextId = 0;
        for (String file : files) {
            if (!file.matches("assets/[a-z0-9_.-]+/font/[a-z0-9_./-]+\\.json")) {
                continue;
            }
            List<Generated> generated = new ArrayList<Generated>();
            String result;
            try {
                Map<String, Object> root = readDocument(workspace, file);
                if (!(root.get("providers") instanceof List)) {
                    continue;
                }
                List<?> providers = (List<?>) root.get("providers");
                List<Object> replacements = new ArrayList<Object>(providers);
                for (int i = 0; i < providers.size(); i++) {
                    Map<String, Object> provider = ClassicPackUpgrades.object(providers.get(i));
                    if (provider == null) {
                        continue;
                    }
                    Object type = provider.get("type");
                    if (!"legacy_unicode".equals(type) && !"minecraft:legacy_unicode".equals(type)) {
                        continue;
                    }
                    // 目标按整个 providers 列表解码；任一旧 provider 不能转换，就完整保留该定义。
                    String sizes = resourcePath(provider.get("sizes"), "", "");
                    String template = requireTemplate(provider.get("template"));
                    Generated converted = encode(workspace, names, sizes, template, nextId++, budget);
                    checkTarget(workspace, occupied, resourcePath(converted.id, "", ".zip"));
                    generated.add(converted);
                    Map<String, Object> updated = new LinkedHashMap<String, Object>(provider);
                    updated.remove("sizes");
                    updated.remove("template");
                    updated.putAll(converted.provider());
                    replacements.set(i, updated);
                }
                if (generated.isEmpty()) {
                    continue;
                }
                root.put("providers", replacements);
                result = Json.write(root) + "\n";
            } catch (IOException e) {
                // 这里只捕获预检错误；发布失败需让工作树事务失败而不是返回不完整资源包。
                workspace.warn("格式 15 " + file + "：字体定义未迁移；" + e.getMessage());
                continue;
            }
            for (Generated font : generated) {
                writeZip(workspace, font);
            }
            workspace.writeText(file, result);
        }
        if (!hasBareInputs(names)) {
            return;
        }
        Generated bare;
        try {
            if (occupied.contains(fold(VANILLA + "font/default.json"))
                    || occupied.contains(fold(VANILLA + "font/uniform.json"))) {
                throw new IOException("已有 default/uniform 字体定义，未推断隐式 fallback 或覆盖其优先级");
            }
            checkTarget(workspace, occupied, FALLBACK);
            bare = encode(workspace, names, VANILLA_SIZES, VANILLA_TEMPLATE, nextId, budget);
            checkTarget(workspace, occupied, resourcePath(bare.id, "", ".zip"));
        } catch (IOException e) {
            workspace.warn("格式 15 旧版默认 Unicode 字体：保留原文件；" + e.getMessage());
            return;
        }
        writeZip(workspace, bare);
        String result = Json.write(Collections.singletonMap("providers", Collections.singletonList(bare.provider()))) + "\n";
        Files.createDirectories(workspace.path(FALLBACK).getParent());
        Files.write(workspace.path(FALLBACK), result.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
    }

    private static Map<String, Object> readDocument(UpgradeWorkspace workspace, String file) throws IOException {
        Path path = workspace.path(file);
        if (Files.size(path) > MAX_FONT_JSON) {
            throw new IOException("字体 JSON 超过读取限制");
        }
        String raw = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
        try {
            return Json.parseObjectRejectingDuplicateKeys(raw);
        } catch (StackOverflowError e) {
            throw new IOException("字体 JSON 嵌套过深", e);
        }
    }

    private static boolean hasBareInputs(Set<String> names) {
        if (names.contains(VANILLA_SIZES)) {
            return true;
        }
        for (String name : names) {
            if (name.matches(VANILLA + "textures/font/unicode_page_[0-9a-f]{2}\\.png")) {
                return true;
            }
        }
        return false;
    }

    private static Generated encode(UpgradeWorkspace workspace, Set<String> names, String sizes, String template,
            int ordinal, Budget budget) throws IOException {
        if (!names.contains(sizes) || Files.size(workspace.path(sizes)) != SIZE_TABLE_BYTES) {
            throw new IOException("缺少本包 65536 字节宽度表或其长度有误：" + sizes);
        }
        byte[] widths = Files.readAllBytes(workspace.path(sizes));
        int count = 0;
        for (byte width : widths) {
            if (width != 0) {
                count++;
            }
        }
        if (count == 0) {
            throw new IOException("宽度表没有可还原字形");
        }
        if (count > budget.remainingGlyphs) {
            throw new IOException("超过本次迁移的旧字形数量限制");
        }
        budget.remainingGlyphs -= count;
        StringBuilder lines = new StringBuilder(count * 70);
        for (int page = 0; page < 256; page++) {
            int start = page << 8;
            boolean used = false;
            for (int i = start; i < start + 256; i++) {
                used |= widths[i] != 0;
            }
            if (!used) {
                continue;
            }
            String pageKey = String.format(Locale.ROOT, "%02x", page);
            String pagePath = resourcePath(template.replace("%s", pageKey), "textures/", ".png");
            if (!names.contains(pagePath)) {
                throw new IOException("缺少本包 Unicode 字形页，不猜测其他包或原版的页内容：" + pagePath);
            }
            UpgradePng.Png size = UpgradePng.dimensions(workspace.path(pagePath));
            if (size.width != 256 || size.height != 256) {
                throw new IOException("旧字体读取器只接收 256×256 页：" + pagePath);
            }
            BufferedImage image = UpgradePng.atlas(workspace.path(pagePath), 256, 256);
            for (int bits : image.getColorModel().getComponentSize()) {
                if (bits > 8) {
                    throw new IOException("PNG 样本超过 8 位，未推断跨解码器的量化：" + pagePath);
                }
            }
            for (int codepoint = start; codepoint < start + 256; codepoint++) {
                int widthByte = widths[codepoint] & 255;
                if (widthByte == 0) {
                    continue;
                }
                int left = widthByte >>> 4;
                int right = widthByte & 15;
                if (left > right || (codepoint >= 0xd800 && codepoint <= 0xdfff)) {
                    throw new IOException("字形裁切或 Unicode 码位无效：U+" + Integer.toHexString(codepoint));
                }
                appendHex(lines, codepoint);
                lines.append(':');
                int x = (codepoint & 15) * 16;
                int y = ((codepoint >>> 4) & 15) * 16;
                for (int row = 0; row < 16; row++) {
                    int bits = 0;
                    for (int column = left; column <= right; column++) {
                        int argb = image.getRGB(x + column, y + row);
                        if ((argb >>> 24) == 0) {
                            continue;
                        }
                        if (argb != 0xffffffff) {
                            throw new IOException("有彩色或半透明可见像素，unihex 无法保真："
                                    + pagePath + " U+" + Integer.toHexString(codepoint));
                        }
                        bits |= 1 << (15 - column);
                    }
                    appendHex(lines, bits);
                }
                lines.append('\n');
            }
        }
        byte[] glyphs = lines.toString().getBytes(StandardCharsets.US_ASCII);
        String id = OUTPUT + digest(glyphs) + "-" + ordinal + ".zip";
        return new Generated(id, glyphs, sizeOverrides(widths));
    }

    /** ZIP 由整个资源包栈按名称查找，不同内容不能使用同一名称。 */
    private static String digest(byte[] glyphs) {
        try {
            byte[] sha = MessageDigest.getInstance("SHA-256").digest(glyphs);
            StringBuilder result = new StringBuilder(64);
            for (byte value : sha) {
                result.append(HEX[(value & 255) >>> 4]).append(HEX[value & 15]);
            }
            return fold(result.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void appendHex(StringBuilder out, int value) {
        for (int shift = 12; shift >= 0; shift -= 4) {
            out.append(HEX[(value >>> shift) & 15]);
        }
    }

    private static List<Map<String, Object>> sizeOverrides(byte[] widths) {
        List<Map<String, Object>> overrides = new ArrayList<Map<String, Object>>();
        for (int high = widths.length - 1; high >= 0;) {
            if (widths[high] == 0) {
                high--;
                continue;
            }
            int low = high;
            while (low > 0 && widths[low - 1] == widths[high]) {
                low--;
            }
            int width = widths[high] & 255;
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("from", codepointString(low));
            // 1.20 的解码器拒绝 from==to。扩大单点区间时只会触及未列字形或已处理的字形。
            entry.put("to", codepointString(low == high ? (high == 0xd7ff ? 0xe000 : high + 1) : high));
            entry.put("left", width >>> 4);
            entry.put("right", width & 15);
            overrides.add(entry);
            high = low - 1;
        }
        return overrides;
    }

    private static String codepointString(int codepoint) {
        return new String(Character.toChars(codepoint));
    }

    private static void writeZip(UpgradeWorkspace workspace, Generated font) throws IOException {
        Path path = workspace.path(resourcePath(font.id, "", ".zip"));
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            ZipEntry entry = new ZipEntry("glyphs.hex");
            entry.setTime(0L);
            zip.putNextEntry(entry);
            zip.write(font.glyphs);
            zip.closeEntry();
        }
    }

    private static String requireTemplate(Object value) throws IOException {
        if (!(value instanceof String)) {
            throw new IOException("缺少旧字体 template");
        }
        String template = (String) value;
        int token = template.indexOf('%');
        if (token < 0 || !template.startsWith("%s", token) || template.indexOf('%', token + 2) >= 0) {
            throw new IOException("仅支持一个旧字体 %s 页索引");
        }
        resourcePath(template.replace("%s", "00"), "textures/", ".png");
        return template;
    }

    private static String resourcePath(Object resource, String prefix, String suffix) throws IOException {
        if (!(resource instanceof String)) {
            throw new IOException("缺少旧字体资源位置");
        }
        String value = (String) resource;
        int colon = value.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : value.substring(0, colon);
        String path = colon < 0 ? value : value.substring(colon + 1);
        if (!namespace.matches("[a-z0-9_.-]+") || namespace.equals(".") || namespace.equals("..")
                || !path.matches("[a-z0-9_./-]+") || !path.endsWith(suffix)) {
            throw new IOException("不支持的旧字体资源位置：" + value);
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IOException("不安全的旧字体资源路径：" + value);
            }
        }
        return "assets/" + namespace + "/" + prefix + path;
    }

    private static void checkTarget(UpgradeWorkspace workspace, Set<String> occupied, String target) throws IOException {
        if (occupied.contains(fold(target)) || Files.exists(workspace.path(target))) {
            throw new IOException("保留已有目标：" + target);
        }
        for (int cut = target.lastIndexOf('/'); cut >= 0; cut = target.lastIndexOf('/', cut - 1)) {
            if (occupied.contains(fold(target.substring(0, cut)))) {
                throw new IOException("目标父路径已被资源文件占用：" + target);
            }
        }
    }

    private static String fold(String path) {
        return path.toLowerCase(Locale.ROOT);
    }

    private static final class Budget {
        int remainingGlyphs = MAX_GLYPHS;
    }

    private static final class Generated {
        final String id;
        final byte[] glyphs;
        final List<Map<String, Object>> overrides;

        Generated(String id, byte[] glyphs, List<Map<String, Object>> overrides) {
            this.id = id;
            this.glyphs = glyphs;
            this.overrides = overrides;
        }

        Map<String, Object> provider() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("type", "unihex");
            result.put("hex_file", id);
            result.put("size_overrides", overrides);
            return result;
        }
    }
}
