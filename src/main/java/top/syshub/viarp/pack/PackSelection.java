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
package top.syshub.viarp.pack;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.io.PathIo;
import top.syshub.viarp.json.Json;

/**
 * 「哪些本地资源包真的被启用了」——数据源是 {@code options.txt} 的 {@code resourcePacks} 行。
 *
 * <p>存在的理由是**时机**。原版的本地包发现（我们的拦截点）在三种场合都会跑：启动、
 * F3+T、以及**打开资源包选择界面**（{@code PackSelectionModel.findNewPacks} →
 * {@code PackRepository.reload}）。如果在拦截点无条件转换，玩家只是进界面看一眼，
 * 目录里所有旧版本包就全被转了一遍——第一次进去可能卡好几分钟。所以拦截点必须能区分
 * 「正在被列出来给人看」和「正要被真正加载」。
 *
 * <p>能拿来区分的、且不引用任何 {@code net.minecraft} 类型的信号就是 {@code options.txt}：
 *
 * <pre>
 *   PackSelectionScreen.onClose
 *    └─ PackSelectionModel.commit → OptionsScreen.applyPacks
 *        └─ Options.updateResourcePacks(repo)
 *            ├─ 按 repo.getSelectedPacks() 重写 resourcePacks / incompatibleResourcePacks
 *            ├─ save()                     ← 先落盘
 *            └─ Minecraft.reloadResourcePacks()
 *                 └─ PackRepository.reload() → 我们的拦截点   ← 此时读到的就是新列表
 * </pre>
 *
 * <p>关键点是 {@code save()} 排在 {@code reloadResourcePacks()} 前面（1.14.4 – 26.3 都是
 * 这个顺序），因此拦截点读到的 {@code options.txt} 一定已经包含玩家刚勾上的包。反过来，
 * 只是打开界面浏览时 {@code options.txt} 不变，未启用的包自然不会被转换。
 *
 * <p>包 id 的形态：{@code FolderRepositorySource} 从 1.14.4 起就固定用
 * {@code "file/" + 文件名}。{@code Options.loadSelectedResourcePacks} 至今仍兼容不带前缀的
 * 老写法（{@code getPack(name)} 失败后再试 {@code getPack("file/" + name)}），所以这里两种
 * 形态都认。
 *
 * <p>每次读取受限的选项快照，只按 {@code resourcePacks} 行的内容复用解析结果。
 */
public final class PackSelection {

    private static final int MAX_OPTIONS_CHARS = 1024 * 1024;
    private final Path optionsFile;

    private String cachedValue;
    private Set<String> cachedIds = Collections.emptySet();
    private IOException cachedParseFailure;
    private boolean failureLogged;

    public PackSelection(Path optionsFile) {
        this.optionsFile = optionsFile;
    }

    /**
     * [packFileName]（{@code resourcepacks/} 下的文件或目录名）当前是否被启用。
     *
     * <p>{@code options.txt} 不存在时一律返回 {@code false}——那是全新的游戏目录，
     * 确实没有任何包被启用。读取失败时同样返回 {@code false} 并打一条警告：宁可暂时不转换
     * （玩家看到原版的「不兼容」提示），也不要在玩家只想浏览目录时闷头转换整个目录。
     */
    public synchronized boolean isEnabled(String packFileName) {
        if (packFileName == null || packFileName.isEmpty()) {
            return false;
        }
        Set<String> ids = selectedIds();
        return ids.contains("file/" + packFileName) || ids.contains(packFileName);
    }

    /** 当前 {@code options.txt} 里的 {@code resourcePacks} 集合（原样，含 {@code file/} 前缀）。 */
    public synchronized Set<String> selectedIds() {
        if (!Files.isRegularFile(optionsFile)) {
            // 全新游戏目录：还没有任何包被启用，这不是错误。
            failureLogged = false;
            return Collections.emptySet();
        }
        String value;
        try {
            value = findLine(readOptions());
        } catch (IOException e) {
            warnOnce("Could not read {}; treating every local pack as not installed", e);
            return Collections.emptySet();
        }
        if (value == null) {
            failureLogged = false;
            return Collections.emptySet();
        }
        if (!value.equals(cachedValue)) {
            cachedValue = value;
            try {
                cachedIds = Collections.unmodifiableSet(parseList(value));
                cachedParseFailure = null;
            } catch (IOException e) {
                cachedIds = Collections.emptySet();
                cachedParseFailure = e;
            }
        }
        if (cachedParseFailure != null) {
            warnOnce("Malformed resourcePacks entry in {}; treating every local pack as not installed", cachedParseFailure);
        } else {
            failureLogged = false;
        }
        return cachedIds;
    }

    private String readOptions() throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        try (Reader reader = Files.newBufferedReader(optionsFile, StandardCharsets.UTF_8)) {
            int length;
            while ((length = reader.read(buffer)) != -1) {
                if (length > MAX_OPTIONS_CHARS - text.length()) {
                    throw new IOException("options.txt exceeds " + MAX_OPTIONS_CHARS + " characters");
                }
                text.append(buffer, 0, length);
            }
        }
        return PathIo.stripBom(text.toString());
    }

    /**
     * 取出 {@code resourcePacks:} 那一行的值。
     *
     * <p>{@code options.txt} 是「每行 {@code key:value}」的自有格式（不是 properties，也不是
     * JSON），只有 value 部分是 JSON 数组。必须精确匹配键名——{@code incompatibleResourcePacks}
     * 以同样的子串结尾，用 {@code contains} 会撞上。
     */
    private static String findLine(String text) {
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("resourcePacks:")) {
                return line.substring("resourcePacks:".length()).trim();
            }
        }
        return null;
    }

    private Set<String> parseList(String value) throws IOException {
        Object parsed = Json.parse(value);
        if (!(parsed instanceof List)) {
            throw new IOException("expected a JSON array, got " + (parsed == null ? "null" : parsed.getClass().getSimpleName()));
        }
        Set<String> ids = new LinkedHashSet<String>();
        for (Object element : (List<?>) parsed) {
            if (element instanceof String) {
                ids.add((String) element);
            }
        }
        return ids;
    }

    /** 连续失败只报一次，恢复后可以再提示下一次失败。 */
    private void warnOnce(String message, Throwable failure) {
        if (!failureLogged) {
            ViaRpLog.warn(message, optionsFile, failure);
            failureLogged = true;
        }
    }
}
