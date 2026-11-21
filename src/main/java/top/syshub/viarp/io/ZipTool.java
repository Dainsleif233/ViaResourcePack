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
package top.syshub.viarp.io;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** 仅用 JDK 解压和生成资源包 ZIP。 */
public final class ZipTool {

    private static final int MAX_ENTRIES = 200_000;
    private static final int MAX_PATH_LENGTH = 1024;
    private static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;

    private ZipTool() {
        throw new UnsupportedOperationException();
    }

    /** 解压到空的工作树；拒绝歧义路径、覆盖条目和超出解压预算的输入。 */
    public static void extract(Path zip, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("ZIP extraction target is not a directory");
        }
        try (DirectoryStream<Path> contents = Files.newDirectoryStream(root)) {
            if (contents.iterator().hasNext()) {
                throw new IOException("ZIP extraction target must be empty");
            }
        }
        try (ZipFile file = new ZipFile(zip.toFile())) {
            if (file.size() > MAX_ENTRIES) {
                throw new IOException("Too many ZIP entries: " + file.size());
            }
            Set<String> names = new HashSet<String>();
            long total = 0;
            byte[] buffer = new byte[65536];
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                String relative = entry.isDirectory() ? name.substring(0, name.length() - 1) : name;
                Path out = entryPath(root, relative);
                if (!names.add(relative.toLowerCase(Locale.ROOT))) {
                    throw new IOException("Duplicate ZIP entry: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                long limit = Math.min(MAX_ENTRY_BYTES, MAX_TOTAL_BYTES - total);
                if (entry.getSize() > limit) {
                    throw new IOException("ZIP data exceeds extraction budget: " + name);
                }
                Files.createDirectories(out.getParent());
                long written = 0;
                CRC32 crc = new CRC32();
                try (InputStream input = file.getInputStream(entry);
                     OutputStream output = new BufferedOutputStream(
                             Files.newOutputStream(out, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        written += count;
                        if (written > limit) {
                            throw new IOException("ZIP data exceeds extraction budget: " + name);
                        }
                        crc.update(buffer, 0, count);
                        output.write(buffer, 0, count);
                    }
                }
                if (entry.getSize() != written || entry.getCrc() != crc.getValue()) {
                    throw new IOException("ZIP entry size or checksum does not match its contents: " + name);
                }
                total += written;
            }
        }
    }

    private static Path entryPath(Path root, String relative) throws IOException {
        if (relative.length() > MAX_PATH_LENGTH || relative.indexOf('\\') >= 0 || relative.indexOf(':') >= 0) {
            throw new IOException("Invalid ZIP entry path: " + relative);
        }
        Path result = root;
        for (String part : relative.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)
                    || part.endsWith(".") || part.endsWith(" ")) {
                throw new IOException("Invalid ZIP entry path: " + relative);
            }
            result = result.resolve(part);
        }
        if (!result.startsWith(root)) {
            throw new IOException("ZIP entry escapes the target directory: " + relative);
        }
        return result;
    }

    /**
     * 把 [source] 目录打包成 [zip]（不含根目录本身）。
     *
     * <p>条目按路径排序、时间戳固定为 0，让同样的输入产出逐字节相同的 zip——
     * 这样服务器包的 SHA-1 校验与我们自己的缓存键都是稳定的。
     */
    public static void pack(final Path source, Path zip) throws IOException {
        Path parent = zip.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final List<Path> files = new ArrayList<Path>();
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
                    throw new IOException("Not a regular pack resource: " + file);
                }
                files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(files, new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                return relative(source, a).compareTo(relative(source, b));
            }
        });

        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zip)));
        try {
            for (Path file : files) {
                ZipEntry entry = new ZipEntry(relative(source, file));
                entry.setTime(0L);
                out.putNextEntry(entry);
                InputStream in = Files.newInputStream(file);
                try {
                    PathIo.copyStream(in, out);
                } finally {
                    in.close();
                }
                out.closeEntry();
            }
        } finally {
            out.close();
        }
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
