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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;

/** 73.0 分图集：只报告能从当前包推断的直接生成物品模型，不修改资源。 */
final class ItemAtlasSplitDiagnostics {

    private static final int MAX_ITEM_FILES = 2048;
    private static final int MAX_MODEL_DEPTH = 8;
    private static final int MAX_JSON_BYTES = 16 * 1024;
    private static final int MAX_TOTAL_JSON_BYTES = 8 * 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 24;
    private static final int MAX_REPORTS = 32;
    private static final int MAX_ID_LENGTH = 256;
    private static final int GENERATED_LAYER_COUNT = 5;
    private static final String VANILLA_ASSETS = "assets/minecraft/";
    private static final Pattern ID_PATTERN = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_/.-]+");

    private ItemAtlasSplitDiagnostics() {
    }

    static void inspect(UpgradeWorkspace workspace) throws IOException {
        new Inspection(workspace).inspect();
    }

    private static final class Inspection {
        private final UpgradeWorkspace workspace;
        private final List<String> files;
        private final byte[] buffer = new byte[MAX_JSON_BYTES + 1];
        private int remainingBytes = MAX_TOTAL_JSON_BYTES;

        Inspection(UpgradeWorkspace workspace) throws IOException {
            this.workspace = workspace;
            // 只建一次工作树清单；精确大小写查找也适用于默认不区分大小写的 Windows。
            this.files = workspace.files();
        }

        void inspect() {
            int inspected = 0;
            int reported = 0;
            boolean customAtlas = hasFile(VANILLA_ASSETS + "atlases/blocks.json")
                    || hasFile(VANILLA_ASSETS + "atlases/items.json");
            for (String file : files) {
                if (!isItemDefinition(file)) {
                    continue;
                }
                if (++inspected > MAX_ITEM_FILES || remainingBytes == 0) {
                    break;
                }
                Map<String, Object> leaf = JsonAccess.object(readObject(file), "model");
                String type = JsonAccess.string(leaf, "type", "");
                if (!"minecraft:model".equals(type) && !"model".equals(type)) {
                    continue;
                }
                String model = JsonAccess.string(leaf, "model", "");
                String[] layers = generatedLayers(model);
                if (layers == null) {
                    continue;
                }
                String item = null;
                String block = null;
                boolean known = true;
                for (String layer : layers) {
                    if (layer == null) {
                        break;
                    }
                    int colon = layer.indexOf(':');
                    String texture = layer.substring(colon + 1);
                    if (!hasFile(assetPath(layer, "textures", ".png"))) {
                        known = false;
                        break;
                    }
                    if (texture.startsWith("item/")) {
                        item = layer;
                    } else if (texture.startsWith("block/")) {
                        block = layer;
                    } else {
                        known = false;
                        break;
                    }
                }
                if (!known || item == null || block == null) {
                    continue;
                }
                if (++reported > MAX_REPORTS) {
                    workspace.warn("73.0: additional probable mixed item/block atlas models omitted (limit: "
                            + MAX_REPORTS + ").");
                    break;
                }
                workspace.warn("73.0: probable mixed item/block atlas in " + file + " (model " + model
                        + "; item sprite " + item + ", block sprite " + block + "). One item model's quads must"
                        + " share one atlas. Based on pack-local textures and vanilla directory sources; atlas"
                        + " aliases, filters and the active pack stack can change resolution."
                        + (customAtlas ? " This pack defines a vanilla blocks or items atlas; review its sources."
                        : ""));
            }
        }

        private String[] generatedLayers(String model) {
            String[] layers = new String[GENERATED_LAYER_COUNT];
            for (int depth = 0; depth <= MAX_MODEL_DEPTH; depth++) {
                if (!isResourceId(model)) {
                    return null;
                }
                String file = assetPath(model, "models", ".json");
                if (!hasFile(file)) {
                    // item/generated 是 vanilla 对内置生成器的标准父级；本包有同名文件时实际读取它。
                    return depth > 0 && (isVanilla(model, "builtin/generated")
                            || isVanilla(model, "item/generated")) && contiguous(layers) ? layers : null;
                }
                if (depth == MAX_MODEL_DEPTH) {
                    return null;
                }
                Map<String, Object> geometry = readObject(file);
                if (geometry == null || geometry.containsKey("elements") || geometry.containsKey("loader")) {
                    return null;
                }
                Map<String, Object> textures = JsonAccess.object(geometry, "textures");
                if (textures == null && geometry.containsKey("textures")) {
                    return null;
                }
                if (textures != null) {
                    for (int layer = 0; layer < layers.length; layer++) {
                        String key = "layer" + layer;
                        if (layers[layer] == null && textures.containsKey(key)) {
                            String value = JsonAccess.string(textures, key, "");
                            if (!isResourceId(value)) {
                                return null;
                            }
                            layers[layer] = value;
                        }
                    }
                }
                model = JsonAccess.string(geometry, "parent", "");
            }
            return null;
        }

        private boolean hasFile(String file) {
            return Collections.binarySearch(files, file) >= 0;
        }

        private Map<String, Object> readObject(String file) {
            // JSON 解析器递归下降；先限制字节数和结构深度。无法读取时不作推断。
            int allowed = Math.min(buffer.length, remainingBytes);
            if (allowed == 0) {
                return null;
            }
            int length = 0;
            try (InputStream input = Files.newInputStream(workspace.path(file))) {
                while (length < allowed) {
                    int count = input.read(buffer, length, allowed - length);
                    if (count < 0) {
                        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(buffer, 0, length))
                                .toString();
                        return boundedStructure(text) ? Json.parseObjectRejectingDuplicateKeys(text) : null;
                    }
                    length += count;
                }
            } catch (IOException ignored) {
                return null;
            } finally {
                remainingBytes -= length;
            }
            return null;
        }
    }

    private static boolean boundedStructure(String text) {
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == '{' || c == '[') {
                if (++depth > MAX_JSON_DEPTH) {
                    return false;
                }
            } else if (c == '}' || c == ']') {
                if (--depth < 0) {
                    return false;
                }
            } else if (c == '/') {
                // 不接受注释，避免把注释中的引号/括号误当作结构。
                return false;
            }
        }
        return depth == 0 && !quoted;
    }

    private static boolean contiguous(String[] layers) {
        boolean gap = false;
        for (String layer : layers) {
            if (layer == null) {
                gap = true;
            } else if (gap) {
                return false;
            }
        }
        return layers[1] != null;
    }

    private static boolean isVanilla(String id, String path) {
        return path.equals(id) || ("minecraft:" + path).equals(id);
    }

    private static boolean isItemDefinition(String file) {
        int namespaceEnd = file.startsWith("assets/") ? file.indexOf('/', 7) : -1;
        return namespaceEnd > 7 && file.startsWith("/items/", namespaceEnd) && file.endsWith(".json")
                && isResourceId(file.substring(7, namespaceEnd) + ":"
                        + file.substring(namespaceEnd + 7, file.length() - 5));
    }

    private static String assetPath(String id, String type, String suffix) {
        int colon = id.indexOf(':');
        String prefix = colon < 0 ? VANILLA_ASSETS : "assets/" + id.substring(0, colon) + "/";
        return prefix + type + "/" + id.substring(colon + 1) + suffix;
    }

    private static boolean isResourceId(String id) {
        if (id.length() > MAX_ID_LENGTH || !ID_PATTERN.matcher(id).matches()) {
            return false;
        }
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        if (".".equals(namespace) || "..".equals(namespace)) {
            return false;
        }
        for (String segment : id.substring(colon + 1).split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }
}
