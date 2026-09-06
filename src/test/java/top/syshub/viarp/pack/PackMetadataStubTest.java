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

import org.junit.jupiter.api.Test;

/** 占位包元数据（{@code stub}）与「本版本能否读出 pack_format」的判定。 */
class PackMetadataStubTest {

    @Test
    void detectsMissingLegacyPackFormat() throws IOException {
        // 1.21.9+ 的写法：只有 min_format / max_format。1.20.1 的解析器读不出 pack_format，
        // 会把整个包从列表里丢掉 —— 这正是需要占位包的情形。
        PackMetadata meta = PackMetadata.parse(
                "{\"pack\":{\"description\":\"new\",\"min_format\":79,\"max_format\":100}}");

        assertThat(meta.hasLegacyPackFormat()).isFalse();
        assertThat(meta.hasFormat()).isTrue();
    }

    @Test
    void detectsPresentLegacyPackFormat() throws IOException {
        PackMetadata meta = PackMetadata.parse("{\"pack\":{\"pack_format\":34,\"description\":\"old\"}}");

        assertThat(meta.hasLegacyPackFormat()).isTrue();
    }

    @Test
    void stubKeepsAPlainTextDescription() throws IOException {
        PackMetadata meta = PackMetadata.parse(
                "{\"pack\":{\"min_format\":79,\"max_format\":100,\"description\":\"Faithful 64x\"}}");

        String stub = meta.stub(PackFormat.of(15), "fallback");
        PackMetadata reparsed = PackMetadata.parse(stub);

        assertThat(reparsed.primaryFormat()).isEqualTo(PackFormat.of(15));
        assertThat(reparsed.description("")).isEqualTo("Faithful 64x");
        // 区间键必须消失，否则老客户端仍读不出 pack_format。
        assertThat(stub).doesNotContain("min_format").doesNotContain("max_format");
    }

    @Test
    void stubKeepsAComponentDescriptionVerbatim() throws IOException {
        // description 可以是一整个文本组件；转成字符串会让原版解析失败。
        PackMetadata meta = PackMetadata.parse(
                "{\"pack\":{\"min_format\":79,\"description\":{\"text\":\"CraftEngine\",\"color\":\"gray\"}}}");

        String stub = meta.stub(PackFormat.of(15), "fallback");

        assertThat(stub).contains("\"text\": \"CraftEngine\"").contains("\"color\": \"gray\"");
        assertThat(PackMetadata.parse(stub).primaryFormat()).isEqualTo(PackFormat.of(15));
    }

    @Test
    void stubFallsBackWhenDescriptionIsMissing() throws IOException {
        PackMetadata meta = PackMetadata.parse("{\"pack\":{\"min_format\":79}}");

        String stub = meta.stub(PackFormat.of(15), "not installed");

        assertThat(PackMetadata.parse(stub).description("")).isEqualTo("not installed");
    }

    @Test
    void stubDropsUnrelatedKeys() throws IOException {
        // overlays 指向占位包里不存在的目录，必须不要复制过来。
        PackMetadata meta = PackMetadata.parse(
                "{\"pack\":{\"min_format\":79,\"description\":\"x\"},"
                        + "\"overlays\":{\"entries\":[{\"directory\":\"ce_overlay_84\",\"min_format\":84}]}}");

        String stub = meta.stub(PackFormat.of(15), "fallback");

        assertThat(stub).doesNotContain("overlays").doesNotContain("ce_overlay_84");
    }
}
