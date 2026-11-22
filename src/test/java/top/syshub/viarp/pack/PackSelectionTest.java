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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 从 {@code options.txt} 判断本地包是否已启用。 */
class PackSelectionTest {

    @Test
    void readsSelectedPacksFromOptions(@TempDir Path temp) throws IOException {
        Path options = write(temp, "lang:zh_cn\n"
                + "resourcePacks:[\"vanilla\",\"fabric\",\"file/Faithful 64x.zip\"]\n"
                + "incompatibleResourcePacks:[]\n");

        PackSelection selection = new PackSelection(options);

        assertThat(selection.isEnabled("Faithful 64x.zip")).isTrue();
        assertThat(selection.isEnabled("Some Other Pack.zip")).isFalse();
    }

    @Test
    void acceptsIdsWithoutTheFilePrefix(@TempDir Path temp) throws IOException {
        // Options.loadSelectedResourcePacks 至今仍兼容不带 file/ 前缀的老写法。
        Path options = write(temp, "resourcePacks:[\"legacy.zip\"]\n");

        assertThat(new PackSelection(options).isEnabled("legacy.zip")).isTrue();
    }

    @Test
    void doesNotConfuseTheIncompatibleList(@TempDir Path temp) throws IOException {
        // incompatibleResourcePacks 以同样的子串结尾，按 contains 匹配会读错这一行。
        Path options = write(temp, "incompatibleResourcePacks:[\"file/old.zip\"]\n"
                + "resourcePacks:[\"vanilla\"]\n");

        assertThat(new PackSelection(options).isEnabled("old.zip")).isFalse();
    }

    @Test
    void treatsMissingFileAsNothingEnabled(@TempDir Path temp) {
        PackSelection selection = new PackSelection(temp.resolve("options.txt"));

        assertThat(selection.isEnabled("anything.zip")).isFalse();
        assertThat(selection.selectedIds()).isEmpty();
    }

    @Test
    void rereadsAfterTheFileChanges(@TempDir Path temp) throws IOException {
        Path options = write(temp, "resourcePacks:[]\n");
        PackSelection selection = new PackSelection(options);
        assertThat(selection.isEnabled("new.zip")).isFalse();

        // 玩家在界面里勾选后原版重写 options.txt，紧接着才触发资源重载。
        Files.write(options, "resourcePacks:[\"file/new.zip\"]\n".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(options, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() + 2000L));

        assertThat(selection.isEnabled("new.zip")).isTrue();

        // 在粗粒度时间戳文件系统或同步恢复后，等长的选择交换可能保留相同时间戳。
        long size = Files.size(options);
        java.nio.file.attribute.FileTime timestamp = Files.getLastModifiedTime(options);
        Files.write(options, "resourcePacks:[\"file/old.zip\"]\n".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(options, timestamp);
        assertThat(Files.size(options)).isEqualTo(size);
        assertThat(Files.getLastModifiedTime(options)).isEqualTo(timestamp);
        assertThat(selection.isEnabled("new.zip")).isFalse();
        assertThat(selection.isEnabled("old.zip")).isTrue();
    }

    @Test
    void survivesAMalformedList(@TempDir Path temp) throws IOException {
        Path options = write(temp, "resourcePacks:not json at all\n");

        assertThat(new PackSelection(options).isEnabled("anything.zip")).isFalse();
    }

    private static Path write(Path dir, String text) throws IOException {
        Path options = dir.resolve("options.txt");
        Files.write(options, text.getBytes(StandardCharsets.UTF_8));
        return options;
    }
}
