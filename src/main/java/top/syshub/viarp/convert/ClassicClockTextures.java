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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 1.8.9→1.9 的默认动态道具只需拆旧帧条；模型由目标原版提供。 */
final class ClassicClockTextures {

    private static final String ASSETS = "assets/minecraft/";
    private static final String MODELS = ASSETS + "models/item/";
    private static final String TEXTURES = ASSETS + "textures/items/";
    private static final String FLAT_TEXTURES = ASSETS + "textures/item/";
    private static final String JSON_SPACE = "[ \\t\\r\\n]*";
    // 仅接受已经逐帧验证的原版侧车形状，拒绝调序、时长、采样及其他未知字段。
    private static final Pattern DEFAULT_ANIMATION = Pattern.compile(JSON_SPACE + "\\{" + JSON_SPACE
            + "\"animation\"" + JSON_SPACE + ":" + JSON_SPACE + "\\{" + JSON_SPACE + "\\}" + JSON_SPACE + "\\}" + JSON_SPACE);

    private ClassicClockTextures() {
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        Set<String> files = new HashSet<String>(workspace.files());
        Set<String> foldedFiles = new HashSet<String>();
        for (String file : files) {
            foldedFiles.add(file.toLowerCase(Locale.ROOT));
        }
        split(workspace, files, foldedFiles, "clock", 64);
        split(workspace, files, foldedFiles, "compass", 32);
    }

    private static void split(UpgradeWorkspace workspace, Set<String> files, Set<String> foldedFiles,
                              String item, int frameCount) throws IOException {
        String source = TEXTURES + item + ".png";
        if (!files.contains(source)) {
            return;
        }
        if (occupied(workspace, foldedFiles, MODELS + item + ".json")) {
            warn(workspace, source, "包内有自定义根模型，未拆旧帧条");
            return;
        }
        String metadata = source + ".mcmeta";
        if (occupied(workspace, foldedFiles, metadata)
                && (!files.contains(metadata) || !hasDefaultAnimation(workspace.path(metadata)))) {
            warn(workspace, source, "侧车不符合默认动画，未拆旧帧条");
            return;
        }
        for (int frame = 0; frame < frameCount; frame++) {
            String name = frameName(item, frame);
            if (occupied(workspace, foldedFiles, MODELS + name + ".json")) {
                warn(workspace, source, "包内有自定义帧模型，未拆旧帧条");
                return;
            }
            if (occupied(workspace, foldedFiles, TEXTURES + name + ".png")
                    || occupied(workspace, foldedFiles, TEXTURES + name + ".png.mcmeta")
                    || occupied(workspace, foldedFiles, FLAT_TEXTURES + name + ".png")
                    || occupied(workspace, foldedFiles, FLAT_TEXTURES + name + ".png.mcmeta")) {
                warn(workspace, source, "编号帧或其侧车已存在，保留已有资源且不与旧帧条混用");
                return;
            }
        }
        BufferedImage strip;
        try {
            strip = UpgradePng.scaledSheet(workspace.path(source), 16, 16 * frameCount);
        } catch (IOException failure) {
            warn(workspace, source, "旧帧条不能按标准布局读取（" + failure.getMessage() + "），未拆图");
            return;
        }
        // 两端原版制品的 RGBA 逐帧匹配：第 i 个方帧对应编号 i，无需重排。保留旧图和侧车供其他引用使用。
        int size = strip.getWidth();
        for (int frame = 0; frame < frameCount; frame++) {
            Path target = workspace.path(TEXTURES + frameName(item, frame) + ".png");
            UpgradePng.writeNew(target, strip.getSubimage(0, frame * size, size, size));
        }
    }

    private static boolean hasDefaultAnimation(Path path) throws IOException {
        return Files.size(path) <= 4096
                && DEFAULT_ANIMATION.matcher(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).matches();
    }

    private static boolean occupied(UpgradeWorkspace workspace, Set<String> foldedFiles, String path) throws IOException {
        return foldedFiles.contains(path) || Files.exists(workspace.path(path), LinkOption.NOFOLLOW_LINKS);
    }

    private static String frameName(String item, int frame) {
        return item + (frame < 10 ? "_0" : "_") + frame;
    }

    private static void warn(UpgradeWorkspace workspace, String source, String reason) {
        workspace.warn("格式 2 [" + source + "] " + reason);
    }
}
