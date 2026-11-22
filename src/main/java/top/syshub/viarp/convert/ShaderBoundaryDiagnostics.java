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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** 只报告可定位的原版着色器接口变化，不改写或编译用户 GLSL。 */
final class ShaderBoundaryDiagnostics {
    private static final String CORE = "assets/minecraft/shaders/core/";
    private static final String[] OLD_TEXT = {
            "rendertype_text", "rendertype_text_intensity", "rendertype_text_see_through",
            "rendertype_text_intensity_see_through", "rendertype_text_background",
            "rendertype_text_background_see_through"
    };
    private static final Pattern IMPORT = Pattern.compile("\\h*#\\h*moj_import\\h*(?:\"[^\"]+\"|<[^>]+>).*");
    private static final int IMPORT_PREVIEW = 4;
    private static final int SCAN_FILES = 512;
    private static final long SCAN_BYTES = 32L * 1024 * 1024;

    private ShaderBoundaryDiagnostics() {
    }

    static void inspect(int boundary, UpgradeWorkspace workspace) throws IOException {
        if (boundary == 85 || boundary == 96) {
            String[] names = boundary == 85 ? OLD_TEXT : new String[] {"text_background"};
            String message = boundary == 85
                    ? "原版文本变体已合并，顶点格式和绑定不同；保留旧文件，不能直接改名"
                    : "原版背景着色器入口已移除，改由字形效果绘制；保留旧文件";
            List<String> files = workspace.files();
            for (String name : names) {
                for (String suffix : new String[] {".vsh", ".fsh"}) {
                    String file = CORE + name + suffix;
                    if (files.contains(file)) {
                        workspace.warn(boundary + ".0 [" + file + "] " + message);
                    }
                }
            }
        } else if (boundary == 93) {
            boolean stages = false;
            boolean limited = false;
            int remainingFiles = SCAN_FILES;
            long remainingBytes = SCAN_BYTES;
            for (String file : workspace.files()) {
                if (!file.startsWith("assets/")) {
                    continue;
                }
                int slash = file.indexOf('/', "assets/".length());
                if (slash < 0 || !file.startsWith("/shaders/", slash)) {
                    continue;
                }
                boolean stage = file.endsWith(".vsh") || file.endsWith(".fsh");
                stages |= stage;
                if (stage || file.endsWith(".glsl")) {
                    long bytes = remainingFiles > 0 ? Files.size(workspace.path(file)) : Long.MAX_VALUE;
                    if (remainingFiles == 0 || bytes > remainingBytes) {
                        limited = true;
                        continue;
                    }
                    remainingFiles--;
                    remainingBytes -= bytes;
                    inspectImports(workspace, file);
                }
            }
            if (limited) {
                workspace.warn("93.0: 着色器导入检查超过包内扫描预算，部分文件未检查；原件均保留。");
            }
            if (stages) {
                workspace.warn("93.0: 保留自定义着色器。两种渲染后端都改用 ShaderC；"
                        + "请核对顶点输入名、阶段间 location、类型、插值和管线绑定。");
            }
        }
    }

    private static void inspectImports(UpgradeWorkspace workspace, String file) throws IOException {
        String source = workspace.readText(file, "93.0 着色器导入");
        if (source == null || !source.contains("moj_import")) {
            return;
        }
        boolean block = false;
        int number = 0;
        int found = 0;
        try (BufferedReader lines = new BufferedReader(new StringReader(source))) {
            String line;
            while ((line = lines.readLine()) != null) {
                number++;
                char[] visible = line.toCharArray();
                boolean quoted = false;
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    char next = i + 1 < line.length() ? line.charAt(i + 1) : 0;
                    if (block) {
                        visible[i] = ' ';
                        if (c == '*' && next == '/') {
                            visible[++i] = ' ';
                            block = false;
                        }
                    } else if (quoted && c == '\\' && next != 0) {
                        i++;
                    } else if (c == '"') {
                        quoted = !quoted;
                    } else if (!quoted && c == '/' && next == '*') {
                        visible[i] = ' ';
                        visible[++i] = ' ';
                        block = true;
                    } else if (!quoted && c == '/' && next == '/') {
                        Arrays.fill(visible, i, visible.length, ' ');
                        break;
                    }
                }
                if (IMPORT.matcher(new String(visible)).matches()) {
                    if (found++ == IMPORT_PREVIEW) {
                        workspace.warn("93.0 [" + file + "] 还有未列出的旧导入，保留原文件");
                        break;
                    }
                    workspace.warn("93.0 [" + file + ":" + number + "] 保留 #moj_import；"
                            + "新 #include 按资源 ID 从 shaders/include/ 查找，不沿用引用文件目录。");
                }
            }
        }
    }
}
