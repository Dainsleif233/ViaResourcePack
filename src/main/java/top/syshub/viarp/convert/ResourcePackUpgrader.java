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
import java.util.List;

import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.pack.PackFormat;
import top.syshub.viarp.pack.PackMetadata;

/** 升级内部工作树：固定原版源格式视图，逐节点迁移，最后才发布新的格式声明。 */
final class ResourcePackUpgrader {

    // 改变产物内容或选择源视图的规则时递增；不得重用旧规则的产物。
    static final String RULES_VERSION = "builtin-14";

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
        if (verbose) {
            ViaRpLog.debug("Resource pack migration route: {} -> {}", from, PackFormatHistory.route(from, target));
        }
        for (PackFormat entered : PackFormatHistory.route(from, target)) {
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
        List<String> files = pack.files();
        for (String overlay : active) {
            String prefix = overlay + "/";
            for (String file : files) {
                if (file.startsWith(prefix + "assets/")) {
                    Path destination = pack.path(file.substring(prefix.length()));
                    Files.createDirectories(destination.getParent());
                    Files.copy(pack.path(file), destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
