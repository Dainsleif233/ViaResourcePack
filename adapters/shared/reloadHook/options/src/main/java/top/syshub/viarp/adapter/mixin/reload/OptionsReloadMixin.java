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
package top.syshub.viarp.adapter.mixin.reload;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;

import top.syshub.viarp.adapter.LocalPackReload;
import top.syshub.viarp.adapter.ViaRpHooks;

/** 连接原版选包确认和启动时的资源包选择。 */
@Mixin(Options.class)
public class OptionsReloadMixin {

    @Shadow protected Minecraft minecraft;
    @Shadow public List<String> resourcePacks;
    @Shadow public List<String> incompatibleResourcePacks;

    @Inject(method = "loadSelectedResourcePacks(Lnet/minecraft/server/packs/repository/PackRepository;)V",
            at = @At("HEAD"))
    private void viarp$keepConvertedPacksSelected(PackRepository repository, CallbackInfo ci) {
        Path packDirectory = this.minecraft.getResourcePackDirectory();
        this.incompatibleResourcePacks.removeIf(id -> {
            if (!this.resourcePacks.contains(id)) {
                return false;
            }
            Pack pack = repository.getPack(id);
            if (pack == null && !id.startsWith("file/")) {
                pack = repository.getPack("file/" + id);
            }
            // 原版遇到已兼容的旧标记会清理它，但也跳过该包一次。只提前清理本模组的产物。
            return pack != null && pack.getPackSource() == PackSource.DEFAULT && pack.getCompatibility().isCompatible()
                    && ViaRpHooks.hasConvertedLocalPack(packDirectory, pack.getId());
        });
    }

    @Redirect(
            method = "updateResourcePacks(Lnet/minecraft/server/packs/repository/PackRepository;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;reloadResourcePacks()"
                            + "Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Void> viarp$submitLocalPackReload(Minecraft game) {
        return LocalPackReload.submit(game);
    }
}
