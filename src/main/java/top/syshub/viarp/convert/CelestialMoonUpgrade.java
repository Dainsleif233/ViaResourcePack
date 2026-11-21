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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 70.0：按原版 4 列 × 2 行的 UV 重用月相像素，原图与显式新图不变。 */
final class CelestialMoonUpgrade {
    private static final String ENVIRONMENT = "assets/minecraft/textures/environment/";
    private static final String SOURCE = ENVIRONMENT + "moon_phases.png";
    private static final String[] PHASES = {
            "full_moon", "waning_gibbous", "third_quarter", "waning_crescent",
            "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"
    };

    private CelestialMoonUpgrade() {
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        if (!workspace.hasFile(SOURCE)) {
            return;
        }
        Set<String> folded = new HashSet<String>();
        for (String name : workspace.files()) {
            folded.add(name.toLowerCase(Locale.ROOT));
        }
        if (occupied(workspace, folded, SOURCE + ".mcmeta")) {
            workspace.warn("70.0: retained moon_phases.png; a metadata sidecar prevents automatic slicing.");
            return;
        }
        BufferedImage moon;
        try {
            moon = UpgradePng.scaledSheet(workspace.path(SOURCE), 128, 64);
        } catch (IOException e) {
            workspace.warn("70.0: retained moon_phases.png; expected a uniformly scaled 128x64 PNG: " + e.getMessage());
            return;
        }
        try {
            int tile = moon.getWidth() / 4;
            for (int i = 0; i < PHASES.length; i++) {
                String target = ENVIRONMENT + "celestial/moon/" + PHASES[i] + ".png";
                if (occupied(workspace, folded, target) || occupied(workspace, folded, target + ".mcmeta")) {
                    workspace.warn("70.0: kept existing moon sprite or sidecar: " + target);
                    continue;
                }
                UpgradePng.writeNew(workspace.path(target), moon.getSubimage(i % 4 * tile, i / 4 * tile, tile, tile));
            }
        } finally {
            moon.flush();
        }
    }

    private static boolean occupied(UpgradeWorkspace workspace, Set<String> folded, String name) throws IOException {
        Path path = workspace.path(name);
        return folded.contains(name.toLowerCase(Locale.ROOT)) || Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }
}
