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
package top.syshub.viarp.adapter;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.convert.ConversionService;
import top.syshub.viarp.convert.ServerPackShortcut;

/**
 * 各版本 mixin 与 core 之间的唯一桥。
 *
 * <p>mixin 里只允许出现对本类的静态调用：这样七个适配器族的 mixin 都是「取参数 →
 * 调这里 → 用返回值替换参数」的三行代码，行为完全一致，也不会在混淆环境里
 * 引入额外的类引用。
 *
 * <p>所有方法都吞掉异常并原样返回入参——拦截点在原版调用栈上，抛出去会直接崩掉
 * 资源加载。
 */
public final class ViaRpHooks {

    private ViaRpHooks() {
        throw new UnsupportedOperationException();
    }

    /**
     * 本地资源包（{@code resourcepacks/} 目录）。
     *
     * <p>只处理该目录的直接子项，因此数据包与世界内置包会原样放行。
     */
    public static Path localPack(Path source) {
        try {
            return ConversionService.get().resolveLocal(source);
        } catch (Throwable failure) {
            logAndIgnore(source, failure);
            return source;
        }
    }

    /** {@link #localPack(Path)} 的 {@link File} 版本（1.19.2 及更早的 hook 用）。 */
    public static File localPack(File source) {
        try {
            return ConversionService.get().resolveLocal(source);
        } catch (Throwable failure) {
            logAndIgnore(source, failure);
            return source;
        }
    }

    /** File 家族只在原版预筛选时借用占位路径，不改变列出的原始 File。 */
    public static File legacyFolderForListing(File source) {
        try {
            return ConversionService.get().resolveLegacyFolderForListing(source);
        } catch (Throwable failure) {
            logAndIgnore(source, failure);
            return source;
        }
    }

    /** 服务器下发或世界捆绑的资源包。 */
    public static Path serverPack(Path source) {
        try {
            return ConversionService.get().resolve(source, ConversionService.PackKind.SERVER);
        } catch (Throwable failure) {
            logAndIgnore(source, failure);
            return source;
        }
    }

    /** 原版已经下载到本地的资源包列表；转换不改变其顺序。 */
    public static List<Path> serverPacks(List<Path> sources) {
        try {
            return ConversionService.get().resolveServerPacks(sources);
        } catch (Throwable failure) {
            ViaRpLog.error("Could not prepare the downloaded resource pack batch; using original paths", failure);
            return sources;
        }
    }

    /**
     * {@link #serverPack(Path)} 的 {@link File} 版本（1.20.2 及更早的 hook 用）。
     *
     * <p>先看 {@link ServerPackShortcut}：如果这个文件是被短路顶替掉的（原版以为它存在，
     * 其实我们跳过了下载），直接返回缓存里的转换产物；否则走正常的转换流程。
     */
    public static File serverPack(File source) {
        try {
            Path substitute = ServerPackShortcut.takeSubstitute(source);
            if (substitute != null) {
                return substitute.toFile();
            }
            return ConversionService.get().resolve(source, ConversionService.PackKind.SERVER);
        } catch (Throwable failure) {
            logAndIgnore(source, failure);
            return source;
        }
    }

    /**
     * 记下服务器声明的资源包内容哈希（1.14.4 – 1.20.2 的
     * {@code downloadAndSelectResourcePack} 里那次 {@code SHA1.matcher(hash)}）。
     *
     * <p>调用点用 {@code @Redirect} 接管，所以这里必须原样把 {@code matcher} 造出来还给原版。
     */
    public static Matcher noteServerPackHash(Pattern pattern, CharSequence hash) {
        try {
            ServerPackShortcut.noteDeclaredHash(hash);
        } catch (Throwable failure) {
            ViaRpLog.error("Could not record the declared server pack hash", failure);
        }
        return pattern.matcher(hash);
    }

    /**
     * 接管 1.14.4 – 1.20.2 里那次 {@code file.exists()}：内容哈希已有转换产物时返回
     * {@code true}，让原版跳过整包下载。
     */
    public static boolean serverPackExists(File file) {
        try {
            return ServerPackShortcut.shouldSkipDownload(file, file.exists());
        } catch (Throwable failure) {
            ViaRpLog.error("Server pack download shortcut failed for {}; downloading normally", file, failure);
            return file.exists();
        }
    }

    /** 这个文件是否被短路顶替掉了（此时原版的 {@code checkHash} 必须跳过）。 */
    public static boolean isServerPackSubstituted(File file) {
        try {
            return ServerPackShortcut.wasSubstituted(file);
        } catch (Throwable failure) {
            ViaRpLog.error("Could not check the server pack shortcut for {}", file, failure);
            return false;
        }
    }

    private static void logAndIgnore(Object source, Throwable failure) {
        ViaRpLog.error("ViaResourcePack hook failed for {}; loading it unchanged", source, failure);
    }
}
