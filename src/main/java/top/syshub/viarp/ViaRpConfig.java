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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

import top.syshub.viarp.io.PathIo;
import top.syshub.viarp.json.Json;

/**
 * 配置（默认 {@code <gameDir>/config/viaresourcepack/config.json}，显式覆盖时随数据根移动）。
 *
 * <p>没有 GUI：各版本的配置界面 API 差异巨大，为了一个开关引入 7 份 UI 适配不值得。
 * 文件不存在时会写一份带默认值的出来。
 */
public final class ViaRpConfig {

    private static final String FILE_NAME = "config.json";

    private static volatile ViaRpConfig instance;

    private final boolean enabled;
    private final boolean convertLocalPacks;
    private final boolean convertServerPacks;
    private final boolean onlyConvertEnabledPacks;
    private final boolean verbose;

    private ViaRpConfig(boolean enabled, boolean convertLocalPacks, boolean convertServerPacks,
                        boolean onlyConvertEnabledPacks, boolean verbose) {
        this.enabled = enabled;
        this.convertLocalPacks = convertLocalPacks;
        this.convertServerPacks = convertServerPacks;
        this.onlyConvertEnabledPacks = onlyConvertEnabledPacks;
        this.verbose = verbose;
    }

    private static ViaRpConfig defaults() {
        return new ViaRpConfig(true, true, true, true, false);
    }

    public static ViaRpConfig get() {
        ViaRpConfig existing = instance;
        if (existing == null) {
            synchronized (ViaRpConfig.class) {
                existing = instance;
                if (existing == null) {
                    try {
                        existing = loadOrCreate(ViaRpPaths.get().ensureConfigPath(FILE_NAME));
                    } catch (IOException e) {
                        ViaRpLog.warn("Could not prepare ViaResourcePack config path; using defaults", e);
                        existing = defaults();
                    }
                    instance = existing;
                }
            }
        }
        return existing;
    }

    static ViaRpConfig loadOrCreate(Path file) {
        ViaRpConfig defaults = defaults();
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.write(file, defaults.toJson().getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException e) {
                ViaRpLog.warn("Could not write default config to {}", file, e);
            }
            return defaults;
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            ViaRpLog.warn("Not a regular config file: {}; using defaults", file);
            return defaults;
        }
        try {
            Map<String, Object> root = Json.parseObject(PathIo.readString(file));
            return new ViaRpConfig(
                    bool(root, "enabled", defaults.enabled),
                    bool(root, "convertLocalPacks", defaults.convertLocalPacks),
                    bool(root, "convertServerPacks", defaults.convertServerPacks),
                    bool(root, "onlyConvertEnabledPacks", defaults.onlyConvertEnabledPacks),
                    bool(root, "verbose", defaults.verbose));
        } catch (IOException e) {
            ViaRpLog.warn("Malformed {}; using defaults", file, e);
            return defaults;
        }
    }

    /** 总开关。关掉后所有拦截点直接放行。 */
    public boolean enabled() {
        return enabled;
    }

    /** 是否转换 {@code resourcepacks/} 下的本地包。 */
    public boolean convertLocalPacks() {
        return convertLocalPacks;
    }

    /** 是否转换服务器/世界下发的包。 */
    public boolean convertServerPacks() {
        return convertServerPacks;
    }

    /**
     * 是否只转换 {@code options.txt} 里已启用的本地包。
     *
     * <p>默认开。关掉后 {@code resourcepacks/} 里所有版本不匹配的包在**打开资源包界面时**
     * 就会被全部转换——目录里放了很多旧包时，第一次进界面会卡很久。想让未启用的包也提前
     * 转好（比如提前预热缓存）时才关它。
     */
    public boolean onlyConvertEnabledPacks() {
        return onlyConvertEnabledPacks;
    }

    /** 打开转换库自身的详细日志。 */
    public boolean verbose() {
        return verbose;
    }

    private String toJson() {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("enabled", Boolean.valueOf(enabled));
        root.put("convertLocalPacks", Boolean.valueOf(convertLocalPacks));
        root.put("convertServerPacks", Boolean.valueOf(convertServerPacks));
        root.put("onlyConvertEnabledPacks", Boolean.valueOf(onlyConvertEnabledPacks));
        root.put("verbose", Boolean.valueOf(verbose));
        return Json.write(root);
    }

    private static boolean bool(Map<String, Object> root, String key, boolean fallback) {
        Object value = root.get(key);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }
}
