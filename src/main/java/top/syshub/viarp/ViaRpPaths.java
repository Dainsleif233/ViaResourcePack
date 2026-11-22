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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

import net.fabricmc.loader.api.FabricLoader;

/**
 * mod 用到的所有目录。
 *
 * <p>唯一依赖的 Fabric API 是 {@code FabricLoader.getGameDir()}——core 不引用任何
 * {@code net.minecraft} 类型。
 *
 * <p>默认只在 {@code <gameDir>/config/viaresourcepack/} 写配置；缓存、转换临时目录
 * 和占位包共用资源包目录下的 {@code .viaresourcepack/} 私有根。
 */
public final class ViaRpPaths {

    /** 覆盖资源包目录（测试与整合启动器用）。 */
    private static final String PROPERTY_RESOURCE_PACKS = "viarp.resourcePacksDir";

    /** 保留历史语义：显式设置时覆盖配置、临时目录与缓存的共同根。 */
    private static final String PROPERTY_CACHE_ROOT = "viarp.cacheDir";

    /** 配置目录名（位于 {@code config/} 下）。 */
    private static final String DIRECTORY_NAME = "viaresourcepack";

    private static final String PRIVATE_DIRECTORY_NAME = ".viaresourcepack";
    private static final String OWNER_FILE_NAME = ".owner";
    private static final byte[] OWNER_MARKER = "ViaResourcePack cache v1\n".getBytes(StandardCharsets.UTF_8);

    private static volatile ViaRpPaths instance;

    private final Path gameDir;
    private final Path resourcePacksDir;
    private final Path configRoot;
    private final Path privateRoot;
    private final Path outputRoot;

    private ViaRpPaths(Path gameDir, Path resourcePacksDir, Path defaultConfigRoot, Path rootOverride) {
        this.gameDir = gameDir;
        this.resourcePacksDir = resourcePacksDir;
        this.configRoot = rootOverride == null ? defaultConfigRoot : rootOverride;
        this.privateRoot = resourcePacksDir.resolve(PRIVATE_DIRECTORY_NAME);
        this.outputRoot = rootOverride == null ? privateRoot : rootOverride;
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
        Path rootOverride = cacheOverride != null && !cacheOverride.trim().isEmpty()
                ? Paths.get(cacheOverride.trim()).toAbsolutePath().normalize() : null;
        Path configRoot = gameDir.resolve("config").resolve(DIRECTORY_NAME);
        return new ViaRpPaths(gameDir, packs, configRoot, rootOverride);
    }

    /** 配置目录；显式覆盖时也是缓存和临时目录的上级目录。 */
    public Path root() {
        return configRoot;
    }

    /** 配置写入前也要排除与原版资源包目录重叠的配置根。 */
    Path ensureConfigPath(String fileName) throws IOException {
        return ensureConfigRoot().resolve(fileName);
    }

    /** {@code options.txt}——已启用资源包列表的来源（见 {@code PackSelection}）。 */
    public Path optionsFile() {
        return gameDir.resolve("options.txt");
    }

    /** 原版资源包目录；默认位于游戏目录下，也可由启动器覆盖。 */
    public Path resourcePacksDir() {
        return resourcePacksDir;
    }

    /** 转换产物缓存目录。 */
    public Path cacheDir() {
        return outputRoot.resolve("cache");
    }

    /** 转换临时目录；启动时会被清空。 */
    public Path workDir() {
        return outputRoot.resolve("work");
    }

    /**
     * 资源包界面用的占位包目录（见 {@code PackPlaceholder}）；启动时会被清空。
     *
     * <p>与 {@link #cacheDir()} 分开放：占位包不是转换产物，重建代价极低，没有保留价值，
     * 混在缓存里只会干扰按大小裁剪的逻辑。
     */
    public Path stubDir() {
        return outputRoot.resolve("stubs");
    }

    /** 只准备一个同名占位目录，不依赖持久缓存可用，也不清理活跃的占位包。 */
    public Path ensureStubDirectory(Path sourceName) throws IOException {
        if (sourceName == null || sourceName.getRoot() != null || sourceName.getNameCount() != 1
                || sourceName.toString().isEmpty() || !sourceName.equals(sourceName.normalize())
                || "..".equals(sourceName.toString())) {
            throw new IOException("Invalid legacy texture pack placeholder name: " + sourceName);
        }
        Path realStubs = ensureDirectDirectory(stubDir(), ensureOutputRoot());
        Path stub = stubDir().resolve(sourceName);
        ensureDirectDirectory(stub, realStubs);
        return stub;
    }

    /**
     * 启动时调用一次：清掉上次运行残留的临时目录，再建好目录结构。
     *
     * <p>清理只能发生在这里。资源包发现流程一旦开始，{@link #stubDir()} 下的占位包就正
     * 被原版持有，中途清空会让它们凭空消失。
     */
    public void prepare() throws IOException {
        Path realRoot = ensureOutputRoot();
        ensureScratchDirectories(realRoot);
        cleanScratchDirectory(workDir(), realRoot);
        cleanScratchDirectory(stubDir(), realRoot);
        ensureScratchDirectories(realRoot);
        ensureDirectDirectory(cacheDir(), realRoot);
    }

    private static void cleanScratchDirectory(final Path path, Path realRoot) throws IOException {
        final Path realPath = requireDirectDirectory(path, realRoot);
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // Windows 会将 junction 当作目录访问，即使没有 FOLLOW_LINKS。只移除链接本身。
                    if (!dir.toRealPath().equals(realPath.resolve(path.relativize(dir)))) {
                        Files.deleteIfExists(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // 无法清理某些旧文件不影响启动。
        }
    }

    /** 只建目录，不删任何东西——运行期随时可调。 */
    public void ensureDirectories() throws IOException {
        Path realRoot = ensureOutputRoot();
        ensureScratchDirectories(realRoot);
        ensureDirectDirectory(cacheDir(), realRoot);
    }

    private Path ensureConfigRoot() throws IOException {
        // 配置不写到原版资源包目录内。显式覆盖时，cache/work/stubs 也不能包含资源包目录。
        checkPackOverlap(configRoot, resourcePacksDir);
        checkPackOverlap(projectedRealPath(configRoot), projectedRealPath(resourcePacksDir));
        if (!Files.isDirectory(configRoot)) {
            Files.createDirectories(configRoot);
        }
        return configRoot.toRealPath();
    }

    private boolean usesSharedRoot() {
        return configRoot.equals(outputRoot);
    }

    private void checkPackOverlap(Path dataRoot, Path packs) throws IOException {
        if (dataRoot.startsWith(packs) || (usesSharedRoot() && (packs.startsWith(dataRoot.resolve("work"))
                || packs.startsWith(dataRoot.resolve("stubs")) || packs.startsWith(dataRoot.resolve("cache"))))) {
            throw new IOException("ViaResourcePack data directories overlap the resource pack directory: " + packs);
        }
    }

    private static Path projectedRealPath(Path path) throws IOException {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS) && path.getParent() != null) {
            return projectedRealPath(path.getParent()).resolve(path.getFileName());
        }
        return path.toRealPath();
    }

    private void ensureScratchDirectories(Path realRoot) throws IOException {
        ensureDirectDirectory(workDir(), realRoot);
        ensureDirectDirectory(stubDir(), realRoot);
    }

    private Path ensureOutputRoot() throws IOException {
        if (usesSharedRoot()) {
            return ensureConfigRoot();
        }
        // 启动器可指定一个链接作为资源包根；只要求模组私有子目录本身不是链接。
        if (!Files.isDirectory(resourcePacksDir)) {
            Files.createDirectories(resourcePacksDir);
        }
        Path realPacks = resourcePacksDir.toRealPath();
        boolean created = createDirectoryIfAbsent(privateRoot);
        Path realPrivate = requireDirectDirectory(privateRoot, realPacks);
        // 原版只发现直接子项；缺少根 pack.mcmeta 的目录不会成为资源包。点前缀本身不起过滤作用。
        Path metadata = privateRoot.resolve("pack.mcmeta");
        if (!Files.notExists(metadata, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Reserved ViaResourcePack data path contains pack.mcmeta: " + privateRoot);
        }
        Path owner = privateRoot.resolve(OWNER_FILE_NAME);
        if (created) {
            Files.write(owner, OWNER_MARKER, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } else if (!hasOwnerMarker(owner)) {
            throw new IOException("Reserved ViaResourcePack data path is not owned by this mod: " + privateRoot);
        }
        return realPrivate;
    }

    private static boolean hasOwnerMarker(Path owner) throws IOException {
        if (!Files.isRegularFile(owner, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (InputStream in = Files.newInputStream(owner, LinkOption.NOFOLLOW_LINKS)) {
            for (byte expected : OWNER_MARKER) {
                if (in.read() != (expected & 0xff)) {
                    return false;
                }
            }
            return in.read() == -1;
        }
    }

    private static boolean createDirectoryIfAbsent(Path path) throws IOException {
        try {
            Files.createDirectory(path);
            return true;
        } catch (FileAlreadyExistsException ignored) {
            return false;
        }
    }

    private static Path ensureDirectDirectory(Path path, Path realParent) throws IOException {
        createDirectoryIfAbsent(path);
        return requireDirectDirectory(path, realParent);
    }

    private static Path requireDirectDirectory(Path path, Path realParent) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a ViaResourcePack directory: " + path);
        }
        Path realPath = path.toRealPath();
        // NOFOLLOW_LINKS 单独不能可靠识别 Windows junction；也必须确认实际落点。
        if (!realPath.equals(realParent.resolve(path.getFileName()))) {
            throw new IOException("ViaResourcePack directory is not a direct child of " + realParent + ": " + path);
        }
        return realPath;
    }

    /** [path] 是否位于资源包目录内（直接子项，不含更深层级）。 */
    public boolean isInResourcePacksDir(Path path) {
        if (path == null) {
            return false;
        }
        Path parent = path.toAbsolutePath().normalize().getParent();
        return parent != null && parent.equals(resourcePacksDir);
    }

    /** [path] 是否是私有路径或内部产物（避免占位、重复转换及递归拦截）。 */
    public boolean isOwnOutput(Path path) {
        if (path == null) {
            return false;
        }
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(privateRoot) || (usesSharedRoot() && (normalized.startsWith(cacheDir())
                || normalized.startsWith(workDir()) || normalized.startsWith(stubDir())));
    }
}
