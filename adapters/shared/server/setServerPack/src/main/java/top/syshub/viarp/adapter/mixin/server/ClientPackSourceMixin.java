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

import net.minecraft.client.resources.ClientPackSource;

import top.syshub.viarp.adapter.ViaRpHooks;

/**
 * MC 1.14.4 – 1.19.2 的服务器资源包拦截点。
 *
 * <p>四个注入点都落在同一个类上，分两件事：
 *
 * <h2>1. 替换要加载的文件（{@code setServerPack}）</h2>
 *
 * {@code setServerPack(File, ...)} 是「下载完成 / 哈希命中缓存之后，把本地文件挂成 Pack
 * 之前」的那一刻。选它而不是下载回调，正是为了覆盖哈希命中缓存的路径：那条路不走网络
 * 回调，但一定会走到这里。
 *
 * <p>1.14.4/1.15 的签名是 {@code (File)}，1.16 起多了一个 {@code PackSource} 参数。因为用的
 * 是 {@code method = "setServerPack"}（不带描述符），Mixin 会按方法名匹配唯一的重载，两代
 * 签名都能命中；{@code argsOnly = true} + {@code File} 类型确保只改到那个文件参数。
 *
 * <h2>2. 跳过重复下载（其余三个）</h2>
 *
 * 这一代的下载去重键是 **URL 的 SHA-1**（{@code new File(serverPackDir, sha1(url))}），
 * 不是内容哈希。URL 带 token 的服务器每次都让缓存落空，同一份包被反复整包下载。
 * 三个注入点合起来把「内容哈希已经转换过」这件事告诉原版：
 *
 * <ul>
 *   <li>{@code Pattern.matcher} → 记下服务器声明的内容哈希（这次调用是
 *       {@code SHA1.matcher(hash)}，全区间恰好一次，且在 {@code exists()} 之前）；</li>
 *   <li>{@code File.exists} → 命中缓存时返回 {@code true}，原版据此跳过 HTTP 下载；</li>
 *   <li>{@code checkHash} → 跳过校验并直接返回 {@code true}：那个 URL 键文件根本不存在，
 *       真去算它的哈希只会失败。</li>
 * </ul>
 *
 * <p>被重定向的两个方法都是 JDK 方法（{@code (CharSequence)Matcher}、{@code ()Z}），
 * 与映射表无关；{@code checkHash} 按方法名匹配。这两个调用点在
 * {@code downloadAndSelectResourcePack} 体内**各只出现一次**、且 {@code matcher} 恒在
 * {@code exists} 之前——已在 1.14.4 / 1.15.2 / 1.16.5 / 1.17.1 / 1.18.2 / 1.19 / 1.19.2 /
 * 1.19.3 / 1.19.4 / 1.20.1 / 1.20.2 的混淆 jar 上逐一反汇编确认，所以不需要
 * {@code ordinal}。
 */
@Mixin(ClientPackSource.class)
public class ClientPackSourceMixin {

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
