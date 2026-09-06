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
package top.syshub.viarp.library;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import com.agentdid127.resourcepack.backwards.BackwardsPackConverter;
import com.agentdid127.resourcepack.forwards.ForwardsPackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import top.syshub.viarp.convert.ConverterEngine;
import top.syshub.viarp.pack.PackFormat;

/**
 * 基于 <a href="https://github.com/agentdid127/ResourcePackConverter">ResourcePackConverter</a>
 * （MIT）的 {@link ConverterEngine} 实现。
 *
 * <p>由 core 通过反射按类名加载（见 {@code ConverterEngines}），所以本类必须有一个
 * 公开的无参构造。
 *
 * <p>RPC 的 API 用 **protocol 编号** 而不是 pack_format 表达版本，因此这里先从它内置的
 * {@code protocol.json} 反查出「pack_format major → 该 major 下最高的 protocol」。取最高
 * 是有意的：同一个 pack_format 往往对应多个游戏版本（如 format 9 = 1.19/1.19.1/1.19.2），
 * 取最高才能让所有该跑的转换器都被 {@code shouldConvert} 命中。
 *
 * <p>当前游戏版本超出 RPC 覆盖范围时（如 26.1 = 84、26.2 = 88），
 * {@link #maxSupportedMajor()} 返回它能转到的最高 major（1.21.11 = 75），调度器据此把目标
 * 夹到那里 —— 见 {@code docs/DESIGN.md} §3.3。
 */
public final class RpcConverterEngine implements ConverterEngine {

    /** 打包时由 Gradle 填入的 RPC 版本坐标（见 {@code library/build.gradle.kts}）。 */
    private static final String RPC_VERSION = readBundledVersion();

    private final Gson gson = new Gson();

    /** pack_format major → protocol（同 major 取最大）。 */
    private final TreeMap<Integer, Integer> majorToProtocol = new TreeMap<Integer, Integer>();

    public RpcConverterEngine() {
        buildFormatTable();
        if (majorToProtocol.isEmpty()) {
            throw new IllegalStateException("ResourcePackConverter's protocol.json carries no usable versions");
        }
    }

    @Override
    public void convert(Path workDir, PackFormat from, PackFormat to, boolean verbose) throws IOException {
        int fromProtocol = protocolFor(from.major());
        int toProtocol = protocolFor(to.major());
        if (fromProtocol == toProtocol) {
            return;
        }

        Pack pack = new WorkingDirectoryPack(workDir);

        // RPC 的 Logger 是全局静态的；把它引到 mod 日志上，避免它往 System.out 刷屏。
        PrintStream stream = ConverterLogStream.create();
        Logger.setStream(stream);
        Logger.setDebug(verbose);
        Logger.resetTab();

        try {
            if (toProtocol > fromProtocol) {
                // 旧 → 新。light 参数只影响 1.13 的光照相关转换；"none" 是 CLI 默认值。
                new ForwardsPackConverter(gson, fromProtocol, toProtocol, "none", workDir, verbose, stream)
                        .runPack(pack);
            } else {
                new BackwardsPackConverter(gson, fromProtocol, toProtocol, workDir, verbose, stream)
                        .runPack(pack);
            }
        } catch (RuntimeException e) {
            throw new IOException("ResourcePackConverter failed converting " + from + " -> " + to, e);
        } finally {
            stream.flush();
        }
    }

    @Override
    public int maxSupportedMajor() {
        return majorToProtocol.lastKey().intValue();
    }

    @Override
    public int minSupportedMajor() {
        return majorToProtocol.firstKey().intValue();
    }

    @Override
    public String describe() {
        return "ResourcePackConverter " + RPC_VERSION
                + " (pack_format " + minSupportedMajor() + "-" + maxSupportedMajor() + ")";
    }

    /**
     * 读打包时写入的 RPC 版本。
     *
     * <p>刻意不硬编码：{@link #describe()} 的返回值会进缓存元数据，换 RPC 版本后
     * 旧的转换产物必须被视为过期。读不到时退回 {@code "unknown"}，此时缓存不会
     * 因升级而失效——比让引擎加载失败要好。
     */
    private static String readBundledVersion() {
        java.io.InputStream in = RpcConverterEngine.class.getResourceAsStream("rpc.properties");
        if (in == null) {
            return "unknown";
        }
        try {
            try {
                java.util.Properties properties = new java.util.Properties();
                properties.load(in);
                String value = properties.getProperty("version");
                return value == null || value.isEmpty() ? "unknown" : value;
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return "unknown";
        }
    }

    /** 找到 ≤ [major] 的最大已知 major 对应的 protocol（目标超出上限时会被夹住）。 */
    private int protocolFor(int major) {
        Map.Entry<Integer, Integer> entry = majorToProtocol.floorEntry(Integer.valueOf(major));
        if (entry == null) {
            entry = majorToProtocol.firstEntry();
        }
        return entry.getValue().intValue();
    }

    private void buildFormatTable() {
        for (String version : Util.getSupportedVersions(gson)) {
            JsonObject entry = Util.getVersionObject(gson, version);
            if (entry == null || !entry.has("pack_format") || !entry.has("protocol_version")) {
                continue;
            }
            int major = entry.get("pack_format").getAsInt();
            int protocol = entry.get("protocol_version").getAsInt();
            Integer existing = majorToProtocol.get(Integer.valueOf(major));
            if (existing == null || protocol > existing.intValue()) {
                majorToProtocol.put(Integer.valueOf(major), Integer.valueOf(protocol));
            }
        }
    }
}
