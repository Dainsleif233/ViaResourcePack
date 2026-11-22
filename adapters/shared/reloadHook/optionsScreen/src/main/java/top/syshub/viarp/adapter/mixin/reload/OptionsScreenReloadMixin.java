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

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.OptionsScreen;

import top.syshub.viarp.adapter.LocalPackReload;

/** 拦截旧版选项界面保存资源包选择时触发的重载。 */
@Mixin(OptionsScreen.class)
public class OptionsScreenReloadMixin {

    @Redirect(
            method = "updatePackList(Lnet/minecraft/server/packs/repository/PackRepository;)V",
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
