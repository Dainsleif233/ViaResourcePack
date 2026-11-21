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

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 格式 4→5 实体纹理；像素映射来自原版兼容读取器。 */
final class ClassicEntityTextures {

    private static final String ENTITY = "assets/minecraft/textures/entity/";
    // 源 x,y → 目标 x,y；矩形宽、高；最后一列 1=翻转 Y，3=翻转 X 和 Y。
    private static final int[][] LEFT = {
            {29, 0, 29, 0, 15, 14, 1}, {59, 0, 14, 0, 15, 14, 1},
            {29, 14, 43, 14, 15, 5, 3}, {44, 14, 29, 14, 14, 5, 3}, {58, 14, 14, 14, 15, 5, 3},
            {29, 19, 29, 19, 15, 14, 1}, {59, 19, 14, 19, 15, 14, 1},
            {29, 33, 43, 33, 15, 10, 3}, {44, 33, 29, 33, 14, 10, 3}, {58, 33, 14, 33, 15, 10, 3},
            {2, 0, 2, 0, 1, 1, 1}, {4, 0, 1, 0, 1, 1, 1},
            {2, 1, 3, 1, 1, 4, 3}, {3, 1, 2, 1, 1, 4, 3}, {4, 1, 1, 1, 1, 4, 3}
    };
    private static final int[][] RIGHT = {
            {14, 0, 29, 0, 15, 14, 1}, {44, 0, 14, 0, 15, 14, 1},
            {0, 14, 0, 14, 14, 5, 3}, {14, 14, 43, 14, 15, 5, 3}, {73, 14, 14, 14, 15, 5, 3},
            {14, 19, 29, 19, 15, 14, 1}, {44, 19, 14, 19, 15, 14, 1},
            {0, 33, 0, 33, 14, 10, 3}, {14, 33, 43, 33, 15, 10, 3}, {73, 33, 14, 33, 15, 10, 3},
            {1, 0, 2, 0, 1, 1, 1}, {3, 0, 1, 0, 1, 1, 1},
            {0, 1, 0, 1, 1, 4, 3}, {1, 1, 3, 1, 1, 4, 3}, {5, 1, 1, 1, 1, 4, 3}
    };

    private static final int[][] SINGLE = {
            {14, 0, 28, 0, 14, 14, 1}, {28, 0, 14, 0, 14, 14, 1},
            {0, 14, 0, 14, 14, 5, 3}, {14, 14, 42, 14, 14, 5, 3}, {28, 14, 28, 14, 14, 5, 3},
            {42, 14, 14, 14, 14, 5, 3}, {14, 19, 28, 19, 14, 14, 1}, {28, 19, 14, 19, 14, 14, 1},
            {0, 33, 0, 33, 14, 10, 3}, {14, 33, 42, 33, 14, 10, 3}, {28, 33, 28, 33, 14, 10, 3},
            {42, 33, 14, 33, 14, 10, 3}, {1, 0, 3, 0, 2, 1, 1}, {3, 0, 1, 0, 2, 1, 1},
            {0, 1, 0, 1, 1, 4, 3}, {1, 1, 4, 1, 2, 4, 3}, {3, 1, 3, 1, 1, 4, 3}, {4, 1, 1, 1, 2, 4, 3}
    };
    private static final String HASHES = "/viarp/migrations/classic-entity/rgba-sha256.tsv";

    private ClassicEntityTextures() {
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        Set<String> files = new HashSet<String>(workspace.files());
        Set<String> occupied = new HashSet<String>();
        for (String file : files) {
            occupied.add(fold(file));
        }
        for (String variant : new String[]{"normal", "trapped", "christmas"}) {
            splitChest(workspace, files, occupied, ENTITY + "chest/" + variant);
        }
        Map<String, String[]> hashes = readHashes();
        for (String eye : new String[]{"conduit/open_eye.png", "conduit/closed_eye.png"}) {
            upgradeEye(workspace, files, occupied, ENTITY + eye, hashes.get(eye)[0]);
        }
        for (String variant : new String[]{"normal", "trapped", "christmas", "ender"}) {
            String name = "chest/" + variant + ".png";
            upgradeSingle(workspace, files, occupied, name, hashes.get(name));
        }
        upgradePatterns(workspace, files, occupied, hashes, "banner", 0, 0, 42, 41);
        upgradePatterns(workspace, files, occupied, hashes, "shield", 2, 2, 12, 22);
    }

    private static void splitChest(UpgradeWorkspace workspace, Set<String> files, Set<String> occupied,
                                   String prefix) throws IOException {
        String source = prefix + "_double.png";
        if (!files.contains(source)) {
            return;
        }
        String left = prefix + "_left.png";
        String right = prefix + "_right.png";
        boolean addLeft = canAdd(workspace, occupied, left);
        boolean addRight = canAdd(workspace, occupied, right);
        if (!addLeft && !addRight) {
            return;
        }
        BufferedImage sheet = readSheet(workspace, source, 128, 64);
        if (sheet == null) {
            return;
        }
        int size = sheet.getHeight();
        if (addLeft) {
            UpgradePng.writeNew(workspace.path(left), chest(sheet, size, LEFT));
        }
        if (addRight) {
            UpgradePng.writeNew(workspace.path(right), chest(sheet, size, RIGHT));
        }
        if (occupied.contains(fold(source + ".mcmeta"))) {
            warn(workspace, source + ".mcmeta", "旧双箱侧车按原路径保留；未推测拆图后的动画或采样元数据");
        }
    }

    private static boolean canAdd(UpgradeWorkspace workspace, Set<String> occupied, String target) throws IOException {
        if (occupied.contains(fold(target)) || occupied.contains(fold(target + ".mcmeta"))) {
            return false;
        }
        Path path = workspace.path(target);
        if (Files.exists(path)) {
            warn(workspace, target, "目标路径已占用，未覆盖");
            return false;
        }
        return true;
    }

    private static BufferedImage chest(BufferedImage source, int size, int[][] rectangles) {
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int scale = size / 64;
        for (int[] rect : rectangles) {
            int width = rect[4] * scale;
            int height = rect[5] * scale;
            for (int y = 0; y < height; y++) {
                int sy = rect[1] * scale + ((rect[6] & 1) != 0 ? height - 1 - y : y);
                for (int x = 0; x < width; x++) {
                    int sx = rect[0] * scale + ((rect[6] & 2) != 0 ? width - 1 - x : x);
                    result.setRGB(rect[2] * scale + x, rect[3] * scale + y, argb(source, sx, sy));
                }
            }
        }
        return result;
    }

    private static void upgradeEye(UpgradeWorkspace workspace, Set<String> files, Set<String> occupied,
                                   String source, String oldHash) throws IOException {
        if (!files.contains(source)) {
            return;
        }
        BufferedImage image = readSheet(workspace, source, 8, 8);
        if (image == null) {
            return;
        }
        int size = image.getWidth();
        // 旧 8×8 的奇数倍不是 16×16 新布局的整数倍；偶数倍只认已知旧指纹。
        if (size % 16 == 0 && !oldHash.equals(nearestRgbaHash(image, 8))) {
            if (!hasTransparentEyePadding(image)) {
                warn(workspace, source, "同路径的高清旧眼图与新版无法可靠区分；保留原图");
            }
            return;
        }
        if (size > 2048 || occupied.contains(fold(source + ".mcmeta"))) {
            warn(workspace, source, "扩展画布超过 PNG 边长限制或存在未核实的侧车；保留原图");
            return;
        }
        BufferedImage result = new BufferedImage(size * 2, size * 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                result.setRGB(x, y, argb(image, x, y));
            }
        }
        replaceImage(workspace.path(source), result);
    }

    private static void upgradeSingle(UpgradeWorkspace workspace, Set<String> files, Set<String> occupied,
                                      String name, String[] hashes) throws IOException {
        String source = ENTITY + name;
        if (!files.contains(source)) {
            return;
        }
        BufferedImage image = readSheet(workspace, source, 64, 64);
        if (image == null || !hasOldLayout(workspace, source, image, hashes)) {
            return;
        }
        if (hasSidecar(workspace, occupied, source)) {
            return;
        }
        replaceImage(workspace.path(source), chest(image, image.getWidth(), SINGLE));
    }

    private static void upgradePatterns(UpgradeWorkspace workspace, Set<String> files, Set<String> occupied,
                                        Map<String, String[]> hashes, String family, int x0, int y0, int x1, int y1)
            throws IOException {
        BufferedImage base = null;
        boolean checkedBase = false;
        String basePath = ENTITY + family + "_base.png";
        for (Map.Entry<String, String[]> entry : hashes.entrySet()) {
            String name = entry.getKey();
            String source = ENTITY + name;
            if (!name.startsWith(family + "/") || !files.contains(source)) {
                continue;
            }
            BufferedImage pattern = readSheet(workspace, source, 64, 64);
            if (pattern == null || !hasOldLayout(workspace, source, pattern, entry.getValue())
                    || hasSidecar(workspace, occupied, source)) {
                continue;
            }
            if (!checkedBase) {
                checkedBase = true;
                if (files.contains(basePath)) {
                    base = readSheet(workspace, basePath, 64, 64);
                }
            }
            int size = pattern.getWidth();
            if (base == null || base.getWidth() != size || occupied.contains(fold(basePath + ".mcmeta"))) {
                warn(workspace, source, "需要同包同尺寸且不带未验证侧车的 " + family + "_base.png；保留原图");
                continue;
            }
            BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            int scale = size / 64;
            for (int y = y0 * scale; y < y1 * scale; y++) {
                for (int x = x0 * scale; x < x1 * scale; x++) {
                    int opacity = (argb(pattern, x, y) >>> 16) & 255;
                    result.setRGB(x, y, opacity << 24 | argb(base, x, y) & 0xFFFFFF);
                }
            }
            replaceImage(workspace.path(source), result);
        }
    }

    private static boolean hasOldLayout(UpgradeWorkspace workspace, String source, BufferedImage image,
                                        String[] hashes) {
        String actual = nearestRgbaHash(image, 64);
        if (actual.equals(hashes[1])) {
            return false;
        }
        if (!actual.equals(hashes[0])) {
            warn(workspace, source, "同路径旧、新布局无法可靠区分；保留原图");
            return false;
        }
        return true;
    }

    private static boolean hasSidecar(UpgradeWorkspace workspace, Set<String> occupied, String source) {
        if (!occupied.contains(fold(source + ".mcmeta"))) {
            return false;
        }
        warn(workspace, source, "存在未核实的纹理侧车；保留原图和侧车");
        return true;
    }

    private static Map<String, String[]> readHashes() throws IOException {
        InputStream raw = ClassicEntityTextures.class.getResourceAsStream(HASHES);
        if (raw == null) {
            throw new IOException("缺少经典实体像素指纹表");
        }
        Map<String, String[]> hashes = new LinkedHashMap<String, String[]>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] row = line.split("\t", -1);
                if (row.length != 3 || row[1].length() != 64 || row[2].length() != 64
                        || hashes.put(row[0], new String[]{row[1], row[2]}) != null) {
                    throw new IOException("无效的经典实体像素指纹表");
                }
            }
        }
        return hashes;
    }

    private static boolean hasTransparentEyePadding(BufferedImage image) {
        int size = image.getWidth();
        if (size % 16 != 0) {
            return false;
        }
        for (int y = 0; y < size; y++) {
            for (int x = y < size / 2 ? size / 2 : 0; x < size; x++) {
                if ((argb(image, x, y) >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BufferedImage readSheet(UpgradeWorkspace workspace, String source, int width, int height)
            throws IOException {
        Path path = workspace.path(source);
        try {
            return UpgradePng.scaledSheet(path, width, height);
        } catch (IOException e) {
            warn(workspace, source, "未转换：" + e.getMessage());
            return null;
        }
    }

    /** 先完整编码再替换已确认的旧工作副本；输入包始终只读。 */
    private static void replaceImage(Path target, BufferedImage image) throws IOException {
        Path staging = Files.createTempDirectory(target.getParent(), ".viarp-entity-");
        Path encoded = staging.resolve("image.png");
        try {
            UpgradePng.writeNew(encoded, image);
            Files.move(encoded, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(encoded);
            Files.delete(staging);
        }
    }

    /** 仅接受基准图每一像素的严格整数倍复制，避免碰巧抽样相同的自定义图。 */
    private static String nearestRgbaHash(BufferedImage image, int base) {
        int scale = image.getWidth() / base;
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        for (int y = 0; y < image.getHeight(); y += scale) {
            for (int x = 0; x < image.getWidth(); x += scale) {
                int pixel = argb(image, x, y);
                for (int sy = y; sy < y + scale; sy++) {
                    for (int sx = x; sx < x + scale; sx++) {
                        if ((sx != x || sy != y) && argb(image, sx, sy) != pixel) {
                            return "";
                        }
                    }
                }
                digest.update((byte) (pixel >>> 16));
                digest.update((byte) (pixel >>> 8));
                digest.update((byte) pixel);
                digest.update((byte) (pixel >>> 24));
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return hex.toString();
    }

    private static int argb(BufferedImage image, int x, int y) {
        if (image.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_GRAY) {
            return image.getRGB(x, y);
        }
        // PNG 灰度通道是原始字节；getRGB 对 JDK 的线性灰色空间会做一次 gamma 变换。
        Raster raster = image.getRaster();
        int gray = pngSample(raster, x, y, 0);
        int alpha = image.getColorModel().hasAlpha() ? pngSample(raster, x, y, 1) : 255;
        return alpha << 24 | gray << 16 | gray << 8 | gray;
    }

    private static int pngSample(Raster raster, int x, int y, int band) {
        int bits = raster.getSampleModel().getSampleSize(band);
        int sample = raster.getSample(x, y, band);
        return bits < 8 ? sample * 255 / ((1 << bits) - 1) : sample >>> (bits - 8);
    }

    private static String fold(String path) {
        return path.toLowerCase(Locale.ROOT);
    }

    private static void warn(UpgradeWorkspace workspace, String file, String reason) {
        workspace.warn("4→5 " + file + "：" + reason);
    }
}
