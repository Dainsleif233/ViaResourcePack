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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonException;
import top.syshub.viarp.pack.PackFormat;

/** 仅重排原版 UV；不改工作树里的旧图、图集、模型或任何侧车。 */
final class EntitySheetUpgrades {

    private static final String VANILLA = "assets/minecraft/";
    private static final String TEXTURES = VANILLA + "textures/";
    private static final String BEDS = "entity/bed/";
    private static final String SIGNS = "entity/signs/";
    private static final String HANGING = SIGNS + "hanging/";

    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static final String[] WOODS = {
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak",
            "bamboo", "crimson", "warped"
    };

    private static final PixelOrder COPY = PixelOrder.COPY;
    private static final PixelOrder FLIP_Y = PixelOrder.FLIP_Y;
    private static final PixelOrder FLIP_X = PixelOrder.FLIP_X;
    private static final PixelOrder CW = PixelOrder.CW;
    private static final PixelOrder HALF = PixelOrder.HALF;
    private static final PixelOrder CCW = PixelOrder.CCW;
    private static final PixelOrder TRANSPOSE = PixelOrder.TRANSPOSE;
    private static final PixelOrder ANTI_TRANSPOSE = PixelOrder.ANTI_TRANSPOSE;

    // 85.0 实体长方体展开 → 86.0 模型正交面；窄条是边框，小方块是床腿投影。
    // 每块数据依次为输出原点、源矩形、像素朝向；未覆盖区域留透明。
    private static final Output[] COLORED_BED = {
            bed("head_up", tile(0, 0, 6, 6, 16, 16, COPY)),
            bed("foot_up", tile(0, 0, 6, 28, 16, 16, COPY)),
            bed("foot_south", tile(0, 7, 22, 22, 16, 6, FLIP_Y),
                    tile(0, 13, 53, 3, 3, 3, COPY), tile(3, 13, 56, 3, 3, 3, COPY),
                    tile(10, 13, 59, 15, 3, 3, COPY), tile(13, 13, 50, 15, 3, 3, COPY)),
            bed("foot_west", tile(0, 7, 0, 28, 6, 16, CCW),
                    tile(7, 13, 56, 0, 3, 3, FLIP_Y), tile(10, 13, 59, 3, 3, 3, COPY),
                    tile(13, 13, 50, 3, 3, 3, COPY)),
            bed("foot_east", tile(0, 7, 22, 28, 6, 16, CW),
                    tile(0, 13, 53, 15, 3, 3, COPY), tile(3, 13, 56, 15, 3, 3, COPY),
                    tile(6, 13, 56, 12, 3, 3, TRANSPOSE)),
            bed("head_west", tile(0, 7, 0, 6, 6, 16, CCW),
                    tile(0, 13, 53, 9, 3, 3, COPY), tile(3, 13, 56, 9, 3, 3, COPY),
                    tile(6, 13, 56, 6, 3, 3, ANTI_TRANSPOSE)),
            bed("head_east", tile(0, 7, 22, 6, 6, 16, CW),
                    tile(7, 13, 56, 18, 3, 3, FLIP_X), tile(10, 13, 59, 21, 3, 3, COPY),
                    tile(13, 13, 50, 21, 3, 3, COPY))
    };
    private static final Output[] SHARED_BED = {
            new Output("block/bed_down", 16, 16, tile(0, 0, 28, 6, 16, 16, HALF)),
            new Output("block/bed_head_north", 16, 16, tile(0, 7, 6, 0, 16, 6, HALF),
                    tile(0, 13, 53, 21, 3, 3, COPY), tile(3, 13, 56, 21, 3, 3, COPY),
                    tile(10, 13, 59, 9, 3, 3, COPY), tile(13, 13, 50, 9, 3, 3, COPY))
    };
    private static final Output SIGN_BLOCK = new Output("block/%s_sign", 32, 32,
            tile(0, 0, 2, 0, 24, 14, COPY), tile(24, 2, 26, 2, 2, 12, COPY),
            tile(0, 16, 28, 2, 24, 12, COPY), tile(24, 16, 0, 2, 2, 12, COPY),
            tile(0, 28, 26, 0, 24, 2, FLIP_Y), tile(28, 0, 2, 16, 4, 14, COPY),
            tile(28, 16, 6, 16, 2, 14, COPY), tile(30, 16, 0, 16, 2, 14, COPY),
            tile(28, 30, 4, 14, 2, 2, FLIP_Y));
    private static final Output SIGN_EDITOR = new Output("gui/signs/%s", 24, 26,
            tile(0, 0, 2, 2, 24, 12, COPY), tile(11, 12, 2, 16, 2, 14, COPY));
    private static final Output HANGING_BLOCK = new Output("block/%s_hanging_sign", 32, 32,
            // 牌面、顶、底；横梁；V 形 / 竖直链条。
            tile(0, 16, 0, 14, 32, 10, COPY), tile(2, 14, 2, 12, 14, 2, COPY),
            tile(2, 26, 16, 12, 14, 2, FLIP_Y), tile(0, 0, 4, 0, 16, 6, COPY),
            tile(0, 9, 20, 0, 16, 4, HALF), tile(16, 4, 20, 4, 4, 2, COPY),
            tile(0, 7, 24, 4, 16, 2, COPY), tile(16, 7, 0, 4, 4, 2, COPY),
            tile(20, 0, 14, 6, 12, 6, COPY), tile(22, 7, 0, 6, 9, 6, COPY));

    private EntitySheetUpgrades() {
    }

    static void apply(PackFormat entered, UpgradeWorkspace workspace) throws IOException {
        int major = entered.major();
        int minor = entered.minor();
        if (major == 86 && minor == 1) {
            warnRemovedAtlas(workspace, "86.1", "beds");
        } else if (major == 86 && minor == 0) {
            new Migration(workspace, "86.0").beds();
        } else if (major == 87 && minor == 0) {
            warnRemovedAtlas(workspace, "87.0", "signs");
            new Migration(workspace, "87.0").signs();
        }
    }

    private static String atlas(String id) {
        return VANILLA + "atlases/" + id + ".json";
    }

    private static void warnRemovedAtlas(UpgradeWorkspace workspace, String edge, String id) throws IOException {
        String path = atlas(id);
        if (workspace.hasFile(path)) {
            workspace.warn(edge + ": retained " + path + "; vanilla no longer loads this atlas. "
                    + "Adapt custom sources to the new block model textures manually.");
        }
    }

    private static Output bed(String face, Tile... tiles) {
        return new Output("block/%s_bed_" + face, 16, 16, tiles);
    }

    private static Tile tile(int dstX, int dstY, int srcX, int srcY, int width, int height, PixelOrder order) {
        return new Tile(dstX, dstY, srcX, srcY, width, height, order);
    }

    private static String fold(String path) {
        return path.toLowerCase(Locale.ROOT);
    }

    private static final class Migration {
        private final UpgradeWorkspace workspace;
        private final String edge;
        private final List<String> files;
        private final Set<String> originals;
        // 同时索引父目录拼写；不能向 Windows 下碰巧与规范拼写同名的目录输出。
        private final Map<String, String> spelling = new HashMap<String, String>();

        Migration(UpgradeWorkspace workspace, String edge) throws IOException {
            this.workspace = workspace;
            this.edge = edge;
            this.files = workspace.files();
            this.originals = new HashSet<String>(files);
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

        void beds() throws IOException {
            Set<String> supported = new HashSet<String>();
            for (String color : COLORS) {
                supported.add(TEXTURES + BEDS + color + ".png");
            }
            warnExtensions(BEDS, supported);
            if (!vanillaAtlasSources("beds", BEDS) || !hasAny(supported)) {
                return;
            }
            for (String color : COLORS) {
                String source = TEXTURES + BEDS + color + ".png";
                if ("black".equals(color)) {
                    slice(source, color, 64, COLORED_BED, SHARED_BED);
                } else {
                    slice(source, color, 64, COLORED_BED);
                }
            }
            warn("bed_down and bed_head_north are shared between all colors and only mapped from black.png; "
                    + "custom per-color undersides, head ends and leg undersides have no equivalent override here.");
        }

        void signs() throws IOException {
            Set<String> supported = new HashSet<String>();
            for (String wood : WOODS) {
                supported.add(TEXTURES + SIGNS + wood + ".png");
                supported.add(TEXTURES + HANGING + wood + ".png");
            }
            warnExtensions(SIGNS, supported);
            if (!vanillaAtlasSources("signs", SIGNS) || !hasAny(supported)) {
                return;
            }
            for (String wood : WOODS) {
                slice(TEXTURES + SIGNS + wood + ".png", wood, 32, new Output[] { SIGN_BLOCK, SIGN_EDITOR });
                slice(TEXTURES + HANGING + wood + ".png", wood, 32, new Output[] { HANGING_BLOCK });
            }
        }

        private boolean hasAny(Set<String> supported) {
            for (String path : supported) {
                if (originals.contains(path)) {
                    return true;
                }
            }
            return false;
        }

        private void warnExtensions(String prefix, Set<String> supported) {
            for (String path : files) {
                if (!path.startsWith("assets/")) {
                    continue;
                }
                int namespaceEnd = path.indexOf('/', "assets/".length());
                if (namespaceEnd < 0 || !fold(path.substring(namespaceEnd + 1)).startsWith("textures/" + prefix)) {
                    continue;
                }
                String base = path.endsWith(".mcmeta") ? path.substring(0, path.length() - 7) : path;
                if (!supported.contains(base) || !originals.contains(base)) {
                    warn("retained unsupported old entity-sheet resource " + path + "; no vanilla UV mapping assumed.");
                }
            }
        }

        /** 原版 directory 条目只会提供同路径 PNG；其他来源有可能替换或过滤目标旧精灵。 */
        private boolean vanillaAtlasSources(String atlasId, String prefix) throws IOException {
            String path = atlas(atlasId);
            if (!spelling.containsKey(fold(path))) {
                return true;
            }
            if (path.equals(spelling.get(fold(path))) && originals.contains(path)) {
                try {
                    Map<String, Object> atlas = Json.parseObject(workspace.readText(path));
                    Object sources = atlas.get("sources");
                    if (atlas.size() == 1 && sources instanceof List && canonicalDirectories((List<?>) sources, prefix)) {
                        return true;
                    }
                } catch (JsonException ex) {
                    // 与形状不支持走同一条保留路径；不推测实际被加载的旧图。
                }
            }
            warn("retained " + path + "; custom or invalid atlas sources may change old sprite lookup, "
                    + "so no textures were generated for this atlas.");
            return false;
        }

        private static boolean canonicalDirectories(List<?> sources, String prefix) {
            for (Object source : sources) {
                if (!(source instanceof Map)) {
                    return false;
                }
                Map<?, ?> entry = (Map<?, ?>) source;
                Object type = entry.get("type");
                if (entry.size() != 3 || !("directory".equals(type) || "minecraft:directory".equals(type))
                        || !prefix.equals(entry.get("prefix"))
                        || !prefix.substring(0, prefix.length() - 1).equals(entry.get("source"))) {
                    return false;
                }
            }
            return true;
        }

        private void slice(String source, String variant, int height, Output[]... groups) throws IOException {
            if (!originals.contains(source)) {
                return;
            }
            if (!source.equals(spelling.get(fold(source))) || spelling.containsKey(fold(source + ".mcmeta"))) {
                warn("retained " + source + "; source spelling collision or metadata (including animation) "
                        + "prevents safe slicing.");
                return;
            }
            List<Output> missing = new ArrayList<Output>();
            for (Output[] outputs : groups) {
                for (Output output : outputs) {
                    String target = output.path(variant);
                    if (available(source, target)) {
                        missing.add(output);
                    }
                }
            }
            if (missing.isEmpty()) {
                return;
            }
            BufferedImage sheet = readSheet(source, height);
            if (sheet == null) {
                return;
            }
            try {
                int scale = sheet.getWidth() / 64;
                for (Output output : missing) {
                    String target = output.path(variant);
                    BufferedImage image = output.render(sheet, scale);
                    try {
                        write(target, image);
                    } catch (FileAlreadyExistsException ex) {
                        collision(source, target);
                    } finally {
                        image.flush();
                    }
                }
            } finally {
                sheet.flush();
            }
        }

        private static String parent(String path) {
            int slash = path.lastIndexOf('/');
            return slash < 0 ? "" : path.substring(0, slash);
        }

        private boolean available(String source, String target) throws IOException {
            if (occupied(target) || occupied(target + ".mcmeta")) {
                collision(source, target);
                return false;
            }
            for (String dir = parent(target); !dir.isEmpty(); dir = parent(dir)) {
                String existing = spelling.get(fold(dir));
                Path path = workspace.path(dir);
                if ((existing != null && !dir.equals(existing))
                        || (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                                && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))) {
                    collision(source, target);
                    return false;
                }
            }
            return true;
        }

        private boolean occupied(String path) throws IOException {
            return spelling.containsKey(fold(path)) || Files.exists(workspace.path(path), LinkOption.NOFOLLOW_LINKS);
        }

        private void collision(String source, String target) {
            warn("kept existing target, sidecar or conflicting path; did not map " + source + " -> " + target);
        }

        private void warn(String text) {
            workspace.warn(edge + ": " + text);
        }

        private BufferedImage readSheet(String source, int height) throws IOException {
            Path path = workspace.path(source);
            try {
                BufferedImage image = UpgradePng.scaledSheet(path, 64, height);
                for (int bits : image.getColorModel().getComponentSize()) {
                    if (bits > 8) {
                        image.flush();
                        warn("retained " + source + "; slicing to RGBA8 would lose pixel precision.");
                        return null;
                    }
                }
                return image;
            } catch (IOException ex) {
                warn("retained " + source + "; expected an integer-scaled 64x" + height
                        + " PNG: " + ex.getMessage());
                return null;
            }
        }

        private void write(String target, BufferedImage image) throws IOException {
            UpgradePng.writeNew(workspace.path(target), image);
            spelling.put(fold(target), target);
        }
    }

    private enum PixelOrder {
        COPY, FLIP_Y, FLIP_X, CW, HALF, CCW, TRANSPOSE, ANTI_TRANSPOSE
    }

    private static final class Output {
        private final String pattern;
        private final int width;
        private final int height;
        private final Tile[] tiles;

        Output(String pattern, int width, int height, Tile... tiles) {
            this.pattern = pattern;
            this.width = width;
            this.height = height;
            this.tiles = tiles;
        }

        String path(String variant) {
            return TEXTURES + pattern.replace("%s", variant) + ".png";
        }

        BufferedImage render(BufferedImage sheet, int scale) {
            BufferedImage image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
            for (Tile tile : tiles) {
                tile.copy(sheet, image, scale);
            }
            return image;
        }
    }

    private static final class Tile {
        private final int dstX;
        private final int dstY;
        private final int srcX;
        private final int srcY;
        private final int width;
        private final int height;
        private final PixelOrder order;

        Tile(int dstX, int dstY, int srcX, int srcY, int width, int height, PixelOrder order) {
            this.dstX = dstX;
            this.dstY = dstY;
            this.srcX = srcX;
            this.srcY = srcY;
            this.width = width;
            this.height = height;
            this.order = order;
        }

        void copy(BufferedImage sheet, BufferedImage target, int scale) {
            int w = width * scale;
            int h = height * scale;
            int fromX = srcX * scale;
            int fromY = srcY * scale;
            int toX = dstX * scale;
            int toY = dstY * scale;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int u = x;
                    int v = y;
                    switch (order) {
                        case COPY: break;
                        case FLIP_Y: v = h - 1 - y; break;
                        case FLIP_X: u = w - 1 - x; break;
                        case CW: u = h - 1 - y; v = x; break;
                        case HALF: u = w - 1 - x; v = h - 1 - y; break;
                        case CCW: u = y; v = w - 1 - x; break;
                        case TRANSPOSE: u = y; v = x; break;
                        case ANTI_TRANSPOSE: u = h - 1 - y; v = w - 1 - x; break;
                    }
                    target.setRGB(toX + u, toY + v, sheet.getRGB(fromX + x, fromY + y));
                }
            }
        }
    }
}
