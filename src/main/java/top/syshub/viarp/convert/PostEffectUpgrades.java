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
import java.util.ArrayList;
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
import top.syshub.viarp.pack.PackFormat;

/** 只改两处相邻格式中已核对原版 shader 闭包的后处理 JSON；不移除任何 shader 原件。 */
final class PostEffectUpgrades {

    private static final String ASSETS = "assets/minecraft/";
    private static final String SHADERS = ASSETS + "shaders/";
    private static final String EFFECTS = ASSETS + "post_effect/";

    private static final PassRule BLIT = new PassRule("blit", "blit", "blit", "BlitConfig",
            new Slot("ColorModulate", 4, 1, 1, 1, 1));
    private static final PassRule INVERT = new PassRule("invert", "blit", "invert", "InvertConfig",
            new Slot("InverseAmount", 1, 0));
    private static final PassRule SOBEL = new PassRule("entity_outline", "sobel", "entity_sobel", "");
    private static final PassRule OUTLINE_BLUR = new PassRule("entity_outline_box_blur", "blur",
            "entity_outline_box_blur", "BlurConfig", new Slot("BlurDir", 2, 1, 1));
    private static final PassRule COLOR = new PassRule(null, "sobel", "color_convolve", "ColorConfig",
            new Slot("RedMatrix", 3), new Slot("GreenMatrix", 3), new Slot("BlueMatrix", 3));
    private static final PassRule BITS = new PassRule(null, "sobel", "bits", "BitsConfig",
            new Slot("Resolution", 1), new Slot("MosaicSize", 1));

    private PostEffectUpgrades() {
    }

    static void apply(PackFormat entered, UpgradeWorkspace workspace) throws IOException {
        int boundary = entered.major();
        if (entered.minor() != 0 || (boundary != 52 && boundary != 57)) {
            return;
        }
        List<String> files = workspace.files();
        List<String> effects = new ArrayList<String>();
        Set<String> packed = new HashSet<String>();
        for (String file : files) {
            packed.add(file.toLowerCase(Locale.ROOT));
            if (file.matches("assets/[^/]+/post_effect/.+\\.json")) {
                effects.add(file);
            }
        }
        if (effects.isEmpty()) {
            return;
        }
        String context = boundary + ".0 后处理";
        boolean filtered = false;
        if (workspace.hasFile("pack.mcmeta")) {
            Map<String, Object> metadata = workspace.readObject("pack.mcmeta", context);
            filtered = metadata == null || metadata.containsKey("filter");
        }
        for (String file : effects) {
            try {
                Map<String, Object> effect = workspace.readObject(file, context);
                if (effect == null) {
                    continue;
                }
                List<Object> passes = JsonAccess.array(effect, "passes");
                if (passes == null && effect.containsKey("passes")) {
                    throw new UnsupportedEffect("invalid passes");
                }
                if (!needsMigration(passes, boundary)) {
                    continue;
                }
                if (filtered) {
                    throw new UnsupportedEffect("pack filter or unreadable metadata prevents establishing shader fallback");
                }
                migrate(effect, file, passes, boundary, packed);
                workspace.writeText(file, Json.write(effect) + "\n");
            } catch (UnsupportedEffect e) {
                workspace.warn(context + " [" + file + "] " + e.getMessage() + "；保留原文件");
            }
        }
    }

    private static boolean needsMigration(List<Object> passes, int boundary) throws UnsupportedEffect {
        boolean legacy = false;
        if (passes != null) {
            for (Object value : passes) {
                Map<String, Object> pass = asObject(value);
                if (pass == null) {
                    throw new UnsupportedEffect("invalid pass");
                }
                legacy |= pass.containsKey("program") || (boundary == 57 && pass.get("uniforms") instanceof List);
                if (boundary == 57 && pass.containsKey("uniforms") && !(pass.get("uniforms") instanceof List)
                        && !(pass.get("uniforms") instanceof Map)) {
                    throw new UnsupportedEffect("invalid uniforms");
                }
            }
        }
        return legacy;
    }

    private static void migrate(Map<String, Object> effect, String file, List<Object> passes, int boundary,
                                Set<String> packed) throws UnsupportedEffect {
        PassRule[] rules;
        String external;
        if ((EFFECTS + "invert.json").equals(file)) {
            rules = new PassRule[] {INVERT, BLIT};
            external = "minecraft:main";
        } else if ((EFFECTS + "entity_outline.json").equals(file)) {
            rules = new PassRule[] {SOBEL, OUTLINE_BLUR, BLIT};
            external = "minecraft:entity_outline";
        } else if (boundary == 57 && (EFFECTS + "creeper.json").equals(file)) {
            rules = new PassRule[] {COLOR, BITS};
            external = "minecraft:main";
        } else {
            throw new UnsupportedEffect("no verified vanilla pass graph at this boundary");
        }
        if (!hasOnly(effect, "targets", "passes")) {
            throw new UnsupportedEffect("unknown effect fields");
        }
        Set<String> internal = new HashSet<String>();
        if (effect.containsKey("targets")) {
            Map<String, Object> targets = JsonAccess.object(effect, "targets");
            if (targets == null) {
                throw new UnsupportedEffect("invalid targets");
            }
            for (Map.Entry<String, Object> entry : targets.entrySet()) {
                String id = targetId(entry.getKey());
                Map<String, Object> definition = asObject(entry.getValue());
                if (id.isEmpty() || id.equals(external) || !internal.add(id)
                        || definition == null || !definition.isEmpty()) {
                    throw new UnsupportedEffect("only distinct empty fullscreen targets are verified");
                }
            }
        }
        Set<String> ready = new HashSet<String>();
        ready.add(external);
        String output = "";
        for (Object value : passes) {
            Map<String, Object> pass = asObject(value);
            output = targetId(JsonAccess.string(pass, "output", ""));
            PassRule rule = findRule(pass, rules, boundary);
            if (!validInput(pass, ready, output) || (!external.equals(output) && !internal.contains(output))) {
                throw new UnsupportedEffect("only one In color sampler and a closed target graph are verified");
            }
            ready.add(output);
            if (overridden(packed, SHADERS + "post/" + rule.vertex + ".vsh")
                    || overridden(packed, SHADERS + "post/" + rule.fragment + ".fsh")
                    || (boundary == 52 && overridden(packed, SHADERS + "post/" + rule.program + ".json"))
                    || (boundary == 57 && overridden(packed, SHADERS + "include/projection.glsl"))) {
                throw new UnsupportedEffect("uses a pack-supplied shader, program or include");
            }
            rewritePass(pass, rule, boundary);
        }
        if (!output.equals(external)) {
            throw new UnsupportedEffect("the last pass must write the vanilla external target");
        }
    }

    private static PassRule findRule(Map<String, Object> pass, PassRule[] rules, int boundary)
            throws UnsupportedEffect {
        if (boundary == 52 && hasOnly(pass, "program", "inputs", "output", "uniforms")) {
            String program = JsonAccess.string(pass, "program", "");
            for (PassRule rule : rules) {
                if (rule.program != null && matchesPost(program, rule.program)) {
                    return rule;
                }
            }
        } else if (boundary == 57 && hasOnly(pass, "vertex_shader", "fragment_shader", "inputs", "output", "uniforms")) {
            String vertex = JsonAccess.string(pass, "vertex_shader", "");
            String fragment = JsonAccess.string(pass, "fragment_shader", "");
            for (PassRule rule : rules) {
                if (matchesPost(vertex, rule.vertex) && matchesPost(fragment, rule.fragment)) {
                    return rule;
                }
            }
        }
        throw new UnsupportedEffect("unverified shader pair or mixed pass schema");
    }

    private static boolean validInput(Map<String, Object> pass, Set<String> ready, String output) {
        List<Object> inputs = JsonAccess.array(pass, "inputs");
        if (inputs == null || inputs.size() != 1) {
            return false;
        }
        Map<String, Object> input = asObject(inputs.get(0));
        if (!hasOnly(input, "sampler_name", "target", "bilinear", "use_depth_buffer")) {
            return false;
        }
        String target = targetId(JsonAccess.string(input, "target", ""));
        return "In".equals(input.get("sampler_name")) && ready.contains(target) && !target.equals(output)
                && (!input.containsKey("bilinear") || input.get("bilinear") instanceof Boolean)
                && (!input.containsKey("use_depth_buffer") || Boolean.FALSE.equals(input.get("use_depth_buffer")));
    }

    private static void rewritePass(Map<String, Object> pass, PassRule rule, int boundary) throws UnsupportedEffect {
        List<Object> overrides = JsonAccess.array(pass, "uniforms");
        if (overrides == null) {
            if (pass.containsKey("uniforms")) {
                throw new UnsupportedEffect("mixed or invalid uniform schema");
            }
            overrides = Collections.emptyList();
        }
        Map<String, List<Object>> byName = new LinkedHashMap<String, List<Object>>();
        for (Object entry : overrides) {
            Map<String, Object> uniform = asObject(entry);
            if (!hasOnly(uniform, "name", "type", "values") || (boundary == 52 && uniform.containsKey("type"))) {
                throw new UnsupportedEffect("unknown or mixed uniform fields");
            }
            String name = JsonAccess.string(uniform, "name", "");
            Slot slot = rule.slot(name);
            List<Object> values = JsonAccess.array(uniform, "values");
            if (slot == null || !validValues(values, slot.width)
                    || (boundary == 57 && !slot.type.equals(uniform.get("type")))
                    || byName.put(name, values) != null) {
                throw new UnsupportedEffect("unverified, duplicate or incorrectly typed uniform");
            }
        }
        List<Object> output = new ArrayList<Object>();
        for (Slot slot : rule.slots) {
            List<?> values = byName.get(slot.name);
            if (values == null && boundary == 52) {
                values = slot.vanillaDefault;
            }
            if (values == null) {
                throw new UnsupportedEffect("missing explicit value for uniform " + slot.name);
            }
            output.add(uniform(slot, values, boundary));
        }
        if (boundary == 52) {
            pass.remove("program");
            pass.put("vertex_shader", "minecraft:post/" + rule.vertex);
            pass.put("fragment_shader", "minecraft:post/" + rule.fragment);
            if (!output.isEmpty() || pass.containsKey("uniforms")) {
                pass.put("uniforms", output);
            }
        } else if (!output.isEmpty()) {
            if (rule == OUTLINE_BLUR) {
                // 目标原版顶点块需要该槽，轮廓 fragment 仍使用自身的常量 radius=2。
                output.add(uniform(new Slot("Radius", 1), Collections.singletonList(2), boundary));
            }
            pass.put("uniforms", Collections.singletonMap(rule.block, output));
        } else if (pass.containsKey("uniforms")) {
            pass.remove("uniforms");
        }
    }

    private static Map<String, Object> uniform(Slot slot, List<?> values, int boundary) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("name", slot.name);
        result.put("type", slot.type);
        if (boundary == 52) {
            result.put("values", values);
        } else {
            result.put("value", slot.width == 1 ? values.get(0) : values);
        }
        return result;
    }

    private static boolean validValues(List<Object> values, int count) {
        if (values == null || values.size() != count) {
            return false;
        }
        for (Object value : values) {
            if (!(value instanceof Number) || !Float.isFinite(((Number) value).floatValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesPost(String id, String path) {
        String post = "post/" + path;
        return post.equals(id) || ("minecraft:" + post).equals(id);
    }

    private static String targetId(String value) {
        if (!value.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")) {
            return "";
        }
        return value.indexOf(':') >= 0 ? value : "minecraft:" + value;
    }

    private static boolean overridden(Set<String> packed, String path) {
        return packed.contains(path) || packed.contains(path + ".mcmeta");
    }

    private static boolean hasOnly(Map<String, Object> object, String... keys) {
        return object != null && Arrays.asList(keys).containsAll(object.keySet());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static final class PassRule {
        final String program;
        final String vertex;
        final String fragment;
        final String block;
        final Slot[] slots;

        PassRule(String program, String vertex, String fragment, String block, Slot... slots) {
            this.program = program;
            this.vertex = vertex;
            this.fragment = fragment;
            this.block = block;
            this.slots = slots;
        }

        Slot slot(String name) {
            for (Slot slot : slots) {
                if (slot.name.equals(name)) {
                    return slot;
                }
            }
            return null;
        }
    }

    private static final class Slot {
        final String name;
        final int width;
        final String type;
        final List<Integer> vanillaDefault;

        Slot(String name, int width, Integer... vanillaDefault) {
            this.name = name;
            this.width = width;
            this.type = width == 1 ? "float" : "vec" + width;
            this.vanillaDefault = Arrays.asList(vanillaDefault);
        }
    }

    private static final class UnsupportedEffect extends Exception {
        UnsupportedEffect(String message) {
            super(message);
        }
    }
}
