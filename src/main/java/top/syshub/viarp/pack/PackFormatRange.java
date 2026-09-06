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

/** 一个闭区间 pack_format 范围（两端都含）。 */
public final class PackFormatRange {

    private final PackFormat min;
    private final PackFormat max;

    public PackFormatRange(PackFormat min, PackFormat max) {
        this.min = min;
        this.max = max;
    }

    public static PackFormatRange single(PackFormat format) {
        return new PackFormatRange(format, format);
    }

    public PackFormat min() {
        return min;
    }

    public PackFormat max() {
        return max;
    }

    /** 只按 major 判定：minor 只代表同 major 内的可选新增特性。 */
    public boolean containsMajor(int major) {
        return major >= min.major() && major <= max.major();
    }

    @Override
    public String toString() {
        return min.equals(max) ? min.toString() : min + "-" + max;
    }
}
