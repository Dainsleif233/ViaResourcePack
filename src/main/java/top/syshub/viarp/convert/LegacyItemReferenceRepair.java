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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;

/** 修正已知早期导出器把本包物品 fallback 写成了打包机绝对路径的问题。 */
final class LegacyItemReferenceRepair {
    private static final String ITEMS = "assets/minecraft/items/";
    private static final String MODELS = "assets/minecraft/models/item/";
    private static final String EXPORTED_MODEL = "\\assets\\minecraft\\models\\item\\";
    private static final Pattern ITEM = Pattern.compile("[a-z0-9_]+");

    private LegacyItemReferenceRepair() {
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        List<String> files = workspace.files();
        Set<String> exactPaths = new HashSet<String>(files);
        for (String entry : files) {
            if (!entry.startsWith(ITEMS) || !entry.endsWith(".json")) {
                continue;
            }
            String item = entry.substring(ITEMS.length(), entry.length() - ".json".length());
            if (!ITEM.matcher(item).matches() || !exactPaths.contains(MODELS + item + ".json")) {
                continue;
            }
            Map<String, Object> root = workspace.readObject(entry, "旧物品导出引用");
            Map<String, Object> model = JsonAccess.object(root, "model");
            Map<String, Object> fallback = JsonAccess.object(model, "fallback");
            if (!vanillaField(model, "type", "range_dispatch") || !vanillaField(model, "property", "custom_model_data")
                    || JsonAccess.array(model, "entries") == null || !vanillaField(fallback, "type", "model")) {
                continue;
            }
            String reference = JsonAccess.string(fallback, "model", "");
            if (!isExportedSameItem(reference, item)) {
                continue;
            }
            // 只认打包时留下的字符串形状，从不打开其绝对路径；精确同包几何已在工作树确认。
            String replacement = "minecraft:item/" + item;
            fallback.put("model", replacement);
            workspace.writeText(entry, Json.write(root));
            workspace.warn("旧物品导出引用 [" + entry + "] 已修为本包几何 " + replacement);
        }
    }

    private static boolean vanillaField(Map<String, Object> object, String key, String expected) {
        String value = JsonAccess.string(object, key, "");
        return expected.equals(value) || ("minecraft:" + expected).equals(value);
    }

    private static boolean isExportedSameItem(String reference, String item) {
        if (reference.length() < 4 || reference.charAt(1) != ':' || reference.charAt(2) != '\\'
                || !reference.endsWith(EXPORTED_MODEL + item)) {
            return false;
        }
        char drive = reference.charAt(0);
        return (drive >= 'A' && drive <= 'Z') || (drive >= 'a' && drive <= 'z');
    }
}
