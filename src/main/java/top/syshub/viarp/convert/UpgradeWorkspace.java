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
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import top.syshub.viarp.io.PathIo;

/** 迁移只能修改已隔离的工作树；逻辑路径统一用 '/'，不得隐式覆盖其他资源。 */
final class UpgradeWorkspace {

    private final Path root;
    private final Set<String> warnings = new LinkedHashSet<String>();

    UpgradeWorkspace(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    Path path(String name) throws IOException {
        if (name.isEmpty() || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
            throw new IOException("Invalid pack path: " + name);
        }
        Path result = root;
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new IOException("Invalid pack path: " + name);
            }
            result = result.resolve(part);
            if (Files.isSymbolicLink(result)) {
                throw new IOException("Pack path is a symbolic link: " + name);
            }
        }
        return result;
    }

    boolean hasFile(String name) throws IOException {
        return Files.isRegularFile(path(name), LinkOption.NOFOLLOW_LINKS);
    }

    List<String> files() throws IOException {
        final List<String> result = new ArrayList<String>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile()) {
                    throw new IOException("Not a regular pack resource: " + root.relativize(file));
                }
                result.add(root.relativize(file).toString().replace('\\', '/'));
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(result);
        return result;
    }

    /** 批量移动：先检查全集；再暂存，支持只变大小写、以及名称相互占位。 */
    void moveFiles(Map<String, String> changes) throws IOException {
        Map<String, Path> originals = new LinkedHashMap<String, Path>();
        for (Map.Entry<String, String> change : changes.entrySet()) {
            if (!change.getKey().equals(change.getValue()) && hasFile(change.getKey())) {
                originals.put(change.getKey(), path(change.getKey()));
            }
        }
        if (originals.isEmpty()) {
            return;
        }
        Set<String> destinations = new HashSet<String>();
        Set<String> unchanged = new HashSet<String>();
        for (String file : files()) {
            if (!originals.containsKey(file)) {
                unchanged.add(fold(file));
            }
        }
        for (String original : originals.keySet()) {
            String destination = changes.get(original);
            if (!destinations.add(fold(destination)) || unchanged.contains(fold(destination))
                    || Files.isDirectory(path(destination), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Pack migration path collision: " + original + " -> " + destination);
            }
        }
        Path staging = Files.createTempDirectory(root, ".viarp-move-");
        Map<Path, Path> staged = new LinkedHashMap<Path, Path>();
        for (Map.Entry<String, Path> source : originals.entrySet()) {
            Path file = staging.resolve(Integer.toString(staged.size()));
            Files.move(source.getValue(), file);
            staged.put(file, path(changes.get(source.getKey())));
        }
        for (Path source : originals.values()) {
            Path parent = source.getParent();
            while (!parent.equals(root)) {
                try {
                    Files.deleteIfExists(parent);
                } catch (DirectoryNotEmptyException e) {
                    break;
                }
                parent = parent.getParent();
            }
        }
        for (Map.Entry<Path, Path> file : staged.entrySet()) {
            Files.createDirectories(file.getValue().getParent());
            Files.move(file.getKey(), file.getValue());
        }
        Files.delete(staging);
    }

    void writeText(String relative, String text) throws IOException {
        PathIo.writeString(path(relative), text);
    }

    String readText(String relative) throws IOException {
        return PathIo.readString(path(relative));
    }

    void warn(String diagnostic) {
        warnings.add(diagnostic);
    }

    List<String> warnings() {
        return new ArrayList<String>(warnings);
    }

    private static String fold(String path) {
        return path.toLowerCase(Locale.ROOT);
    }
}
