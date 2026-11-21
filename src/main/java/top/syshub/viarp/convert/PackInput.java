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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import top.syshub.viarp.pack.PackFormat;
import top.syshub.viarp.pack.PackMetadata;

/** 只用根元数据或可验证的旧纹理布局识别源；不以文件名或 Minecraft 版本猜测。 */
final class PackInput {

    private static final String METADATA = "pack.mcmeta";
    private static final int METADATA_LIMIT = 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 200_000;
    private static final int MAX_LEGACY_PROBES = 20_000;

    private final PackMetadata metadata;
    private final PackFormat format;

    private PackInput(PackMetadata metadata, PackFormat format) {
        this.metadata = metadata;
        this.format = format;
    }

    static PackInput inspect(Path source, boolean zip) throws IOException {
        return zip ? readZip(source) : readDirectory(source);
    }

    PackMetadata metadata() {
        return metadata;
    }

    PackFormat format() {
        return format;
    }

    boolean isLegacyTexturePack() {
        return metadata == null && format.isKnown();
    }

    boolean canUpgradeTo(PackFormat target) {
        if (metadata == null) {
            return format.isKnown() && format.compareTo(target) < 0;
        }
        // 0.x 仅是旧纹理形态的内部标记，不接受伪造的官方 0.x 声明。
        return format.major() > 0 && metadata.canUpgradeTo(target);
    }

    private static PackInput readZip(Path source) throws IOException {
        try (ZipFile zip = new ZipFile(source.toFile())) {
            ZipEntry declared = zip.getEntry(METADATA);
            if (declared != null) {
                if (declared.isDirectory()) {
                    return unknown();
                }
                try (InputStream input = zip.getInputStream(declared)) {
                    return declared(input);
                }
            }
            if (zip.size() > MAX_ZIP_ENTRIES) {
                return unknown();
            }
            boolean atlas = isZipFile(zip, "terrain.png") || isZipFile(zip, "gui/items.png");
            boolean described = isZipFile(zip, "pack.txt");
            boolean loose = false;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.regionMatches(true, 0, "assets/", 0, "assets/".length())) {
                    return unknown();
                }
                if (!atlas && !loose && !entry.isDirectory() && isStandaloneTexture(name, described)) {
                    loose = true;
                }
            }
            return legacy(atlas, loose);
        }
    }

    private static boolean isZipFile(ZipFile zip, String name) {
        ZipEntry entry = zip.getEntry(name);
        return entry != null && !entry.isDirectory();
    }

    private static PackInput readDirectory(final Path source) throws IOException {
        Path declared = source.resolve(METADATA);
        if (Files.exists(declared, LinkOption.NOFOLLOW_LINKS)) {
            if (!isFile(declared)) {
                return unknown();
            }
            try (InputStream input = Files.newInputStream(declared)) {
                return declared(input);
            }
        }
        if (Files.exists(source.resolve("assets"), LinkOption.NOFOLLOW_LINKS)) {
            return unknown();
        }
        if (isFile(source.resolve("terrain.png")) || (Files.isDirectory(source.resolve("gui"), LinkOption.NOFOLLOW_LINKS)
                && isFile(source.resolve("gui/items.png")))) {
            return legacy(true, false);
        }
        final boolean described = isFile(source.resolve("pack.txt"));
        Path root = described ? source : source.resolve("textures");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return unknown();
        }
        final boolean[] loose = {false};
        Files.walkFileTree(root, Collections.emptySet(), described ? 3 : 2, new SimpleFileVisitor<Path>() {
            private int remaining = MAX_LEGACY_PROBES;

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                String name = source.relativize(file).toString().replace('\\', '/');
                loose[0] = attributes.isRegularFile() && isStandaloneTexture(name, described);
                return loose[0] || --remaining == 0 ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
        });
        return legacy(false, loose[0]);
    }

    private static boolean isStandaloneTexture(String name, boolean described) {
        return LegacyTextureUpgrade.isKnownLooseTexture(name)
                || (described && LegacyTextureUpgrade.isKnownStandaloneTexture(name));
    }

    private static boolean isFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static PackInput declared(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bytes = new byte[8192];
        int count;
        while ((count = input.read(bytes)) != -1) {
            if (buffer.size() + count > METADATA_LIMIT) {
                throw new IOException("pack.mcmeta exceeds " + METADATA_LIMIT + " bytes");
            }
            buffer.write(bytes, 0, count);
        }
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(buffer.toByteArray())).toString();
        PackMetadata metadata = PackMetadata.parse(text);
        return new PackInput(metadata, PackFormatHistory.latestDeclared(metadata.supportedFormats()));
    }

    private static PackInput legacy(boolean atlas, boolean loose) {
        return new PackInput(null, atlas ? PackFormatHistory.ATLAS_TEXTURES
                : loose ? PackFormatHistory.LOOSE_TEXTURES : PackFormat.UNKNOWN);
    }

    private static PackInput unknown() {
        return new PackInput(null, PackFormat.UNKNOWN);
    }
}
