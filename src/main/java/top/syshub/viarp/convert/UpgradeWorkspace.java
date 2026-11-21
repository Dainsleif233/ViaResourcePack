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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
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
import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonException;

/** 迁移只能修改已隔离的工作树；逻辑路径统一用 '/'，不得隐式覆盖其他资源。 */
final class UpgradeWorkspace {

    private static final int MAX_TEXT_BYTES = 4 * 1024 * 1024;
    private static final int MAX_WARNINGS = 256;
    private static final int MAX_WARNING_LENGTH = 2048;

    private final Path root;
    private final Set<String> warnings = new LinkedHashSet<String>();
    private boolean warningsTruncated;

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

    String readText(String relative, String context) throws IOException {
        Path file = path(relative);
        if (Files.size(file) > MAX_TEXT_BYTES) {
            warn(context + " [" + relative + "] 文本超过迁移读取上限；保留原文件");
            return null;
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException ex) {
            warn(context + " [" + relative + "] 无效 UTF-8；保留原字节，不替换字符");
            return null;
        }
    }

    Map<String, Object> readObject(String relative, String context) throws IOException {
        String text = readText(relative, context);
        if (text == null) {
            return null;
        }
        try {
            return Json.parseObjectRejectingDuplicateKeys(text);
        } catch (JsonException ex) {
            warn(context + " [" + relative + "] JSON 无法安全解析；保留原文，不改写字段");
            return null;
        }
    }

    void warn(String diagnostic) {
        // 文件名可能来自任意输入；限制体积并保证一条诊断只占一行。
        int length = Math.min(MAX_WARNING_LENGTH, diagnostic.length());
        StringBuilder singleLine = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = diagnostic.charAt(i);
            singleLine.append(Character.isISOControl(c) || c == '\u2028' || c == '\u2029' ? ' ' : c);
        }
        if (length < diagnostic.length()) {
            singleLine.append("…");
        }
        String message = singleLine.toString();
        if (warnings.size() < MAX_WARNINGS) {
            warnings.add(message);
        } else if (!warnings.contains(message)) {
            warningsTruncated = true;
        }
    }

    List<String> warnings() {
        List<String> result = new ArrayList<String>(warnings);
        if (warningsTruncated) {
            result.add("Additional migration diagnostics were omitted (limit: " + MAX_WARNINGS + ").");
        }
        return result;
    }

    private static String fold(String path) {
        return path.toLowerCase(Locale.ROOT);
    }
}
