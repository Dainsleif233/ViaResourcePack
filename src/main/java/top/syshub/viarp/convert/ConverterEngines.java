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

import top.syshub.viarp.ViaRpLog;

/**
 * 加载 {@link ConverterEngine} 实现。
 *
 * <p>实现类在嵌套库 mod（{@code viarp-library}）里，用反射按名加载：core 与库分属
 * 两个 Fabric mod，编译期互不可见，但运行期同在 Knot 的类加载器下，按名查找即可。
 */
public final class ConverterEngines {

    private static final String IMPLEMENTATION = "top.syshub.viarp.library.RpcConverterEngine";

    private static volatile ConverterEngine engine;
    private static volatile boolean attempted;

    private ConverterEngines() {
        throw new UnsupportedOperationException();
    }

    /** 转换引擎；嵌套库缺失或初始化失败时返回 {@code null}。 */
    public static ConverterEngine engine() {
        if (!attempted) {
            synchronized (ConverterEngines.class) {
                if (!attempted) {
                    engine = load();
                    attempted = true;
                }
            }
        }
        return engine;
    }

    private static ConverterEngine load() {
        try {
            Class<?> type = Class.forName(IMPLEMENTATION, true, ConverterEngines.class.getClassLoader());
            ConverterEngine loaded = (ConverterEngine) type.getDeclaredConstructor().newInstance();
            ViaRpLog.info("Conversion engine: {}", loaded.describe());
            return loaded;
        } catch (ClassNotFoundException e) {
            ViaRpLog.error("Conversion engine {} is missing — the nested library jar did not load. "
                    + "Resource pack conversion is disabled.", IMPLEMENTATION);
            return null;
        } catch (ReflectiveOperationException e) {
            ViaRpLog.error("Failed to initialise conversion engine {}", IMPLEMENTATION, e);
            return null;
        } catch (LinkageError e) {
            ViaRpLog.error("Conversion engine {} failed to link — the nested library jar is likely "
                    + "incompatible with this Minecraft version", IMPLEMENTATION, e);
            return null;
        }
    }
}
