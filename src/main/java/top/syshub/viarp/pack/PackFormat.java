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

/**
 * 资源包格式版本号。
 *
 * <p>1.21.9 之前只有一个整数（major）；1.21.9 起 {@code pack.mcmeta} 允许写
 * {@code [major, minor]}，同一 major 内 minor 只表示「新增了可选特性」，
 * 因此**兼容性判定只看 major**，minor 仅用于写回元数据时保真。
 */
public final class PackFormat implements Comparable<PackFormat> {

    public static final PackFormat UNKNOWN = new PackFormat(-1, 0);

    private final int major;
    private final int minor;

    public PackFormat(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public static PackFormat of(int major) {
        return new PackFormat(major, 0);
    }

    public static PackFormat of(int major, int minor) {
        return new PackFormat(major, minor);
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public boolean isKnown() {
        return major >= 0;
    }

    @Override
    public int compareTo(PackFormat other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        return Integer.compare(minor, other.minor);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PackFormat)) {
            return false;
        }
        PackFormat other = (PackFormat) obj;
        return major == other.major && minor == other.minor;
    }

    @Override
    public int hashCode() {
        return major * 31 + minor;
    }

    @Override
    public String toString() {
        return minor == 0 ? Integer.toString(major) : major + "." + minor;
    }
}
