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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import top.syshub.viarp.pack.PackFormat;

/** 真正跑一遍 ResourcePackConverter，确认 relocate 后的 gson/zip4j 与工作目录短路都成立。 */
class RpcConverterEngineTest {

    @Test
    void reportsSupportedRange() {
        RpcConverterEngine engine = new RpcConverterEngine();
        // RPC 63ba2cd402 的 protocol.json 覆盖 1.7.2（format 1）到 1.21.11（format 75）。
        assertThat(engine.minSupportedMajor()).isEqualTo(1);
        assertThat(engine.maxSupportedMajor()).isEqualTo(75);
        // describe() 进缓存元数据，必须带上具体的 RPC 版本，换版本后旧缓存才会失效。
        assertThat(engine.describe()).contains("ResourcePackConverter").contains("1-75");
    }

    @Test
    void convertsForwardsInPlace(@TempDir Path temp) throws IOException {
        Path pack = temp.resolve("pack");
        Files.createDirectories(pack.resolve("assets/minecraft/textures/blocks"));
        write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":3,\"description\":\"test\"}}");
        // 1.12 时代的 lang 文件是 .lang，1.13+ 改成 .json —— 这是 LangConverter 会动的东西。
        Files.createDirectories(pack.resolve("assets/minecraft/lang"));
        write(pack.resolve("assets/minecraft/lang/en_us.lang"), "tile.stone.name=Stone\n");

        RpcConverterEngine engine = new RpcConverterEngine();
        engine.convert(pack, PackFormat.of(3), PackFormat.of(15), false);

        // 目录被原地修改，没有产生 *_converted 兄弟目录。
        assertThat(temp.resolve("pack_converted")).doesNotExist();
        assertThat(temp.resolve("pack_converted.zip")).doesNotExist();
        assertThat(pack).isDirectory();

        // 1.13 起纹理目录从 blocks/ 改名成 block/，是最容易观察的一个转换结果。
        assertThat(pack.resolve("assets/minecraft/textures/block")).isDirectory();
        assertThat(pack.resolve("assets/minecraft/lang/en_us.json")).isRegularFile();
    }

    @Test
    void convertingToTheSameFormatIsANoOp(@TempDir Path temp) throws IOException {
        Path pack = temp.resolve("pack");
        Files.createDirectories(pack);
        write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"test\"}}");

        RpcConverterEngine engine = new RpcConverterEngine();
        assertThatCode(() -> engine.convert(pack, PackFormat.of(15), PackFormat.of(15), false))
                .doesNotThrowAnyException();
    }

    /**
     * 1.21.11（format 75）是 26.1 / 26.2 的转换落点：{@code ConversionService} 把超出
     * 引擎范围的目标夹到 {@link RpcConverterEngine#maxSupportedMajor()}，所以这一步必须
     * 真的能跑通，否则新版本上等于完全没转换。
     */
    @Test
    void convertsUpToTheNewestSupportedFormat(@TempDir Path temp) throws IOException {
        Path pack = temp.resolve("pack");
        // 1.21.11 把日月纹理搬进了 environment/celestial/。
        Files.createDirectories(pack.resolve("assets/minecraft/textures/environment"));
        write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":64,\"description\":\"test\"}}");
        writePng(pack.resolve("assets/minecraft/textures/environment/sun.png"));

        RpcConverterEngine engine = new RpcConverterEngine();
        engine.convert(pack, PackFormat.of(64), PackFormat.of(75), false);

        assertThat(pack.resolve("assets/minecraft/textures/environment/celestial/sun.png")).isRegularFile();
    }

    private static void writePng(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javax.imageio.ImageIO.write(image, "png", file.toFile());
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }
}
