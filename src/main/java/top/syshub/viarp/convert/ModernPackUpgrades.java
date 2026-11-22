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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;
import top.syshub.viarp.pack.PackFormat;

/** 现代格式的增量内容规则；只处理已选 overlay 投影后的 assets/。 */
final class ModernPackUpgrades {

    private static final String VANILLA = "assets/minecraft/";
    private static final String LEGACY_PALETTE = "trims/color_palettes/";
    private static final List<String> TRIM_MATERIALS = Arrays.asList("amethyst", "copper", "diamond", "emerald",
            "gold", "iron", "lapis", "netherite", "quartz", "redstone", "resin");
    private static final List<String> DARK_PALETTES = Arrays.asList("copper_darker", "diamond_darker",
            "gold_darker", "iron_darker", "netherite_darker");
    private static final List<String> TRIM_PATTERNS = Arrays.asList("bolt", "coast", "dune", "eye", "flow", "host",
            "raiser", "rib", "sentry", "shaper", "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild");
    // 注册入口、贴图源、新注册 ID；格式 97 并非所有旧贴图都与旧入口同名。
    private static final String[][] MAP_RENAMES_96 = {
            {"ancient_city_map", "ancient_city_map", "buried_ancient_city_map"},
            {"mineshaft_map", "mineshaft_map", "buried_mineshaft_map"},
            {"abandoned_campsite_map", "abandoned_campsite_map", "abandoned_camp_map"}
    };
    private static final String[][] MAP_RENAMES_97 = {
            {"ocean_explorer_map", "ocean_monument_map", "ocean_monument_map"},
            {"woodland_explorer_map", "woodland_mansion_map", "woodland_mansion_map"},
            {"trial_explorer_map", "trial_chamber_map", "buried_trial_chambers_map"},
            {"jungle_explorer_map", "jungle_temple_map", "jungle_pyramid_map"},
            {"swamp_explorer_map", "swamp_hut_map", "swamp_hut_map"}
    };

    private ModernPackUpgrades() {
    }

    static void apply(PackFormat entered, UpgradeWorkspace workspace) throws IOException {
        int major = entered.major();
        int minor = entered.minor();
        if (major == 70 && minor == 0) {
            AliasFiles aliases = new AliasFiles(workspace);
            aliases.texture("environment/sun", "environment/celestial/sun");
            aliases.texture("environment/end_flash", "environment/celestial/end_flash");
            CelestialMoonUpgrade.apply(workspace);
        } else if (major == 71 && minor == 0) {
            migrateMipmapMetadata(workspace);
        } else if (major == 73 && minor == 0) {
            if (workspace.hasFile(VANILLA + "atlases/blocks.json")) {
                workspace.warn("73.0: retained custom blocks atlas; review item sprite sources for the separate items atlas.");
            }
            ItemAtlasSplitDiagnostics.inspect(workspace);
        } else if (major == 83 && minor == 0) {
            SpecialItemTransforms.apply(workspace);
        } else if ((major == 86 && minor <= 1) || (major == 87 && minor == 0)) {
            EntitySheetUpgrades.apply(entered, workspace);
        } else if (major == 88 && minor == 0) {
            AliasFiles aliases = new AliasFiles(workspace);
            Map<String, String> sprites = new LinkedHashMap<String, String>();
            for (String oldId : Arrays.asList("block/quartz_pillar", "block/purpur_pillar")) {
                addTextureAlias(aliases, sprites, oldId, oldId + "_side");
            }
            rewriteModelReferences(workspace, sprites, new LinkedHashMap<String, String>());
        } else if (major == 89 && minor == 0) {
            migrateTrimPalettes(workspace);
        } else if (major == 91 && minor == 0) {
            migrateElementShade(workspace);
        } else if (major == 94 && minor == 0) {
            migrateEquipmentTrimOverrides(workspace);
        } else if (major == 95 && minor == 0) {
            inspectLegacyMapTints(workspace);
        } else if (major == 96 && minor == 0) {
            migrateExplorerMaps(workspace, MAP_RENAMES_96);
        } else if (major == 97 && minor == 0) {
            migrateExplorerMaps(workspace, MAP_RENAMES_97);
        }

        if ((major == 65 && minor == 0) || (major == 69 && minor == 0)
                || (major == 75 && minor == 0) || (major == 84 && minor == 0)
                || (major == 88 && minor == 0) || (major == 97 && minor == 1)) {
            warnUnsupportedExtensions(workspace);
        }
    }

    private static void migrateMipmapMetadata(UpgradeWorkspace workspace) throws IOException {
        for (String file : workspace.files()) {
            String relative = assetPath(file);
            if (!relative.startsWith("textures/") || !relative.endsWith(".png.mcmeta")) {
                continue;
            }
            Map<String, Object> root = readObject(workspace, file);
            Map<String, Object> texture = JsonAccess.object(root, "texture");
            if (texture == null || !texture.containsKey("darkened_cutout_mipmap")) {
                continue;
            }
            Object legacy = texture.get("darkened_cutout_mipmap");
            if (!(legacy instanceof Boolean)) {
                workspace.warn("71.0: retained invalid texture.darkened_cutout_mipmap in " + file);
                continue;
            }
            String strategy = Boolean.TRUE.equals(legacy) ? "dark_cutout" : "auto";
            if (texture.containsKey("mipmap_strategy") && !strategy.equals(texture.get("mipmap_strategy"))) {
                workspace.warn("71.0: retained conflicting mipmap settings in " + file
                        + "; the new mipmap_strategy takes precedence.");
                continue;
            }
            texture.remove("darkened_cutout_mipmap");
            texture.put("mipmap_strategy", strategy);
            writeObject(workspace, file, root);
            if (Boolean.FALSE.equals(legacy)) {
                workspace.warn("71.0: explicit non-darkened mipmaps now use auto; cutout mipmaps may look different: "
                        + file);
            }
        }
    }

    private static void migrateTrimPalettes(UpgradeWorkspace workspace) throws IOException {
        AliasFiles aliases = new AliasFiles(workspace);
        Map<String, String> palettes = new LinkedHashMap<String, String>();
        palettes.put(LEGACY_PALETTE + "trim_palette", "trim_base");
        for (String name : TRIM_MATERIALS) {
            palettes.put(LEGACY_PALETTE + name, "trim/" + name);
        }
        for (String name : DARK_PALETTES) {
            palettes.put(LEGACY_PALETTE + name, "trim/" + name);
        }
        for (Map.Entry<String, String> pair : palettes.entrySet()) {
            aliases.texture(pair.getKey(), "palettes/" + pair.getValue());
        }

        String removedAtlas = VANILLA + "atlases/armor_trims.json";
        boolean customArmorAtlas = workspace.hasFile(removedAtlas);
        if (customArmorAtlas) {
            workspace.warn("89.0: retained " + removedAtlas + "; it is no longer loaded. Entity trims use runtime "
                    + "palettes; custom atlas replacements require equipment overrides and/or palette sidecars.");
        }
        for (String file : workspace.files()) {
            String relative = assetPath(file);
            if (relative.startsWith("atlases/") && relative.endsWith(".json") && !file.equals(removedAtlas)) {
                rewritePaletteSources(workspace, file, palettes);
            } else if (relative.startsWith("textures/trims/entity/") && relative.endsWith(".png")) {
                if (!customArmorAtlas && isVanillaTrimTexture(file)) {
                    ensureBasePalette(workspace, file);
                } else {
                    workspace.warn("89.0: retained entity trim texture; verify palette.base_palette and custom "
                            + "equipment overrides: " + file);
                }
            }
        }
    }

    private static boolean isVanillaTrimTexture(String file) {
        for (String layer : Arrays.asList("humanoid", "humanoid_leggings")) {
            String start = VANILLA + "textures/trims/entity/" + layer + "/";
            if (file.startsWith(start)) {
                return TRIM_PATTERNS.contains(file.substring(start.length(), file.length() - 4));
            }
        }
        return false;
    }

    private static void ensureBasePalette(UpgradeWorkspace workspace, String texture) throws IOException {
        String file = texture + ".mcmeta";
        Map<String, Object> root = workspace.hasFile(file) ? readObject(workspace, file)
                : new LinkedHashMap<String, Object>();
        if (root == null || root.containsKey("palette")) {
            return;
        }
        Map<String, Object> palette = new LinkedHashMap<String, Object>();
        palette.put("base_palette", "minecraft:trim_base");
        root.put("palette", palette);
        writeObject(workspace, file, root);
    }

    private static void rewritePaletteSources(UpgradeWorkspace workspace, String file, Map<String, String> palettes)
            throws IOException {
        Map<String, Object> root = readObject(workspace, file);
        List<Object> sources = JsonAccess.array(root, "sources");
        if (sources == null) {
            return;
        }
        boolean changed = false;
        for (Object value : sources) {
            Map<String, Object> source = object(value);
            if (!"paletted_permutations".equals(vanillaPath(JsonAccess.string(source, "type", "")))) {
                continue;
            }
            changed |= rewritePaletteField(workspace, file, source, "palette_key", palettes);
            Map<String, Object> permutations = JsonAccess.object(source, "permutations");
            if (permutations != null) {
                for (String key : permutations.keySet()) {
                    changed |= rewritePaletteField(workspace, file, permutations, key, palettes);
                }
            }
        }
        if (changed) {
            writeObject(workspace, file, root);
        }
    }

    private static boolean rewritePaletteField(UpgradeWorkspace workspace, String file, Map<String, Object> object,
                                                String field, Map<String, String> palettes) throws IOException {
        String id = JsonAccess.string(object, field, "");
        if (replaceId(object, field, palettes)) {
            return true;
        }
        // A source pack may already carry the new palette; otherwise it cannot be inferred from the old ID alone.
        if (resourceId(id)) {
            int colon = id.indexOf(':');
            String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
            String path = colon < 0 ? id : id.substring(colon + 1);
            if (!"trim_base".equals(vanillaPath(id)) && !knownTrimPalette(vanillaPath(id))
                    && !workspace.hasFile("assets/" + namespace + "/textures/palettes/" + path + ".png")) {
                workspace.warn("89.0: retained custom or unresolved palette ID " + id + " in " + file
                        + "; it must resolve under textures/palettes/ on the target.");
            }
        }
        return false;
    }

    private static void migrateEquipmentTrimOverrides(UpgradeWorkspace workspace) throws IOException {
        for (String file : workspace.files()) {
            String relative = assetPath(file);
            if (!relative.startsWith("equipment/") || !relative.endsWith(".json")) {
                continue;
            }
            Map<String, Object> root = readObject(workspace, file);
            if (root == null || !root.containsKey("trim_palette_replacements")) {
                continue;
            }
            Map<String, Object> replacements = JsonAccess.object(root, "trim_palette_replacements");
            if (root.containsKey("trim_overrides") || replacements == null) {
                workspace.warn("94.0: retained invalid/conflicting equipment trim overrides: " + file);
                continue;
            }
            List<Object> converted = new ArrayList<Object>();
            Set<String> materials = new HashSet<String>();
            for (Map.Entry<String, Object> entry : replacements.entrySet()) {
                String old = vanillaPath(entry.getKey());
                String material = old.startsWith("trim/") ? old.substring(5) : "";
                if (!TRIM_MATERIALS.contains(material) || !materials.add(material) || !(entry.getValue() instanceof String)
                        || !resourceId((String) entry.getValue())) {
                    converted = null;
                    break;
                }
                Map<String, Object> when = new LinkedHashMap<String, Object>();
                when.put("material", "minecraft:" + material);
                Map<String, Object> override = new LinkedHashMap<String, Object>();
                override.put("when", when);
                override.put("palette", entry.getValue());
                converted.add(override);
            }
            if (converted == null) {
                workspace.warn("94.0: retained legacy trim_palette_replacements in " + file
                        + "; choose explicit trim material or pattern IDs for the new ordered trim_overrides.");
                continue;
            }
            root.remove("trim_palette_replacements");
            root.put("trim_overrides", converted);
            writeObject(workspace, file, root);
        }
    }

    private static void migrateElementShade(UpgradeWorkspace workspace) throws IOException {
        for (String file : workspace.files()) {
            if (!isGeometry(file)) {
                continue;
            }
            Map<String, Object> model = readObject(workspace, file);
            List<Object> elements = JsonAccess.array(model, "elements");
            if (elements == null) {
                continue;
            }
            boolean changed = false;
            for (Object value : elements) {
                Map<String, Object> element = object(value);
                if (element == null || !element.containsKey("shade")) {
                    continue;
                }
                Object shade = element.get("shade");
                if (!(shade instanceof Boolean) || (element.containsKey("shade_direction_override")
                        && (!Boolean.FALSE.equals(shade) || !"up".equals(element.get("shade_direction_override"))))) {
                    workspace.warn("91.0: retained invalid/conflicting elements[].shade in " + file
                            + "; the target uses shade_direction_override.");
                    continue;
                }
                element.remove("shade");
                if (Boolean.FALSE.equals(shade)) {
                    element.put("shade_direction_override", "up");
                }
                changed = true;
            }
            if (changed) {
                writeObject(workspace, file, model);
            }
        }
    }

    private static void migrateExplorerMaps(UpgradeWorkspace workspace, String[][] renames) throws IOException {
        AliasFiles aliases = new AliasFiles(workspace);
        Map<String, String> sprites = new LinkedHashMap<String, String>();
        Map<String, String> models = new LinkedHashMap<String, String>();
        for (String[] rename : renames) {
            String oldModel = "item/" + rename[0];
            String oldSprite = "item/" + rename[1];
            String target = "item/" + rename[2];
            if (!oldSprite.equals(target)) {
                addTextureAlias(aliases, sprites, oldSprite, target);
            }
            if (aliases.file(VANILLA + "models/" + oldModel + ".json", VANILLA + "models/" + target + ".json")) {
                models.put(oldModel, target);
            }
            aliases.file(VANILLA + "items/" + rename[0] + ".json", VANILLA + "items/" + rename[2] + ".json");
        }
        rewriteModelReferences(workspace, sprites, models);
        for (String file : workspace.files()) {
            if (!file.startsWith(VANILLA + "lang/") || !file.endsWith(".json")
                    || file.indexOf('/', (VANILLA + "lang/").length()) >= 0) {
                continue;
            }
            Map<String, Object> language = readObject(workspace, file);
            if (language == null) {
                continue;
            }
            boolean changed = false;
            for (String[] rename : renames) {
                Object value = language.get("item.minecraft." + rename[0]);
                String destination = "item.minecraft." + rename[2];
                if (value instanceof String && !language.containsKey(destination)) {
                    language.put(destination, value);
                    changed = true;
                }
            }
            if (changed) {
                writeObject(workspace, file, language);
            }
        }
    }

    private static void addTextureAlias(AliasFiles aliases, Map<String, String> sprites, String from, String to)
            throws IOException {
        if (aliases.texture(from, to)) {
            sprites.put(from, to);
        }
    }

    private static void rewriteModelReferences(UpgradeWorkspace workspace, Map<String, String> sprites,
                                               Map<String, String> models) throws IOException {
        if (sprites.isEmpty() && models.isEmpty()) {
            return;
        }
        for (String file : workspace.files()) {
            if (isGeometry(file)) {
                Map<String, Object> root = readObject(workspace, file);
                if (root != null && rewriteGeometry(root, sprites, models)) {
                    writeObject(workspace, file, root);
                }
            } else if (!models.isEmpty() && isItemDefinition(file)) {
                Map<String, Object> root = readObject(workspace, file);
                boolean changed = false;
                for (Map<String, Object> node : itemNodes(root)) {
                    String type = vanillaPath(JsonAccess.string(node, "type", ""));
                    if ("model".equals(type)) {
                        changed |= replaceId(node, "model", models);
                    } else if ("special".equals(type)) {
                        changed |= replaceId(node, "base", models);
                    }
                }
                if (changed) {
                    writeObject(workspace, file, root);
                }
            }
        }
    }

    private static boolean rewriteGeometry(Map<String, Object> model, Map<String, String> sprites,
                                           Map<String, String> models) {
        boolean changed = replaceId(model, "parent", models);
        Map<String, Object> textures = JsonAccess.object(model, "textures");
        if (textures != null) {
            for (String key : textures.keySet()) {
                changed |= replaceId(textures, key, sprites);
            }
        }
        List<Object> elements = JsonAccess.array(model, "elements");
        if (elements != null) {
            for (Object value : elements) {
                Map<String, Object> faces = JsonAccess.object(object(value), "faces");
                if (faces != null) {
                    for (Object face : faces.values()) {
                        changed |= replaceId(object(face), "texture", sprites);
                    }
                }
            }
        }
        return changed;
    }

    private static void inspectLegacyMapTints(UpgradeWorkspace workspace) throws IOException {
        for (String file : workspace.files()) {
            if (!isItemDefinition(file)) {
                continue;
            }
            for (Map<String, Object> node : itemNodes(readObject(workspace, file))) {
                if (!"model".equals(vanillaPath(JsonAccess.string(node, "type", "")))) {
                    continue;
                }
                List<Object> tints = JsonAccess.array(node, "tints");
                if (tints == null) {
                    continue;
                }
                for (Object tint : tints) {
                    if ("map_color".equals(vanillaPath(JsonAccess.string(object(tint), "type", "")))) {
                        workspace.warn("95.0: retained removed map_color tint in " + file
                                + "; dynamic map tints cannot be replaced by a constant without changing behavior.");
                    }
                }
            }
        }
    }

    /** 只访问 vanilla item-model 子节点槽；不深入 NBT、自定义属性、tint 或 special 渲染器对象。 */
    private static List<Map<String, Object>> itemNodes(Map<String, Object> root) {
        List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
        Deque<Map<String, Object>> pending = new ArrayDeque<Map<String, Object>>();
        push(pending, JsonAccess.object(root, "model"));
        while (!pending.isEmpty()) {
            Map<String, Object> node = pending.pop();
            nodes.add(node);
            String type = vanillaPath(JsonAccess.string(node, "type", ""));
            if ("condition".equals(type)) {
                push(pending, JsonAccess.object(node, "on_true"));
                push(pending, JsonAccess.object(node, "on_false"));
            } else if ("select".equals(type) || "range_dispatch".equals(type)) {
                push(pending, JsonAccess.object(node, "fallback"));
                List<Object> entries = JsonAccess.array(node, "select".equals(type) ? "cases" : "entries");
                if (entries != null) {
                    for (Object entry : entries) {
                        push(pending, JsonAccess.object(object(entry), "model"));
                    }
                }
            } else if ("composite".equals(type)) {
                List<Object> children = JsonAccess.array(node, "models");
                if (children != null) {
                    for (Object child : children) {
                        push(pending, object(child));
                    }
                }
            }
        }
        return nodes;
    }

    private static void push(Deque<Map<String, Object>> pending, Map<String, Object> child) {
        if (child != null) {
            pending.push(child);
        }
    }

    private static void warnUnsupportedExtensions(UpgradeWorkspace workspace) throws IOException {
        boolean shaders = false;
        boolean extensions = false;
        for (String file : workspace.files()) {
            String relative = assetPath(file);
            shaders |= relative.startsWith("shaders/") || relative.startsWith("post_effect/");
            extensions |= relative.startsWith("optifine/") || relative.startsWith("mcpatcher/")
                    || relative.startsWith("citresewn/");
        }
        if (shaders) {
            workspace.warn("Retained custom shaders/post effects; verify target uniforms, render passes and shader interfaces.");
        }
        if (extensions) {
            workspace.warn("Retained OptiFine/MCPatcher/CIT Resewn assets; verify mod support and any item/texture references.");
        }
    }

    private static boolean isGeometry(String file) {
        String relative = assetPath(file);
        return relative.startsWith("models/") && relative.endsWith(".json");
    }

    private static boolean isItemDefinition(String file) {
        String relative = assetPath(file);
        return relative.startsWith("items/") && relative.endsWith(".json");
    }

    private static String assetPath(String file) {
        int slash = file.startsWith("assets/") ? file.indexOf('/', 7) : -1;
        return slash > 7 ? file.substring(slash + 1) : "";
    }

    private static boolean knownTrimPalette(String path) {
        if (!path.startsWith("trim/")) {
            return false;
        }
        String name = path.substring(5);
        return TRIM_MATERIALS.contains(name) || DARK_PALETTES.contains(name);
    }

    private static boolean resourceId(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (!namespace.matches("[a-z0-9_.-]+") || ".".equals(namespace) || "..".equals(namespace)
                || !path.matches("[a-z0-9/_.-]+")) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static String vanillaPath(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id.indexOf(':') < 0 ? id : "";
    }

    private static boolean replaceId(Map<String, Object> owner, String field, Map<String, String> replacements) {
        String id = JsonAccess.string(owner, field, "");
        String replacement = replacements.get(vanillaPath(id));
        if (replacement == null) {
            return false;
        }
        owner.put(field, id.startsWith("minecraft:") ? "minecraft:" + replacement : replacement);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static Map<String, Object> readObject(UpgradeWorkspace workspace, String file) throws IOException {
        return workspace.readObject(file, "现代格式迁移");
    }

    private static void writeObject(UpgradeWorkspace workspace, String file, Map<String, Object> root) throws IOException {
        workspace.writeText(file, Json.write(root) + "\n");
    }

    /** 保留来源供不受管理的 shader/CIT 等引用使用；PNG 和侧车作为一个冲突单元。 */
    private static final class AliasFiles {
        private final UpgradeWorkspace workspace;
        private final Set<String> paths = new HashSet<String>();

        AliasFiles(UpgradeWorkspace workspace) throws IOException {
            this.workspace = workspace;
            for (String file : workspace.files()) {
                paths.add(fold(file));
            }
        }

        boolean texture(String from, String to) throws IOException {
            String source = VANILLA + "textures/" + from + ".png";
            String target = VANILLA + "textures/" + to + ".png";
            String sourceMeta = source + ".mcmeta";
            String targetMeta = target + ".mcmeta";
            boolean png = workspace.hasFile(source);
            boolean metadata = workspace.hasFile(sourceMeta);
            if (!png && !metadata) {
                return true;
            }
            if (occupied(target) || occupied(targetMeta)) {
                warnConflict(source, target);
                return false;
            }
            if (png) {
                copy(source, target);
            }
            if (metadata) {
                copy(sourceMeta, targetMeta);
            }
            return true;
        }

        boolean file(String from, String to) throws IOException {
            if (!workspace.hasFile(from)) {
                return true;
            }
            if (occupied(to)) {
                warnConflict(from, to);
                return false;
            }
            copy(from, to);
            return true;
        }

        private boolean occupied(String file) throws IOException {
            Path path = workspace.path(file);
            for (String parent = file; parent.indexOf('/') >= 0; parent = parent.substring(0, parent.lastIndexOf('/'))) {
                if (paths.contains(fold(parent))) {
                    return true;
                }
            }
            return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        }

        private void copy(String from, String to) throws IOException {
            Path target = workspace.path(to);
            Files.createDirectories(target.getParent());
            Files.copy(workspace.path(from), target);
            paths.add(fold(to));
        }

        private void warnConflict(String from, String to) {
            workspace.warn("Retained legacy resource; migration destination already exists or is blocked: " + from
                    + " -> " + to + ". The target may not use the legacy customization.");
        }

        private static String fold(String file) {
            return file.toLowerCase(Locale.ROOT);
        }
    }
}
