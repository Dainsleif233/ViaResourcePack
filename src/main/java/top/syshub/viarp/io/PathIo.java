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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 文件操作小工具。全部只用 Java 7 NIO，以便编译成 Java 8 字节码。 */
public final class PathIo {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private PathIo() {
        throw new UnsupportedOperationException();
    }

    public static String readString(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return stripBom(new String(bytes, UTF_8));
    }

    public static void writeString(Path path, String text) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, text.getBytes(UTF_8));
    }

    public static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
    }

    /** 递归删除 [path]（不存在则静默返回）。 */
    public static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** 递归删除，失败只记录不抛（用于清理临时目录）。 */
    public static void deleteQuietly(Path path) {
        try {
            deleteRecursively(path);
        } catch (IOException ignored) {
            // 清理失败不影响主流程。
        }
    }

    /** 把 [from] 目录树复制到 [to]。 */
    public static void copyDirectory(final Path from, final Path to) throws IOException {
        if (!Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS)
                || to.toAbsolutePath().normalize().startsWith(from.toAbsolutePath().normalize())) {
            throw new IOException("Invalid pack directory copy: " + from + " -> " + to);
        }
        Files.createDirectory(to);
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(from)) {
                    Files.createDirectory(to.resolve(relativeName(from, dir)));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                requireRegularFile(file, attrs);
                Path target = to.resolve(relativeName(from, file));
                Files.copy(file, target, LinkOption.NOFOLLOW_LINKS);
                requireRegularFile(target, Files.readAttributes(target, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 尽量原子地把 [from] 移到 [to]。
     *
     * <p>先试 ATOMIC_MOVE（同卷时可用），失败则退化为 REPLACE_EXISTING 移动，
     * 再失败（跨卷 + 目录）则复制后删源。
     */
    public static void move(Path from, Path to) throws IOException {
        Path parent = to.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (IOException ignored) {
            // 跨文件系统或平台不支持原子移动，继续走下面的退化路径。
        }
        try {
            deleteRecursively(to);
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (IOException ignored) {
            // 目录跨卷移动会失败，退化为复制。
        }
        if (Files.isDirectory(from)) {
            deleteRecursively(to);
            copyDirectory(from, to);
        } else {
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
        deleteRecursively(from);
    }

    /** 文件用原始字节 SHA-1；目录用排序后的类型、路径与定长内容摘要。 */
    public static String sha1(final Path path) throws IOException {
        final MessageDigest digest = newSha1();
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory()) {
            requireRegularFile(path, attributes);
            updateWithFile(digest, path);
            return hex(digest.digest());
        }
        final List<String> entries = new ArrayList<String>();
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                if (!directory.equals(path)) {
                    entries.add(relativeName(path, directory) + '/');
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                requireRegularFile(file, attrs);
                entries.add(relativeName(path, file));
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(entries);
        MessageDigest perFile = newSha1();
        for (String entry : entries) {
            boolean directory = entry.endsWith("/");
            digest.update((byte) (directory ? 'D' : 'F'));
            digest.update(entry.getBytes(UTF_8));
            digest.update((byte) 0);
            if (!directory) {
                updateWithFile(perFile, path.resolve(entry));
                digest.update(perFile.digest());
            }
        }
        return hex(digest.digest());
    }

    private static String relativeName(Path root, Path entry) throws IOException {
        StringBuilder name = new StringBuilder();
        for (Path segment : root.relativize(entry)) {
            String part = segment.toString();
            if (part.indexOf('\\') >= 0 || part.indexOf(':') >= 0) {
                throw new IOException("Invalid pack path component: " + part);
            }
            if (name.length() != 0) {
                name.append('/');
            }
            name.append(part);
        }
        return name.toString();
    }

    private static void requireRegularFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
            throw new IOException("Not a regular pack resource: " + file);
        }
    }

    private static void updateWithFile(MessageDigest digest, Path file) throws IOException {
        InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
        try {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        } finally {
            in.close();
        }
    }

    private static MessageDigest newSha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // 每个 JRE 都必须实现 SHA-1，走到这里说明运行环境已经坏了。
            throw new IllegalStateException("SHA-1 is unavailable", e);
        }
    }

    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0F];
        }
        return new String(out);
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
    }

    /** 文件大小；目录或读取失败返回 -1。 */
    public static long sizeOrMinusOne(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : -1L;
        } catch (IOException e) {
            return -1L;
        }
    }

    /** 最后修改时间（毫秒）；读取失败返回 -1。 */
    public static long lastModifiedOrMinusOne(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return -1L;
        }
    }
}
