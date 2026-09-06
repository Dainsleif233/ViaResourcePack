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
import java.nio.file.Path;

import top.syshub.viarp.pack.PackFormat;

/**
 * 真正干转换活的引擎。
 *
 * <p>core 只知道这个接口；实现（基于 ResourcePackConverter）住在嵌套库 mod 里，
 * 由 {@link ConverterEngines} 反射加载。这样 core 不需要在编译期看到 RPC 的类型，
 * 嵌套库缺失时 core 也仍能加载（只是转换功能停用）。
 */
public interface ConverterEngine {

    /**
     * 就地把 [workDir]（一个已解压的资源包目录）从 [from] 转换到 [to]。
     *
     * @param workDir 已解压的资源包目录，含 {@code pack.mcmeta}；方法允许原地修改它
     * @param from    源包声明的 pack_format
     * @param to      目标 pack_format
     * @param verbose 是否打开转换器自身的详细日志（对应 {@code config.json} 的 {@code verbose}）
     * @throws IOException 转换失败
     */
    void convert(Path workDir, PackFormat from, PackFormat to, boolean verbose) throws IOException;

    /**
     * 该引擎能处理的最高 pack_format major。
     *
     * <p>目标版本超出这个上限时，调度器会把 {@code to} 夹到这里，然后只改写
     * {@code pack.mcmeta}——见 {@code docs/DESIGN.md} §3.3。
     */
    int maxSupportedMajor();

    /** 该引擎能处理的最低 pack_format major。 */
    int minSupportedMajor();

    /** 引擎名（写进缓存元数据，便于日后按引擎版本失效缓存）。 */
    String describe();
}
