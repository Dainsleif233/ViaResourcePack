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
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 补拆原版 GUI 小部件；不为原版旧渲染中没有纹理的部件合成图像。 */
final class ClassicWidgetDetails {

    private static final String GUI = "assets/minecraft/textures/gui/";
    private static final String REALMS_GUI = "assets/realms/textures/gui/realms/";
    private static final String WIDGET = GUI + "sprites/widget/";
    private static final String TAB_BORDER = "{\"left\":2,\"top\":2,\"right\":2,\"bottom\":0}";
    private static final String COMPACT_HANDLE_BORDER = "{\"left\":2,\"top\":2,\"right\":2,\"bottom\":3}";

    // 旧 UV、首个独立 ID 和官方 PNG 像素对照见 docs/research/classic-widget-details.md。
    private static final Sheet[] SHEETS = {
        new Sheet(GUI + "widgets.png", 256, 256,
                new Sprite("locked_button", 0, 146, 20, 20),
                new Sprite("locked_button_highlighted", 0, 166, 20, 20),
                new Sprite("locked_button_disabled", 0, 186, 20, 20),
                new Sprite("unlocked_button", 20, 146, 20, 20),
                new Sprite("unlocked_button_highlighted", 20, 166, 20, 20),
                new Sprite("unlocked_button_disabled", 20, 186, 20, 20)),
        new Sheet(GUI + "checkbox.png", 64, 64,
                new Sprite("checkbox", 0, 0, 20, 20),
                new Sprite("checkbox_highlighted", 20, 0, 20, 20),
                new Sprite("checkbox_selected", 0, 20, 20, 20),
                new Sprite("checkbox_selected_highlighted", 20, 20, 20, 20)),
        new Sheet(GUI + "tab_button.png", 256, 256,
                new Sprite("tab_selected", 0, 0, 130, 24, TAB_BORDER),
                new Sprite("tab_selected_highlighted", 0, 24, 130, 24, TAB_BORDER),
                new Sprite("tab", 0, 48, 130, 24, TAB_BORDER),
                new Sprite("tab_highlighted", 0, 72, 130, 24, TAB_BORDER)),
        new Sheet(GUI + "book.png", 256, 256,
                new Sprite("page_forward", 0, 192, 23, 13),
                new Sprite("page_forward_highlighted", 23, 192, 23, 13),
                new Sprite("page_backward", 0, 205, 23, 13),
                new Sprite("page_backward_highlighted", 23, 205, 23, 13)),
        new Sheet(REALMS_GUI + "cross_icon.png", 14, 28,
                new Sprite("cross_button", 0, 0, 14, 14),
                new Sprite("cross_button_highlighted", 0, 14, 14, 14)),
        new Sheet(REALMS_GUI + "slot_frame.png", 80, 80,
                new Sprite("slot_frame", 0, 0, 80, 80))
    };

    private static final String SLIDER = GUI + "slider.png";
    private static final Sprite[] TRACKS = {
        new Sprite("slider", 0, 0, 200, 20, "1"),
        new Sprite("slider_highlighted", 0, 20, 200, 20, "1")
    };
    private static final Sprite[] WIDE_HANDLES = {
        new Sprite("slider_handle", 0, 40, 200, 20, "3"),
        new Sprite("slider_handle_highlighted", 0, 60, 200, 20, "3")
    };
    private static final Sprite[] COMPACT_HANDLES = {
        new Sprite("slider_handle", 0, 40, 8, 20, COMPACT_HANDLE_BORDER, 196),
        new Sprite("slider_handle_highlighted", 0, 60, 8, 20, COMPACT_HANDLE_BORDER, 196)
    };

    private ClassicWidgetDetails() {
    }

    /** 格式 16–18 相同的精灵。未知最终格式时，不生成跨界时改变尺寸的滑杆手柄。 */
    static void apply(UpgradeWorkspace workspace) throws IOException {
        apply(workspace, new Sheet(SLIDER, 256, 256, TRACKS));
    }

    /** 同时回补滑杆手柄：最终格式是 16 时传 false，最终格式至少 17 时传 true。 */
    static void apply(UpgradeWorkspace workspace, boolean compactSliderHandles) throws IOException {
        Sprite[] sliders = compactSliderHandles ? COMPACT_HANDLES : WIDE_HANDLES;
        apply(workspace, new Sheet(SLIDER, 256, 256, TRACKS[0], TRACKS[1], sliders[0], sliders[1]));
    }

    private static void apply(UpgradeWorkspace workspace, Sheet slider) throws IOException {
        boolean present = workspace.hasFile(SLIDER);
        for (Sheet sheet : SHEETS) {
            present |= workspace.hasFile(sheet.path);
        }
        if (!present) {
            return;
        }
        Set<String> files = new HashSet<String>(workspace.files());
        Set<String> occupied = new HashSet<String>();
        Set<String> ambiguous = new HashSet<String>();
        for (String file : files) {
            String folded = fold(file);
            if (!occupied.add(folded)) {
                ambiguous.add(folded);
            }
        }
        for (Sheet sheet : SHEETS) {
            writeMissingSprites(workspace, files, occupied, ambiguous, sheet);
        }
        writeMissingSprites(workspace, files, occupied, ambiguous, slider);
    }

    private static void writeMissingSprites(UpgradeWorkspace workspace, Set<String> files, Set<String> occupied,
                                            Set<String> ambiguous, Sheet sheet) throws IOException {
        if (!files.contains(sheet.path) || ambiguous.contains(fold(sheet.path))) {
            return;
        }
        List<Sprite> missing = new ArrayList<Sprite>();
        for (Sprite sprite : sheet.sprites) {
            String target = WIDGET + sprite.id + ".png";
            if (isPresent(workspace, occupied, target) || isPresent(workspace, occupied, target + ".mcmeta")) {
                continue;
            }
            if (hasOccupiedParent(occupied, target)) {
                workspace.warn("GUI 小部件 " + sheet.path + "：目标目录被文件占用，保留旧图");
                return;
            }
            missing.add(sprite);
        }
        if (missing.isEmpty()) {
            return;
        }
        if (isPresent(workspace, occupied, sheet.path + ".mcmeta")) {
            workspace.warn("GUI 小部件 " + sheet.path + "：无法安全分配旧侧车，保留旧 PNG 和侧车");
            return;
        }
        BufferedImage image;
        try {
            image = UpgradePng.scaledSheet(workspace.path(sheet.path), sheet.width, sheet.height);
        } catch (IOException e) {
            workspace.warn("GUI 小部件 " + sheet.path + "：无法拆分旧 PNG，保留旧图（" + e.getMessage() + "）");
            return;
        }
        try {
            int scale = image.getWidth() / sheet.width;
            for (Sprite sprite : missing) {
                String target = WIDGET + sprite.id + ".png";
                UpgradePng.writeNew(workspace.path(target), sprite.crop(image, scale));
                if (sprite.border != null) {
                    // GUI 元数据采用逻辑尺寸，高清包只缩放像素矩形。
                    String metadata = "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":" + sprite.width
                            + ",\"height\":" + sprite.height + ",\"border\":" + sprite.border + "}}}\n";
                    Files.write(workspace.path(target + ".mcmeta"), metadata.getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                }
            }
        } finally {
            image.flush();
        }
    }

    private static boolean isPresent(UpgradeWorkspace workspace, Set<String> occupied, String path) throws IOException {
        return occupied.contains(fold(path)) || Files.exists(workspace.path(path), LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean hasOccupiedParent(Set<String> occupied, String target) {
        for (int slash = target.lastIndexOf('/'); slash >= 0; slash = target.lastIndexOf('/', slash - 1)) {
            if (occupied.contains(fold(target.substring(0, slash)))) {
                return true;
            }
        }
        return false;
    }

    private static String fold(String path) {
        return path.toLowerCase(Locale.ROOT);
    }

    private static final class Sheet {
        final String path;
        final int width;
        final int height;
        final Sprite[] sprites;

        Sheet(String path, int width, int height, Sprite... sprites) {
            this.path = path;
            this.width = width;
            this.height = height;
            this.sprites = sprites;
        }
    }

    private static final class Sprite {
        final String id;
        final int x;
        final int y;
        final int width;
        final int height;
        final String border;
        final int rightX;

        Sprite(String id, int x, int y, int width, int height) {
            this(id, x, y, width, height, null);
        }

        Sprite(String id, int x, int y, int width, int height, String border) {
            this(id, x, y, width, height, border, -1);
        }

        Sprite(String id, int x, int y, int width, int height, String border, int rightX) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.border = border;
            this.rightX = rightX;
        }

        BufferedImage crop(BufferedImage image, int scale) {
            if (rightX == -1) {
                return image.getSubimage(x * scale, y * scale, width * scale, height * scale);
            }
            // 1.20.1 将 200 像素宽的逻辑手柄绘成 8 像素，旧九宫格的左右边界各被限定为 4。
            // 同色彩模型下直接复制栅格样本，保留包括全透明像素在内的原值。
            int half = width / 2 * scale;
            int rows = height * scale;
            ColorModel color = image.getColorModel();
            WritableRaster compact = color.createCompatibleWritableRaster(width * scale, rows);
            compact.setRect(image.getSubimage(x * scale, y * scale, half, rows).getRaster());
            compact.setRect(half, 0, image.getSubimage(rightX * scale, y * scale, half, rows).getRaster());
            return new BufferedImage(color, compact, color.isAlphaPremultiplied(), null);
        }
    }
}
