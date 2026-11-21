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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


import top.syshub.viarp.convert.UpgradePng.Png;
import top.syshub.viarp.json.Json;

/** 只处理经原版证据确认的旧纹理；两次迁移共用隔离的工作树。 */
final class LegacyTextureUpgrade {

    private static final String NAMESPACE = "assets/minecraft/";
    private static final String BLOCKS = "textures/blocks/";
    private static final String ITEMS = "textures/items/";
    private static final int GRID = 16;
    private static final int MAX_SIDE = 4096;
    private static final long MAX_PIXELS = (long) MAX_SIDE * MAX_SIDE;
    // 每步最多生成一个上限图像的像素，两步合计不超过 2 × MAX_PIXELS。
    private static final long MAX_COPY_BYTES = 64L * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 64 * 1024;
    private static final int MAX_FRAMES = 4096;
    private static final int WARNING_PREVIEW = 12;
    private static final Pattern FRAME = Pattern.compile("[0-9]{1,10}(?:\\s*\\*\\s*[0-9]{1,10})?");

    private LegacyTextureUpgrade() {
    }

    /** 和目录/ZIP 轻量识别共用精确大小写白名单，不能把模组贴图当成原版 1.5 散图。 */
    static boolean isKnownLooseTexture(String path) {
        return inLooseDomain(path) && Rules.PATHS.containsKey(path);
    }

    /** 单改 GUI、护甲等的纹理包可能既没有图集，也没有 1.5 方块/物品的散图。 */
    static boolean isKnownStandaloneTexture(String path) {
        List<String> targets = path.endsWith(".png") ? Rules.PATHS.get(path) : null;
        return targets != null && !targets.isEmpty();
    }

    static void unstitch(UpgradeWorkspace workspace) throws IOException {
        List<String> files = workspace.files();
        Set<String> occupied = foldedFiles(files);
        Budget budget = new Budget();
        if (files.contains("terrain.png")) {
            unstitchAtlas(workspace, occupied, budget, "terrain.png", BLOCKS, Rules.BLOCK_TILES);
        }
        if (files.contains("gui/items.png")) {
            unstitchAtlas(workspace, occupied, budget, "gui/items.png", ITEMS, Rules.ITEM_TILES);
        }
    }

    private static void unstitchAtlas(UpgradeWorkspace workspace, Set<String> occupied, Budget budget,
                                      String name, String domain, Map<Integer, String> tiles) throws IOException {
        BufferedImage image;
        try {
            image = UpgradePng.atlas(workspace.path(name), GRID, GRID);
        } catch (IOException e) {
            workspace.warn("旧图集 " + name + " 未拆分并保留原图：" + e.getMessage());
            return;
        }
        int width = image.getWidth() / GRID;
        int height = image.getHeight() / GRID;
        int written = 0;
        int protectedTiles = 0;
        int limited = 0;
        try {
            for (Map.Entry<Integer, String> entry : tiles.entrySet()) {
                String target = domain + entry.getValue() + ".png";
                List<String> finalPaths = Rules.PATHS.get(target);
                boolean needed = false;
                for (String finalPath : finalPaths) {
                    if (canPlaceTexture(workspace, occupied, finalPath)) {
                        needed = true;
                        break;
                    }
                }
                if (!needed || !canPlaceTexture(workspace, occupied, target)) {
                    protectedTiles++;
                    continue;
                }
                if (!budget.reserve((long) width * height, 0)) {
                    limited++;
                    continue;
                }
                int index = entry.getKey();
                // 子图沿用原始光栅和色彩模型，不做缩放、混合或精度转换。
                BufferedImage tile = image.getSubimage(index % GRID * width, index / GRID * height, width, height);
                UpgradePng.writeNew(workspace.path(target), tile);
                occupied.add(fold(target));
                written++;
            }
        } finally {
            image.flush();
        }
        int unverified = GRID * GRID - tiles.size();
        workspace.warn("旧图集 " + name + "：已拆 " + written + " 格，" + protectedTiles
                + " 格因显式散图、侧车或路径冲突未覆盖，" + unverified
                + " 格没有可跨期验证的索引（含未分配格）；原图保留，未拆分格需以原版回退或手动迁移。"
                + (limited == 0 ? "" : " 另有 " + limited + " 格超过输出像素预算。"));
        workspace.warn("旧图集 " + name + " 中缺完整帧条的"
                + (BLOCKS.equals(domain) ? "火、水、岩浆等" : "钟及指南针等")
                + "程序化动画不从单格重建；有显式散图时仍可迁移。");
    }

    static void toResourcePack(UpgradeWorkspace workspace) throws IOException {
        // 该入口只接受旧包。遇到现代元数据时不退回到旧格式或覆盖原声明。
        if (Files.exists(workspace.path("pack.mcmeta"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("旧纹理迁移不改写已有的 pack.mcmeta");
        }
        List<String> files = workspace.files();
        Set<String> exact = new HashSet<String>(files);
        Set<String> occupied = foldedFiles(files);
        Budget budget = new Budget();
        Map<String, String> moves = new LinkedHashMap<String, String>();
        List<Placement> placements = new ArrayList<Placement>();
        List<String> unknown = new ArrayList<String>();
        List<String> removed = new ArrayList<String>();
        List<String> conflicts = new ArrayList<String>();
        for (String source : files) {
            List<String> declaredTargets = Rules.PATHS.get(source);
            if (declaredTargets == null) {
                if (isUnknownLegacyFile(source, exact)) {
                    unknown.add(source);
                }
                continue;
            }
            if (declaredTargets.isEmpty()) {
                removed.add(source);
                continue;
            }
            boolean png = source.endsWith(".png");
            boolean legacySidecar = png && exact.contains(source + ".mcmeta");
            List<String> targets = new ArrayList<String>();
            for (String target : declaredTargets) {
                if (png ? canPlaceTexture(workspace, occupied, target) : canPlace(workspace, occupied, target)) {
                    targets.add(target);
                } else {
                    conflicts.add(source + " → " + target);
                }
            }
            if (targets.isEmpty()) {
                continue;
            }
            String generated = png ? metadata(workspace, exact, source, targets.get(0), legacySidecar) : null;
            if (png && targets.size() > 1) {
                limitCopies(workspace, budget, source, targets, legacySidecar, generated);
            }
            String primary = targets.get(0);
            for (String target : targets) {
                occupied.add(fold(target));
                if (png) {
                    occupied.add(fold(target + ".mcmeta"));
                }
            }
            moves.put(source, primary);
            if (legacySidecar) {
                moves.put(source + ".mcmeta", primary + ".mcmeta");
            }
            if (targets.size() > 1 || generated != null) {
                placements.add(new Placement(primary, targets.subList(1, targets.size()), legacySidecar, generated));
            }
        }
        workspace.moveFiles(moves);
        for (Placement placement : placements) {
            placement.write(workspace);
        }
        warnPaths(workspace, "没有可验证的格式 1 对应项，保留旧路径（可能是模组/补丁或版本特有纹理）", unknown);
        warnPaths(workspace, "原版迁移声明无格式 1 目标，保留旧路径供手工调整", removed);
        warnPaths(workspace, "保留显式目标或冲突路径，不覆盖纹理/侧车", conflicts);
        workspace.writeText("pack.mcmeta", packMetadata(workspace));
    }

    private static String metadata(UpgradeWorkspace workspace, Set<String> exact,
                                   String source, String target, boolean legacySidecar) throws IOException {
        String textFile = replacePngExtension(source, ".txt");
        if (legacySidecar) {
            if (inLooseDomain(source) && exact.contains(textFile)) {
                workspace.warn("旧动画 " + textFile + " 保留原文，优先保留显式侧车 " + source + ".mcmeta");
            }
            return null;
        }
        String sampling = samplingMetadata(target);
        if (sampling != null || !inLooseDomain(source) || !exact.contains(textFile)) {
            return sampling;
        }
        try {
            Png png = UpgradePng.dimensions(workspace.path(source));
            return animationMetadata(source, png, readUtf8(workspace.path(textFile)));
        } catch (IOException e) {
            workspace.warn("旧动画 " + textFile + " 未生成侧车，保留原文：" + e.getMessage());
            return null;
        }
    }

    private static String animationMetadata(String source, Png png, String specification) throws IOException {
        if (png.height % png.width != 0) {
            throw new IOException("PNG 不是方形帧组成的竖条");
        }
        int frameCount = png.height / png.width;
        if (specification.trim().isEmpty()) {
            if ((source.equals(ITEMS + "clock.png") && frameCount == 64)
                    || (source.equals(ITEMS + "compass.png") && frameCount == 32)) {
                return "{\"animation\":{}}\n";
            }
            throw new IOException("只有原版帧数的钟和指南针可使用空定义");
        }
        List<Object> frames = new ArrayList<Object>();
        for (String line : specification.split("\\r\\n|\\r|\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            for (String token : line.split(",", -1)) {
                String frame = token.trim();
                if (frame.isEmpty()) {
                    continue;
                }
                if (frames.size() >= MAX_FRAMES || !FRAME.matcher(frame).matches()) {
                    throw new IOException("帧定义无效或帧列表过长");
                }
                int star = frame.indexOf('*');
                long index = Long.parseLong(star < 0 ? frame : frame.substring(0, star).trim());
                long ticks = star < 0 ? 1 : Long.parseLong(frame.substring(star + 1).trim());
                if (index >= frameCount || ticks < 1 || ticks > Integer.MAX_VALUE) {
                    throw new IOException("帧索引或时长越界");
                }
                if (ticks == 1) {
                    frames.add(index);
                } else {
                    Map<String, Long> timed = new LinkedHashMap<String, Long>();
                    timed.put("index", index);
                    timed.put("time", ticks);
                    frames.add(timed);
                }
            }
        }
        if (frames.isEmpty()) {
            throw new IOException("没有有效帧");
        }
        return Json.write(Collections.singletonMap("animation", Collections.singletonMap("frames", frames))) + "\n";
    }

    private static String samplingMetadata(String target) {
        // Mojang TextureEnder 同一固定提交的 texture_txt_to_mcmeta.txt：只为存在的目标补缺失侧车。
        switch (target) {
            case NAMESPACE + "textures/misc/enchanted_item_glint.png":
            case NAMESPACE + "textures/misc/pumpkinblur.png":
            case NAMESPACE + "textures/misc/vignette.png":
                return "{\"texture\":{\"blur\":true}}\n";
            case NAMESPACE + "textures/misc/shadow.png":
                return "{\"texture\":{\"clamp\":true}}\n";
            default:
                return null;
        }
    }

    private static void limitCopies(UpgradeWorkspace workspace, Budget budget, String source, List<String> targets,
                                    boolean legacySidecar, String generated) throws IOException {
        Path path = workspace.path(source);
        long pixels;
        long bytes;
        try {
            Png png = UpgradePng.dimensions(path);
            pixels = (long) png.width * png.height;
            bytes = Files.size(path);
            if (legacySidecar) {
                long sidecarBytes = Files.size(workspace.path(source + ".mcmeta"));
                if (sidecarBytes > MAX_TEXT_BYTES) {
                    throw new IOException("原侧车超过复制预算");
                }
                bytes += sidecarBytes;
            } else if (generated != null) {
                bytes += generated.getBytes(StandardCharsets.UTF_8).length;
            }
        } catch (IOException e) {
            workspace.warn("一对多纹理 " + source + " 只迁移到 " + targets.get(0) + "：" + e.getMessage());
            targets.subList(1, targets.size()).clear();
            return;
        }
        for (int i = 1; i < targets.size(); i++) {
            if (!budget.reserve(pixels, bytes)) {
                workspace.warn("一对多纹理 " + source + " 的额外目标超过复制预算：" + targets.subList(i, targets.size()));
                targets.subList(i, targets.size()).clear();
                break;
            }
        }
    }

    private static String packMetadata(UpgradeWorkspace workspace) throws IOException {
        String description = "Converted legacy texture pack";
        if (workspace.hasFile("pack.txt")) {
            try {
                String text = readUtf8(workspace.path("pack.txt"));
                String first = text.split("\\r\\n|\\r|\\n", 2)[0];
                if (!first.trim().isEmpty()) {
                    description = first;
                }
            } catch (IOException e) {
                workspace.warn("pack.txt 无法按限长 UTF-8 读取，保留原文并使用默认描述");
            }
        }
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("pack_format", 1);
        metadata.put("description", description);
        return Json.write(Collections.singletonMap("pack", metadata)) + "\n";
    }

    private static String readUtf8(Path path) throws IOException {
        if (Files.size(path) > MAX_TEXT_BYTES) {
            throw new IOException("文本超过大小限制");
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (result.length() + count > MAX_TEXT_BYTES) {
                    throw new IOException("文本超过大小限制");
                }
                result.append(buffer, 0, count);
            }
            return result.length() > 0 && result.charAt(0) == '\uFEFF' ? result.substring(1) : result.toString();
        }
    }

    private static boolean inLooseDomain(String name) {
        return name.startsWith(BLOCKS) || name.startsWith(ITEMS);
    }

    private static String replacePngExtension(String path, String extension) {
        return path.substring(0, path.length() - ".png".length()) + extension;
    }

    private static boolean isUnknownLegacyFile(String source, Set<String> exact) {
        if (source.startsWith("assets/") || source.equals("pack.png") || source.equals("pack.txt")
                || source.equals("terrain.png") || source.equals("gui/items.png")) {
            return false;
        }
        if (source.endsWith(".png.mcmeta")) {
            String png = source.substring(0, source.length() - ".mcmeta".length());
            return !exact.contains(png) || !Rules.PATHS.containsKey(png);
        }
        if (inLooseDomain(source) && source.endsWith(".txt")) {
            String png = source.substring(0, source.length() - ".txt".length()) + ".png";
            return !exact.contains(png) || !isKnownLooseTexture(png);
        }
        String lower = fold(source);
        return lower.endsWith(".png") || lower.startsWith("anim/") || lower.startsWith("ctm/")
                || lower.startsWith("mcpatcher/") || lower.startsWith("optifine/");
    }

    private static void warnPaths(UpgradeWorkspace workspace, String message, List<String> paths) {
        if (!paths.isEmpty()) {
            workspace.warn(message + "（" + paths.size() + " 项）："
                    + paths.subList(0, Math.min(paths.size(), WARNING_PREVIEW))
                    + (paths.size() > WARNING_PREVIEW ? " 等" : ""));
        }
    }

    private static Set<String> foldedFiles(List<String> files) {
        Set<String> names = new HashSet<String>();
        for (String file : files) {
            names.add(fold(file));
        }
        return names;
    }

    private static String fold(String path) {
        return path.toLowerCase(Locale.ROOT);
    }

    private static boolean canPlaceTexture(UpgradeWorkspace workspace, Set<String> occupied, String target)
            throws IOException {
        // 孤立目标侧车也不能自动挂到不相关的新纹理上。
        return canPlace(workspace, occupied, target) && canPlace(workspace, occupied, target + ".mcmeta");
    }

    private static boolean canPlace(UpgradeWorkspace workspace, Set<String> occupied, String target)
            throws IOException {
        if (occupied.contains(fold(target)) || Files.exists(workspace.path(target), LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        for (int slash = target.lastIndexOf('/'); slash >= 0; slash = target.lastIndexOf('/', slash - 1)) {
            if (occupied.contains(fold(target.substring(0, slash)))) {
                return false;
            }
        }
        return true;
    }

    private static final class Budget {
        private long pixels;
        private long bytes;

        boolean reserve(long addedPixels, long addedBytes) {
            if (addedPixels > MAX_PIXELS - pixels || addedBytes > MAX_COPY_BYTES - bytes) {
                return false;
            }
            pixels += addedPixels;
            bytes += addedBytes;
            return true;
        }
    }

    private static final class Placement {
        final String primary;
        final List<String> copies;
        final boolean existingSidecar;
        final String generatedSidecar;

        Placement(String primary, List<String> copies, boolean existingSidecar, String generatedSidecar) {
            this.primary = primary;
            this.copies = copies;
            this.existingSidecar = existingSidecar;
            this.generatedSidecar = generatedSidecar;
        }

        void write(UpgradeWorkspace workspace) throws IOException {
            if (generatedSidecar != null) {
                workspace.writeText(primary + ".mcmeta", generatedSidecar);
            }
            for (String copy : copies) {
                Path target = workspace.path(copy);
                Files.createDirectories(target.getParent());
                Files.copy(workspace.path(primary), target);
                if (existingSidecar || generatedSidecar != null) {
                    Files.copy(workspace.path(primary + ".mcmeta"), workspace.path(copy + ".mcmeta"));
                }
            }
        }
    }

    private static final class Rules {
        static final Map<String, List<String>> PATHS = readPaths();
        static final Map<Integer, String> BLOCK_TILES;
        static final Map<Integer, String> ITEM_TILES;

        static {
            Map<Integer, String> blocks = new LinkedHashMap<Integer, String>();
            Map<Integer, String> items = new LinkedHashMap<Integer, String>();
            readAtlas(blocks, items);
            BLOCK_TILES = Collections.unmodifiableMap(blocks);
            ITEM_TILES = Collections.unmodifiableMap(items);
        }

        private static void readAtlas(Map<Integer, String> blocks, Map<Integer, String> items) {
            String resource = "/viarp/migrations/legacy/atlas-shared.tsv";
            InputStream input = LegacyTextureUpgrade.class.getResourceAsStream(resource);
            if (input == null) {
                throw new IllegalStateException("Missing legacy atlas rules: " + resource);
            }
            Set<String> sources = new HashSet<String>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split("\t", -1);
                    if (fields.length != 4 || (!"terrain".equals(fields[0]) && !"items".equals(fields[0]))) {
                        throw new IOException("Malformed legacy atlas rule: " + line);
                    }
                    boolean terrain = "terrain".equals(fields[0]);
                    Map<Integer, String> tiles = terrain ? blocks : items;
                    int x = coordinate(fields[1]);
                    int y = coordinate(fields[2]);
                    String source = (terrain ? BLOCKS : ITEMS) + fields[3] + ".png";
                    List<String> targets = PATHS.get(source);
                    if (targets == null || targets.isEmpty() || !sources.add(source)
                            || tiles.put(x + GRID * y, fields[3]) != null) {
                        throw new IOException("Duplicate or unresolved legacy atlas rule: " + line);
                    }
                }
                if (blocks.isEmpty() || items.isEmpty()) {
                    throw new IOException("Missing terrain or item atlas coordinates");
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load legacy atlas rules: " + resource, e);
            }
        }

        private static int coordinate(String field) throws IOException {
            try {
                int value = Integer.parseInt(field);
                if (value >= 0 && value < GRID) {
                    return value;
                }
            } catch (NumberFormatException e) {
                throw new IOException("Invalid legacy atlas coordinate: " + field, e);
            }
            throw new IOException("Out-of-range legacy atlas coordinate: " + field);
        }

        private static Map<String, List<String>> readPaths() {
            String resource = "/viarp/migrations/legacy/texture-paths.tsv";
            InputStream input = LegacyTextureUpgrade.class.getResourceAsStream(resource);
            if (input == null) {
                throw new IllegalStateException("Missing legacy texture path rules: " + resource);
            }
            Map<String, List<String>> paths = new LinkedHashMap<String, List<String>>();
            Set<String> destinations = new HashSet<String>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int tab = line.indexOf('\t');
                    if (tab < 1 || tab == line.length() - 1) {
                        throw new IOException("Malformed legacy texture path rule: " + line);
                    }
                    String source = line.substring(0, tab);
                    List<String> targets = new ArrayList<String>();
                    String mapping = line.substring(tab + 1);
                    if (!mapping.equals("-")) {
                        for (String value : mapping.split(" ")) {
                            String target = "=".equals(value) ? source : value;
                            if (target.isEmpty() || !destinations.add(fold(target))) {
                                throw new IOException("Duplicate or empty legacy texture target: " + target);
                            }
                            targets.add(NAMESPACE + target);
                        }
                    }
                    if (paths.put(source, Collections.unmodifiableList(targets)) != null) {
                        throw new IOException("Duplicate legacy texture source: " + source);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load legacy texture path rules: " + resource, e);
            }
            return Collections.unmodifiableMap(paths);
        }
    }
}
