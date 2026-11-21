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
package top.syshub.viarp.adapter.mixin.server;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig;

import top.syshub.viarp.adapter.ViaRpHooks;

/**
 * MC 1.20.3 – 26.3 的服务器资源包拦截点。
 *
 * <p>1.20.3 起服务器包改由 {@code ServerPackManager} + {@code DownloadQueue} 管理，
 * 而 {@code DownloadedPackSource.loadRequestedPacks(List&lt;IdAndPath&gt;)} 是
 * 「即将为每个包构造 {@code FilePackResources} 之前」的那一刻。
 *
 * <p>选这里而不是 {@code pushPack} 或下载回调，关键在于**哈希缓存命中**：
 * {@code DownloadQueue} 发现 {@code downloads/} 里已有匹配 SHA-1 的文件时不会走网络回调，
 * 但一定会把该文件的路径塞进 {@code IdAndPath} 交到这个方法。因此新下载与缓存命中两条
 * 路径都被覆盖。单人世界的捆绑包（{@code pushLocalPack}）走的也是同一个方法。
 *
 * <p>该方法与 {@code IdAndPath} 的签名从 1.20.3 到 26.3 一致
 * （{@code (Ljava/util/List;)Ljava/util/List;}，记录只有 {@code id} / {@code path} 两个组件）。
 */
@Mixin(DownloadedPackSource.class)
public class DownloadedPackSourceMixin {

    @ModifyVariable(
            method = "loadRequestedPacks(Ljava/util/List;)Ljava/util/List;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private List<PackReloadConfig.IdAndPath> viarp$convertServerPacks(List<PackReloadConfig.IdAndPath> requested) {
        if (requested == null || requested.isEmpty()) {
            return requested;
        }
        List<PackReloadConfig.IdAndPath> converted = new ArrayList<>(requested.size());
        boolean changed = false;
        for (PackReloadConfig.IdAndPath entry : requested) {
            Path source = entry.path();
            Path resolved = ViaRpHooks.serverPack(source);
            if (resolved == null || resolved.equals(source)) {
                converted.add(entry);
            } else {
                converted.add(new PackReloadConfig.IdAndPath(entry.id(), resolved));
                changed = true;
            }
        }
        // 没有任何一项变化时返回原列表，避免多一次无意义的拷贝。
        return changed ? converted : requested;
    }
}
