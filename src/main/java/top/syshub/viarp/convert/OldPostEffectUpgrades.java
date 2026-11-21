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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;

/** 35→36 只迁移依赖已核对原版着色器的反色链；不移植程序或 GLSL。 */
final class OldPostEffectUpgrades {

    private static final String ROOT = "assets/minecraft/";
    private static final String OLD_PROGRAM = ROOT + "shaders/program/";
    private static final String SHADER = ROOT + "shaders/post/";
    private static final String OLD_INVERT = SHADER + "invert.json";
    private static final String OLD_OUTLINE = SHADER + "entity_outline.json";
    private static final String NEW_INVERT = ROOT + "post_effect/invert.json";
    private static final String CONTEXT = "36.0 后处理";
    private static final String[] OLD_DEPENDENCIES = {"invert.json", "blit.json", "blit.vsh", "blit.fsh", "invert.fsh"};
    private static final String[] NEW_DEPENDENCIES = {"blit.json", "blit.vsh", "blit.fsh", "invert.fsh"};

    private OldPostEffectUpgrades() {
    }

    static void apply(UpgradeWorkspace workspace) throws IOException {
        List<String> files = workspace.files();
        if (files.contains(OLD_OUTLINE)) {
            Map<String, Object> outline = workspace.readObject(OLD_OUTLINE, CONTEXT);
            if (outline != null && outline.containsKey("passes")) {
                warn(workspace, OLD_OUTLINE, "无法保真迁移旧轮廓的混合状态；原路径也可能遮蔽 36 的同名程序");
            }
        }
        if (!files.contains(OLD_INVERT)) {
            return;
        }
        Map<String, Object> effect = workspace.readObject(OLD_INVERT, CONTEXT);
        if (effect == null || (!effect.containsKey("passes") && effect.containsKey("vertex")
                && effect.containsKey("fragment"))) {
            return;
        }

        Set<String> packed = new HashSet<String>();
        for (String file : files) {
            String folded = file.toLowerCase(Locale.ROOT);
            packed.add(folded);
            if (!file.equals(OLD_INVERT) && folded.equals(OLD_INVERT)) {
                warn(workspace, OLD_INVERT, "原路径存在仅大小写不同的资源");
                return;
            }
        }
        if (pathConflict(workspace, NEW_INVERT) || pathConflict(workspace, NEW_INVERT + ".mcmeta")) {
            warn(workspace, OLD_INVERT, "目标路径或伴随元数据已占用");
            return;
        }
        if (packed.contains(OLD_INVERT + ".mcmeta") || hasOverride(packed, OLD_PROGRAM, OLD_DEPENDENCIES)
                || hasOverride(packed, SHADER, NEW_DEPENDENCIES)) {
            warn(workspace, OLD_INVERT, "存在包内着色器程序、阶段或伴随元数据");
            return;
        }
        if (packed.contains("pack.mcmeta")) {
            Map<String, Object> metadata = files.contains("pack.mcmeta")
                    ? workspace.readObject("pack.mcmeta", CONTEXT) : null;
            if (metadata == null || metadata.containsKey("filter")) {
                warn(workspace, OLD_INVERT, "资源过滤器或不可读元数据阻止确认原版依赖");
                return;
            }
        }

        Map<String, Object> targets = JsonAccess.object(effect, "targets");
        List<Object> passes = JsonAccess.array(effect, "passes");
        Map<String, Object> swap = JsonAccess.object(targets, "swap");
        if (swap == null) {
            swap = JsonAccess.object(targets, "minecraft:swap");
        }
        if (!hasOnly(effect, "targets", "passes") || swap == null || targets.size() != 1 || !swap.isEmpty()
                || passes == null || passes.size() != 2) {
            warn(workspace, OLD_INVERT, "只验证了原版双 pass 与单个全屏 swap 目标");
            return;
        }
        Map<String, Object> invert = asObject(passes.get(0));
        Map<String, Object> blit = asObject(passes.get(1));
        if (!matchesPass(invert, "invert", "main", "swap") || !matchesPass(blit, "blit", "swap", "main")) {
            warn(workspace, OLD_INVERT, "只验证了原版 main→swap→main 的颜色采样链");
            return;
        }
        List<?> amount = readUniform(invert, "InverseAmount", Collections.singletonList(0));
        List<Integer> neutral = Arrays.asList(1, 1, 1, 1);
        List<?> color = readUniform(blit, "ColorModulate", neutral);
        double factor = amount == null ? Double.NaN : ((Number) amount.get(0)).doubleValue();
        if (!(factor >= 0 && factor <= 1) || color == null
                || color.stream().anyMatch(value -> ((Number) value).floatValue() != 1)) {
            warn(workspace, OLD_INVERT, "只支持 [0,1] 内的 InverseAmount 与无调色的 blit");
            return;
        }

        rewritePass(invert, "invert", "InverseAmount", amount);
        // 36 的编译后程序可被多个效果共享，显式清除其他链留下的 blit 调色。
        rewritePass(blit, "blit", "ColorModulate", neutral);
        String result = Json.write(effect) + "\n";
        // 原链路径在 36 恰好是新 invert 程序路径，不能保留第二份。
        workspace.moveFiles(Collections.singletonMap(OLD_INVERT, NEW_INVERT));
        workspace.writeText(NEW_INVERT, result);
    }

    private static boolean matchesPass(Map<String, Object> pass, String name, String inputTarget, String outputTarget) {
        if (!hasOnly(pass, "name", "inputs", "output", "uniforms") || !name.equals(pass.get("name"))
                || !matchesTarget(pass.get("output"), outputTarget)) {
            return false;
        }
        List<Object> inputs = JsonAccess.array(pass, "inputs");
        Map<String, Object> input = inputs != null && inputs.size() == 1 ? asObject(inputs.get(0)) : null;
        return hasOnly(input, "sampler_name", "target", "bilinear", "use_depth_buffer")
                && "In".equals(input.get("sampler_name")) && matchesTarget(input.get("target"), inputTarget)
                && falseOrAbsent(input, "bilinear") && falseOrAbsent(input, "use_depth_buffer");
    }

    private static List<?> readUniform(Map<String, Object> pass, String name, List<Integer> defaults) {
        if (!pass.containsKey("uniforms")) {
            return defaults;
        }
        List<Object> uniforms = JsonAccess.array(pass, "uniforms");
        if (uniforms == null || uniforms.size() > 1) {
            return null;
        }
        if (uniforms.isEmpty()) {
            return defaults;
        }
        Map<String, Object> uniform = asObject(uniforms.get(0));
        if (!hasOnly(uniform, "name", "values") || !name.equals(uniform.get("name"))) {
            return null;
        }
        List<Object> values = JsonAccess.array(uniform, "values");
        return values != null && values.size() == defaults.size()
                && values.stream().allMatch(value -> value instanceof Number
                        && Float.isFinite(((Number) value).floatValue())) ? values : null;
    }

    private static void rewritePass(Map<String, Object> pass, String program, String name, List<?> values) {
        Map<String, Object> uniform = new LinkedHashMap<String, Object>();
        uniform.put("name", name);
        uniform.put("values", values);
        pass.remove("name");
        pass.put("program", "minecraft:post/" + program);
        pass.put("uniforms", Collections.singletonList(uniform));
    }

    private static boolean hasOverride(Set<String> packed, String directory, String[] dependencies) {
        for (String dependency : dependencies) {
            String path = directory + dependency;
            if (packed.contains(path) || packed.contains(path + ".mcmeta")) {
                return true;
            }
        }
        return false;
    }

    private static boolean pathConflict(UpgradeWorkspace workspace, String name) throws IOException {
        // 检查实际目录项，防止不区分大小写的文件系统把目标写入另一棵目录（包括空目录）。
        Path parent = workspace.path(name);
        String[] parts = name.split("/");
        for (int i = 0; i < parts.length; i++) {
            parent = parent.getParent();
        }
        for (int i = 0; i < parts.length; i++) {
            Path exact = null;
            try (DirectoryStream<Path> children = Files.newDirectoryStream(parent)) {
                for (Path child : children) {
                    String found = child.getFileName().toString();
                    if (!found.equalsIgnoreCase(parts[i])) {
                        continue;
                    }
                    if (!found.equals(parts[i]) || i == parts.length - 1
                            || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                        return true;
                    }
                    exact = child;
                }
            }
            if (exact == null) {
                return Files.exists(parent.resolve(parts[i]), LinkOption.NOFOLLOW_LINKS);
            }
            parent = exact;
        }
        return false;
    }

    private static boolean matchesTarget(Object value, String target) {
        return target.equals(value) || ("minecraft:" + target).equals(value);
    }

    private static boolean falseOrAbsent(Map<String, Object> fields, String name) {
        return !fields.containsKey(name) || Boolean.FALSE.equals(fields.get(name));
    }

    private static boolean hasOnly(Map<String, Object> object, String... keys) {
        return object != null && Arrays.asList(keys).containsAll(object.keySet());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static void warn(UpgradeWorkspace workspace, String path, String message) {
        workspace.warn(CONTEXT + " [" + path + "] " + message + "；保留原文件");
    }
}
