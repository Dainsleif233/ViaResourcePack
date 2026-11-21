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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonException;
import top.syshub.viarp.pack.PackFormat;

/** 18–64 的小步迁移；按原版实际路径和已确认的结构槽操作。 */
final class IntermediatePackUpgrades {

    private static final String VANILLA = "assets/minecraft/";
    private static final String GUI = VANILLA + "textures/gui/";
    private static final String ITEM_ENTRIES = "/viarp/migrations/intermediate/item-entrypoints-44.txt";
    private static final int MAX_SHEET_EDGE = 4096;

    private IntermediatePackUpgrades() {
    }

    static void apply(PackFormat entered, UpgradeWorkspace workspace) throws IOException {
        if (entered.minor() != 0) {
            return;
        }
        switch (entered.major()) {
            case 18:
                // 原版首界是 16；这里回补以 16/17 为起点、仍携带旧拼图的包。
                splitGuiAtlas(workspace);
                break;
            case 22:
                // 1.20.3-pre1 (683e0e30f)：旧 grass 对应 short_grass，不是 grass_block。
                renameVanillaResource(workspace, 22, "grass", "short_grass", true);
                break;
            case 24:
                // 24w03a (07a9fef3a)：旧海龟鳞甲改 ID；与新增的 armadillo_scute 无关。
                renameVanillaResource(workspace, 24, "scute", "turtle_scute", false);
                warnAssets(workspace, 24, "TTF 度量与变换有变化，请复核字体回退和位移，保留定义",
                        ".ttf", ".otf");
                break;
            case 25:
                // 24w04a 移除 IViewRotMat，不能按名字替代坐标空间。
                warnAssets(workspace, 25, "原版移除了部分 shader uniform（例如 IViewRotMat），保留着色器，需人工复核",
                        "/shaders/");
                break;
            case 30:
                splitMapIcons(workspace);
                break;
            case 36:
                warnAssets(workspace, 36, "后处理 pass、target、shader 源和 include 的结构有变化，保留原件，需人工迁移",
                        "/shaders/", "/post_effect/");
                break;
            case 37:
                moveEquipmentTextures(workspace);
                break;
            case 44:
                moveEquipmentDefinitions(workspace);
                migrateItemEntrypoints(workspace);
                warnAssets(workspace, 44, "OptiFine/CIT 不是原版模型 predicate，保留自定义规则，需用目标版本的模组复核",
                        "/optifine/cit/", "/mcpatcher/cit/", "/citresewn/cit/");
                break;
            case 47:
                // 25w02a (9d861cd57)：布尔 active 首次拆为三态，awake 的材质/模型有已知新 ID。
                migrateCreakingHeart(workspace);
                break;
            case 48:
                moveTexturePairs(workspace, 48, new String[][] {
                        { "entity/pig/pig_saddle", "entity/equipment/pig_saddle/saddle" },
                        { "entity/strider/strider_saddle", "entity/equipment/strider_saddle/saddle" }
                });
                MountSaddleUpgrades.apply(workspace);
                break;
            case 52:
                // 25w07a (02027c155)：post pass 从 program 析为独立 vertex_shader/fragment_shader。
                warnAssets(workspace, 52, "原版改用渲染管线及独立 vertex_shader/fragment_shader；保留核心着色器和后处理，需人工迁移",
                        "/shaders/core/", "/shaders/post/", "/post_effect/");
                break;
            case 54:
                moveTexturePairs(workspace, 54, new String[][] {
                        { "misc/enchanted_glint_entity", "misc/enchanted_glint_armor" }
                });
                break;
            case 60:
                migrateMobEffectsAtlas(workspace);
                break;
            default:
                // 其余发布节点未确认本类能安全执行的迁移；不是完整的语义覆盖声明。
                break;
        }
    }

    /** 16 与 18 共用；仅拆原版已知坐标的独立 UI 精灵，旧图保留。 */
    static void splitGuiAtlas(UpgradeWorkspace workspace) throws IOException {
        Set<String> occupied = occupiedFiles(workspace);
        // 旧 1.20.1 Gui / AbstractWidget UV；23w31a 同名 sprite，按钮有原版 nine_slice。
        writeSprites(workspace, occupied, GUI + "widgets.png", GUI + "sprites/", 256, Arrays.asList(
                new Sprite("hud/hotbar", 0, 0, 182, 22),
                new Sprite("hud/hotbar_selection", 0, 22, 24, 23),
                new Sprite("hud/hotbar_offhand_left", 24, 22, 29, 24),
                new Sprite("hud/hotbar_offhand_right", 53, 22, 29, 24),
                new Sprite("widget/button_disabled", 0, 46, 200, 20, 1),
                new Sprite("widget/button", 0, 66, 200, 20, 3),
                new Sprite("widget/button_highlighted", 0, 86, 200, 20, 3)));
        writeSprites(workspace, occupied, GUI + "icons.png", GUI + "sprites/", 256, hudSprites());
    }

    private static List<Sprite> hudSprites() {
        List<Sprite> sprites = new ArrayList<Sprite>(Arrays.asList(
                new Sprite("hud/crosshair", 0, 0, 15, 15),
                new Sprite("hud/crosshair_attack_indicator_full", 68, 94, 16, 16),
                new Sprite("hud/crosshair_attack_indicator_background", 36, 94, 16, 4),
                new Sprite("hud/crosshair_attack_indicator_progress", 52, 94, 16, 4),
                new Sprite("hud/hotbar_attack_indicator_background", 0, 94, 18, 18),
                new Sprite("hud/hotbar_attack_indicator_progress", 18, 94, 18, 18),
                new Sprite("hud/jump_bar_background", 0, 84, 182, 5),
                new Sprite("hud/jump_bar_cooldown", 0, 74, 182, 5),
                new Sprite("hud/jump_bar_progress", 0, 89, 182, 5),
                new Sprite("hud/experience_bar_background", 0, 64, 182, 5),
                new Sprite("hud/experience_bar_progress", 0, 69, 182, 5),
                new Sprite("hud/armor_empty", 16, 9, 9, 9),
                new Sprite("hud/armor_half", 25, 9, 9, 9),
                new Sprite("hud/armor_full", 34, 9, 9, 9),
                new Sprite("hud/air", 16, 18, 9, 9),
                new Sprite("hud/air_bursting", 25, 18, 9, 9),
                new Sprite("hud/food_empty", 16, 27, 9, 9),
                new Sprite("hud/food_full", 52, 27, 9, 9),
                new Sprite("hud/food_half", 61, 27, 9, 9),
                new Sprite("hud/food_empty_hunger", 133, 27, 9, 9),
                new Sprite("hud/food_full_hunger", 88, 27, 9, 9),
                new Sprite("hud/food_half_hunger", 97, 27, 9, 9),
                new Sprite("hud/heart/vehicle_container", 52, 9, 9, 9),
                new Sprite("hud/heart/vehicle_full", 88, 9, 9, 9),
                new Sprite("hud/heart/vehicle_half", 97, 9, 9, 9)));
        // 1.20.1 Gui.HeartType 的列索引；hardcore 是同图集的第二行（y=45）。
        String prefix = "hud/heart/";
        sprites.add(new Sprite(prefix + "container", 16, 0, 9, 9));
        sprites.add(new Sprite(prefix + "container_blinking", 25, 0, 9, 9));
        sprites.add(new Sprite(prefix + "container_hardcore", 16, 45, 9, 9));
        sprites.add(new Sprite(prefix + "container_hardcore_blinking", 25, 45, 9, 9));
        String[] types = { "", "poisoned_", "withered_", "absorbing_", "frozen_" };
        int[] columns = { 52, 88, 124, 160, 178 };
        for (int i = 0; i < types.length; i++) {
            for (int mode = 0; mode < 2; mode++) {
                String name = prefix + types[i] + (mode == 1 ? "hardcore_" : "");
                int y = mode == 1 ? 45 : 0;
                int x = columns[i];
                sprites.add(new Sprite(name + "full", x, y, 9, 9));
                sprites.add(new Sprite(name + "half", x + 9, y, 9, 9));
                // 旧吸收/冻结心形从不使用独立的闪烁列，不能伪造新变体。
                if (i < 3) {
                    sprites.add(new Sprite(name + "full_blinking", x + 18, y, 9, 9));
                    sprites.add(new Sprite(name + "half_blinking", x + 27, y, 9, 9));
                }
            }
        }
        return sprites;
    }

    private static void writeSprites(UpgradeWorkspace workspace, Set<String> occupied, String source,
                                     String directory, int edge, List<Sprite> sprites) throws IOException {
        if (!workspace.hasFile(source)) {
            return;
        }
        List<Sprite> missing = new ArrayList<Sprite>();
        for (Sprite sprite : sprites) {
            String target = directory + sprite.name + ".png";
            if (!occupied.contains(fold(target)) && !occupied.contains(fold(target + ".mcmeta"))
                    && !Files.exists(workspace.path(target), LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(workspace.path(target + ".mcmeta"), LinkOption.NOFOLLOW_LINKS)) {
                missing.add(sprite);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        if (occupied.contains(fold(source + ".mcmeta"))) {
            workspace.warn("分图：" + source + " 有元数据，无法无损映射到单个精灵；保留旧 PNG 与侧车");
            return;
        }
        BufferedImage sheet = readSheet(workspace, source, edge);
        if (sheet == null) {
            return;
        }
        try {
            int scale = sheet.getWidth() / edge;
            for (Sprite sprite : missing) {
                String target = directory + sprite.name + ".png";
                Path path = workspace.path(target);
                Files.createDirectories(path.getParent());
                try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
                    if (!ImageIO.write(sheet.getSubimage(sprite.x * scale, sprite.y * scale,
                            sprite.width * scale, sprite.height * scale), "png", out)) {
                        throw new IOException("No PNG writer for " + target);
                    }
                }
                occupied.add(fold(target));
                if (sprite.border > 0) {
                    workspace.writeText(target + ".mcmeta", "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\","
                            + "\"width\":" + sprite.width + ",\"height\":" + sprite.height
                            + ",\"border\":" + sprite.border + "}}}\n");
                    occupied.add(fold(target + ".mcmeta"));
                }
            }
        } finally {
            sheet.flush();
        }
    }

    private static BufferedImage readSheet(UpgradeWorkspace workspace, String source, int edge) throws IOException {
        Path path = workspace.path(source);
        try (ImageInputStream stream = new FileImageInputStream(path.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    if ("png".equalsIgnoreCase(reader.getFormatName())) {
                        reader.setInput(stream, true, true);
                        int width = reader.getWidth(0);
                        int height = reader.getHeight(0);
                        if (width == height && width >= edge && width <= MAX_SHEET_EDGE && width % edge == 0) {
                            return reader.read(0);
                        }
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException ex) {
            workspace.warn("分图：" + source + " 无法解码 PNG；保留旧图");
            return null;
        }
        workspace.warn("分图：" + source + " 只拆分 " + edge + "×" + edge
                + " 的整数倍 PNG（上限 " + MAX_SHEET_EDGE + "），保留旧图");
        return null;
    }

    private static final class Sprite {
        final String name;
        final int x;
        final int y;
        final int width;
        final int height;
        final int border;

        Sprite(String name, int x, int y, int width, int height) {
            this(name, x, y, width, height, 0);
        }

        Sprite(String name, int x, int y, int width, int height, int border) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.border = border;
        }
    }

    private static void splitMapIcons(UpgradeWorkspace workspace) throws IOException {
        // 24w11a 枚举 ID + MapRenderer 的 16 列 UV；24w12a MapDecorationTypes 的 asset_id。
        String[] names = { "player", "frame", "red_marker", "blue_marker", "target_x", "target_point",
                "player_off_map", "player_off_limits", "woodland_mansion", "ocean_monument", "white_banner",
                "orange_banner", "magenta_banner", "light_blue_banner", "yellow_banner", "lime_banner",
                "pink_banner", "gray_banner", "light_gray_banner", "cyan_banner", "purple_banner", "blue_banner",
                "brown_banner", "green_banner", "red_banner", "black_banner", "red_x", "desert_village",
                "plains_village", "savanna_village", "snowy_village", "taiga_village", "jungle_temple", "swamp_hut" };
        List<Sprite> sprites = new ArrayList<Sprite>();
        for (int i = 0; i < names.length; i++) {
            sprites.add(new Sprite(names[i], i % 16 * 8, i / 16 * 8, 8, 8));
        }
        writeSprites(workspace, occupiedFiles(workspace), VANILLA + "textures/map/map_icons.png",
                VANILLA + "textures/map/decorations/", 128, sprites);
    }

    private static void renameVanillaResource(UpgradeWorkspace workspace, int format, String from, String to,
                                               boolean block) throws IOException {
        Map<String, String> names = new LinkedHashMap<String, String>();
        names.put("item/" + from, "item/" + to);
        if (block) {
            names.put("block/" + from, "block/" + to);
        }
        Set<String> occupied = occupiedFiles(workspace);
        Map<String, String> files = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> name : names.entrySet()) {
            addTexturePair(workspace, occupied, files, name.getKey(), name.getValue());
            files.put(VANILLA + "models/" + name.getKey() + ".json", VANILLA + "models/" + name.getValue() + ".json");
        }
        if (block) {
            files.put(VANILLA + "blockstates/" + from + ".json", VANILLA + "blockstates/" + to + ".json");
        }
        workspace.moveFiles(files);
        rewriteModelReferences(workspace, format, names, names);
    }

    private static void moveTexturePairs(UpgradeWorkspace workspace, int format, String[][] pairs) throws IOException {
        Set<String> occupied = occupiedFiles(workspace);
        Map<String, String> files = new LinkedHashMap<String, String>();
        Map<String, String> names = new LinkedHashMap<String, String>();
        for (String[] pair : pairs) {
            addTexturePair(workspace, occupied, files, pair[0], pair[1]);
            names.put(pair[0], pair[1]);
        }
        workspace.moveFiles(files);
        rewriteModelReferences(workspace, format, Collections.<String, String>emptyMap(), names);
    }

    private static void addTexturePair(UpgradeWorkspace workspace, Set<String> occupied, Map<String, String> files,
                                       String oldId, String newId) throws IOException {
        String from = VANILLA + "textures/" + oldId + ".png";
        String to = VANILLA + "textures/" + newId + ".png";
        boolean png = workspace.hasFile(from);
        boolean meta = workspace.hasFile(from + ".mcmeta");
        if (!png && !meta) {
            return;
        }
        // 避免旧 PNG 与目标目录中已有的作者侧车混合，反之亦然。批量移动还会拒绝同名冲突。
        if ((png && !meta && occupied(workspace, occupied, to + ".mcmeta"))
                || (meta && !png && occupied(workspace, occupied, to))) {
            throw new IOException("Pack migration texture/sidecar collision: " + from + " -> " + to);
        }
        files.put(from, to);
        files.put(from + ".mcmeta", to + ".mcmeta");
    }

    private static void moveEquipmentTextures(UpgradeWorkspace workspace) throws IOException {
        // 24w36a EquipmentModel.Layer 与原版设备资产：仅已知原版材质，含皮革 overlay。
        List<String[]> pairs = new ArrayList<String[]>();
        for (String material : Arrays.asList("leather", "chainmail", "iron", "gold", "diamond", "netherite", "turtle")) {
            pairs.add(new String[] { "models/armor/" + material + "_layer_1", "entity/equipment/humanoid/" + material });
            if (!"turtle".equals(material)) {
                pairs.add(new String[] { "models/armor/" + material + "_layer_2",
                        "entity/equipment/humanoid_leggings/" + material });
            }
        }
        pairs.add(new String[] { "models/armor/leather_layer_1_overlay", "entity/equipment/humanoid/leather_overlay" });
        pairs.add(new String[] { "models/armor/leather_layer_2_overlay", "entity/equipment/humanoid_leggings/leather_overlay" });
        pairs.add(new String[] { "entity/elytra", "entity/equipment/wings/elytra" });
        pairs.add(new String[] { "entity/wolf/wolf_armor", "entity/equipment/wolf_body/armadillo_scute" });
        pairs.add(new String[] { "entity/wolf/wolf_armor_overlay", "entity/equipment/wolf_body/armadillo_scute_overlay" });
        moveTexturePairs(workspace, 37, pairs.toArray(new String[0][]));
        warnAssets(workspace, 37, "未知装备贴图路径不推导层、材质或染色定义，保留原件", "/textures/models/armor/");
    }

    private static void moveEquipmentDefinitions(UpgradeWorkspace workspace) throws IOException {
        Map<String, String> moves = new LinkedHashMap<String, String>();
        for (String file : workspace.files()) {
            String relative = assetPath(file);
            if (relative == null || !relative.startsWith("models/equipment/") || !relative.endsWith(".json")) {
                continue;
            }
            Map<String, Object> root = readObject(workspace, 44, file);
            if (root == null) {
                continue;
            }
            Map<String, Object> layers = object(root.get("layers"));
            if (layers == null) {
                // 这里也可能是普通几何模型目录，不移动没有 layers 的文件。
                continue;
            }
            if (root.size() != 1 || !compatibleEquipmentLayers(layers)) {
                warn(workspace, 44, file, "无法确认装备 layers 与目标 codec 一致，保留在原位置");
                continue;
            }
            String assetRoot = file.substring(0, file.length() - relative.length());
            moves.put(file, assetRoot + relative.substring("models/".length()));
        }
        workspace.moveFiles(moves);
    }

    private static boolean compatibleEquipmentLayers(Map<String, Object> layers) {
        if (layers.isEmpty()) {
            return false;
        }
        List<String> layerTypes = Arrays.asList("humanoid", "humanoid_leggings", "wings", "wolf_body", "horse_body", "llama_body");
        for (Map.Entry<String, Object> entry : layers.entrySet()) {
            if (!layerTypes.contains(entry.getKey()) || !(entry.getValue() instanceof List)
                    || ((List<?>) entry.getValue()).isEmpty()) {
                return false;
            }
            for (Object value : (List<?>) entry.getValue()) {
                Map<String, Object> layer = object(value);
                if (layer == null || !resourceId(layer.get("texture"))) {
                    return false;
                }
                for (String field : layer.keySet()) {
                    if ("texture".equals(field)
                            || ("use_player_texture".equals(field) && layer.get(field) instanceof Boolean)) {
                        continue;
                    }
                    if ("dyeable".equals(field)) {
                        Map<String, Object> dye = object(layer.get(field));
                        if (dye != null && (dye.isEmpty() || (dye.size() == 1 && rgb(dye.get("color_when_undyed"))))) {
                            continue;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean rgb(Object value) {
        if (value instanceof Long) {
            long rgb = ((Long) value).longValue();
            return rgb >= Integer.MIN_VALUE && rgb <= Integer.MAX_VALUE;
        }
        if (!(value instanceof List) || ((List<?>) value).size() != 3) {
            return false;
        }
        for (Object channel : (List<?>) value) {
            if (!(channel instanceof Number) || !Double.isFinite(((Number) channel).doubleValue())) {
                return false;
            }
        }
        return true;
    }

    private static void migrateCreakingHeart(UpgradeWorkspace workspace) throws IOException {
        Set<String> occupied = occupiedFiles(workspace);
        Map<String, String> textures = new LinkedHashMap<String, String>();
        textures.put("block/creaking_heart_active", "block/creaking_heart_awake");
        textures.put("block/creaking_heart_top_active", "block/creaking_heart_top_awake");
        Map<String, String> models = new LinkedHashMap<String, String>();
        models.put("block/creaking_heart_active", "block/creaking_heart_awake");
        models.put("block/creaking_heart_active_horizontal", "block/creaking_heart_awake_horizontal");
        Map<String, String> moves = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> texture : textures.entrySet()) {
            addTexturePair(workspace, occupied, moves, texture.getKey(), texture.getValue());
        }
        for (Map.Entry<String, String> model : models.entrySet()) {
            moves.put(VANILLA + "models/" + model.getKey() + ".json", VANILLA + "models/" + model.getValue() + ".json");
        }
        workspace.moveFiles(moves);
        rewriteModelReferences(workspace, 47, models, textures);
        String state = VANILLA + "blockstates/creaking_heart.json";
        if (workspace.hasFile(state)) {
            Map<String, Object> root = readObject(workspace, 47, state);
            Map<String, Object> variants = root == null ? null : object(root.get("variants"));
            if (root != null && (root.containsKey("multipart") || variants == null
                    || variants.keySet().stream().anyMatch(key -> key.matches("(^|.*,)active=(true|false)(,.*|$)")))) {
                warn(workspace, 47, state, "active 布尔状态改为 creaking_heart_state，无法推断新增 dormant 的模型，保留条件并人工迁移");
            }
        }
    }

    private static void migrateMobEffectsAtlas(UpgradeWorkspace workspace) throws IOException {
        String old = VANILLA + "atlases/mob_effects.json";
        String target = VANILLA + "atlases/gui.json";
        if (!workspace.hasFile(old)) {
            return;
        }
        if (Files.exists(workspace.path(target), LinkOption.NOFOLLOW_LINKS)) {
            warn(workspace, 60, old, "状态效果并入 GUI 图集；已有显式 GUI 定义，保留两份原件以供人工合并");
            return;
        }
        Map<String, Object> atlas = readObject(workspace, 60, old);
        if (atlas == null) {
            return;
        }
        Object sources = atlas.get("sources");
        if (atlas.size() != 1 || !(sources instanceof List)) {
            warn(workspace, 60, old, "未知状态效果图集结构，保留原件以供人工合并");
            return;
        }
        List<Object> migrated = new ArrayList<Object>();
        for (Object source : (List<?>) sources) {
            Map<String, Object> entry = migrateMobEffectsSource(object(source));
            if (entry == null) {
                // 过滤和未知源依赖此前的输出与顺序，不能只复制列表中的一部分。
                warn(workspace, 60, old, "仅支持 directory 和 single 图集源；保留整个原件，不把旧过滤规则施加到 GUI");
                return;
            }
            migrated.add(entry);
        }
        if (!migrated.isEmpty()) {
            // c48962f90：GUI 查找时给效果 ID 的 path 加 mob_effect/，保留 namespace。
            atlas.put("sources", migrated);
            workspace.writeText(target, Json.write(atlas));
        }
    }

    private static Map<String, Object> migrateMobEffectsSource(Map<String, Object> old) {
        if (old == null) {
            return null;
        }
        Object type = old.get("type");
        Map<String, Object> entry = new LinkedHashMap<String, Object>(old);
        if ("directory".equals(type) || "minecraft:directory".equals(type)) {
            Object source = old.get("source");
            Object prefix = old.get("prefix");
            if (old.size() != 3 || !(source instanceof String) || !resourcePath((String) source)
                    || !(prefix instanceof String) || !resourcePath(prefix + "atlas_probe")) {
                return null;
            }
            entry.put("prefix", "mob_effect/" + prefix);
        } else if ("single".equals(type) || "minecraft:single".equals(type)) {
            Object resource = old.get("resource");
            Object sprite = old.getOrDefault("sprite", resource);
            if (old.size() != (old.containsKey("sprite") ? 3 : 2) || !resourceId(resource) || !resourceId(sprite)) {
                return null;
            }
            String id = (String) sprite;
            int colon = id.indexOf(':') + 1;
            entry.put("sprite", id.substring(0, colon) + "mob_effect/" + id.substring(colon));
        } else {
            return null;
        }
        return entry;
    }

    private static void rewriteModelReferences(UpgradeWorkspace workspace, int format, Map<String, String> models,
                                               Map<String, String> textures) throws IOException {
        for (String file : workspace.files()) {
            String path = assetPath(file);
            if (path == null || !path.endsWith(".json")) {
                continue;
            }
            boolean model = path.startsWith("models/") && !path.startsWith("models/equipment/");
            boolean state = !models.isEmpty() && path.startsWith("blockstates/");
            if (!model && !state) {
                continue;
            }
            Map<String, Object> root = readObject(workspace, format, file);
            if (root == null) {
                continue;
            }
            boolean changed = model ? rewriteGeometry(root, models, textures) : rewriteBlockstate(root, models);
            if (changed) {
                workspace.writeText(file, Json.write(root));
            }
        }
    }

    private static boolean rewriteGeometry(Map<String, Object> root, Map<String, String> models, Map<String, String> textures) {
        boolean changed = rewriteId(root, "parent", models);
        Map<String, Object> slots = object(root.get("textures"));
        if (slots != null) {
            for (String slot : slots.keySet()) {
                changed |= rewriteId(slots, slot, textures);
            }
        }
        if (!models.isEmpty() && root.get("overrides") instanceof List) {
            for (Object override : (List<?>) root.get("overrides")) {
                changed |= rewriteId(object(override), "model", models);
            }
        }
        if (root.get("elements") instanceof List) {
            for (Object value : (List<?>) root.get("elements")) {
                Map<String, Object> element = object(value);
                Map<String, Object> faces = element == null ? null : object(element.get("faces"));
                if (faces != null) {
                    for (Object face : faces.values()) {
                        changed |= rewriteId(object(face), "texture", textures);
                    }
                }
            }
        }
        return changed;
    }

    private static boolean rewriteBlockstate(Map<String, Object> root, Map<String, String> models) {
        boolean changed = false;
        Map<String, Object> variants = object(root.get("variants"));
        if (variants != null) {
            for (Object variant : variants.values()) {
                changed |= rewriteVariant(variant, models);
            }
        }
        if (root.get("multipart") instanceof List) {
            for (Object value : (List<?>) root.get("multipart")) {
                Map<String, Object> part = object(value);
                if (part != null) {
                    changed |= rewriteVariant(part.get("apply"), models);
                }
            }
        }
        return changed;
    }

    private static boolean rewriteVariant(Object value, Map<String, String> models) {
        if (!(value instanceof List)) {
            return rewriteId(object(value), "model", models);
        }
        boolean changed = false;
        for (Object variant : (List<?>) value) {
            changed |= rewriteId(object(variant), "model", models);
        }
        return changed;
    }

    private static boolean rewriteId(Map<String, Object> object, String key, Map<String, String> names) {
        if (object == null || !(object.get(key) instanceof String)) {
            return false;
        }
        String old = (String) object.get(key);
        boolean vanilla = old.startsWith("minecraft:");
        String replacement = names.get(vanilla ? old.substring("minecraft:".length()) : old);
        if (replacement == null) {
            return false;
        }
        object.put(key, vanilla ? "minecraft:" + replacement : replacement);
        return true;
    }

    private static void migrateItemEntrypoints(UpgradeWorkspace workspace) throws IOException {
        Map<String, String> vanillaEntries = null;
        Set<String> occupied = occupiedFiles(workspace);
        for (String file : workspace.files()) {
            String relative = assetPath(file);
            if (relative == null || !relative.startsWith("models/item/") || !relative.endsWith(".json")) {
                continue;
            }
            String item = relative.substring("models/item/".length(), relative.length() - ".json".length());
            if (!resourcePath(item)) {
                continue;
            }
            String assetRoot = file.substring(0, file.length() - relative.length());
            String output = assetRoot + "items/" + item + ".json";
            if (occupied.contains(fold(output)) || Files.exists(workspace.path(output), LinkOption.NOFOLLOW_LINKS)) {
                // 显式新定义优先；不要解析或覆盖作者自己的入口。
                continue;
            }
            Map<String, Object> old = readObject(workspace, 44, file);
            if (old == null) {
                continue;
            }
            Object legacy = old.get("overrides");
            boolean hasOverrides = old.containsKey("overrides")
                    && (!(legacy instanceof List) || !((List<?>) legacy).isEmpty());
            if (!VANILLA.equals(assetRoot)) {
                if (hasOverrides) {
                    warn(workspace, 44, file, "不能从自定义几何文件名推断真实物品入口；保留旧 predicate，需定义 items 入口");
                }
                continue;
            }
            if (vanillaEntries == null) {
                vanillaEntries = loadVanillaEntrypoints();
            }
            String vanillaModel = vanillaEntries.get(item);
            if (vanillaModel == null) {
                if (hasOverrides) {
                    warn(workspace, 44, file, "不是已证实的原版入口；可能是变体或辅助模型，不生成 items 定义");
                }
                continue;
            }
            if ("-".equals(vanillaModel)) {
                warn(workspace, 44, file, "该物品的原版入口有额外行为（如染色、条件或特殊渲染），保留旧模型；若显示异常需人工定义");
                continue;
            }
            Map<String, Object> entry = modelEntry("minecraft:item/" + item);
            if (hasOverrides) {
                entry = customModelDataDispatch(entry, legacy);
                if (entry == null) {
                    warn(workspace, 44, file, "只转换阈值可无歧义解析为有限 float 的 CustomModelData 单谓词；保留旧模型和 predicate，需人工调整");
                    continue;
                }
            }
            Map<String, Object> root = new LinkedHashMap<String, Object>();
            root.put("model", entry);
            workspace.writeText(output, Json.write(root));
            occupied.add(fold(output));
        }
    }

    private static Map<String, String> loadVanillaEntrypoints() throws IOException {
        InputStream stream = IntermediatePackUpgrades.class.getResourceAsStream(ITEM_ENTRIES);
        if (stream == null) {
            throw new IOException("Missing item migration resource " + ITEM_ENTRIES);
        }
        Map<String, String> entries = new LinkedHashMap<String, String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf(' ');
                if (separator < 1) {
                    throw new IOException("Invalid item migration resource row: " + line);
                }
                String id = line.substring(0, separator);
                String model = line.substring(separator + 1);
                if (!resourcePath(id) || (!"-".equals(model) && !resourcePath(model))
                        || entries.put(id, model) != null) {
                    throw new IOException("Invalid or duplicate item migration resource row: " + line);
                }
            }
        }
        return entries;
    }

    private static Map<String, Object> modelEntry(String model) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("type", "minecraft:model");
        value.put("model", model);
        return value;
    }

    private static Map<String, Object> customModelDataDispatch(Map<String, Object> fallback, Object overrides) {
        if (!(overrides instanceof List)) {
            return null;
        }
        List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
        float laterMinimum = Float.POSITIVE_INFINITY;
        // 旧 BakedOverrides 先试后项；后面已有同样或更低的阈值时，这一项永远不会命中。
        List<?> legacyEntries = (List<?>) overrides;
        for (int i = legacyEntries.size() - 1; i >= 0; i--) {
            Map<String, Object> old = object(legacyEntries.get(i));
            if (old == null || old.size() != 2 || !resourceId(old.get("model"))) {
                return null;
            }
            Map<String, Object> predicates = object(old.get("predicate"));
            if (predicates == null || predicates.size() != 1) {
                return null;
            }
            Object threshold = predicates.containsKey("custom_model_data") ? predicates.get("custom_model_data")
                    : predicates.get("minecraft:custom_model_data");
            if (!(threshold instanceof Number)) {
                return null;
            }
            float value = legacyFloatThreshold((Number) threshold);
            if (!Float.isFinite(value)) {
                return null;
            }
            if (value >= laterMinimum) {
                continue;
            }
            laterMinimum = value;
            String reference = (String) old.get("model");
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            // 用可往返同一 float 的十进制数，不走 Json 对 Float/Double 的整数截断分支。
            BigDecimal encoded = new BigDecimal(Float.toString(value)).stripTrailingZeros();
            entry.put("threshold", encoded.scale() < 0 ? encoded.setScale(0) : encoded);
            entry.put("model", modelEntry(reference.indexOf(':') < 0 ? "minecraft:" + reference : reference));
            entries.add(entry);
        }
        Collections.reverse(entries);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("type", "minecraft:range_dispatch");
        result.put("property", "minecraft:custom_model_data");
        // 旧缺失组件和新缺失组件/索引都读作 0，正常参与阈值匹配，而不是直接走 fallback。
        result.put("index", Integer.valueOf(0));
        result.put("fallback", fallback);
        result.put("entries", entries);
        return result;
    }

    private static float legacyFloatThreshold(Number number) {
        float value = number.floatValue();
        if (number instanceof Double && Float.isFinite(value)) {
            double parsed = number.doubleValue();
            if (parsed != value) {
                // 原版直接将 JSON 数字读为 float。核心 JSON 先读为 Double：恰在 float 中点时，
                // 丢掉的十进制尾数可能改变原版舍入方向，无法无歧义转换。
                float adjacent = Math.nextAfter(value, parsed);
                if (parsed == ((double) value + adjacent) * 0.5) {
                    return Float.NaN;
                }
            }
        }
        // 旧组件是 int，转 float 后只有正零；旧阈值比较不区分零的符号。
        return value == 0.0f ? 0.0f : value;
    }

    private static Map<String, Object> readObject(UpgradeWorkspace workspace, int format, String file) throws IOException {
        if (Files.size(workspace.path(file)) > 4 * 1024 * 1024) {
            warn(workspace, format, file, "JSON 超过迁移规则读取上限，保留原件");
            return null;
        }
        String text = workspace.readText(file);
        if (tooDeep(text)) {
            warn(workspace, format, file, "JSON 嵌套超过迁移规则读取上限，保留原件");
            return null;
        }
        try {
            return Json.parseObject(text);
        } catch (JsonException ex) {
            warn(workspace, format, file, "无法解析 JSON，保留原件");
            return null;
        }
    }

    // Json 的读取和写回均为递归；在接触不受信包的内容前先限制嵌套。
    private static boolean tooDeep(String text) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char next = text.charAt(i);
            if (inString && next == '\\') {
                i++; // 跳过被转义字符。
            } else if (next == '"') {
                inString = !inString;
            } else if (!inString && (next == '[' || next == '{')) {
                if (++depth > 128) {
                    return true;
                }
            } else if (!inString && (next == ']' || next == '}')) {
                depth--;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static String assetPath(String file) {
        if (!file.startsWith("assets/")) {
            return null;
        }
        int slash = file.indexOf('/', "assets/".length());
        if (slash < 0 || !file.substring("assets/".length(), slash).matches("[a-z0-9_.-]+")) {
            return null;
        }
        return file.substring(slash + 1);
    }

    private static boolean resourceId(Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        String id = (String) value;
        int colon = id.indexOf(':');
        return colon < 0 ? resourcePath(id)
                : id.substring(0, colon).matches("[a-z0-9_.-]+") && resourcePath(id.substring(colon + 1));
    }

    private static boolean resourcePath(String path) {
        if (!path.matches("[a-z0-9_./-]+")) {
            return false;
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> occupiedFiles(UpgradeWorkspace workspace) throws IOException {
        Set<String> occupied = new HashSet<String>();
        for (String file : workspace.files()) {
            occupied.add(fold(file));
        }
        return occupied;
    }

    private static boolean occupied(UpgradeWorkspace workspace, Set<String> files, String name) throws IOException {
        return files.contains(fold(name)) || Files.exists(workspace.path(name), LinkOption.NOFOLLOW_LINKS);
    }

    private static String fold(String file) {
        return file.toLowerCase(Locale.ROOT);
    }

    private static void warnAssets(UpgradeWorkspace workspace, int format, String message, String... patterns) throws IOException {
        for (String file : workspace.files()) {
            String path = assetPath(file);
            if (path == null) {
                continue;
            }
            for (String pattern : patterns) {
                if (pattern.endsWith("/") ? ("/" + path).startsWith(pattern) : path.endsWith(pattern)) {
                    warn(workspace, format, file, message);
                    return;
                }
            }
        }
    }

    private static void warn(UpgradeWorkspace workspace, int format, String file, String message) {
        workspace.warn("进入格式 " + format + "：" + file + "：" + message);
    }
}
