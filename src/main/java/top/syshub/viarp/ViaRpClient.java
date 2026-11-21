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
package top.syshub.viarp;

import java.io.IOException;

import net.fabricmc.api.ClientModInitializer;

import top.syshub.viarp.convert.ConversionService;
import top.syshub.viarp.pack.GameVersion;

/**
 * 客户端入口。
 *
 * <p>只做「预热」：建目录、读配置、探测版本、预热服务，让这些一次性开销发生在
 * 启动阶段而不是第一次资源重载的中途（那时正卡在加载界面上）。真正的拦截由各版本
 * 适配器的 mixin 完成，它们直接调用 {@link ConversionService}。
 */
public final class ViaRpClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ViaRpPaths paths = ViaRpPaths.get();
        try {
            paths.prepare();
        } catch (IOException e) {
            ViaRpLog.error("Could not prepare {}; conversion may fail later", paths.root(), e);
        }

        ViaRpConfig config = ViaRpConfig.get();
        if (!config.enabled()) {
            ViaRpLog.info("ViaResourcePack is disabled in {}", paths.root().resolve("config.json"));
            return;
        }

        // 预先探测游戏版本，初始化转换服务。
        GameVersion.current();
        ConversionService.get();
    }
}
