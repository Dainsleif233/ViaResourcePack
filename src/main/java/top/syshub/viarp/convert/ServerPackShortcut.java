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

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import top.syshub.viarp.ViaRpLog;

/**
 * 服务器资源包「跳过重复下载」的短路逻辑（仅 MC 1.14.4 – 1.20.2 需要）。
 *
 * <h2>问题</h2>
 *
 * 1.14.4 – 1.20.2 的 {@code downloadAndSelectResourcePack} 是这样决定「要不要下载」的：
 *
 * <pre>
 *   String key = sha1(url.toString());          // 注意：URL 的哈希，不是内容的哈希
 *   File file = new File(serverPackDir, key);
 *   if (file.exists()) { ...复用... } else { ...HTTP 下载... }
 *   // 下载/复用之后才 checkHash(declaredHash, file) 校验内容
 * </pre>
 *
 * 也就是说本地去重键是 **URL**。很多服务器（CraftEngine、ItemsAdder 之类的插件）每次
 * 下发的 URL 带 token 或时间戳，于是 URL 一变、缓存必然落空，同一份内容被反复整包下载。
 * 实测在一个测试实例的 {@code server-resource-packs/} 里躺着 4 个文件名不同、内容 SHA-1
 * 完全相同的包。
 *
 * <p>1.20.3+ 换成了 {@code HttpUtil.downloadFile}，缓存文件名直接用**声明的内容哈希**
 * （{@code cachedFilePath(dir, hashCode)} + {@code checkExistingFile}），URL 变了也照样命中，
 * 所以那一代不需要本类。
 *
 * <h2>做法</h2>
 *
 * 服务器在下发包时**已经告诉了我们内容哈希**，而我们的缓存键正是「源内容 SHA-1 + 目标
 * major」。所以只要那个哈希对应的转换产物还在缓存里，这次下载就完全没有必要：
 *
 * <pre>
 *   Pattern.matcher(hash)  ← 记下服务器声明的哈希（每个版本恰好一次调用，且在下面之前）
 *   file.exists()          ← 缓存命中时返回 true，跳过 HTTP 下载
 *   checkHash(hash, file)  ← 返回 true：那个 URL 键文件根本不存在，不能真去算它的哈希
 *   setServerPack(file)    ← 换成缓存里的转换产物（复用已有的 ModifyVariable 钩子）
 * </pre>
 *
 * 三个拦截点的方法描述符只含 JDK 类型（{@code (CharSequence)Matcher}、{@code ()Z}、
 * {@code (String,File)Z}），在 1.14.4 – 1.20.2 全区间一字未变，所以两个遗留适配器族各加
 * 同一份代码即可，不必按签名再拆族。
 *
 * <h2>为什么用两种不同的传递方式</h2>
 *
 * {@code Pattern.matcher} 与 {@code file.exists()} 在同一次方法调用内、同一个线程上，
 * 用 {@link ThreadLocal} 传递最省事。但 {@code checkHash} 与 {@code setServerPack} 是在
 * {@code thenCompose} 的回调里跑的，线程已经换成 Downloader 线程，ThreadLocal 传不过去。
 * 那两步能拿到的稳定身份是**那个 URL 键文件路径本身**，所以改用一张以它为键的映射表。
 */
public final class ServerPackShortcut {

    /** 同一次 {@code downloadAndSelectResourcePack} 调用内，服务器声明的内容哈希。 */
    private static final ThreadLocal<String> DECLARED_HASH = new ThreadLocal<String>();

    /** URL 键文件的绝对路径 → 我们准备顶替上去的转换产物。 */
    private static final Map<String, Path> SUBSTITUTES = new ConcurrentHashMap<String, Path>();

    /**
     * 同时在场的服务器包不会多（一个连接一个），这个上限只是防止异常路径下
     * （比如某个版本的控制流让 {@code setServerPack} 一直不被调用）无界增长。
     */
    private static final int MAX_ENTRIES = 32;

    private ServerPackShortcut() {
        throw new UnsupportedOperationException();
    }

    /** 记下服务器声明的内容哈希；空串表示服务器没给哈希（原版会跳过校验）。 */
    public static void noteDeclaredHash(CharSequence hash) {
        DECLARED_HASH.set(hash == null ? null : hash.toString());
    }

    /**
     * 该不该跳过下载。
     *
     * @param exists 原版 {@code file.exists()} 的真实结果
     * @return 传回给原版的值：{@code true} 表示「本地已有，不用下载」
     */
    public static boolean shouldSkipDownload(File file, boolean exists) {
        try {
            return decide(file, exists);
        } catch (Throwable failure) {
            // 这里在原版调用栈上，任何异常都必须吞掉，否则连不上服务器。
            ViaRpLog.error("Server pack shortcut failed for {}; falling back to a normal download", file, failure);
            return exists;
        }
    }

    private static boolean decide(File file, boolean exists) {
        String hash = DECLARED_HASH.get();
        DECLARED_HASH.remove();

        if (exists) {
            // 同一个 URL 又来了一次：原版自己的缓存命中，走它的正常流程（包括真实哈希校验）。
            return true;
        }
        if (hash == null || hash.length() != 40) {
            // 服务器没声明哈希（或不是 SHA-1），无从判断内容，只能老实下载。
            return false;
        }

        Path artifact = ConversionService.get().cachedForSourceSha1(hash);
        if (artifact == null) {
            return false;
        }

        prune();
        SUBSTITUTES.put(key(file), artifact);
        ViaRpLog.info("Server pack {} is already converted in the cache; skipping the download", hash);
        return true;
    }

    /** {@code checkHash} 是否应当被跳过（我们顶替了这个文件，它并不存在）。 */
    public static boolean wasSubstituted(File file) {
        return file != null && SUBSTITUTES.containsKey(key(file));
    }

    /**
     * 取出为 [file] 准备的转换产物并消费掉这条记录；没有则返回 {@code null}。
     *
     * <p>由服务器包的 {@code setServerPack} 钩子调用——它是这条短路的终点。
     */
    public static Path takeSubstitute(File file) {
        return file == null ? null : SUBSTITUTES.remove(key(file));
    }

    private static void prune() {
        if (SUBSTITUTES.size() >= MAX_ENTRIES) {
            // 走到这里说明有记录没被 setServerPack 消费掉；整体清掉最省心，
            // 代价只是那些连接退化成正常下载。
            ViaRpLog.debug("Dropping {} stale server pack shortcuts", Integer.valueOf(SUBSTITUTES.size()));
            SUBSTITUTES.clear();
        }
    }

    private static String key(File file) {
        return file.getAbsolutePath();
    }
}
