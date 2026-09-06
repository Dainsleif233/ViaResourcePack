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

import com.agentdid127.resourcepack.library.pack.Pack;

/**
 * 把一个**已经解压好**的目录直接当成 RPC 的工作目录。
 *
 * <p>原版 {@link Pack} 的语义是「原包在 {@code path}，工作副本在
 * {@code <path>_converted}」，{@code Handler.setup()} 负责复制、{@code ZipPack.Handler.finish()}
 * 负责重新打包。ViaResourcePack 自己管解压、打包与缓存（要控制输出位置、要产出可复现
 * 的 zip），所以这里把两端都短路掉：
 *
 * <ul>
 *   <li>{@code getWorkingPath()} 直接返回传入的目录，转换器就地修改；</li>
 *   <li>{@code Handler.setup()} 只做 BOM 清理（RPC 原版 setup 里那步是必要的，
 *       它的 JSON 读取遇到 BOM 会失败），不复制；</li>
 *   <li>{@code Handler.finish()} 什么都不做。</li>
 * </ul>
 */
final class WorkingDirectoryPack extends Pack {

    WorkingDirectoryPack(Path workDir) {
        super(workDir);
    }

    /**
     * 工作目录就是传入的目录本身。
     *
     * <p>不引入新字段是有意的：父类构造器会调用 {@link #createHandler()}，此时子类
     * 字段还没赋值；改读父类已经存好的 {@code path}（即 {@link #getOriginalPath()}）
     * 就没有这个初始化顺序问题。
     */
    @Override
    public Path getWorkingPath() {
        return getOriginalPath();
    }

    @Override
    public Pack.Handler createHandler() {
        return new Handler(this);
    }

    /** 不复制、不打包，只清 BOM。 */
    private static final class Handler extends Pack.Handler {

        Handler(Pack pack) {
            super(pack);
        }

        @Override
        public void setup() throws IOException {
            BomStripper.strip(pack.getWorkingPath());
        }

        @Override
        public void finish() {
            // 打包与落盘由 ViaResourcePack 的缓存层负责。
        }
    }
}
