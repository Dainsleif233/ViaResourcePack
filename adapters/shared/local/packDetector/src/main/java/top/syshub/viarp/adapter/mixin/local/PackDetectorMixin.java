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
package top.syshub.viarp.adapter.mixin.local;

import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.packs.repository.PackDetector;

import top.syshub.viarp.adapter.ViaRpHooks;

/**
 * MC 1.20.2 – 26.3 的本地资源包拦截点。
 *
 * <p>{@code PackDetector.detectPackResources} 是 1.20.2 之后所有本地包（资源包、
 * 数据包、世界内置包）的唯一漏斗，签名 {@code (Path, List) -> Object} 从 1.20.2 一直到
 * 26.3 一致（混淆版本用映射核对，26.1–26.3 用原名核对）。在这里改掉
 * {@code path} 参数，原版后续的 symlink 校验、zip/目录判定、{@code pack.mcmeta} 读取、
 * {@code PackResources} 构造全都作用在转换后的文件上，其余逻辑一行不动。
 *
 * <p>因为这个漏斗不区分资源包与数据包，{@link ViaRpHooks#localPack(Path)} 里用
 * 「父目录必须是客户端 resourcepacks 目录」把数据包排除掉。
 */
@Mixin(PackDetector.class)
public class PackDetectorMixin {

    @ModifyVariable(
            method = "detectPackResources(Ljava/nio/file/Path;Ljava/util/List;)Ljava/lang/Object;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Path viarp$convertLocalPack(Path path) {
        return ViaRpHooks.localPack(path);
    }
}
