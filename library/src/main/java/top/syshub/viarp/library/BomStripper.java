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
package top.syshub.viarp.library;

import java.io.IOException;
import java.nio.file.Path;

import com.agentdid127.resourcepack.library.utilities.BomDetector;

/**
 * 清掉工作目录里文本文件的 UTF-8 BOM。
 *
 * <p>为什么必须做：RPC 的 JSON 读取遇到 BOM 会抛异常，它原本在 {@code Pack.Handler.setup()}
 * 里顺带处理，而我们替换掉了那一步（见 {@link WorkingDirectoryPack}）。扩展名列表与
 * RPC 原版保持一致。
 */
final class BomStripper {

    private static final String[] EXTENSIONS = {".txt", ".json", ".mcmeta", ".properties", ".lang"};

    private BomStripper() {
        throw new UnsupportedOperationException();
    }

    static void strip(Path root) throws IOException {
        new BomDetector(root.toString(), EXTENSIONS).removeBOMs();
    }
}
