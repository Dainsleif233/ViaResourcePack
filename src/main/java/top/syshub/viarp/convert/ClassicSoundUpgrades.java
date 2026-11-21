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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import top.syshub.viarp.json.Json;

/** 声音定义的注册 ID 与 OGG 资源 ID 是两个独立的空间。 */
final class ClassicSoundUpgrades {

    private static final String MINECRAFT = "minecraft";
    private static final String VANILLA = "assets/" + MINECRAFT + "/";
    private static final String SOUND_DEFINITION = "/sounds.json";

    private ClassicSoundUpgrades() {
    }

    static void migrateLegacyFiles(UpgradeWorkspace workspace) throws IOException {
        String table = "/viarp/migrations/classic/format-2-sound-files.tsv";
        Map<String, String> paths = ClassicPackUpgrades.readMappings(table,
                "(?:sound|records|music)/[A-Za-z0-9_./-]+\\.ogg", "sounds/[A-Za-z0-9_./-]+\\.ogg");
        List<String> files = workspace.files();
        Set<String> occupied = new HashSet<String>();
        for (String file : files) {
            occupied.add(file.toLowerCase(Locale.ROOT));
        }
        for (String file : files) {
            if (!file.startsWith(VANILLA)) {
                continue;
            }
            String mapped = paths.get(file.substring(VANILLA.length()));
            if (mapped == null) {
                continue;
            }
            String target = VANILLA + mapped;
            Path destination = workspace.path(target);
            Path parent = destination.getParent();
            while (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
                parent = parent.getParent();
            }
            if (occupied.contains(target.toLowerCase(Locale.ROOT)) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                ClassicPackUpgrades.warn(workspace, 2, file, "与目标音频位置冲突 " + mapped + "；保留原文件");
                continue;
            }
            Files.createDirectories(destination.getParent());
            Files.copy(workspace.path(file), destination);
            occupied.add(target.toLowerCase(Locale.ROOT));
        }
    }

    static void migrateEvents(UpgradeWorkspace workspace, int edge) throws IOException {
        String table = "/viarp/migrations/classic/format-" + edge + "-sound-events.tsv";
        Map<String, String> aliases = ClassicPackUpgrades.readMappings(table, "[A-Za-z0-9_.-]+", "[a-z0-9_.-]+");
        Set<String> collisions = new HashSet<String>();
        List<String> files = workspace.files();
        String vanillaFile = VANILLA + "sounds.json";
        Map<String, Object> vanilla = files.contains(vanillaFile)
                ? ClassicPackUpgrades.readObject(workspace, edge, vanillaFile) : null;
        if (vanilla != null) {
            for (Map.Entry<String, String> alias : aliases.entrySet()) {
                if (vanilla.containsKey(alias.getKey()) && vanilla.containsKey(alias.getValue())) {
                    collisions.add(alias.getKey());
                }
            }
        }
        for (String file : files) {
            if (!file.matches("assets/[A-Za-z0-9_.-]+/sounds\\.json")) {
                continue;
            }
            String owner = file.substring("assets/".length(), file.length() - SOUND_DEFINITION.length());
            Map<String, Object> root = file.equals(vanillaFile) ? vanilla
                    : ClassicPackUpgrades.readObject(workspace, edge, file);
            if (root != null && migrateFile(workspace, file, owner, edge, root, aliases, collisions)) {
                workspace.writeText(file, Json.write(root));
            }
        }
    }

    private static boolean migrateFile(UpgradeWorkspace workspace, String file, String owner, int edge,
                                       Map<String, Object> root, Map<String, String> aliases, Set<String> collisions) {
        boolean changed = false;
        for (String key : new ArrayList<String>(root.keySet())) {
            Map<String, Object> event = ClassicPackUpgrades.object(root.get(key));
            if (event == null) {
                continue;
            }
            changed |= migrateReferences(workspace, file, owner, edge, event, aliases, collisions);
            if (MINECRAFT.equals(owner) && aliases.containsKey(key)) {
                String target = aliases.get(key);
                if (collisions.contains(key)) {
                    ClassicPackUpgrades.warn(workspace, edge, file,
                            "已有目标声音事件 " + target + "；保留两个定义，不合并");
                } else {
                    // 保留旧名，避免让其他资源包仍指向旧名的自定义事件失效。
                    root.put(target, event);
                    changed = true;
                }
            }
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static boolean migrateReferences(UpgradeWorkspace workspace, String file, String owner, int edge,
                                             Map<String, Object> event, Map<String, String> aliases,
                                             Set<String> collisions) {
        if (!(event.get("sounds") instanceof List)) {
            return false;
        }
        boolean changed = false;
        List<Object> sounds = (List<Object>) event.get("sounds");
        for (int index = 0; index < sounds.size(); index++) {
            Object value = sounds.get(index);
            if (value instanceof String) {
                String previous = (String) value;
                String replacement = reference(workspace, file, owner, edge, previous, false, aliases, collisions);
                if (!previous.equals(replacement)) {
                    sounds.set(index, replacement);
                    changed = true;
                }
            } else {
                Map<String, Object> spec = ClassicPackUpgrades.object(value);
                if (spec == null || !(spec.get("name") instanceof String)) {
                    continue;
                }
                Object type = spec.get("type");
                if (type != null && !"file".equals(type) && !"event".equals(type)) {
                    continue;
                }
                String previous = (String) spec.get("name");
                String replacement = reference(workspace, file, owner, edge, previous, "event".equals(type), aliases, collisions);
                if (!previous.equals(replacement)) {
                    spec.put("name", replacement);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static String reference(UpgradeWorkspace workspace, String file, String owner, int edge, String previous,
                                    boolean event, Map<String, String> aliases, Set<String> collisions) {
        int colon = previous.indexOf(':');
        if (event && edge == 2 && colon >= 0) {
            // 格式 1 原版把显式前缀作为事件路径的一部分，并不按后来的 ResourceLocation 解析。
            ClassicPackUpgrades.warn(workspace, edge, file, "未重写格式 1 中带显式前缀的事件引用 " + previous);
            return previous;
        }
        String namespace = colon < 0 ? (edge == 2 ? owner : MINECRAFT) : previous.substring(0, colon);
        String path = previous.substring(colon + 1);
        if (!namespace.matches("[A-Za-z0-9_.-]+") || !path.matches("[A-Za-z0-9/_.-]+")) {
            return previous;
        }
        if (event && MINECRAFT.equals(namespace) && !collisions.contains(path)) {
            path = aliases.getOrDefault(path, path);
        }
        return colon >= 0 || !MINECRAFT.equals(namespace) ? namespace + ":" + path : path;
    }
}
