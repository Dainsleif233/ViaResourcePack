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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonException;

/** 格式 4 中经 1.12.2、1.13 和 1.14.4 验证的方块状态子集。 */
final class ClassicBlockStates {

    private static final String STATES = "assets/minecraft/blockstates/";
    private static final Pattern MODEL_ID = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final List<String> UNUSED = Arrays.asList("planks", "wool", "tallgrass");
    private static final Map<String, String> BLOCKS = new LinkedHashMap<String, String>();
    private static final Map<String, String> MODELS = new HashMap<String, String>();

    static {
        for (String wood : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            BLOCKS.put(wood + "_planks", wood + "_planks");
        }
        for (String dye : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
                "gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            BLOCKS.put(dye + "_wool", dye + "_wool");
        }
        BLOCKS.put("silver_wool", "light_gray_wool");
        MODELS.put("silver_wool", "light_gray_wool");
        BLOCKS.put("stone", "stone");
        for (String stone : new String[]{"granite", "diorite", "andesite"}) {
            BLOCKS.put(stone, stone);
            BLOCKS.put("smooth_" + stone, "polished_" + stone);
            MODELS.put(stone + "_smooth", "polished_" + stone);
        }
        BLOCKS.put("dead_bush", "dead_bush");
        BLOCKS.put("fern", "fern");
        BLOCKS.put("grass", "grass_block");
        BLOCKS.put("tall_grass", "grass");
        MODELS.put("tall_grass", "grass");
        MODELS.put("grass_normal", "grass_block");
        MODELS.put("grass_snowed", "grass_block_snow");
    }

    private ClassicBlockStates() {
    }

    /** 拒绝改写的来源和用户提供的目标也不能被通用模型引用改写器重写。 */
    static boolean handles(String file) {
        file = file.toLowerCase(Locale.ROOT);
        if (!file.startsWith(STATES) || !file.endsWith(".json")) {
            return false;
        }
        String name = file.substring(STATES.length(), file.length() - ".json".length());
        return BLOCKS.containsKey(name) || BLOCKS.containsValue(name) || UNUSED.contains(name);
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        Set<String> files = new HashSet<String>(workspace.files());
        Map<String, String> occupied = new HashMap<String, String>();
        for (String file : files) {
            String key = file.toLowerCase(Locale.ROOT);
            if (occupied.putIfAbsent(key, file) != null) {
                occupied.put(key, ""); // 大小写折叠后有多个文件，不挑其中之一。
            }
        }
        for (String unused : UNUSED) {
            if (files.contains(state(unused))) {
                warn(workspace, state(unused), "1.12.2 原版不读取此聚合状态路径；不猜测扩展选择器，保留原文件");
            }
        }
        Map<String, Transition> candidates = new LinkedHashMap<String, Transition>();
        for (Map.Entry<String, String> rule : BLOCKS.entrySet()) {
            String source = state(rule.getKey());
            if (!files.contains(source)) {
                continue;
            }
            if (!source.equals(occupied.get(source))) {
                warn(workspace, source, "状态来源路径存在大小写冲突；保留原文件");
                continue;
            }
            Map<String, Object> converted = convert(workspace, source, rule.getKey(), files);
            if (converted != null) {
                candidates.put(source, new Transition(state(rule.getValue()), converted));
            }
        }
        // 先验证整个图。只有确认旧雪草会成功迁走时，才能把短草移入 grass.json。
        boolean rejected;
        do {
            rejected = false;
            Iterator<Map.Entry<String, Transition>> iterator = candidates.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Transition> entry = iterator.next();
                String target = entry.getValue().target;
                if (target.equals(entry.getKey())) {
                    continue;
                }
                String existing = occupied.get(target);
                Transition leaving = candidates.get(target);
                if ((existing != null && (!target.equals(existing) || leaving == null || target.equals(leaving.target)))
                        || (existing == null && Files.exists(workspace.path(target), LinkOption.NOFOLLOW_LINKS))) {
                    warn(workspace, entry.getKey(), "已有目标或目标仍被占用，不覆盖 " + target);
                    iterator.remove();
                    rejected = true;
                }
            }
        } while (rejected);
        Map<String, String> moves = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Transition> entry : candidates.entrySet()) {
            moves.put(entry.getKey(), entry.getValue().target);
        }
        workspace.moveFiles(moves);
        for (Transition transition : candidates.values()) {
            workspace.writeText(transition.target, Json.write(transition.root));
        }
    }

    private static Map<String, Object> convert(UpgradeWorkspace workspace, String source, String name, Set<String> files)
            throws IOException {
        String text = ClassicPackUpgrades.text(workspace, 4, source);
        if (text == null) {
            return null;
        }
        Map<String, Object> root;
        try {
            root = Json.parseObjectRejectingDuplicateKeys(text);
        } catch (JsonException | StackOverflowError failure) {
            warn(workspace, source, "JSON 不安全（含重复键或语法错误）；保留原文件");
            return null;
        }
        Map<String, Object> variants = ClassicPackUpgrades.object(root.get("variants"));
        if (variants == null || root.size() != 1) {
            warn(workspace, source, "非已核实的纯 variants 结构；保留原文件");
            return null;
        }
        if (only(variants, "") || ("tall_grass".equals(name) && isDoublePlant(variants))) {
            return null; // 已经是目标形状，不碰此显式资源。
        }
        boolean snowy = "grass".equals(name);
        if (snowy ? !hasSnowStates(variants) : !only(variants, "normal")) {
            warn(workspace, source, "未找到恰好等价的旧状态选择器；保留原文件");
            return null;
        }
        Map<String, Object> newVariants = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : variants.entrySet()) {
            Object payload = entry.getValue();
            if (!rewriteModels(payload, files)) {
                warn(workspace, source, "变体模型或属性不明确；保留原文件，不生成不完整目标");
                return null;
            }
            newVariants.put(snowy ? entry.getKey() : "", payload);
        }
        root.put("variants", newVariants);
        return root;
    }

    private static boolean only(Map<String, Object> variants, String name) {
        return variants.size() == 1 && variants.containsKey(name);
    }

    private static boolean hasSnowStates(Map<String, Object> variants) {
        return variants.size() == 2 && variants.containsKey("snowy=false") && variants.containsKey("snowy=true");
    }

    private static boolean isDoublePlant(Map<String, Object> variants) {
        return variants.size() == 2 && variants.containsKey("half=lower") && variants.containsKey("half=upper");
    }

    private static boolean rewriteModels(Object payload, Set<String> files) {
        List<?> entries = payload instanceof List ? (List<?>) payload : Collections.singletonList(payload);
        if (entries.isEmpty()) {
            return false;
        }
        for (Object element : entries) {
            Map<String, Object> variant = ClassicPackUpgrades.object(element);
            if (variant == null || !(variant.get("model") instanceof String)) {
                return false;
            }
            for (Map.Entry<String, Object> entry : variant.entrySet()) {
                if (!validAttribute(entry.getKey(), entry.getValue())) {
                    return false;
                }
            }
            String target = modelId((String) variant.get("model"), files);
            if (target == null) {
                return false;
            }
            variant.put("model", target);
        }
        return true;
    }

    private static boolean validAttribute(String key, Object value) {
        switch (key) {
            case "model":
                return value instanceof String;
            case "uvlock":
                return value instanceof Boolean;
            case "x":
            case "y":
                return value instanceof Long && (Long) value >= 0L && (Long) value <= 270L && (Long) value % 90L == 0L;
            case "weight":
                return value instanceof Long && (Long) value > 0L && (Long) value <= Integer.MAX_VALUE;
            default:
                return false;
        }
    }

    private static String modelId(String id, Set<String> files) {
        if (!MODEL_ID.matcher(id).matches()) {
            return null;
        }
        int colon = id.indexOf(':');
        String namespace = colon == -1 ? "minecraft" : id.substring(0, colon);
        String legacy = id.substring(colon + 1);
        for (String segment : legacy.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        String original = "block/" + legacy;
        String modelRoot = "assets/" + namespace + "/models/";
        boolean localOriginal = files.contains(modelRoot + original + ".json");
        // 1.12.2 无条件在模型路径前加 block/。已有现代目录的混合输入，除非包内确实有旧解析指向的文件，否则不猜。
        if (!localOriginal && (legacy.startsWith("block/") || legacy.startsWith("item/") || legacy.startsWith("builtin/"))) {
            return null;
        }
        String target = original;
        if ("minecraft".equals(namespace)) {
            String alias = MODELS.get(legacy);
            if (alias != null) {
                String renamed = "block/" + alias;
                boolean localRenamed = files.contains(modelRoot + renamed + ".json");
                boolean localGrass = files.contains(modelRoot + "block/grass.json");
                if ("tall_grass".equals(legacy) && localGrass) {
                    // 旧 grass 是雪草方块的模板，新 grass 是短草；与其猜其时代，不如沿仍存在的旧模型（若有）。
                    if (!localOriginal) {
                        return null;
                    }
                } else if ("grass_normal".equals(legacy) && localGrass && !localOriginal && !localRenamed) {
                    // 没有自定义 grass_normal，仍可能通过旧模板继承作者外观，不能换成现代内置模型。
                    return null;
                } else if (!localOriginal || localRenamed) {
                    target = renamed;
                }
            } else if ("grass".equals(legacy) && !localOriginal) {
                return null; // 同名旧模板和现代短草并非同一模型。
            }
        }
        return colon == -1 ? target : namespace + ":" + target;
    }

    private static String state(String name) {
        return STATES + name + ".json";
    }

    private static void warn(UpgradeWorkspace workspace, String source, String diagnostic) {
        ClassicPackUpgrades.warn(workspace, 4, source, diagnostic);
    }

    private static final class Transition {
        final String target;
        final Map<String, Object> root;

        Transition(String target, Map<String, Object> root) {
            this.target = target;
            this.root = root;
        }
    }
}
