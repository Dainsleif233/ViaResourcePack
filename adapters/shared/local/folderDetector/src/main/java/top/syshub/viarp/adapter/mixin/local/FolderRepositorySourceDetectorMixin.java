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

import net.minecraft.server.packs.repository.FolderRepositorySource;

import top.syshub.viarp.adapter.ViaRpHooks;

/**
 * MC 1.19.3 – 1.20.1 的本地资源包拦截点。
 *
 * <p>这三个版本还没有 {@code PackDetector}（1.20.2 才抽出来），探测逻辑是
 * {@code FolderRepositorySource.detectPackResources(Path, boolean)} 这个静态方法，
 * 由 {@code discoverPacks} 对目录里每一项调用一次。语义与后来的 {@code PackDetector}
 * 完全一致，因此同样在 HEAD 改掉 {@code path} 参数即可。
 *
 * <p>{@code boolean} 参数是 {@code isBuiltin}（是否内置包）。这里不看它：内置包不在
 * {@code resourcepacks/} 目录下，{@link ViaRpHooks#localPack(Path)} 的目录检查会放行。
 */
@Mixin(FolderRepositorySource.class)
public class FolderRepositorySourceDetectorMixin {

    @ModifyVariable(
            method = "detectPackResources(Ljava/nio/file/Path;Z)"
                    + "Lnet/minecraft/server/packs/repository/Pack$ResourcesSupplier;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static Path viarp$convertLocalPack(Path path) {
        return ViaRpHooks.localPack(path);
    }
}
