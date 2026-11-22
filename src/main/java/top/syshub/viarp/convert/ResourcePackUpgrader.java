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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.pack.PackFormat;
import top.syshub.viarp.pack.PackMetadata;

/** 升级内部工作树：固定原版源格式视图，逐节点迁移，最后才发布新的格式声明。 */
final class ResourcePackUpgrader {

    // 产物、源视图或缓存内诊断有变化时递增；不得重用旧规则的结果。
    static final String RULES_VERSION = "builtin-32";

    private ResourcePackUpgrader() {
    }

    static boolean canUpgrade(PackFormat from, PackFormat target) {
        return PackFormatHistory.knows(from) && PackFormatHistory.knows(target)
                && from.compareTo(target) < 0 && target.compareTo(PackFormatHistory.LAST_CLIENT_TARGET) <= 0;
    }

    static List<String> upgrade(Path workDir, PackInput source, PackFormat target, boolean verbose) throws IOException {
        PackFormat from = source.format();
        if (!source.canUpgradeTo(target) || !canUpgrade(from, target)) {
            throw new IOException("No upward route from " + from + " to " + target);
        }
        UpgradeWorkspace pack = new UpgradeWorkspace(workDir);
        PackMetadata metadata = source.metadata();
        if (metadata != null) {
            projectOverlays(pack, metadata.overlaysFor(from));
        }
        List<PackFormat> route = PackFormatHistory.route(from, target);
        if (verbose) {
            // config 已主动开启详细日志，不要求另外开启 Minecraft 的 DEBUG 级别。
            ViaRpLog.info("Resource pack migration route: {} -> {}", from, route);
        }
        // 同一旧滑杆在格式 16 与 17+ 的缺省尺寸不同，只按最终格式生成一次。
        int widgetBoundary = Math.max(16, from.major() + 1);
        // 独立绘画纹理在格式 4 内引入。首次到 4 时拆分；以旧格式 4 为源时在 5 补做。
        int paintingBoundary = Math.max(4, from.major() + 1);
        for (PackFormat entered : route) {
            if (widgetBoundary <= 18 && entered.major() == widgetBoundary) {
                ClassicWidgetDetails.apply(pack, target.major() >= 17);
            }
            if (entered.equals(PackFormatHistory.LOOSE_TEXTURES)) {
                LegacyTextureUpgrade.unstitch(pack);
            } else if (entered.major() == 1) {
                LegacyTextureUpgrade.toResourcePack(pack);
            } else if (entered.major() < 18) {
                ClassicPackUpgrades.apply(entered, pack);
            } else if (entered.major() < 65) {
                IntermediatePackUpgrades.apply(entered, pack);
            } else {
                ModernPackUpgrades.apply(entered, pack);
            }
            if (paintingBoundary <= 5 && entered.major() == paintingBoundary) {
                ClassicPaintingUpgrades.apply(pack);
            }
        }
        if (from.major() >= 44) {
            LegacyItemReferenceRepair.apply(pack);
        }
        // 对旧纹理包，格式 1 步骤负责从 pack.txt 生成原有描述。
        String migratedText = pack.readText("pack.mcmeta", "升级结果");
        if (migratedText == null) {
            throw new IOException("Cannot verify migrated pack.mcmeta");
        }
        PackMetadata migrated = PackMetadata.parse(migratedText);
        pack.writeText("pack.mcmeta", migrated.withProjectedOverlays(target));
        return pack.warnings();
    }

    private static void projectOverlays(UpgradeWorkspace pack, List<String> active) throws IOException {
        if (active.isEmpty()) {
            return;
        }
        Map<String, Integer> order = new HashMap<String, Integer>();
        Map<String, String> names = new HashMap<String, String>();
        for (int i = 0; i < active.size(); i++) {
            String name = active.get(i);
            requireSamePath(names, name, name);
            order.put(name, i);
        }

        // 只扫描一次工作树；同路径的精确覆盖只复制声明顺序上的最终赢家。
        List<String> entries = pack.entries(true);
        Map<String, String> sources = new LinkedHashMap<String, String>();
        for (String entry : entries) {
            int separator = entry.indexOf('/');
            if (separator < 0 || entry.endsWith("/")) {
                continue;
            }
            String layer = entry.substring(0, separator);
            String declared = names.get(layer.toLowerCase(Locale.ROOT));
            String destination = entry.substring(separator + 1);
            if (declared == null || !isAssetsPath(destination)) {
                continue;
            }
            if (!layer.equals(declared) || !destination.startsWith("assets/")) {
                throw new IOException("Ambiguous overlay asset path collision: " + declared + " vs " + entry);
            }
            String previous = sources.get(destination);
            if (previous == null || order.get(layer) > order.get(previous.substring(0, previous.indexOf('/')))) {
                sources.put(destination, entry);
            }
        }
        if (sources.isEmpty()) {
            return;
        }

        // 不把不同大小写或文件/目录重名误当原版的精确覆盖。任何碰撞都在第一次复制前失败。
        Map<String, String> layout = new HashMap<String, String>();
        for (String entry : entries) {
            if (isAssetsPath(entry)) {
                registerPath(layout, entry);
            }
        }
        for (String destination : sources.keySet()) {
            String part = destination;
            while (registerPath(layout, part)) {
                int last = part.lastIndexOf('/', part.length() - 2);
                if (last < 0) {
                    break;
                }
                part = part.substring(0, last + 1);
            }
        }
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path destination = pack.path(source.getKey());
            Files.createDirectories(destination.getParent());
            Files.copy(pack.path(source.getValue()), destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isAssetsPath(String path) {
        return "assets".equalsIgnoreCase(path) || path.regionMatches(true, 0, "assets/", 0, 7);
    }

    private static boolean registerPath(Map<String, String> layout, String path) throws IOException {
        String key = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return requireSamePath(layout, key, path);
    }

    private static boolean requireSamePath(Map<String, String> paths, String key, String path) throws IOException {
        String previous = paths.putIfAbsent(key.toLowerCase(Locale.ROOT), path);
        if (previous != null && !previous.equals(path)) {
            throw new IOException("Ambiguous overlay asset path collision: " + previous + " vs " + path);
        }
        return previous == null;
    }
}
