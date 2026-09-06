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
package top.syshub.viarp.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;

import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonAccess;

/**
 * 当前运行版本的资源包格式。
 *
 * <p>数据来自 Minecraft jar 自带的 {@code /version.json}——原版的
 * {@code DetectedVersion} 自己也是这么读的，1.14.4 – 26.2 全都有这个文件。走 classpath
 * 资源而不是 {@code SharedConstants.getCurrentVersion()}，是为了让 core 完全不引用
 * {@code net.minecraft}（跨 7 个适配器族复用同一份 Java 8 字节码）。
 *
 * <p>三代 schema：
 * <pre>
 *   1.14.4 – 1.16.5   "pack_version": 4
 *   1.17   – 1.21.8   "pack_version": { "resource": 32, "data": 41 }
 *   1.21.9 – 26.2     "pack_version": { "resource_major": 88, "resource_minor": 0, ... }
 * </pre>
 */
public final class GameVersion {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String RESOURCE = "/version.json";

    private static volatile GameVersion current;

    private final String id;
    private final int protocol;
    private final PackFormat packFormat;

    private GameVersion(String id, int protocol, PackFormat packFormat) {
        this.id = id;
        this.protocol = protocol;
        this.packFormat = packFormat;
    }

    /** 当前运行的游戏版本；读取失败时返回 {@code packFormat} 未知的实例。 */
    public static GameVersion current() {
        GameVersion detected = current;
        if (detected == null) {
            synchronized (GameVersion.class) {
                detected = current;
                if (detected == null) {
                    detected = detect();
                    current = detected;
                }
            }
        }
        return detected;
    }

    /** 版本 id，例如 {@code "1.21.4"} / {@code "26.2"}；未知时为 {@code "unknown"}。 */
    public String id() {
        return id;
    }

    /** 当前版本的**资源包** pack_format。 */
    public PackFormat packFormat() {
        return packFormat;
    }

    @Override
    public String toString() {
        return id + " (protocol " + protocol + ", resource pack format " + packFormat + ")";
    }

    // ────────────────────────────── 探测 ──────────────────────────────

    private static GameVersion detect() {
        String text = readResource();
        if (text == null) {
            ViaRpLog.warn("Could not read {} from the classpath; "
                    + "resource pack conversion will be disabled", RESOURCE);
            return new GameVersion("unknown", -1, PackFormat.UNKNOWN);
        }
        try {
            Map<String, Object> root = Json.parseObject(text);
            String id = JsonAccess.string(root, "id", "unknown");
            int protocol = JsonAccess.integer(root, "protocol_version", -1);
            PackFormat format = readPackFormat(root);
            GameVersion version = new GameVersion(id, protocol, format);
            ViaRpLog.info("Detected Minecraft {}", version);
            return version;
        } catch (IOException e) {
            ViaRpLog.warn("Malformed {}; resource pack conversion will be disabled", RESOURCE, e);
            return new GameVersion("unknown", -1, PackFormat.UNKNOWN);
        }
    }

    private static PackFormat readPackFormat(Map<String, Object> root) {
        Object raw = root.get("pack_version");
        if (raw instanceof Number || raw instanceof String) {
            // 1.14.4 – 1.16.5：单个整数，资源包与数据包共用。
            int major = JsonAccess.integer(raw, -1);
            return major < 0 ? PackFormat.UNKNOWN : PackFormat.of(major);
        }
        Map<String, Object> pack = JsonAccess.object(root, "pack_version");
        if (pack == null) {
            return PackFormat.UNKNOWN;
        }
        if (JsonAccess.hasKey(pack, "resource_major")) {
            // 1.21.9+：major/minor 分开。
            int major = JsonAccess.integer(pack, "resource_major", -1);
            int minor = JsonAccess.integer(pack, "resource_minor", 0);
            return major < 0 ? PackFormat.UNKNOWN : PackFormat.of(major, Math.max(0, minor));
        }
        // 1.17 – 1.21.8：resource / data 两个整数。
        int major = JsonAccess.integer(pack, "resource", -1);
        return major < 0 ? PackFormat.UNKNOWN : PackFormat.of(major);
    }

    private static String readResource() {
        // 优先用 MC 的类加载器：Fabric 下 mod 与游戏同在 Knot 的类加载器里，所以
        // 本类自己的 getResourceAsStream 就能看到游戏 jar 的根资源；再兜底试一次
        // 系统类加载器，覆盖某些整合启动器把游戏放在不同层的情况。
        InputStream in = GameVersion.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            ClassLoader loader = GameVersion.class.getClassLoader();
            if (loader != null) {
                in = loader.getResourceAsStream(RESOURCE.substring(1));
            }
        }
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), UTF_8);
        } catch (IOException e) {
            ViaRpLog.warn("Failed to read {}", RESOURCE, e);
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // 关闭失败无所谓，内容已经读到了。
            }
        }
    }
}
