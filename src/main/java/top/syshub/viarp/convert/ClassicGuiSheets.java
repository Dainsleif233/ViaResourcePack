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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 格式 16：补拆原版 1.20.1→23w31a 已核实的槽位；不处理 widgets.png / icons.png。 */
final class ClassicGuiSheets {

    private static final String GUI = "assets/minecraft/textures/gui/";
    private static final String SPRITES = GUI + "sprites/";
    private static final int EDGE = 256;
    private static final String[] SHEETS = { "bars", "advancements/widgets", "advancements/tabs",
            "container/beacon", "container/creative_inventory/tabs", "container/horse" };

    private ClassicGuiSheets() {
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        Set<String> occupied = null;
        List<String> files = null;
        for (String sheet : SHEETS) {
            String source = GUI + sheet + ".png";
            if (!workspace.hasFile(source)) {
                continue;
            }
            if (files == null) {
                files = workspace.files();
                occupied = new HashSet<String>();
                for (String file : files) {
                    occupied.add(fold(file));
                }
            }
            // Windows 也只读取实际路径为原版 ID 的输入，不把大写别名视为原版路径。
            if (files.contains(source)) {
                writeMissingSprites(workspace, occupied, source, sprites(sheet));
            }
        }
    }

    private static void writeMissingSprites(UpgradeWorkspace workspace, Set<String> occupied, String source,
                                            List<Sprite> sprites) throws IOException {
        List<Sprite> missing = new ArrayList<Sprite>();
        for (Sprite sprite : sprites) {
            String target = SPRITES + sprite.id + ".png";
            if (occupied.contains(fold(target)) || occupied.contains(fold(target + ".mcmeta"))
                    || Files.exists(workspace.path(target), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(workspace.path(target + ".mcmeta"), LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            boolean blocked = false;
            for (int slash = target.lastIndexOf('/'); slash >= 0; slash = target.lastIndexOf('/', slash - 1)) {
                if (occupied.contains(fold(target.substring(0, slash)))) {
                    workspace.warn("进入格式 16：" + source + "：目标目录被文件占用，保留旧图");
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                missing.add(sprite);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        if (occupied.contains(fold(source + ".mcmeta"))
                || Files.exists(workspace.path(source + ".mcmeta"), LinkOption.NOFOLLOW_LINKS)) {
            workspace.warn("进入格式 16：" + source + "：无法把旧侧车安全分配到各精灵，保留旧 PNG 和侧车");
            return;
        }
        BufferedImage sheet;
        try {
            sheet = UpgradePng.scaledSheet(workspace.path(source), EDGE, EDGE);
        } catch (IOException ex) {
            workspace.warn("进入格式 16：" + source + "：无法拆分旧 PNG，保留旧图（" + ex.getMessage() + "）");
            return;
        }
        try {
            int scale = sheet.getWidth() / EDGE;
            for (Sprite sprite : missing) {
                String target = SPRITES + sprite.id + ".png";
                BufferedImage cut = sheet.getSubimage(sprite.x * scale, sprite.y * scale,
                        sprite.width * scale, sprite.height * scale);
                UpgradePng.writeNew(workspace.path(target), cut);
                if (sprite.border != 0) {
                    // 九宫格是逻辑尺寸，不乘高清纹理的像素倍率。
                    String metadata = "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":" + sprite.width
                            + ",\"height\":" + sprite.height + ",\"border\":" + sprite.border + "}}}\n";
                    Files.write(workspace.path(target + ".mcmeta"), metadata.getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                }
            }
        } finally {
            sheet.flush();
        }
    }

    private static String fold(String path) {
        return path.toLowerCase(Locale.ROOT);
    }

    // 原版 PNG 与旧渲染调用对照见 docs/research/classic-gui-sheets.md。
    private static List<Sprite> sprites(String sheet) {
        List<Sprite> sprites = new ArrayList<Sprite>();
        switch (sheet) {
            case "bars":
                addBossBars(sprites, 0, "pink", "blue", "red", "green", "yellow", "purple", "white");
                addBossBars(sprites, 80, "notched_6", "notched_10", "notched_12", "notched_20");
                break;
            case "advancements/widgets":
                addAdvancementWidgets(sprites);
                break;
            case "advancements/tabs":
                addAdvancementTabs(sprites, "above", 0, 0, 28, 32, "left", "middle", "right");
                addAdvancementTabs(sprites, "below", 84, 0, 28, 32, "left", "middle", "right");
                addAdvancementTabs(sprites, "left", 0, 64, 32, 28, "top", "middle", "bottom");
                addAdvancementTabs(sprites, "right", 96, 64, 32, 28, "top", "middle", "bottom");
                break;
            case "container/beacon":
                sprites.add(new Sprite("container/beacon/button", 0, 219, 22, 22));
                sprites.add(new Sprite("container/beacon/button_selected", 22, 219, 22, 22));
                sprites.add(new Sprite("container/beacon/button_disabled", 44, 219, 22, 22));
                sprites.add(new Sprite("container/beacon/button_highlighted", 66, 219, 22, 22));
                sprites.add(new Sprite("container/beacon/confirm", 90, 220, 18, 18));
                sprites.add(new Sprite("container/beacon/cancel", 112, 220, 18, 18));
                break;
            case "container/creative_inventory/tabs":
                addCreativeTabs(sprites);
                break;
            case "container/horse":
                sprites.add(new Sprite("container/horse/chest_slots", 0, 166, 90, 54));
                sprites.add(new Sprite("container/horse/armor_slot", 0, 220, 18, 18));
                sprites.add(new Sprite("container/horse/saddle_slot", 18, 220, 18, 18));
                sprites.add(new Sprite("container/horse/llama_armor_slot", 36, 220, 18, 18));
                break;
            default:
                throw new AssertionError(sheet);
        }
        return sprites;
    }

    private static void addBossBars(List<Sprite> sprites, int start, String... names) {
        for (int i = 0; i < names.length; i++) {
            String id = "boss_bar/" + names[i];
            int y = start + i * 10;
            sprites.add(new Sprite(id + "_background", 0, y, 182, 5));
            sprites.add(new Sprite(id + "_progress", 0, y + 5, 182, 5));
        }
    }

    private static void addAdvancementWidgets(List<Sprite> sprites) {
        String[] frames = { "task", "challenge", "goal" };
        String[] states = { "obtained", "unobtained" };
        for (int i = 0; i < states.length; i++) {
            sprites.add(new Sprite("advancements/box_" + states[i], 0, i * 26, 200, 26));
            for (int j = 0; j < frames.length; j++) {
                sprites.add(new Sprite("advancements/" + frames[j] + "_frame_" + states[i],
                        j * 26, 128 + i * 26, 26, 26));
            }
        }
        sprites.add(new Sprite("advancements/title_box", 0, 52, 200, 26, 10));
    }

    private static void addAdvancementTabs(List<Sprite> sprites, String kind, int x, int y, int width, int height,
                                           String... positions) {
        for (int i = 0; i < positions.length; i++) {
            String id = "advancements/tab_" + kind + "_" + positions[i];
            sprites.add(new Sprite(id, x + i * width, y, width, height));
            sprites.add(new Sprite(id + "_selected", x + i * width, y + height, width, height));
        }
    }

    private static void addCreativeTabs(List<Sprite> sprites) {
        String prefix = "container/creative_inventory/";
        String[] rows = { "top_unselected", "top_selected", "bottom_unselected", "bottom_selected" };
        for (int i = 0; i < rows.length; i++) {
            for (int column = 0; column < 7; column++) {
                sprites.add(new Sprite(prefix + "tab_" + rows[i] + "_" + (column + 1),
                        column * 26, i * 32, 26, 32));
            }
        }
        sprites.add(new Sprite(prefix + "scroller", 232, 0, 12, 15));
        sprites.add(new Sprite(prefix + "scroller_disabled", 244, 0, 12, 15));
    }

    private static final class Sprite {
        final String id;
        final int x;
        final int y;
        final int width;
        final int height;
        final int border;

        Sprite(String id, int x, int y, int width, int height) {
            this(id, x, y, width, height, 0);
        }

        Sprite(String id, int x, int y, int width, int height, int border) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.border = border;
        }
    }
}
