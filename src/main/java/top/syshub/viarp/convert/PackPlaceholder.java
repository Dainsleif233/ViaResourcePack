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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.ViaRpPaths;
import top.syshub.viarp.io.PathIo;
import top.syshub.viarp.pack.PackFormat;
import top.syshub.viarp.pack.PackMetadata;

/**
 * 「占位包」：让**尚未启用**的新版本资源包能出现在资源包选择界面里。
 *
 * <h2>要解决的问题</h2>
 *
 * 1.21.9 起资源包可以只写 {@code min_format} / {@code max_format} 而不写
 * {@code pack_format}。而 1.20.1 及更早的 {@code PackMetadataSectionSerializer} 是这样读的：
 *
 * <pre>
 *   int i = GsonHelper.getAsInt(jsonObject, "pack_format");   // 缺键直接抛
 * </pre>
 *
 * 于是老客户端读这类包时抛 {@code JsonSyntaxException: Missing pack_format}，
 * {@code Pack.readPackInfo} 返回 {@code null}，{@code Pack.readMetaAndCreate} 也返回
 * {@code null}，{@code FolderRepositorySource.loadPacks} 干脆不把它交给 consumer——
 * 这个包**根本不进 PackRepository**，界面上连「不兼容」都看不到，它就是不存在。
 *
 * <p>这与「只转换已启用的包」（见 {@code ConversionService.resolveLocal}）撞成死锁：
 * 包必须先被玩家勾选才会转换，但玩家在界面里看不到它，永远没法勾选。
 *
 * <h2>做法</h2>
 *
 * 对这类「本版本读不出元数据、且还没启用」的包，把路径换成一个即时生成的**占位目录**：
 *
 * <pre>
 *   config/viaresourcepack/stubs/&lt;源文件名&gt;/
 *       pack.mcmeta     只有 pack_format（当前版本）+ 原样搬过来的 description
 *       pack.png        源包里有就复制过来，让界面显示真实图标
 * </pre>
 *
 * 于是它以真实的名字、说明、图标出现在「可用」列表里且显示为兼容。玩家勾选并确认后
 * {@code options.txt} 变化，启用判据放行，下一次探测返回的就是**完整转换后**的包。
 *
 * <h2>为什么占位目录必须与源文件同名</h2>
 *
 * 包 id 是 {@code "file/" + path.getFileName()}（1.14.4 至今未变）。占位目录换个名字，
 * 玩家勾选后写进 {@code options.txt} 的就是另一个 id，启用判据再也匹配不上。所以目录名
 * 逐字节等于源文件名——包括 {@code .zip} 后缀。目录带 {@code .zip} 后缀不影响原版判定：
 * {@code PackDetector} 与 1.14.4 的 {@code createSupplier} 都先看 {@code isDirectory()}，
 * 后缀只在「是普通文件」那条分支里才有意义。
 *
 * <h2>占位包只是个空壳</h2>
 *
 * 里面没有任何 {@code assets/}。玩家勾选后原版会先用这个空壳做一次重载（那一瞬间等于没
 * 装任何东西），紧接着 {@code options.txt} 已经更新，下一次探测就换成真货了。刻意不预先
 * 转换：那就回到了「进界面即全量转换」的老问题上。
 */
final class PackPlaceholder {

    private static final String META = "pack.mcmeta";
    private static final String ICON = "pack.png";

    private final ViaRpPaths paths;

    PackPlaceholder(ViaRpPaths paths) {
        this.paths = paths;
    }

    /**
     * 为 [source] 生成（或复用）占位包，返回占位目录；任何失败都返回 [source]。
     *
     * @param source   {@code resourcepacks/} 下的源包（zip 或目录）
     * @param metadata 已解析的源包元数据，用来搬运 {@code description}
     * @param target   当前游戏的 pack_format
     */
    Path create(Path source, boolean isZip, PackMetadata metadata, PackFormat target) {
        Path name = source.getFileName();
        if (name == null) {
            return source;
        }
        try {
            Path stub = paths.stubDir().resolve(name.toString());
            // 每次重新生成：源包可能被换掉，而占位包的成本只有两个小文件。
            PathIo.deleteQuietly(stub);
            Files.createDirectories(stub);            PathIo.writeString(stub.resolve(META),
                    metadata.stub(target, "Not installed yet — converted by ViaResourcePack on enable"));
            copyIcon(source, isZip, stub);
            ViaRpLog.debug("Listing {} through a placeholder pack; it will be converted when enabled", name);
            return stub;
        } catch (IOException e) {
            ViaRpLog.warn("Could not build a placeholder pack for {}; it stays hidden in the pack list", source, e);
            return source;
        }
    }

    /** 把源包里的 {@code pack.png} 复制进占位目录（没有就跳过）。 */
    private static void copyIcon(Path source, boolean isZip, Path stub) {
        try {
            if (isZip) {
                copyIconFromZip(source, stub);
            } else {
                Path icon = source.resolve(ICON);
                if (Files.isRegularFile(icon)) {
                    Files.copy(icon, stub.resolve(ICON));
                }
            }
        } catch (IOException e) {
            // 图标只是好看，缺了原版会用默认图标。
            ViaRpLog.debug("No usable {} in {}", ICON, source, e);
        }
    }

    private static void copyIconFromZip(Path zip, Path stub) throws IOException {
        ZipFile file = new ZipFile(zip.toFile());
        try {
            ZipEntry entry = file.getEntry(ICON);
            if (entry == null) {
                return;
            }
            InputStream in = file.getInputStream(entry);
            try {
                Files.copy(in, stub.resolve(ICON));
            } finally {
                in.close();
            }
        } finally {
            file.close();
        }
    }
}
