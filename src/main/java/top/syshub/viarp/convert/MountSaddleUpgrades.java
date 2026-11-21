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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 47 → 48：从组合的实体纹理提取原版鞍具 UV；不删除旧图，不合成缺失纹理。 */
final class MountSaddleUpgrades {
    private static final String ENTITY = "assets/minecraft/textures/entity/";
    private static final String SADDLE_ASSET = "assets/minecraft/equipment/saddle.json";
    private static final String[][] SOURCES = {
            { "camel", "camel/camel" },
            { "donkey", "horse/donkey" },
            { "mule", "horse/mule" },
            { "skeleton_horse", "horse/horse_skeleton" },
            { "zombie_horse", "horse/horse_zombie" },
            { "horse", "horse/horse_white", "horse/horse_creamy", "horse/horse_chestnut", "horse/horse_brown",
                    "horse/horse_black", "horse/horse_gray", "horse/horse_darkbrown" }
    };

    // (u, v, width, height, depth)：25w03a CamelSaddleModel / EquineSaddleModel 的鞍、笼头和缰绳。
    // 参见 docs/research/mount-texture-evidence.md；体表仍在装备模型中，掩膜之外必须透明。
    private static final Mask CAMEL = new Mask(128, new int[][] {
            { 74, 64, 9, 5, 11 }, { 92, 114, 7, 3, 11 }, { 0, 89, 15, 12, 27 },
            { 98, 42, 0, 7, 15 }, { 84, 57, 7, 7, 0 }, { 60, 87, 7, 8, 19 },
            { 21, 64, 7, 14, 7 }, { 50, 64, 5, 5, 6 }, { 74, 70, 1, 2, 2 }
    });
    private static final Mask EQUINE = new Mask(64, new int[][] {
            { 26, 0, 10, 9, 9 }, { 29, 5, 1, 2, 2 }, { 32, 2, 0, 3, 16 },
            { 1, 1, 6, 5, 6 }, { 19, 0, 4, 5, 2 }
    });

    private MountSaddleUpgrades() {
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        for (String[] group : SOURCES) {
            for (int i = 1; i < group.length; i++) {
                if (workspace.hasFile(source(group[i]))) {
                    new Migration(workspace).run();
                    return;
                }
            }
        }
    }

    private static String source(String name) {
        return ENTITY + name + ".png";
    }

    private static String fold(String path) {
        return path.toLowerCase(Locale.ROOT);
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static final class Migration {
        private final UpgradeWorkspace workspace;
        private final Set<String> files;
        private final Map<String, String> spelling = new HashMap<String, String>();

        Migration(UpgradeWorkspace workspace) throws IOException {
            this.workspace = workspace;
            this.files = new HashSet<String>(workspace.files());
            for (String file : files) {
                for (String part = file; !part.isEmpty(); part = parent(part)) {
                    String key = fold(part);
                    String previous = spelling.putIfAbsent(key, part);
                    if (previous != null && !previous.equals(part)) {
                        spelling.put(key, "");
                    }
                }
            }
        }

        void run() throws IOException {
            if (occupied(SADDLE_ASSET) || occupied(SADDLE_ASSET + ".mcmeta")) {
                warn("kept custom saddle equipment asset; skipped vanilla saddle extraction.");
                return;
            }
            for (String[] group : SOURCES) {
                String mount = group[0];
                String target = ENTITY + "equipment/" + mount + "_saddle/saddle.png";
                extract(group, target, "camel".equals(mount) ? CAMEL : EQUINE);
            }
        }

        private void extract(String[] group, String target, Mask mask) throws IOException {
            int found = 0;
            for (int i = 1; i < group.length; i++) {
                if (files.contains(source(group[i]))) {
                    found++;
                }
            }
            if (found == 0 || !available(target)) {
                return;
            }
            if (found != group.length - 1) {
                warn("skipped horse_saddle; all seven old coat textures are needed for one shared saddle.");
                return;
            }
            BufferedImage saddle = null;
            try {
                for (int i = 1; i < group.length; i++) {
                    BufferedImage sheet = readSheet(source(group[i]), mask.size);
                    if (sheet == null) {
                        return;
                    }
                    try {
                        if (saddle == null) {
                            saddle = new BufferedImage(sheet.getWidth(), sheet.getHeight(), BufferedImage.TYPE_INT_ARGB);
                            mask.transfer(sheet, saddle, false);
                        } else if (sheet.getWidth() != saddle.getWidth() || !mask.transfer(sheet, saddle, true)) {
                            warn("skipped horse_saddle; the old coats disagree on saddle pixels or resolution.");
                            return;
                        }
                    } finally {
                        sheet.flush();
                    }
                }
                if (saddle != null) {
                    try {
                        UpgradePng.writeNew(workspace.path(target), saddle);
                    } catch (FileAlreadyExistsException ex) {
                        warn("kept existing saddle texture: " + target);
                    }
                }
            } finally {
                if (saddle != null) {
                    saddle.flush();
                }
            }
        }

        private BufferedImage readSheet(String source, int size) throws IOException {
            if (!source.equals(spelling.get(fold(source))) || occupied(source + ".mcmeta")) {
                warn("retained " + source + "; source spelling collision or metadata prevents safe saddle extraction.");
                return null;
            }
            Path path = workspace.path(source);
            try {
                BufferedImage image = UpgradePng.scaledSheet(path, size, size);
                for (int bits : image.getColorModel().getComponentSize()) {
                    if (bits > 8) {
                        image.flush();
                        warn("retained " + source + "; saddle extraction to RGBA8 would lose pixel precision.");
                        return null;
                    }
                }
                return image;
            } catch (IOException ex) {
                warn("retained " + source + "; expected an integer-scaled " + size + "×" + size + " PNG: "
                        + ex.getMessage());
                return null;
            }
        }

        private boolean available(String target) throws IOException {
            if (occupied(target) || occupied(target + ".mcmeta") || !directoriesAvailable(target)) {
                warn("kept existing saddle texture, sidecar or conflicting path: " + target);
                return false;
            }
            return true;
        }

        private boolean directoriesAvailable(String target) throws IOException {
            for (String dir = parent(target); !dir.isEmpty(); dir = parent(dir)) {
                String existing = spelling.get(fold(dir));
                Path path = workspace.path(dir);
                if ((existing != null && !existing.equals(dir)) || (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))) {
                    return false;
                }
            }
            return true;
        }

        private boolean occupied(String name) throws IOException {
            return spelling.containsKey(fold(name)) || Files.exists(workspace.path(name), LinkOption.NOFOLLOW_LINKS);
        }

        private void warn(String message) {
            workspace.warn("48.0: " + message);
        }
    }

    private static final class Mask {
        private final int size;
        private final boolean[][] used;

        Mask(int size, int[][] cubes) {
            this.size = size;
            this.used = new boolean[size][size];
            for (int[] cube : cubes) {
                int u = cube[0];
                int v = cube[1];
                int w = cube[2];
                int h = cube[3];
                int d = cube[4];
                rectangle(u + d, v, 2 * w, d);
                rectangle(u, v + d, 2 * (w + d), h);
            }
        }

        private void rectangle(int u, int v, int width, int height) {
            for (int y = v; y < v + height; y++) {
                for (int x = u; x < u + width; x++) {
                    used[y][x] = true;
                }
            }
        }

        private boolean transfer(BufferedImage sheet, BufferedImage saddle, boolean compare) {
            int width = sheet.getWidth();
            int scale = width / size;
            int[] row = new int[width];
            int[] reference = compare ? new int[width] : null;
            for (int y = 0; y < width; y++) {
                sheet.getRGB(0, y, width, 1, row, 0, width);
                for (int x = 0; x < width; x++) {
                    // 全透明像素的 RGB 在原版非模糊 cutout 下不参与渲染。
                    if (!used[y / scale][x / scale] || (row[x] >>> 24) == 0) {
                        row[x] = 0;
                    }
                }
                if (compare) {
                    saddle.getRGB(0, y, width, 1, reference, 0, width);
                    if (!Arrays.equals(row, reference)) {
                        return false;
                    }
                } else {
                    saddle.setRGB(0, y, width, 1, row, 0, width);
                }
            }
            return true;
        }
    }
}
