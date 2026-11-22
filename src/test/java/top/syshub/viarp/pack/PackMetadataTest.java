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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import top.syshub.viarp.json.Json;
import top.syshub.viarp.json.JsonException;

/** 覆盖三代 {@code pack.mcmeta} schema 的解析与改写。 */
class PackMetadataTest {

    @Test
    void readsLegacySingleFormat() throws IOException {
        PackMetadata meta = PackMetadata.parse("{\"pack\":{\"pack_format\":4,\"description\":\"old\"}}");

        assertThat(meta.primaryFormat()).isEqualTo(PackFormat.of(4));
        assertThat(meta.supports(PackFormat.of(4))).isTrue();
        assertThat(meta.supports(PackFormat.of(15))).isFalse();
        assertThat(meta.description("fallback")).isEqualTo("old");
    }

    @Test
    void readsSupportedFormatsAsObject() throws IOException {
        PackMetadata meta = PackMetadata.parse(
                "{\"pack\":{\"pack_format\":18,"
                        + "\"supported_formats\":{\"min_inclusive\":15,\"max_inclusive\":22}}}");

        assertThat(meta.supportedFormats().min()).isEqualTo(PackFormat.of(15));
        assertThat(meta.supportedFormats().max()).isEqualTo(PackFormat.of(22));
        assertThat(meta.supports(PackFormat.of(15))).isFalse();
        assertThat(meta.supports(PackFormat.of(16))).isTrue();
        assertThat(meta.supports(PackFormat.of(22))).isTrue();
        assertThat(meta.supports(PackFormat.of(23))).isFalse();
    }

    @Test
    void readsSupportedFormatsAsArray() throws IOException {
        PackMetadata meta = PackMetadata.parse(
                "{\"pack\":{\"pack_format\":32,\"supported_formats\":[32,46]}}");

        assertThat(meta.supports(PackFormat.of(34))).isTrue();
        assertThat(meta.supports(PackFormat.of(55))).isFalse();
    }

    @Test
    void readsMinorAwareSchema() throws IOException {
        // 1.21.9+：min_format / max_format 可以写成 [major, minor]。
        PackMetadata meta = PackMetadata.parse(
                "{\"pack\":{\"description\":\"new\",\"min_format\":[69,0],\"max_format\":[75,2]}}");

        assertThat(meta.primaryFormat()).isEqualTo(PackFormat.of(69, 0));
        assertThat(meta.supportedFormats().max()).isEqualTo(PackFormat.of(75, 2));
        assertThat(meta.supports(PackFormat.of(75, 2))).isTrue();
        assertThat(meta.supports(PackFormat.of(75, 3))).isFalse();
        assertThat(meta.canUpgradeTo(PackFormat.of(65, 0))).isFalse();
        assertThat(meta.canUpgradeTo(PackFormat.of(75, 1))).isFalse();
        assertThat(meta.canUpgradeTo(PackFormat.of(75, 3))).isTrue();
    }

    @Test
    void omittedMaximumMinorIncludesAllMinorsAndIgnoresOverlayKey() throws IOException {
        // formats 是 overlay 键，不能代替 pack 段的 min_format / max_format。
        PackMetadata meta = PackMetadata.parse(
                "{\"pack\":{\"min_format\":[69],\"max_format\":75,\"formats\":[1,2]}}");

        assertThat(meta.supports(PackFormat.of(75, 2048))).isTrue();
        assertThat(meta.canUpgradeTo(PackFormat.of(75, 2048))).isFalse();
        assertThat(meta.canUpgradeTo(PackFormat.of(76))).isTrue();
        assertThat(meta.supports(PackFormat.of(2))).isFalse();
    }

    @Test
    void treatsMissingFormatAsUnknown() throws IOException {
        PackMetadata meta = PackMetadata.parse("{\"pack\":{\"description\":\"no format\"}}");

        assertThat(meta.hasFormat()).isFalse();
        assertThat(meta.supports(PackFormat.of(15))).isFalse();
    }

    @Test
    void rewriteKeepsOtherKeysAndDropsRangeKeys() throws IOException {
        PackMetadata meta = PackMetadata.parse(
                "{\"pack\":{\"pack_format\":9,\"supported_formats\":[9,12],\"description\":\"keep me\"},"
                        + "\"language\":{\"xx\":{\"name\":\"X\",\"region\":\"Y\",\"bidirectional\":false}}}");

        String rewritten = meta.withFormat(PackFormat.of(97, 1));
        PackMetadata reparsed = PackMetadata.parse(rewritten);

        assertThat(reparsed.primaryFormat()).isEqualTo(PackFormat.of(97, 1));
        assertThat(reparsed.supportedFormats().max()).isEqualTo(PackFormat.of(97, 1));
        assertThat(reparsed.description("")).isEqualTo("keep me");
        // 非 pack 段原样保留。
        assertThat(rewritten).contains("\"language\"").contains("\"bidirectional\"");
        // 新产物只能声明实际转换过的一个格式。
        assertThat(rewritten).doesNotContain("supported_formats").doesNotContain("pack_format");
        assertThat(rewritten).contains("min_format").contains("max_format");
    }

    @Test
    void minimalMetadataIsParseable() throws IOException {
        String text = PackMetadata.minimal(PackFormat.of(46), "converted");
        PackMetadata meta = PackMetadata.parse(text);

        assertThat(meta.primaryFormat()).isEqualTo(PackFormat.of(46));
        assertThat(meta.description("")).isEqualTo("converted");
    }

    @Test
    void toleratesBomCommentsAndTrailingCommas() throws IOException {
        // 现实里的资源包常有这些毛病，原版自己也是 lenient 读的。
        String text = "\uFEFF{\n"
                + "  // 作者留下的注释\n"
                + "  \"pack\": {\n"
                + "    \"pack_format\": 15,\n"
                + "    \"description\": \"messy\",\n"
                + "  },\n"
                + "}";

        PackMetadata meta = PackMetadata.parse(text);
        assertThat(meta.primaryFormat()).isEqualTo(PackFormat.of(15));
        assertThat(meta.description("")).isEqualTo("messy");
    }

    @Test
    void rejectsAmbiguousDeclarationsAndBoundsNesting() {
        assertThatThrownBy(() -> PackMetadata.parse("{\"pack\":{\"pack_format\":3,\"pack_format\":4}}"))
                .isInstanceOf(JsonException.class).hasMessageContaining("duplicate object key");

        StringBuilder input = new StringBuilder("{\"pack\":{\"pack_format\":4},\"extension\":");
        for (int i = 0; i < 120; i++) {
            input.append('[');
        }
        input.append("null");
        for (int i = 0; i < 120; i++) {
            input.append(']');
        }
        input.append('}');
        assertThatThrownBy(() -> PackMetadata.parse(input.toString()))
                .isInstanceOf(JsonException.class).hasMessageContaining("JSON nesting exceeds");
    }

    @Test
    void jsonRoundTripsNestedStructures() throws IOException {
        String source = "{\"a\":[1,2,{\"b\":\"c\"}],\"d\":{\"e\":true,\"f\":null},\"g\":-1.5}";
        Object parsed = Json.parse(source);
        Object reparsed = Json.parse(Json.write(parsed));

        assertThat(Json.write(reparsed)).isEqualTo(Json.write(parsed));

        PackMetadata precise = PackMetadata.parse("{\"pack\":{\"pack_format\":4,\"description\":\"precise\"},"
                + "\"extension\":[9223372036854775809,1e400,1.234567890123456789,-0.0,1e-400]}");
        String rewritten = precise.withFormat(PackFormat.of(5));
        assertThat(rewritten).contains("9223372036854775809", "1E+400", "1.234567890123456789", "-0.0", "1E-400");
        assertThat(PackMetadata.parse(rewritten).primaryFormat()).isEqualTo(PackFormat.of(5));
        assertThat(PackMetadata.parse("{\"pack\":{\"pack_format\":4.00000000000000001,\"description\":\"invalid\"}}")
                .primaryFormat().isKnown()).isFalse();
    }
}
