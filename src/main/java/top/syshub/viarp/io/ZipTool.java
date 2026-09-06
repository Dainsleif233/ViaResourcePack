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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * zip 解压 / 打包。
 *
 * <p>刻意只用 {@code java.util.zip}：core 不引用 relocate 后的 zip4j，这样嵌套库
 * 缺失时 core 仍能加载（只是转换功能不可用）。
 */
public final class ZipTool {

    private ZipTool() {
        throw new UnsupportedOperationException();
    }

    /**
     * 把 [zip] 解压到 [target]。
     *
     * <p>会拒绝任何逃出 [target] 的条目名（zip slip）。
     */
    public static void extract(Path zip, Path target) throws IOException {
        Files.createDirectories(target);
        Path root = target.toAbsolutePath().normalize();
        ZipFile file = new ZipFile(zip.toFile());
        try {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.isEmpty()) {
                    continue;
                }
                Path out = root.resolve(name.replace('\\', '/')).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("Zip entry escapes the target directory: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Path parent = out.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                InputStream in = file.getInputStream(entry);
                try {
                    OutputStream os = new BufferedOutputStream(Files.newOutputStream(out));
                    try {
                        PathIo.copyStream(in, os);
                    } finally {
                        os.close();
                    }
                } finally {
                    in.close();
                }
            }
        } finally {
            file.close();
        }
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
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
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
