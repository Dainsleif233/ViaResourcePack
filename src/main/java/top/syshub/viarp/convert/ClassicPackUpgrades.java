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
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonException;
import top.syshub.viarp.pack.PackFormat;

/** 仅执行有原版依据的迁移子集；依据见 docs/research/classic-pack-formats.md。 */
final class ClassicPackUpgrades {

    private static final String VANILLA = "assets/minecraft/";
    private static final int MAX_TEXT_BYTES = 4 * 1024 * 1024;
    private static final String BLOCK_ATLAS = VANILLA + "atlases/blocks.json";
    private static final String WIDGETS = VANILLA + "textures/gui/widgets.png";
    private static final String FLAT_TEXTURE_TABLE = "/viarp/migrations/classic/format-4-textures.tsv";
    // 这些别名另以原版模型或语言记录确认，不能用图像同一性筛掉正式版的重绘。
    private static final Map<String, String> FLAT_TEXTURE_EXCEPTIONS = pairs(
            "blocks/grass_top", "block/grass_block_top",
            "blocks/grass_side", "block/grass_block_side",
            "blocks/grass_side_overlay", "block/grass_block_side_overlay",
            "blocks/grass_side_snowed", "block/grass_block_snow",
            "blocks/planks_oak", "block/oak_planks",
            "blocks/wool_colored_red", "block/red_wool",
            "blocks/water_still", "block/water_still",
            "items/record_13", "item/music_disc_13",
            "items/dye_powder_red", "item/red_dye",
            "item/rose_red", "item/red_dye");
    private static final String FLAT_LANG_TABLE = "/viarp/migrations/classic/format-4-language.tsv";
    private static final String EARLY_TEXTURE_TABLE = "/viarp/migrations/classic/format-2-early-textures.tsv";
    private static final Map<String, String> PATH_TEXTURES = pairs(
            "block/grass_path_top", "block/dirt_path_top", "block/grass_path_side", "block/dirt_path_side");
    private static final Map<String, String> PATH_MODELS = pairs(
            "block/grass_path", "block/dirt_path", "item/grass_path", "item/dirt_path");
    private static final Map<String, String> PATH_LANG = pairs("block.minecraft.grass_path", "block.minecraft.dirt_path");

    private ClassicPackUpgrades() {
    }

    static void apply(PackFormat entered, UpgradeWorkspace workspace) throws IOException {
        if (entered.minor() != 0) {
            return;
        }
        switch (entered.major()) {
            case 2:
                ClassicClockTextures.apply(workspace);
                upgradeModels(workspace);
                ClassicSoundUpgrades.migrateLegacyFiles(workspace);
                ClassicSoundUpgrades.migrateEvents(workspace, 2);
                break;
            case 3:
                lowercaseAssets(workspace);
                break;
            case 4:
                flatten(workspace);
                ClassicSoundUpgrades.migrateEvents(workspace, 4);
                break;
            case 5:
                upgradeEntityTextures(workspace);
                break;
            case 6:
                warnOldWalls(workspace);
                break;
            case 7:
                renameDirtPath(workspace);
                copySquidTexture(workspace);
                break;
            case 8:
                warnFile(workspace, 8, VANILLA + "textures/gui/container/inventory.png", "新版窄布局药效图块需要作者补画");
                break;
            case 9:
                // filter 是可选新增的元数据，没有旧资源需要据此移动。
                break;
            case 11:
                // 旧资源适配层被移除；再次提示依赖旧 UV 的资源，不能宣称仍由游戏代为修复。
                warnOldEntityLayouts(workspace, 11);
                break;
            case 12:
                upgradeAtlas(workspace);
                break;
            case 13:
                upgradeGlintAndSmithing(workspace);
                break;
            case 14:
                warnFile(workspace, 14, VANILLA + "textures/gui/title/minecraft.png", "主菜单 Logo 图块布局已变化，需人工调整坐标");
                warnFile(workspace, 14, "assets/realms/textures/gui/realms/invite_icon.png", "Realms 邀请图块布局已变化，需人工调整坐标");
                break;
            case 15:
                ClassicUnicodeFonts.apply(workspace);
                warnFile(workspace, 15, VANILLA + "texts/credits.json", "旧版 credits 的 titles 层级及页面呈现需核对 disciplines");
                break;
            case 16:
                upgradeGui(workspace);
                break;
            case 17:
                warnFile(workspace, 17, WIDGETS, "文本框和滚动条现在使用 widget/text_field{,_highlighted} 与 widget/scroller，未从旧纹理推测绘制");
                break;
            default:
                // 10 未发行；其余版本由其他迁移组负责。
                break;
        }
    }

    private static void upgradeModels(UpgradeWorkspace workspace) throws IOException {
        List<String> files = workspace.files();
        Map<String, String> earlyTextures = renameEarlyTextures(workspace, files);
        // builtin/generated 仍是有效的生成标记。只在整条原版父链均未被包覆盖时替换，
        // 否则可能改变继承语义，或让生成根经由包内模型（包括其他命名空间）指回自己。
        boolean vanillaGenerated = !files.contains(VANILLA + "models/item/generated.json");
        boolean vanillaHandheld = vanillaGenerated && !files.contains(VANILLA + "models/item/handheld.json");
        boolean vanillaHandheldRod = vanillaHandheld && !files.contains(VANILLA + "models/item/handheld_rod.json");
        for (String file : files) {
            if (!isAsset(file, "/models/", ".json")) {
                continue;
            }
            Map<String, Object> model = readObject(workspace, 2, file);
            if (model == null) {
                continue;
            }
            Object parent = model.get("parent");
            boolean rod = file.equals(VANILLA + "models/item/fishing_rod.json");
            boolean changed = rewriteModel(model, id -> id, id -> replaceVanilla(id, earlyTextures));
            if ("builtin/generated".equals(parent) || "minecraft:builtin/generated".equals(parent)) {
                boolean sword = file.equals(VANILLA + "models/item/diamond_sword.json");
                if (rod ? vanillaHandheldRod : sword ? vanillaHandheld : vanillaGenerated) {
                    String target = rod ? "item/handheld_rod" : sword ? "item/handheld" : "item/generated";
                    model.put("parent", ((String) parent).startsWith("minecraft:") ? "minecraft:" + target : target);
                    changed = true;
                }
                if (!model.containsKey("overrides")) {
                    if (file.equals(VANILLA + "models/item/bow.json")) {
                        List<Object> overrides = new ArrayList<Object>();
                        overrides.add(override("pulling", "item/bow_pulling_0", null));
                        overrides.add(override("pulling", "item/bow_pulling_1", 0.65));
                        overrides.add(override("pulling", "item/bow_pulling_2", 0.9));
                        model.put("overrides", overrides);
                        changed = true;
                    } else if (rod) {
                        List<Object> overrides = new ArrayList<Object>();
                        overrides.add(override("cast", "item/fishing_rod_cast", null));
                        model.put("overrides", overrides);
                        changed = true;
                    }
                }
            } else if ("builtin/clock".equals(parent) || "builtin/compass".equals(parent)
                    || "minecraft:builtin/clock".equals(parent) || "minecraft:builtin/compass".equals(parent)) {
                warn(workspace, 2, file, "旧版钟表/指南针依赖动态帧；保留旧 parent 和贴图，不生成帧模型");
            }
            Map<String, Object> display = object(model.get("display"));
            if (display != null) {
                for (String view : new String[]{"firstperson", "thirdperson"}) {
                    if (object(display.get(view)) == null) {
                        continue;
                    }
                    Object old = display.remove(view);
                    if (!display.containsKey(view + "_righthand")) {
                        display.put(view + "_righthand", old);
                    } else if (!old.equals(display.get(view + "_righthand"))) {
                        warn(workspace, 2, file, "旧 " + view + " 与显式右手变换不同；采用显式右手变换");
                    }
                    if (!display.containsKey(view + "_lefthand")) {
                        warn(workspace, 2, file, view + " 无独立左手变换；使用客户端右手回退，未推测镜像参数");
                    }
                    changed = true;
                }
            }
            if (changed) {
                workspace.writeText(file, Json.write(model));
            }
        }
    }

    private static Map<String, Object> override(String predicate, String model, Double pull) {
        Map<String, Object> predicates = new LinkedHashMap<String, Object>();
        predicates.put(predicate, 1);
        if (pull != null) {
            predicates.put("pull", pull);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("predicate", predicates);
        result.put("model", model);
        return result;
    }

    private static void lowercaseAssets(UpgradeWorkspace workspace) throws IOException {
        List<String> files = workspace.files();
        Set<String> original = new HashSet<String>(files);
        Map<String, String> changes = new LinkedHashMap<String, String>();
        for (String file : files) {
            if (!file.startsWith("assets/")) {
                continue;
            }
            if (!file.matches("assets/[A-Za-z0-9_.-]+/[A-Za-z0-9/_.-]+")) {
                warn(workspace, 3, file, "目标资源 ID 不支持原路径中的字符；保留原文件");
                continue;
            }
            String target = fold(file);
            if (!file.equals(target)) {
                changes.put(file, target);
            }
        }
        workspace.moveFiles(changes);
        rewriteAssets(workspace, 3,
                id -> lowercaseId(workspace, id, "models/", ".json", original),
                id -> lowercaseId(workspace, id, "textures/", ".png", original),
                id -> lowercaseStateModelId(workspace, id, original),
                id -> id, true);
        lowerLanguageMetadata(workspace);
    }

    private static String lowercaseStateModelId(UpgradeWorkspace workspace, String id, Set<String> files) {
        ResourceId parsed = ResourceId.parse(id);
        String folder = "models/";
        if (parsed != null && !files.contains("assets/" + parsed.namespace + "/models/" + parsed.path + ".json")) {
            folder = "models/block/";
        }
        return lowercaseId(workspace, id, folder, ".json", files);
    }

    private static String lowercaseId(UpgradeWorkspace workspace, String id, String folder, String suffix,
                                      Set<String> files) {
        ResourceId parsed = ResourceId.parse(id);
        String lower = fold(id);
        if (parsed == null || lower.equals(id)) {
            return id;
        }
        if ("minecraft".equals(fold(parsed.namespace))
                || files.contains("assets/" + parsed.namespace + "/" + folder + parsed.path + suffix)) {
            return lower;
        }
        warn(workspace, 3, id, "该自定义资源引用没有可核对的包内路径；保留原 ID");
        return id;
    }

    private static void lowerLanguageMetadata(UpgradeWorkspace workspace) throws IOException {
        if (!workspace.hasFile("pack.mcmeta")) {
            return;
        }
        Map<String, Object> root = readObject(workspace, 3, "pack.mcmeta");
        Map<String, Object> language = root == null ? null : object(root.get("language"));
        if (language == null) {
            return;
        }
        Map<String, Object> normalized = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : language.entrySet()) {
            String key = entry.getKey();
            String folded = key.matches("[A-Za-z0-9_]+") ? fold(key) : key;
            if (normalized.containsKey(folded)) {
                throw new IOException("Pack migration locale collision in pack.mcmeta: " + key + " -> " + folded);
            }
            normalized.put(folded, entry.getValue());
        }
        if (!normalized.equals(language)) {
            root.put("language", normalized);
            workspace.writeText("pack.mcmeta", Json.write(root));
        }
    }

    private static void flatten(UpgradeWorkspace workspace) throws IOException {
        ClassicBlockStates.apply(workspace);
        List<String> files = workspace.files();
        Set<String> original = new HashSet<String>(files);
        Map<String, String> changes = new LinkedHashMap<String, String>();
        String textureDirectory = VANILLA + "textures/";
        Map<String, String> textures = unambiguousTextureMoves(workspace, original, textureDirectory);
        for (String file : files) {
            if (file.startsWith(textureDirectory)) {
                String stem = textureStem(file, textureDirectory);
                if (stem != null) {
                    String target = textures.get(stem);
                    if (target != null) {
                        changes.put(file, textureDirectory + target + file.substring(textureDirectory.length() + stem.length()));
                    } else if (stem.startsWith("blocks/") || stem.startsWith("items/")) {
                        // 未核对的 vanilla ID 不冒充新旧同名，否则可能绑定到不同对象。
                        warn(workspace, 4, file, "未核对原版 flatten 一对一映射；保留文件与原引用，不猜测目标");
                    }
                }
            } else if (file.startsWith(VANILLA + "recipes/")) {
                warn(workspace, 4, file, "配方已迁入世界数据包；保留原文件，不转换为数据包");
            } else if (file.startsWith(VANILLA + "blockstates/") && file.endsWith(".json")
                    && (file.endsWith("/log.json") || file.endsWith("/log2.json"))) {
                warn(workspace, 4, file, "旧状态包含多种目标方块；保留文件名和状态条件，不拆分变种");
            }
        }
        workspace.moveFiles(changes);
        Map<String, String> language = readMappings(FLAT_LANG_TABLE, "[A-Za-z0-9_.-]+", "[a-z0-9_.-]+");
        rewriteAssets(workspace, 4, UnaryOperator.identity(),
                id -> flattenTextureReference(workspace, id, textures),
                id -> flattenStateModel(id, original), id -> language.getOrDefault(id, id), false);
        ClassicModelOverrides.apply(workspace);
        convertLanguages(workspace, language);
    }

    private static Map<String, String> unambiguousTextureMoves(UpgradeWorkspace workspace, Set<String> files,
                                                              String directory) throws IOException {
        Map<String, String> textures = flattenTextureTable();
        Set<String> occupied = foldedFiles(files);
        Map<String, Integer> targets = new HashMap<String, Integer>();
        List<String> present = new ArrayList<String>();
        for (Map.Entry<String, String> pair : textures.entrySet()) {
            String source = directory + pair.getKey() + ".png";
            if (files.contains(source) || files.contains(source + ".mcmeta")) {
                present.add(pair.getKey());
                targets.merge(pair.getValue(), 1, Integer::sum);
            }
        }
        for (String id : present) {
            String mapped = textures.get(id);
            String target = directory + mapped + ".png";
            if (targets.get(mapped) > 1 || occupied.contains(target) || occupied.contains(target + ".mcmeta")
                    || Files.exists(workspace.path(target)) || Files.exists(workspace.path(target + ".mcmeta"))) {
                warn(workspace, 4, id, "多个旧来源或已有目标纹理/侧车；保留旧纹理和原引用，不覆盖 " + target);
                textures.remove(id);
            }
        }
        return textures;
    }

    private static Map<String, String> renameEarlyTextures(UpgradeWorkspace workspace, List<String> files)
            throws IOException {
        Map<String, String> candidates = readMappings(EARLY_TEXTURE_TABLE, "items/[a-z0-9_.-]+", "items/[a-z0-9_.-]+");
        Set<String> exact = new HashSet<String>(files);
        Set<String> occupied = foldedFiles(files);
        Map<String, String> applied = new LinkedHashMap<String, String>();
        String root = VANILLA + "textures/";
        for (Map.Entry<String, String> entry : candidates.entrySet()) {
            String source = root + entry.getKey() + ".png";
            String target = root + entry.getValue() + ".png";
            boolean hasOld = exact.contains(source) || exact.contains(source + ".mcmeta");
            if (hasOld && (occupied.contains(target) || occupied.contains(target + ".mcmeta")
                    || Files.exists(workspace.path(target)) || Files.exists(workspace.path(target + ".mcmeta")))) {
                warn(workspace, 2, source, "已有新名纹理或侧车，保留两套文件与原引用");
            } else {
                applied.put(entry.getKey(), entry.getValue());
            }
        }
        Map<String, String> moves = new LinkedHashMap<String, String>();
        scheduleTextures(moves, applied);
        // 格式 1 允许大小写；不要把不同拼法的用户文件当作同一个已确认的旧 ID。
        moves.keySet().retainAll(exact);
        workspace.moveFiles(moves);
        return applied;
    }

    private static Map<String, String> flattenTextureTable() throws IOException {
        Map<String, String> textures = readMappings(FLAT_TEXTURE_TABLE,
                "(?:blocks|items)/[a-z0-9_.-]+", "(?:block|item)/[a-z0-9_.-]+");
        textures.putAll(FLAT_TEXTURE_EXCEPTIONS);
        return textures;
    }

    static Map<String, String> readMappings(String resource, String sourcePattern, String targetPattern) throws IOException {
        InputStream stream = ClassicPackUpgrades.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("Missing classic migration table: " + resource);
        }
        Map<String, String> mappings = new LinkedHashMap<String, String>();
        try (BufferedReader entries = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = entries.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }
                String[] pair = line.split("\t", -1);
                if (pair.length != 2 || !pair[0].matches(sourcePattern) || !pair[1].matches(targetPattern)
                        || mappings.putIfAbsent(pair[0], pair[1]) != null) {
                    throw new IOException("Invalid classic migration table entry: " + resource + ": " + line);
                }
            }
        }
        return mappings;
    }

    private static String flattenTextureReference(UpgradeWorkspace workspace, String id, Map<String, String> textures) {
        ResourceId parsed = ResourceId.parse(id);
        if (parsed != null && "minecraft".equals(parsed.namespace) && !textures.containsKey(parsed.path)
                && (parsed.path.startsWith("blocks/") || parsed.path.startsWith("items/"))) {
            warn(workspace, 4, id, "旧原版纹理 ID 无已证实的目标映射；保留引用");
        }
        return replaceVanilla(id, textures);
    }

    private static String flattenStateModel(String id, Set<String> original) {
        ResourceId parsed = ResourceId.parse(id);
        if (parsed == null) {
            return id;
        }
        String path = parsed.path;
        if (!path.startsWith("block/") && !path.startsWith("item/") && !path.startsWith("builtin/")) {
            String root = "assets/" + parsed.namespace + "/models/";
            // 不移动自定义模型。仅在同包可见旧布局，或原版的传统裸 ID 时补目录。
            if (!original.contains(root + path + ".json")
                    && (original.contains(root + "block/" + path + ".json")
                    || ("minecraft".equals(parsed.namespace) && path.indexOf('/') < 0))) {
                path = "block/" + path;
            }
        }
        return parsed.withPath(path);
    }

    private static void convertLanguages(UpgradeWorkspace workspace, Map<String, String> language) throws IOException {
        List<String> files = workspace.files();
        Set<String> occupied = foldedFiles(files);
        for (String file : files) {
            if (!isAsset(file, "/lang/", ".lang")) {
                continue;
            }
            String locale = file.substring(file.lastIndexOf('/') + 1, file.length() - ".lang".length());
            if (!locale.matches("[a-z0-9_]+") || !file.equals("assets/" + namespace(file) + "/lang/" + locale + ".lang")) {
                warn(workspace, 4, file, "无法确认语言文件的 locale；保留旧 .lang");
                continue;
            }
            String target = file.substring(0, file.length() - 5) + ".json";
            if (occupied.contains(fold(target))) {
                warn(workspace, 4, file, "目标 " + target + " 已存在；保留现有 JSON 和旧 .lang，不合并覆盖");
                continue;
            }
            String raw = text(workspace, 4, file);
            if (raw == null) {
                continue;
            }
            Map<String, Object> entries = new LinkedHashMap<String, Object>();
            Set<String> ambiguous = new HashSet<String>();
            Set<String> legacyKeys = new LinkedHashSet<String>();
            boolean vanilla = file.startsWith(VANILLA);
            try (BufferedReader lines = new BufferedReader(new StringReader(raw))) {
                int lineNumber = 0;
                String line;
                while ((line = lines.readLine()) != null) {
                    lineNumber++;
                    if (line.trim().isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int split = line.indexOf('=');
                    if (split <= 0) {
                        warn(workspace, 4, file, "第 " + lineNumber + " 行无有效键值；旧 .lang 保留，JSON 仅含可解析行");
                        continue;
                    }
                    String key = line.substring(0, split);
                    String mapped = vanilla ? language.getOrDefault(key, key) : key;
                    if (vanilla && ("item.record.name".equals(key) || "tile.wood.name".equals(key))) {
                        warn(workspace, 4, file, "旧键 " + key + " 对应多个目标对象；以原键保留，不复制到新键");
                    } else if (vanilla && key.equals(mapped) && key.endsWith(".name")
                            && (key.startsWith("tile.") || key.startsWith("item."))) {
                        legacyKeys.add(key);
                    }
                    String value = line.substring(split + 1);
                    if (ambiguous.contains(mapped)) {
                        continue;
                    }
                    if (entries.containsKey(mapped) && !value.equals(entries.get(mapped))) {
                        warn(workspace, 4, file, "第 " + lineNumber + " 行翻译键碰撞 " + mapped + "；旧 .lang 保留，JSON 省略冲突项");
                        ambiguous.add(mapped);
                        entries.remove(mapped);
                    } else {
                        entries.put(mapped, value);
                    }
                }
            }
            if (!legacyKeys.isEmpty()) {
                warn(workspace, 4, file, "这些旧翻译键没有核准的新键，在 JSON 中按原键保留：" + summarize(legacyKeys));
            }
            workspace.writeText(target, Json.write(entries));
            // 原始字节保留，既便于追查不能翻译的行，也不影响目标的 JSON 语言加载。
            occupied.add(fold(target));
        }
    }

    private static void upgradeEntityTextures(UpgradeWorkspace workspace) throws IOException {
        Map<String, String> textures = pairs("entity/iron_golem", "entity/iron_golem/iron_golem");
        Map<String, String> changes = new LinkedHashMap<String, String>();
        scheduleTextures(changes, textures);
        workspace.moveFiles(changes);
        rewriteAssets(workspace, 5, id -> id, id -> replaceVanilla(id, textures), id -> id, id -> id, false);
        ClassicEntityTextures.apply(workspace);
    }

    private static void warnOldEntityLayouts(UpgradeWorkspace workspace, int edge) throws IOException {
        for (String variant : new String[] {"normal", "trapped", "christmas"}) {
            String chest = VANILLA + "textures/entity/chest/" + variant;
            if (workspace.hasFile(chest + "_double.png")
                    && (!workspace.hasFile(chest + "_left.png") || !workspace.hasFile(chest + "_right.png"))) {
                warn(workspace, edge, chest + "_double.png", "原版不再从旧双箱图生成缺少的左右纹理");
            }
        }
    }

    private static void warnOldWalls(UpgradeWorkspace workspace) throws IOException {
        for (String file : workspace.files()) {
            if (!file.startsWith(VANILLA + "blockstates/") || !file.endsWith("_wall.json")) {
                continue;
            }
            Map<String, Object> state = readObject(workspace, 6, file);
            if (state == null) {
                continue;
            }
            if (hasOldWallCondition(state.get("multipart")) || hasOldWallCondition(state.get("variants"))) {
                warn(workspace, 6, file, "旧墙方向 true/false 已改为 none/low/tall，无法从旧几何确定高低墙；保留原状态和模型");
            }
        }
    }

    private static boolean hasOldWallCondition(Object node) {
        if (node instanceof List) {
            for (Object child : (List<?>) node) {
                if (hasOldWallCondition(child)) {
                    return true;
                }
            }
        } else if (node instanceof Map) {
            for (Map.Entry<String, Object> entry : object(node).entrySet()) {
                for (String side : new String[]{"north", "east", "south", "west"}) {
                    if ((side.equals(entry.getKey()) && (entry.getValue() instanceof Boolean
                            || "true".equals(entry.getValue()) || "false".equals(entry.getValue())))
                            || entry.getKey().matches("(?:.*,)?" + side + "=(?:true|false)(?:,.*)?")) {
                        return true;
                    }
                }
                if (hasOldWallCondition(entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void renameDirtPath(UpgradeWorkspace workspace) throws IOException {
        Map<String, String> changes = new LinkedHashMap<String, String>();
        scheduleTextures(changes, PATH_TEXTURES);
        schedule(changes, VANILLA + "models/", ".json", PATH_MODELS);
        schedule(changes, VANILLA + "blockstates/", ".json", pairs("grass_path", "dirt_path"));
        workspace.moveFiles(changes);
        rewriteAssets(workspace, 7, id -> replaceVanilla(id, PATH_MODELS), id -> replaceVanilla(id, PATH_TEXTURES),
                id -> replaceVanilla(id, PATH_MODELS), id -> PATH_LANG.getOrDefault(id, id), false);
        for (String file : workspace.files()) {
            if (file.startsWith(VANILLA + "lang/") && file.endsWith(".json")) {
                Map<String, Object> language = readObject(workspace, 7, file);
                if (language != null && renameKeys(workspace, 7, file, language, id -> PATH_LANG.getOrDefault(id, id))) {
                    workspace.writeText(file, Json.write(language));
                }
            }
        }
        warnFile(workspace, 7, VANILLA + "textures/entity/zombie/drowned.png", "新版溺尸手臂镜像 UV 已变");
        warnFile(workspace, 7, VANILLA + "textures/entity/zombie/drowned_outer_layer.png", "新版溺尸外层手臂镜像 UV 已变");
    }

    private static void copySquidTexture(UpgradeWorkspace workspace) throws IOException {
        String source = VANILLA + "textures/entity/squid.png";
        if (!workspace.hasFile(source)) {
            return;
        }
        // 早期格式 7 仍读取旧路径；正式 1.17 只换了路径，保留两端的读取位置。
        String target = VANILLA + "textures/entity/squid/squid.png";
        Set<String> existing = foldedFiles(workspace.files());
        if (existing.contains(target) || existing.contains(target + ".mcmeta")
                || Files.exists(workspace.path(target)) || Files.exists(workspace.path(target + ".mcmeta"))) {
            warn(workspace, 7, source, "普通鱿鱼的新纹理或侧车已存在，不覆盖 " + target);
            return;
        }
        Path destination = workspace.path(target);
        Files.createDirectories(destination.getParent());
        Files.copy(workspace.path(source), destination);
        if (workspace.hasFile(source + ".mcmeta")) {
            Files.copy(workspace.path(source + ".mcmeta"), workspace.path(target + ".mcmeta"));
        }
    }

    private static void upgradeAtlas(UpgradeWorkspace workspace) throws IOException {
        List<String> files = workspace.files();
        Set<String> existing = new HashSet<String>(files);
        Set<String> candidates = new LinkedHashSet<String>();
        for (String file : files) {
            if (!isAsset(file, "/models/", ".json")) {
                continue;
            }
            Map<String, Object> model = readObject(workspace, 12, file);
            if (model == null) {
                continue;
            }
            // 只观察模型本身的实值纹理；不会把整包的 PNG 或任意字体和 UI 填入方块图集。
            UnaryOperator<String> capture = id -> {
                ResourceId parsed = ResourceId.parse(id);
                if (parsed != null && id.equals(fold(id))
                        && (!"minecraft".equals(parsed.namespace) || !"missingno".equals(parsed.path))
                        && existing.contains("assets/" + parsed.namespace + "/textures/" + parsed.path + ".png")
                        && !defaultBlockAtlas(parsed.path)) {
                    candidates.add(parsed.namespace + ":" + parsed.path);
                }
                return id;
            };
            rewriteModel(model, id -> id, capture);
        }
        if (!candidates.isEmpty()) {
            if (workspace.hasFile(BLOCK_ATLAS) || hasPackFilter(workspace, 12)) {
                warn(workspace, 12, BLOCK_ATLAS, "保留现有图集或过滤器；请检查这些包内模型纹理是否已纳入方块图集：" + summarize(candidates));
            } else {
                List<Object> sources = new ArrayList<Object>();
                for (String id : candidates) {
                    Map<String, Object> single = new LinkedHashMap<String, Object>();
                    single.put("type", "single");
                    single.put("resource", id);
                    sources.add(single);
                }
                Map<String, Object> atlas = new LinkedHashMap<String, Object>();
                atlas.put("sources", sources);
                workspace.writeText(BLOCK_ATLAS, Json.write(atlas));
            }
        }
        warnFile(workspace, 12, VANILLA + "textures/entity/illager/vex.png", "旧恼鬼皮肤/模型布局需人工重排");
        warnFile(workspace, 12, VANILLA + "textures/gui/container/creative_inventory/tabs.png", "创造模式标签页布局已变");
    }

    private static boolean defaultBlockAtlas(String path) {
        return path.startsWith("block/") || path.startsWith("item/") || path.startsWith("entity/conduit/")
                || path.equals("entity/bell/bell_body") || path.equals("entity/enchanting_table_book");
    }

    private static boolean hasPackFilter(UpgradeWorkspace workspace, int edge) throws IOException {
        if (!workspace.hasFile("pack.mcmeta")) {
            return false;
        }
        Map<String, Object> metadata = readObject(workspace, edge, "pack.mcmeta");
        return metadata == null || metadata.containsKey("filter");
    }

    private static void upgradeGlintAndSmithing(UpgradeWorkspace workspace) throws IOException {
        String smithing = VANILLA + "textures/gui/container/smithing.png";
        String legacy = VANILLA + "textures/gui/container/legacy_smithing.png";
        Set<String> existing = foldedFiles(workspace.files());
        if (workspace.hasFile(smithing)) {
            if (existing.contains(fold(legacy)) || existing.contains(fold(legacy + ".mcmeta"))) {
                warn(workspace, 13, smithing, "目标旧式锻造 GUI 或侧车已存在；保留两套资产，不覆盖");
            } else {
                workspace.moveFiles(pairs(smithing, legacy, smithing + ".mcmeta", legacy + ".mcmeta"));
            }
        }
        String glint = VANILLA + "textures/misc/enchanted_item_glint.png";
        if (!workspace.hasFile(glint)) {
            return;
        }
        for (String suffix : new String[]{"item", "entity"}) {
            String target = VANILLA + "textures/misc/enchanted_glint_" + suffix + ".png";
            if (existing.contains(fold(target)) || existing.contains(fold(target + ".mcmeta"))) {
                warn(workspace, 13, glint, "保留显式光效目标或侧车，不覆盖 " + target);
                continue;
            }
            Files.copy(workspace.path(glint), workspace.path(target));
            if (workspace.hasFile(glint + ".mcmeta")) {
                Files.copy(workspace.path(glint + ".mcmeta"), workspace.path(target + ".mcmeta"));
            }
        }
        warn(workspace, 13, glint, "保留原光效，并仅填充未提供的新光效位置；新版物品与实体混合效果可能不同");
    }

    private static void upgradeGui(UpgradeWorkspace workspace) throws IOException {
        IntermediatePackUpgrades.splitGuiAtlas(workspace);
        for (String name : new String[]{"bars.png", "advancements/widgets.png", "container/beacon.png",
                "container/creative_inventory/tabs.png", "container/horse.png"}) {
            warnFile(workspace, 16, VANILLA + "textures/gui/" + name, "新版使用独立 GUI 精灵；未推断旧图集坐标");
        }
    }

    private static void rewriteAssets(UpgradeWorkspace workspace, int edge, UnaryOperator<String> modelId,
                                      UnaryOperator<String> textureId, UnaryOperator<String> stateModelId,
                                      UnaryOperator<String> translation, boolean lowercaseSound) throws IOException {
        List<String> assets = workspace.files();
        Set<String> files = lowercaseSound ? new HashSet<String>(assets) : null;
        for (String file : assets) {
            boolean model = isAsset(file, "/models/", ".json");
            boolean state = isAsset(file, "/blockstates/", ".json")
                    && (edge != 4 || !ClassicBlockStates.handles(file));
            boolean sound = file.startsWith("assets/") && file.equals("assets/" + namespace(file) + "/sounds.json");
            if (!model && !state && !sound) {
                continue;
            }
            Map<String, Object> root = readObject(workspace, edge, file);
            if (root == null) {
                continue;
            }
            boolean changed;
            if (model) {
                changed = rewriteModel(root, modelId, textureId);
            } else if (state) {
                changed = rewriteState(root, stateModelId);
            } else {
                changed = rewriteSounds(workspace, edge, file, root, files, translation, lowercaseSound);
            }
            if (changed) {
                workspace.writeText(file, Json.write(root));
            }
        }
    }

    private static boolean rewriteModel(Map<String, Object> root, UnaryOperator<String> modelId,
                                        UnaryOperator<String> textureId) {
        boolean changed = rewriteValue(root, "parent", modelId);
        Map<String, Object> textures = object(root.get("textures"));
        if (textures != null) {
            for (String key : textures.keySet()) {
                changed |= rewriteValue(textures, key, textureId);
            }
        }
        if (root.get("overrides") instanceof List) {
            for (Object value : (List<?>) root.get("overrides")) {
                changed |= rewriteValue(object(value), "model", modelId);
            }
        }
        if (root.get("elements") instanceof List) {
            for (Object value : (List<?>) root.get("elements")) {
                Map<String, Object> element = object(value);
                Map<String, Object> faces = element == null ? null : object(element.get("faces"));
                if (faces != null) {
                    for (Object face : faces.values()) {
                        changed |= rewriteValue(object(face), "texture", textureId);
                    }
                }
            }
        }
        return changed;
    }

    private static boolean rewriteState(Map<String, Object> root, UnaryOperator<String> modelId) {
        boolean changed = false;
        Map<String, Object> variants = object(root.get("variants"));
        if (variants != null) {
            for (Object entry : variants.values()) {
                changed |= rewriteStateModels(entry, modelId);
            }
        }
        if (root.get("multipart") instanceof List) {
            for (Object entry : (List<?>) root.get("multipart")) {
                Map<String, Object> part = object(entry);
                if (part != null) {
                    changed |= rewriteStateModels(part.get("apply"), modelId);
                }
            }
        }
        return changed;
    }

    private static boolean rewriteStateModels(Object value, UnaryOperator<String> modelId) {
        if (!(value instanceof List)) {
            return rewriteValue(object(value), "model", modelId);
        }
        boolean changed = false;
        for (Object model : (List<?>) value) {
            changed |= rewriteValue(object(model), "model", modelId);
        }
        return changed;
    }

    private static boolean rewriteSounds(UpgradeWorkspace workspace, int edge, String file,
                                         Map<String, Object> root, Set<String> files,
                                         UnaryOperator<String> translation, boolean lowercase) {
        String namespace = namespace(file);
        Map<String, Object> previousKeys = new LinkedHashMap<String, Object>(root);
        boolean changed = lowercase && renameKeys(workspace, edge, file, root,
                id -> id.matches("[A-Za-z0-9_./-]+") ? fold(id) : id);
        for (Object value : root.values()) {
            Map<String, Object> event = object(value);
            if (event == null) {
                continue;
            }
            if ("minecraft".equals(namespace)) {
                changed |= rewriteValue(event, "subtitle", translation);
            }
            if (lowercase && event.get("sounds") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> sounds = (List<Object>) event.get("sounds");
                for (int index = 0; index < sounds.size(); index++) {
                    Object sound = sounds.get(index);
                    if (sound instanceof String) {
                        String name = lowerSoundId(workspace, (String) sound, false, namespace, previousKeys, files);
                        if (!name.equals(sound)) {
                            sounds.set(index, name);
                            changed = true;
                        }
                    } else {
                        Map<String, Object> spec = object(sound);
                        if (spec != null) {
                            boolean isEvent = "event".equals(spec.get("type"));
                            changed |= rewriteValue(spec, "name", id -> lowerSoundId(workspace, id, isEvent, namespace,
                                    previousKeys, files));
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static String lowerSoundId(UpgradeWorkspace workspace, String id, boolean event, String owner,
                                       Map<String, Object> previousKeys, Set<String> files) {
        ResourceId parsed = ResourceId.parse(id);
        if (parsed == null || fold(id).equals(id)) {
            return id;
        }
        if (event && owner.equalsIgnoreCase(parsed.namespace) && previousKeys.containsKey(parsed.path)) {
            return fold(id);
        }
        return lowercaseId(workspace, id, "sounds/", ".ogg", files);
    }

    private static boolean rewriteValue(Map<String, Object> root, String key, UnaryOperator<String> rewrite) {
        Object old = root == null ? null : root.get(key);
        if (!(old instanceof String)) {
            return false;
        }
        String updated = rewrite.apply((String) old);
        if (old.equals(updated)) {
            return false;
        }
        root.put(key, updated);
        return true;
    }

    private static boolean renameKeys(UpgradeWorkspace workspace, int edge, String file, Map<String, Object> root,
                                      UnaryOperator<String> rewrite) {
        Map<String, Object> mapped = new LinkedHashMap<String, Object>();
        boolean changed = false;
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            String key = rewrite.apply(entry.getKey());
            if (mapped.containsKey(key)) {
                warn(workspace, edge, file, "键归并冲突 " + key + "；保留所有原始键和值");
                return false;
            }
            mapped.put(key, entry.getValue());
            changed |= !entry.getKey().equals(key);
        }
        if (changed) {
            root.clear();
            root.putAll(mapped);
        }
        return changed;
    }

    private static void scheduleTextures(Map<String, String> changes, Map<String, String> ids) {
        schedule(changes, VANILLA + "textures/", ".png", ids);
        schedule(changes, VANILLA + "textures/", ".png.mcmeta", ids);
    }

    private static void schedule(Map<String, String> changes, String folder, String suffix, Map<String, String> ids) {
        for (Map.Entry<String, String> id : ids.entrySet()) {
            changes.put(folder + id.getKey() + suffix, folder + id.getValue() + suffix);
        }
    }

    private static String replaceVanilla(String id, Map<String, String> rules) {
        ResourceId parsed = ResourceId.parse(id);
        if (parsed == null || !"minecraft".equals(parsed.namespace)) {
            return id;
        }
        return parsed.withPath(rules.getOrDefault(parsed.path, parsed.path));
    }

    private static String textureStem(String file, String prefix) {
        String suffix = file.endsWith(".png.mcmeta") ? ".png.mcmeta" : ".png";
        return file.endsWith(suffix) ? file.substring(prefix.length(), file.length() - suffix.length()) : null;
    }

    private static boolean isAsset(String file, String group, String suffix) {
        return file.startsWith("assets/") && file.startsWith(group, 7 + namespace(file).length()) && file.endsWith(suffix);
    }

    private static String namespace(String file) {
        int end = file.indexOf('/', 7);
        return end == -1 ? "" : file.substring(7, end);
    }

    private static Set<String> foldedFiles(Collection<String> files) {
        Set<String> result = new HashSet<String>();
        for (String file : files) {
            result.add(fold(file));
        }
        return result;
    }

    private static String fold(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    private static String summarize(Set<String> ids) {
        List<String> examples = new ArrayList<String>();
        for (String id : ids) {
            if (examples.size() == 8) {
                break;
            }
            examples.add(id);
        }
        return String.join(", ", examples) + (ids.size() > examples.size() ? "（共 " + ids.size() + " 个）" : "");
    }

    static Map<String, Object> readObject(UpgradeWorkspace workspace, int edge, String file) throws IOException {
        String raw = text(workspace, edge, file);
        if (raw == null) {
            return null;
        }
        try {
            return Json.parseObjectRejectingDuplicateKeys(raw);
        } catch (JsonException | StackOverflowError failure) {
            warn(workspace, edge, file, "JSON 无法安全解析；保留原文，不改写字段");
            return null;
        }
    }

    static String text(UpgradeWorkspace workspace, int edge, String file) throws IOException {
        Path path = workspace.path(file);
        if (Files.size(path) > MAX_TEXT_BYTES) {
            warn(workspace, edge, file, "文本超过迁移读取上限；保留原文件");
            return null;
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
            return value.startsWith("\uFEFF") ? value.substring(1) : value;
        } catch (CharacterCodingException failure) {
            warn(workspace, edge, file, "无效 UTF-8；保留原字节，不替换字符");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static Map<String, String> pairs(String... entries) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i], entries[i + 1]);
        }
        return result;
    }

    private static void warnFile(UpgradeWorkspace workspace, int edge, String file, String message) throws IOException {
        if (workspace.hasFile(file)) {
            warn(workspace, edge, file, message + "；保留原文件");
        }
    }

    static void warn(UpgradeWorkspace workspace, int edge, String file, String message) {
        workspace.warn("格式 " + edge + " [" + file + "] " + message);
    }

    private static final class ResourceId {
        private final String namespace;
        private final String path;
        private final boolean qualified;

        private ResourceId(String namespace, String path, boolean qualified) {
            this.namespace = namespace;
            this.path = path;
            this.qualified = qualified;
        }

        private static ResourceId parse(String value) {
            int colon = value.indexOf(':');
            String namespace = colon == -1 ? "minecraft" : value.substring(0, colon);
            String path = value.substring(colon + 1);
            if (!namespace.matches("[A-Za-z0-9_.-]+") || !path.matches("[A-Za-z0-9/_.-]+")) {
                return null;
            }
            return new ResourceId(namespace, path, colon != -1);
        }

        private String withPath(String updated) {
            return qualified ? namespace + ":" + updated : updated;
        }
    }
}
