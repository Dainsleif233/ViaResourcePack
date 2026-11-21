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
import java.nio.file.StandardCopyOption;

import top.syshub.viarp.ViaRpPaths;
import top.syshub.viarp.io.PathIo;
import top.syshub.viarp.pack.PackFormat;
import top.syshub.viarp.pack.PackMetadata;

/** 只为缺少 pack.mcmeta 的旧纹理包提供可选条目；不会为更高格式的包伪造兼容性。 */
final class PackPlaceholder {

    private final ViaRpPaths paths;

    PackPlaceholder(ViaRpPaths paths) {
        this.paths = paths;
    }

    synchronized Path create(Path source, PackFormat target) throws IOException {
        // 保留原文件名，让老版本按路径文件名生成的 file/ ID 也保持不变。
        Path stub = paths.stubDir().resolve(source.getFileName());
        Path meta = stub.resolve("pack.mcmeta");
        if (Files.isSymbolicLink(stub) || Files.isSymbolicLink(meta)) {
            throw new IOException("Invalid legacy texture pack placeholder path");
        }
        if (!Files.isRegularFile(meta, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(stub);
            Path temporary = Files.createTempFile(stub, ".metadata-", ".tmp");
            try {
                PathIo.writeString(temporary, PackMetadata.minimal(target,
                        "ViaResourcePack: legacy texture pack; upgraded when enabled"));
                Files.move(temporary, meta, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        return stub;
    }
}
