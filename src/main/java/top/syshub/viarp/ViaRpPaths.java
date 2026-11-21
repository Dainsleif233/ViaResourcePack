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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.fabricmc.loader.api.FabricLoader;

import top.syshub.viarp.io.PathIo;

/**
 * mod 用到的所有目录。
 *
 * <p>唯一依赖的 Fabric API 是 {@code FabricLoader.getGameDir()}——core 不引用任何
 * {@code net.minecraft} 类型。
 *
 * <p>数据目录是 {@code <gameDir>/config/viaresourcepack/}：{@code config/} 是 Fabric
 * 生态约定的 mod 配置位置，整合包与启动器的备份/同步规则也都按这个目录来。
 */
public final class ViaRpPaths {

    /** 覆盖资源包目录（测试与整合启动器用）。 */
    private static final String PROPERTY_RESOURCE_PACKS = "viarp.resourcePacksDir";

    /** 覆盖缓存根目录。 */
    private static final String PROPERTY_CACHE_ROOT = "viarp.cacheDir";

    /** 数据目录名（位于 {@code config/} 下）。 */
    private static final String DIRECTORY_NAME = "viaresourcepack";

    private static volatile ViaRpPaths instance;

    private final Path gameDir;
    private final Path resourcePacksDir;
    private final Path root;

    private ViaRpPaths(Path gameDir, Path resourcePacksDir, Path root) {
        this.gameDir = gameDir;
        this.resourcePacksDir = resourcePacksDir;
        this.root = root;
    }

    public static ViaRpPaths get() {
        ViaRpPaths existing = instance;
        if (existing == null) {
            synchronized (ViaRpPaths.class) {
                existing = instance;
                if (existing == null) {
                    existing = create();
                    instance = existing;
                }
            }
        }
        return existing;
    }

    private static ViaRpPaths create() {
        Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();

        String packsOverride = System.getProperty(PROPERTY_RESOURCE_PACKS);
        Path packs = packsOverride != null && !packsOverride.trim().isEmpty()
                ? Paths.get(packsOverride.trim()).toAbsolutePath().normalize()
                : gameDir.resolve("resourcepacks");

        String cacheOverride = System.getProperty(PROPERTY_CACHE_ROOT);
        Path root = cacheOverride != null && !cacheOverride.trim().isEmpty()
                ? Paths.get(cacheOverride.trim()).toAbsolutePath().normalize()
                : gameDir.resolve("config").resolve(DIRECTORY_NAME);

        return new ViaRpPaths(gameDir, packs, root);
    }

    /** 缓存与工作目录的根。 */
    public Path root() {
        return root;
    }

    /** {@code options.txt}——已启用资源包列表的来源（见 {@code PackSelection}）。 */
    public Path optionsFile() {
        return gameDir.resolve("options.txt");
    }

    /** 转换产物缓存目录。 */
    public Path cacheDir() {
        return root.resolve("cache");
    }

    /** 转换临时目录；启动时会被清空。 */
    public Path workDir() {
        return root.resolve("work");
    }

    /**
     * 资源包界面用的占位包目录（见 {@code PackPlaceholder}）；启动时会被清空。
     *
     * <p>与 {@link #cacheDir()} 分开放：占位包不是转换产物，重建代价极低，没有保留价值，
     * 混在缓存里只会干扰按大小裁剪的逻辑。
     */
    public Path stubDir() {
        return root.resolve("stubs");
    }

    /**
     * 启动时调用一次：清掉上次运行残留的临时目录，再建好目录结构。
     *
     * <p>清理只能发生在这里。资源包发现流程一旦开始，{@link #stubDir()} 下的占位包就正
     * 被原版持有，中途清空会让它们凭空消失。
     */
    public void prepare() throws IOException {
        PathIo.deleteQuietly(workDir());
        PathIo.deleteQuietly(stubDir());
        ensureDirectories();
    }

    /** 只建目录，不删任何东西——运行期随时可调。 */
    public void ensureDirectories() throws IOException {
        Files.createDirectories(cacheDir());
        Files.createDirectories(workDir());
        Files.createDirectories(stubDir());
    }

    /** [path] 是否位于资源包目录内（直接子项，不含更深层级）。 */
    public boolean isInResourcePacksDir(Path path) {
        if (path == null) {
            return false;
        }
        Path parent = path.toAbsolutePath().normalize().getParent();
        return parent != null && parent.equals(resourcePacksDir);
    }

    /** [path] 是否是我们自己产出的缓存文件（避免二次拦截造成递归）。 */
    public boolean isOwnOutput(Path path) {
        if (path == null) {
            return false;
        }
        return path.toAbsolutePath().normalize().startsWith(root);
    }
}
