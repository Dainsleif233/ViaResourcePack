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

import java.io.File;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.packs.repository.FolderRepositorySource;

import top.syshub.viarp.adapter.ViaRpHooks;

/**
 * MC 1.14.4 – 1.19.2 的本地资源包拦截点。
 *
 * <p>这一代还是 {@code java.io.File} 时代：{@code FolderRepositorySource.createSupplier(File)}
 * 为目录里每个候选包生成一个 {@code Supplier<PackResources>}，是「拿到文件但还没打开」
 * 的那一刻。1.14.4 – 1.19.2 期间它的签名始终是 {@code (File) -> Supplier}（1.16 加了
 * PackSource 字段，但没动这个方法），所以一个 mixin 覆盖整段区间。
 */
@Mixin(FolderRepositorySource.class)
public class FolderRepositorySourceMixin {

    // 原版先按 pack.mcmeta 预筛选目录；这个方法的参数不参与外层包 ID 计算。
    @ModifyVariable(
            method = "method_14431(Ljava/io/File;)Z",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static File viarp$recognizeLegacyFolder(File file) {
        return ViaRpHooks.legacyFolderForListing(file);
    }

    @ModifyVariable(
            method = "createSupplier(Ljava/io/File;)Ljava/util/function/Supplier;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private File viarp$convertLocalPack(File file) {
        return ViaRpHooks.localPack(file);
    }
}
