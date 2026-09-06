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

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.resources.DownloadedPackSource;

import top.syshub.viarp.adapter.ViaRpHooks;

/**
 * MC 1.19.3 – 1.20.2 的服务器资源包拦截点。
 *
 * <p>1.19.3 把 {@code ClientPackSource} 拆成了两个类：内置包发现留在
 * {@code ClientPackSource}，服务器包搬到了新的
 * {@code net.minecraft.client.resources.DownloadedPackSource}（注意不是 1.20.3 起的
 * {@code ...resources.server.DownloadedPackSource}）。类换了名字，方法体本身几乎照搬，
 * 所以这份 mixin 与 {@link ClientPackSourceMixin} 的注入点一一对应：
 *
 * <ul>
 *   <li>{@code setServerPack(File, PackSource)} —— 替换要加载的文件；</li>
 *   <li>{@code Pattern.matcher} / {@code File.exists} / {@code checkHash} —— 让「内容哈希
 *       已有转换产物」时跳过整包重复下载。这一代的下载去重键仍是 **URL 的 SHA-1**，
 *       直到 1.20.3 换用 {@code HttpUtil.downloadFile} 才改成按内容哈希命名缓存文件。</li>
 * </ul>
 *
 * <p>详细理由见 {@link ClientPackSourceMixin} 与 {@code ServerPackShortcut} 的类注释。
 */
@Mixin(DownloadedPackSource.class)
public class DownloadedPackSourceLegacyMixin {

    @ModifyVariable(method = "setServerPack", at = @At("HEAD"), argsOnly = true)
    private File viarp$convertServerPack(File file) {
        return ViaRpHooks.serverPack(file);
    }

    @Redirect(
            method = "downloadAndSelectResourcePack",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/regex/Pattern;matcher(Ljava/lang/CharSequence;)"
                            + "Ljava/util/regex/Matcher;"
            )
    )
    private Matcher viarp$noteServerPackHash(Pattern pattern, CharSequence hash) {
        return ViaRpHooks.noteServerPackHash(pattern, hash);
    }

    @Redirect(
            method = "downloadAndSelectResourcePack",
            at = @At(value = "INVOKE", target = "Ljava/io/File;exists()Z")
    )
    private boolean viarp$skipDownloadIfConverted(File file) {
        return ViaRpHooks.serverPackExists(file);
    }

    @Inject(method = "checkHash", at = @At("HEAD"), cancellable = true)
    private void viarp$skipHashCheckForSubstitute(String hash, File file,
                                                  CallbackInfoReturnable<Boolean> callback) {
        if (ViaRpHooks.isServerPackSubstituted(file)) {
            callback.setReturnValue(Boolean.TRUE);
        }
    }
}
