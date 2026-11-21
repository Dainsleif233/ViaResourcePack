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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import top.syshub.viarp.pack.PackFormat;

/** 已实际发行的主线资源包格式；跳号不是转换节点，minor 不能折叠。 */
final class PackFormatHistory {

    static final PackFormat ATLAS_TEXTURES = PackFormat.of(0);
    static final PackFormat LOOSE_TEXTURES = PackFormat.of(0, 1);
    static final PackFormat LAST_CLIENT_TARGET = PackFormat.of(97, 1);

    // 源节点: Mojang 客户端原版 /version.json 或旧版的资源包版本常量。
    // 0.0、0.1 是项目内部顺序标记，不是 Mojang 的 pack_format 声明。
    private static final String FORMATS = "0 0.1 1 2 3 4 5 6 7 8 9 11 12 13 14 15 16 17 "
            + "18 19 20 21 22 24 25 26 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 "
            + "43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64 "
            + "65 65.1 65.2 66 67 68 69 70 70.1 71 72 73 74 75 76 77 78 78.1 79 80 "
            + "81 81.1 82 83 84 85 86 86.1 86.2 87 88 89 90 91 92 93 94 95 96 97 97.1 "
            + "98 99 100 100.1 100.2 101 102 103 104";
    private static final List<PackFormat> RELEASED = released();

    private PackFormatHistory() {
    }

    static boolean knows(PackFormat format) {
        return Collections.binarySearch(RELEASED, format) >= 0;
    }

    static List<PackFormat> route(PackFormat from, PackFormat target) {
        int start = Collections.binarySearch(RELEASED, from);
        int end = Collections.binarySearch(RELEASED, target);
        if (start < 0 || end <= start || target.compareTo(LAST_CLIENT_TARGET) > 0) {
            throw new IllegalArgumentException("No upgrade route from " + from + " to " + target);
        }
        return RELEASED.subList(start + 1, end + 1);
    }

    private static List<PackFormat> released() {
        List<PackFormat> all = new ArrayList<PackFormat>();
        for (String text : FORMATS.split(" ")) {
            int dot = text.indexOf('.');
            PackFormat format = dot < 0 ? PackFormat.of(Integer.parseInt(text))
                    : PackFormat.of(Integer.parseInt(text.substring(0, dot)),
                            Integer.parseInt(text.substring(dot + 1)));
            if (!all.isEmpty() && all.get(all.size() - 1).compareTo(format) >= 0) {
                throw new IllegalStateException("Unordered resource pack format catalog: " + text);
            }
            all.add(format);
        }
        return Collections.unmodifiableList(all);
    }
}
