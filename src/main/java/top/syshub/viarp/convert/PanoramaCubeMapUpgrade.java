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
import java.awt.image.ColorModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

import top.syshub.viarp.io.PathIo;

/** 60.0：25w19a 改用 GPU 立方体贴图，六个面必须是同一尺寸的正方形。 */
final class PanoramaCubeMapUpgrade {
    private static final String FACE_PREFIX = "assets/minecraft/textures/gui/title/background/panorama_";
    private static final int FACE_COUNT = 6;

    private PanoramaCubeMapUpgrade() {
    }

    static void upgrade(UpgradeWorkspace workspace) throws IOException {
        String[] names = new String[FACE_COUNT];
        boolean anyFace = false;
        for (int i = 0; i < FACE_COUNT; i++) {
            names[i] = FACE_PREFIX + i + ".png";
            anyFace |= workspace.hasFile(names[i]);
        }
        if (!anyFace) {
            return;
        }

        // 仅处理已投影到根层的有效路径，不遍历其他 overlay 的副本来补面。
        // Windows 的 Files.isRegularFile 本身不区分大小写，再核对实际路径拼写。
        Set<String> entries = new HashSet<String>(workspace.files());
        Path[] faces = new Path[FACE_COUNT];
        int found = 0;
        for (int i = 0; i < FACE_COUNT; i++) {
            if (entries.contains(names[i])) {
                faces[i] = workspace.path(names[i]);
                found++;
            }
        }
        if (found == 0) {
            return;
        }
        if (found != FACE_COUNT) {
            retain(workspace, "六面不完整（" + found + "/" + FACE_COUNT + "）；不推断其他包或原版提供的面");
            return;
        }

        int width = 0;
        int height = 0;
        for (int i = 0; i < FACE_COUNT; i++) {
            UpgradePng.Png image;
            try {
                image = UpgradePng.dimensions(faces[i]);
            } catch (IOException e) {
                retain(workspace, names[i] + "：无法验证 PNG：" + e.getMessage());
                return;
            }
            if (i == 0) {
                width = image.width;
                height = image.height;
            } else if (image.width != width || image.height != height) {
                retain(workspace, "六面尺寸不一致（panorama_0.png 为 " + width + "×" + height
                        + "，panorama_" + i + ".png 为 " + image.width + "×" + image.height + "）；不推断共同边长");
                return;
            }
        }
        if (width == height) {
            return;
        }
        for (String name : names) {
            if (Files.exists(workspace.path(name + ".mcmeta"), LinkOption.NOFOLLOW_LINKS)) {
                retain(workspace, name + " 有自定义旁车，未推断旧取样语义");
                return;
            }
        }

        // 旧版把每个 PNG 独立铺满一张立方体面，UV 四角都是 0/1。
        // 保留长轴的采样密度，短轴按完整 UV 最近点取样；不裁去任何内容，不添边。
        int side = Math.max(width, height);
        Path staging = Files.createTempDirectory(faces[0].getParent(), ".viarp-panorama-");
        try {
            for (int i = 0; i < FACE_COUNT; i++) {
                BufferedImage image;
                try {
                    image = UpgradePng.atlas(faces[i], 1, 1);
                } catch (IOException e) {
                    retain(workspace, names[i] + "：无法读取 PNG：" + e.getMessage());
                    return;
                }
                try {
                    if (image.getWidth() != width || image.getHeight() != height || !hasRgb8Samples(image)) {
                        retain(workspace, names[i] + "：不是可无色深转换的 8 位 sRGB 图像");
                        return;
                    }
                    BufferedImage square = resample(image, side);
                    try {
                        UpgradePng.writeNew(stagedFace(staging, i), square);
                    } finally {
                        square.flush();
                    }
                } finally {
                    image.flush();
                }
            }
            // 六面全部成功编码方可替换。I/O 失败向上传播，转换不发布不完整的工作树。
            for (int i = 0; i < FACE_COUNT; i++) {
                Files.move(stagedFace(staging, i), faces[i], StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            PathIo.deleteRecursively(staging);
        }
    }

    private static Path stagedFace(Path staging, int face) {
        return staging.resolve(Integer.toString(face) + ".png");
    }

    private static boolean hasRgb8Samples(BufferedImage image) {
        ColorModel model = image.getColorModel();
        if (!model.getColorSpace().isCS_sRGB() || model.getNumColorComponents() != 3 || model.isAlphaPremultiplied()) {
            return false;
        }
        for (int bits : model.getComponentSize()) {
            if (bits != 8) {
                return false;
            }
        }
        return true;
    }

    private static BufferedImage resample(BufferedImage source, int side) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        int[] sourceRow = new int[width];
        int[] targetRow = new int[side];
        for (int y = 0; y < side; y++) {
            int inputY = sample(y, height, side);
            source.getRGB(0, inputY, width, 1, sourceRow, 0, width);
            for (int x = 0; x < side; x++) {
                targetRow[x] = sourceRow[sample(x, width, side)];
            }
            square.setRGB(0, y, side, 1, targetRow, 0, side);
        }
        return square;
    }

    private static int sample(int coordinate, int oldLength, int newLength) {
        // 以目标 texel 中心的 (coordinate + 0.5) / newLength 映射回旧面；边长由 UpgradePng 限定。
        return (2 * coordinate + 1) * oldLength / (2 * newLength);
    }

    private static void retain(UpgradeWorkspace workspace, String reason) {
        workspace.warn("60.0：保留原版标题全景贴图未转换；" + reason + "。");
    }
}
