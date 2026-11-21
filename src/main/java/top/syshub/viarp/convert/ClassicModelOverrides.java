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
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 保全未提供方块状态的旧模型覆盖；仅复制已证实为同一资源身份的模型。 */
final class ClassicModelOverrides {

    private static final int MAX_DEPENDENCIES = 32;
    private static final Pattern MODEL_ID = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final String MODELS = "assets/minecraft/models/block/";
    private static final String[][] RENAMES = {
            {"silver_wool", "light_gray_wool"},
            {"granite_smooth", "polished_granite"},
            {"diorite_smooth", "polished_diorite"},
            {"andesite_smooth", "polished_andesite"}
    };

    private ClassicModelOverrides() {
    }

    /** 在格式 4 的引用改写完成后，复制已更新纹理引用的完整文件；旧 ID 继续可用。 */
    static void apply(UpgradeWorkspace workspace) throws IOException {
        Map<String, String> occupied = new HashMap<String, String>();
        for (String file : workspace.files()) {
            String folded = file.toLowerCase(Locale.ROOT);
            if (occupied.putIfAbsent(folded, file) != null) {
                occupied.put(folded, "");
            }
        }
        for (String[] rename : RENAMES) {
            String source = MODELS + rename[0] + ".json";
            String original = occupied.get(source);
            if (original == null) {
                continue;
            }
            if (!source.equals(original)) {
                ClassicPackUpgrades.warn(workspace, 4, source, "来源路径大小写不明确；不复制模型");
                continue;
            }
            String target = MODELS + rename[1] + ".json";
            Path targetPath = workspace.path(target);
            if (occupied.containsKey(target) || Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                ClassicPackUpgrades.warn(workspace, 4, source, "保留已有模型目标，不覆盖 " + target);
                continue;
            }
            Map<String, Object> model = ClassicPackUpgrades.readObject(workspace, 4, source);
            if (model == null) {
                continue;
            }
            if (!canCopy(workspace, occupied, model, source, target)) {
                ClassicPackUpgrades.warn(workspace, 4, source, "模型本地依赖不明确或会变成自引用；保留原文件");
                continue;
            }
            // 子模型不会继承父模型的 overrides，因此复制整份文件，而非合成 parent 别名。
            Files.copy(workspace.path(source), targetPath);
            occupied.put(target, target);
        }
    }

    private static boolean canCopy(UpgradeWorkspace workspace, Map<String, String> occupied,
                                   Map<String, Object> root, String source, String target) throws IOException {
        Set<String> visited = new HashSet<String>();
        Deque<Object> pending = new ArrayDeque<Object>();
        enqueueReferences(root, pending);
        while (!pending.isEmpty()) {
            String file = modelFile(pending.removeFirst());
            if (file == null || file.equals(source) || file.equals(target)) {
                return false;
            }
            String local = occupied.get(file);
            if (local == null || !visited.add(file)) {
                continue;
            }
            if (visited.size() > MAX_DEPENDENCIES || !file.equals(local)) {
                return false;
            }
            Map<String, Object> model = ClassicPackUpgrades.readObject(workspace, 4, local);
            if (model == null) {
                return false;
            }
            enqueueReferences(model, pending);
        }
        return true;
    }

    private static void enqueueReferences(Map<String, Object> model, Deque<Object> queue) {
        Object parent = model.get("parent");
        if (parent != null) {
            queue.addLast(parent);
        }
        if (model.get("overrides") instanceof List) {
            for (Object value : (List<?>) model.get("overrides")) {
                Map<String, Object> override = ClassicPackUpgrades.object(value);
                if (override != null && override.get("model") != null) {
                    queue.addLast(override.get("model"));
                }
            }
        }
    }

    private static String modelFile(Object value) {
        if (!(value instanceof String) || !MODEL_ID.matcher((String) value).matches()) {
            return null;
        }
        String id = (String) value;
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (".".equals(namespace) || "..".equals(namespace)) {
            return null;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return "assets/" + namespace + "/models/" + path + ".json";
    }
}
