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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 52→53：保留作者的双层纹理与原染色，不把两层预烘焙成新版蛋图。 */
final class SpawnEggUpgrades {
    private static final String VANILLA = "assets/minecraft/";
    private static final String MODELS = VANILLA + "models/item/";
    private static final String ITEMS = VANILLA + "items/";
    private static final String TEXTURES = VANILLA + "textures/item/";
    private static final String BASE = TEXTURES + "spawn_egg.png";
    private static final String OVERLAY = TEXTURES + "spawn_egg_overlay.png";
    private static final String META = ".mcmeta";
    private static final String TEMPLATE = MODELS + "template_spawn_egg.json";
    private static final String TEMPLATE_ID = "minecraft:item/template_spawn_egg";
    private static final String TEMPLATE_JSON = "{\"parent\":\"minecraft:item/generated\",\"textures\":{"
            + "\"layer0\":\"minecraft:item/spawn_egg\",\"layer1\":\"minecraft:item/spawn_egg_overlay\"}}\n";
    private static final String TINTS = "/viarp/migrations/intermediate/spawn-egg-tints.tsv";
    private static final int EGG_COUNT = 81;
    // 通用 PNG 检查已经限制字节、块数和边长；在出生蛋规则中进一步约束解码和动画工作量。
    private static final long MAX_TEXTURE_PIXELS = 1024L * 1024;
    private static final int MAX_METADATA_BYTES = 64 * 1024;
    private static final int MAX_FRAMES = 4096;
    private static final String CONTEXT = "53: 出生蛋双层迁移";

    private SpawnEggUpgrades() {
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        EntryNames names = new EntryNames(workspace.entries(true));
        if (!names.occupied(BASE) && !names.occupied(OVERLAY)
                && !names.occupied(BASE + META) && !names.occupied(OVERLAY + META)) {
            return;
        }
        if (!names.exactFile(BASE) || !names.exactFile(OVERLAY)) {
            warn(workspace, "需要两张路径大小写精确且无冲突的旧纹理");
            return;
        }
        if (names.occupied(VANILLA + "atlases/blocks.json") || names.occupied(VANILLA + "atlases/items.json")) {
            warn(workspace, "包内有自定义 blocks/items 图集，不能确认两张精灵的来源");
            return;
        }
        if (names.occupied("pack.mcmeta")) {
            Map<String, Object> metadata = names.exactFile("pack.mcmeta")
                    ? workspace.readObject("pack.mcmeta", CONTEXT) : null;
            if (metadata == null || metadata.containsKey("filter")) {
                warn(workspace, "包元数据无法核实或声明了资源过滤，不能确认模板/图集可见");
                return;
            }
        }
        if (names.occupied(MODELS + "generated.json")) {
            warn(workspace, "包内覆盖 item/generated，不能确认旧两层几何");
            return;
        }
        boolean createTemplate = !names.occupied(TEMPLATE);
        if (!names.safeParents(TEMPLATE) || (!createTemplate && (!names.exactFile(TEMPLATE)
                || !verifiedTemplate(workspace.readObject(TEMPLATE, CONTEXT))))) {
            warn(workspace, "旧模板有冲突或不属于已验证的两层形状");
            return;
        }
        if (!names.safeParents(ITEMS + "entry.json")) {
            warn(workspace, "items 路径存在文件或大小写冲突");
            return;
        }
        Map<String, int[]> entries = loadTints();
        entries.keySet().removeIf(id -> names.occupied(ITEMS + id + ".json")
                || names.occupied(ITEMS + id + ".json" + META)
                || names.occupied(MODELS + id + ".json") || names.occupied(TEXTURES + id + ".png")
                || names.occupied(TEXTURES + id + ".png" + META));
        if (entries.isEmpty()) {
            return;
        }

        int baseFrame = textureFrame(workspace, names, BASE);
        int overlayFrame = textureFrame(workspace, names, OVERLAY);
        if (baseFrame == 0 || overlayFrame == 0) {
            return;
        }
        if (baseFrame != overlayFrame) {
            warn(workspace, "两张旧纹理的单帧尺寸不同");
            return;
        }
        // 旧模板在 53 的原版资产中已移除。这里只能检查本包；全局引用仍按资源包优先级解析。
        if (createTemplate) {
            writeNew(workspace, TEMPLATE, TEMPLATE_JSON);
        }
        for (Map.Entry<String, int[]> egg : entries.entrySet()) {
            int[] argb = egg.getValue();
            writeNew(workspace, ITEMS + egg.getKey() + ".json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + TEMPLATE_ID + "\",\"tints\":["
                    + "{\"type\":\"minecraft:constant\",\"value\":" + argb[0] + "},"
                    + "{\"type\":\"minecraft:constant\",\"value\":" + argb[1] + "}]}}\n");
        }
    }

    private static boolean verifiedTemplate(Map<String, Object> model) {
        if (model == null || model.size() != 2 || !vanillaId(model.get("parent"), "item/generated")
                || !(model.get("textures") instanceof Map)) {
            return false;
        }
        Map<?, ?> textures = (Map<?, ?>) model.get("textures");
        return textures.size() == 2 && vanillaId(textures.get("layer0"), "item/spawn_egg")
                && vanillaId(textures.get("layer1"), "item/spawn_egg_overlay");
    }

    private static boolean vanillaId(Object value, String path) {
        return path.equals(value) || ("minecraft:" + path).equals(value);
    }

    private static int textureFrame(UpgradeWorkspace workspace, EntryNames names, String relative) throws IOException {
        Path png = workspace.path(relative);
        String sidecar = relative + META;
        Map<String, Object> metadata = Collections.emptyMap();
        if (names.occupied(sidecar)) {
            if (!names.exactFile(sidecar) || Files.size(workspace.path(sidecar)) > MAX_METADATA_BYTES) {
                warn(workspace, "旧纹理侧车有路径冲突或超过预算：" + sidecar);
                return 0;
            }
            metadata = workspace.readObject(sidecar, CONTEXT);
            if (metadata == null) {
                return 0;
            }
        }
        try {
            UpgradePng.Png size = UpgradePng.dimensions(png);
            if ((long) size.width * size.height > MAX_TEXTURE_PIXELS) {
                throw new IOException("像素数超过出生蛋检查预算");
            }
            int frame = verifiedFrame(metadata, size.width, size.height);
            if (frame == 0) {
                throw new IOException("帧尺寸或侧车不属于已验证的动画/纹理格式");
            }
            // 头部检查不解压 IDAT；额外做一次有预算的解码，但不写回用户图像。
            UpgradePng.atlas(png, 1, 1).flush();
            return frame;
        } catch (IOException e) {
            warn(workspace, relative + "：" + e.getMessage());
            return 0;
        }
    }

    private static int verifiedFrame(Map<String, Object> metadata, int width, int height) {
        for (Map.Entry<String, Object> section : metadata.entrySet()) {
            if (!(section.getValue() instanceof Map)) {
                return 0;
            }
            if ("texture".equals(section.getKey())) {
                for (Map.Entry<?, ?> field : ((Map<?, ?>) section.getValue()).entrySet()) {
                    if ((!"blur".equals(field.getKey()) && !"clamp".equals(field.getKey()))
                            || !(field.getValue() instanceof Boolean)) {
                        return 0;
                    }
                }
            } else if (!"animation".equals(section.getKey())) {
                return 0;
            }
        }
        Object animation = metadata.get("animation");
        if (animation == null) {
            return width == height ? width : 0;
        }
        Map<?, ?> fields = (Map<?, ?>) animation;
        int frameW = width;
        int frameH = height;
        if (!fields.containsKey("width") && !fields.containsKey("height")) {
            frameW = frameH = Math.min(width, height);
        }
        for (Map.Entry<?, ?> field : fields.entrySet()) {
            Object value = field.getValue();
            if ("width".equals(field.getKey()) && inRange(value, 1, width)) {
                frameW = ((Long) value).intValue();
            } else if ("height".equals(field.getKey()) && inRange(value, 1, height)) {
                frameH = ((Long) value).intValue();
            } else if (!("frametime".equals(field.getKey()) && inRange(value, 1, Integer.MAX_VALUE))
                    && !("interpolate".equals(field.getKey()) && value instanceof Boolean)
                    && !("frames".equals(field.getKey()) && value instanceof List)) {
                return 0;
            }
        }
        if (frameW != frameH || width % frameW != 0 || height % frameH != 0) {
            return 0;
        }
        int count = (width / frameW) * (height / frameH);
        if (count > MAX_FRAMES) {
            return 0;
        }
        if (fields.containsKey("frames")) {
            List<?> frames = (List<?>) fields.get("frames");
            if (frames.isEmpty() || frames.size() > MAX_FRAMES) {
                return 0;
            }
            for (Object frame : frames) {
                if (frame instanceof Map) {
                    Map<?, ?> detail = (Map<?, ?>) frame;
                    if (!inRange(detail.get("index"), 0, count - 1)
                            || detail.size() != (detail.containsKey("time") ? 2 : 1)
                            || (detail.containsKey("time") && !inRange(detail.get("time"), 1, Integer.MAX_VALUE))) {
                        return 0;
                    }
                } else if (!inRange(frame, 0, count - 1)) {
                    return 0;
                }
            }
        }
        return frameW;
    }

    private static boolean inRange(Object value, int min, int max) {
        return value instanceof Long && (Long) value >= min && (Long) value <= max;
    }

    private static Map<String, int[]> loadTints() throws IOException {
        InputStream stream = SpawnEggUpgrades.class.getResourceAsStream(TINTS);
        if (stream == null) {
            throw new IOException("Missing spawn egg migration resource " + TINTS);
        }
        Map<String, int[]> result = new LinkedHashMap<String, int[]>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length != 3 || !fields[0].matches("[a-z0-9_]+_spawn_egg")) {
                    throw new IOException("Invalid spawn egg migration row: " + line);
                }
                int[] argb;
                try {
                    argb = new int[] {Integer.parseInt(fields[1]), Integer.parseInt(fields[2])};
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid spawn egg migration colors: " + line, e);
                }
                if ((argb[0] >>> 24) != 255 || (argb[1] >>> 24) != 255 || result.put(fields[0], argb) != null) {
                    throw new IOException("Non-opaque or duplicate spawn egg migration row: " + line);
                }
            }
        }
        if (result.size() != EGG_COUNT) {
            throw new IOException("Expected " + EGG_COUNT + " vanilla spawn eggs in " + TINTS);
        }
        return result;
    }

    private static void writeNew(UpgradeWorkspace workspace, String relative, String json) throws IOException {
        Path file = workspace.path(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void warn(UpgradeWorkspace workspace, String reason) {
        workspace.warn(CONTEXT + "：" + reason + "；保留原文件");
    }

    /** 即便文件系统忽略大小写，也不能把近似名称当作原版文件或安全的写入目录。 */
    private static final class EntryNames {
        private final Map<String, String> folded = new HashMap<String, String>();

        EntryNames(List<String> entries) {
            for (String entry : entries) {
                String name = entry.endsWith("/") ? entry.substring(0, entry.length() - 1) : entry;
                String key = fold(name);
                if (folded.putIfAbsent(key, entry) != null) {
                    folded.put(key, "");
                }
            }
        }

        boolean occupied(String path) {
            return folded.containsKey(fold(path));
        }

        boolean exactFile(String path) {
            return path.equals(folded.get(fold(path))) && safeParents(path);
        }

        boolean safeParents(String path) {
            for (int slash = path.lastIndexOf('/'); slash >= 0; slash = path.lastIndexOf('/', slash - 1)) {
                String prefix = path.substring(0, slash);
                String actual = folded.get(fold(prefix));
                if (actual != null && !actual.equals(prefix + '/')) {
                    return false;
                }
            }
            return true;
        }

        private static String fold(String path) {
            return path.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
        }
    }
}
