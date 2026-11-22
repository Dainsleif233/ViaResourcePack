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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;

/** 83.0：只还原已经用相邻源码和官方物品定义核对的特殊模型姿态。 */
final class SpecialItemTransforms {

    private static final Pattern ITEM_DEFINITION = Pattern.compile("assets/[a-z0-9_.-]+/items/[a-z0-9_/.-]+[.]json");
    private static final String TRANSFORMATION = "transformation";

    private SpecialItemTransforms() {
    }

    /** 仅在向上进入 83.0 时对已经投影的来源资源执行，不改几何模型的 display。 */
    static void apply(UpgradeWorkspace workspace) throws IOException {
        for (String file : workspace.files()) {
            if (!ITEM_DEFINITION.matcher(file).matches()) {
                continue;
            }
            Map<String, Object> root = workspace.readObject(file, "83.0 special item models");
            if (migrate(workspace, file, JsonAccess.object(root, "model"))) {
                workspace.writeText(file, Json.write(root) + "\n");
            }
        }
    }

    private static boolean migrate(UpgradeWorkspace workspace, String file, Map<String, Object> model) {
        Deque<Frame> pending = new ArrayDeque<Frame>();
        push(pending, model, false);
        boolean changed = false;
        while (!pending.isEmpty()) {
            Frame frame = pending.pop();
            Map<String, Object> node = frame.model;
            boolean hasTransformation = frame.ancestorHasTransformation || node.containsKey(TRANSFORMATION);
            String type = vanillaPath(node, "type");
            switch (type) {
                case "special":
                    changed |= migrateLeaf(workspace, file, node, hasTransformation);
                    break;
                case "condition":
                    push(pending, JsonAccess.object(node, "on_true"), hasTransformation);
                    push(pending, JsonAccess.object(node, "on_false"), hasTransformation);
                    break;
                case "select":
                case "range_dispatch":
                    push(pending, JsonAccess.object(node, "fallback"), hasTransformation);
                    List<Object> entries = JsonAccess.array(node, "select".equals(type) ? "cases" : "entries");
                    if (entries != null) {
                        for (Object entry : entries) {
                            push(pending, JsonAccess.object(object(entry), "model"), hasTransformation);
                        }
                    }
                    break;
                case "composite":
                    List<Object> children = JsonAccess.array(node, "models");
                    if (children != null) {
                        for (Object child : children) {
                            push(pending, object(child), hasTransformation);
                        }
                    }
                    break;
                default:
                    // 不走进 mod 节点、属性、tint 或 special 渲染器自己的参数。
                    break;
            }
        }
        return changed;
    }

    private static boolean migrateLeaf(UpgradeWorkspace workspace, String file, Map<String, Object> node,
            boolean hasTransformation) {
        Map<String, Object> renderer = JsonAccess.object(node, "model");
        String type = vanillaPath(renderer, "type");
        switch (type) {
            case "shield":
            case "trident":
            case "conduit":
                if (hasTransformation) {
                    // 不能判断是目标姿态还是其他用途；绝不叠加或替换作者提供的字段。
                    return false;
                }
                if (node.size() == 3 && renderer.size() == 1 && knownBase(type, vanillaPath(node, "base"))) {
                    node.put(TRANSFORMATION, transformation("conduit".equals(type)));
                    return true;
                }
                warn(workspace, file, type, "leaf does not match a verified legacy schema/base");
                break;
            case "shulker_box":
                if (renderer.containsKey("orientation")) {
                    warn(workspace, file, type, "target ignores legacy orientation; review rotation and placement");
                } else if (!hasTransformation) {
                    warn(workspace, file, type, "review the removed built-in pose and scale");
                }
                break;
            case "bed":
                if (!renderer.containsKey("part")) {
                    warn(workspace, file, type, "83.0 requires part and renders one half per leaf; later formats change bed models again");
                }
                break;
            case "banner":
            case "copper_golem_statue":
            case "head":
            case "player_head":
            case "standing_sign":
            case "hanging_sign":
                if (!hasTransformation) {
                    warn(workspace, file, type, "review the removed built-in pose");
                }
                break;
            default:
                // chest / decorated_pot 不需要凭空加入变换；其他 ID 不推测兼容性。
                break;
        }
        return false;
    }

    private static boolean knownBase(String renderer, String base) {
        switch (renderer) {
            case "shield":
                return "item/shield".equals(base) || "item/shield_blocking".equals(base);
            case "trident":
                return "item/trident_in_hand".equals(base) || "item/trident_throwing".equals(base);
            case "conduit":
                return "item/conduit".equals(base);
            default:
                return false;
        }
    }

    private static Map<String, Object> transformation(boolean conduit) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("left_rotation", Arrays.asList(0, 0, 0, 1));
        result.put("right_rotation", Arrays.asList(0, 0, 0, 1));
        if (conduit) {
            result.put("scale", Arrays.asList(1, 1, 1));
            result.put("translation", Arrays.asList(0.5, 0.5, 0.5));
        } else {
            result.put("scale", Arrays.asList(1, -1, -1));
            result.put("translation", Arrays.asList(0, 0, 0));
        }
        return result;
    }

    private static String vanillaPath(Map<String, Object> node, String key) {
        String id = JsonAccess.string(node, key, "");
        return id.startsWith("minecraft:") ? id.substring(10) : id.indexOf(':') < 0 ? id : "";
    }

    private static void warn(UpgradeWorkspace workspace, String file, String renderer, String reason) {
        workspace.warn("83.0: retained special item renderer minecraft:" + renderer + " in " + file + "; " + reason + ".");
    }

    private static void push(Deque<Frame> pending, Map<String, Object> model, boolean ancestorHasTransformation) {
        if (model != null) {
            pending.push(new Frame(model, ancestorHasTransformation));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static final class Frame {
        private final Map<String, Object> model;
        private final boolean ancestorHasTransformation;

        private Frame(Map<String, Object> model, boolean ancestorHasTransformation) {
            this.model = model;
            this.ancestorHasTransformation = ancestorHasTransformation;
        }
    }
}
