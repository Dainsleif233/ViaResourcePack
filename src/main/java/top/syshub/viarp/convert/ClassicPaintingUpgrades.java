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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 把已核实的旧绘画图集槽位补成独立纹理，不移除旧图集。 */
final class ClassicPaintingUpgrades {

    private static final String PAINTING = "assets/minecraft/textures/painting/";
    private static final String SOURCE = PAINTING + "paintings_kristoffer_zetterstrand.png";
    private static final int EDGE = 256;

    // 左上角坐标与大小均为原版像素；依据见 docs/research/classic-paintings.md。
    private static final Crop[] CROPS = {
            new Crop("kebab", 0, 0, 16, 16),
            new Crop("aztec", 16, 0, 16, 16),
            new Crop("alban", 32, 0, 16, 16),
            new Crop("aztec2", 48, 0, 16, 16),
            new Crop("bomb", 64, 0, 16, 16),
            new Crop("plant", 80, 0, 16, 16),
            new Crop("wasteland", 96, 0, 16, 16),
            new Crop("pool", 0, 32, 32, 16),
            new Crop("courbet", 32, 32, 32, 16),
            new Crop("sea", 64, 32, 32, 16),
            new Crop("sunset", 96, 32, 32, 16),
            new Crop("creebet", 128, 32, 32, 16),
            new Crop("wanderer", 0, 64, 16, 32),
            new Crop("graham", 16, 64, 16, 32),
            new Crop("match", 0, 128, 32, 32),
            new Crop("bust", 32, 128, 32, 32),
            new Crop("stage", 64, 128, 32, 32),
            new Crop("void", 96, 128, 32, 32),
            new Crop("skull_and_roses", 128, 128, 32, 32),
            new Crop("wither", 160, 128, 32, 32),
            new Crop("fighters", 0, 96, 64, 32),
            new Crop("pointer", 0, 192, 64, 64),
            new Crop("pigscene", 64, 192, 64, 64),
            new Crop("burning_skull", 128, 192, 64, 64),
            new Crop("skeleton", 192, 64, 64, 48),
            new Crop("donkey_kong", 192, 112, 64, 48),
            new Crop("back", 192, 0, 16, 16),
    };

    private ClassicPaintingUpgrades() {
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        if (!workspace.hasFile(SOURCE)) {
            return;
        }
        // Windows 上也只接受确切的原版输入路径；输出保护按不区分大小写判断。
        List<String> files = workspace.files();
        if (!files.contains(SOURCE)) {
            return;
        }
        Set<String> occupied = new HashSet<String>();
        for (String file : files) {
            occupied.add(file.toLowerCase(Locale.ROOT));
        }
        List<Crop> missing = new ArrayList<Crop>();
        for (Crop crop : CROPS) {
            String target = crop.target();
            if (!exists(workspace, occupied, target) && !exists(workspace, occupied, target + ".mcmeta")) {
                missing.add(crop);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        if (exists(workspace, occupied, SOURCE + ".mcmeta")) {
            workspace.warn("经典绘画：无法把旧侧车安全分配到各纹理，保留旧 PNG 和侧车：" + SOURCE);
            return;
        }
        BufferedImage sheet;
        try {
            sheet = UpgradePng.scaledSheet(workspace.path(SOURCE), EDGE, EDGE);
        } catch (IOException ex) {
            workspace.warn("经典绘画：无法拆分旧 PNG，保留旧图：" + SOURCE + "（" + ex.getMessage() + "）");
            return;
        }
        try {
            int scale = sheet.getWidth() / EDGE;
            for (Crop crop : missing) {
                BufferedImage image = sheet.getSubimage(crop.x * scale, crop.y * scale,
                        crop.width * scale, crop.height * scale);
                UpgradePng.writeNew(workspace.path(crop.target()), image);
            }
        } finally {
            sheet.flush();
        }
    }

    private static boolean exists(UpgradeWorkspace workspace, Set<String> occupied, String relative) throws IOException {
        return occupied.contains(relative.toLowerCase(Locale.ROOT))
                || Files.exists(workspace.path(relative), LinkOption.NOFOLLOW_LINKS);
    }

    private static final class Crop {
        final String id;
        final int x;
        final int y;
        final int width;
        final int height;

        Crop(String id, int x, int y, int width, int height) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        String target() {
            return PAINTING + id + ".png";
        }
    }
}
