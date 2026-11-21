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
package top.syshub.viarp.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.syshub.viarp.io.PathIo;
import top.syshub.viarp.pack.PackFormat;

/** 缓存键的规范表示；不让外部 SHA-1、索引内容或规则标识直接成为路径。 */
final class CacheKey {

    private static final String SHA1 = "[0-9a-f]{40}";
    private static final String FORMAT_NUMBER = "(?:0|[1-9][0-9]{0,9})";
    private static final Pattern SHA1_PATTERN = Pattern.compile("[0-9A-Fa-f]{40}");
    private static final Pattern CURRENT = Pattern.compile(
            "v2-" + SHA1 + "-((zip|dir)-(" + FORMAT_NUMBER + ")-(" + FORMAT_NUMBER + ")-[0-9a-f]{64})");
    private static final Pattern LEGACY = Pattern.compile(SHA1 + "-(" + FORMAT_NUMBER + ")");

    final boolean zip;
    final String namespace;

    private CacheKey(boolean zip, String namespace) {
        this.zip = zip;
        this.namespace = namespace;
    }

    static String create(String sourceSha1, boolean zip, PackFormat target, String rulesVersion) {
        return "v2-" + normalizeSha1(sourceSha1) + "-" + namespace(zip, target, rulesVersion);
    }

    static String namespace(boolean zip, PackFormat target, String rulesVersion) {
        if (target == null || !target.isKnown()) {
            throw new IllegalArgumentException("A known target pack format is required");
        }
        if (rulesVersion == null || rulesVersion.isEmpty()
                || !StandardCharsets.UTF_8.newEncoder().canEncode(rulesVersion)) {
            throw new IllegalArgumentException("A nonempty UTF-8 conversion rules version is required");
        }
        return (zip ? "zip" : "dir") + "-" + target.major() + "-" + target.minor()
                + "-" + fingerprint(rulesVersion);
    }

    static CacheKey parse(String key) {
        if (key == null) {
            return null;
        }
        Matcher matcher = CURRENT.matcher(key);
        if (!matcher.matches() || !fitsFormatNumber(matcher.group(3)) || !fitsFormatNumber(matcher.group(4))) {
            return null;
        }
        return new CacheKey("zip".equals(matcher.group(2)), matcher.group(1));
    }

    static boolean isLegacy(String key) {
        if (key == null) {
            return false;
        }
        Matcher matcher = LEGACY.matcher(key);
        return matcher.matches() && fitsFormatNumber(matcher.group(1));
    }

    static String normalizeSha1(String sourceSha1) {
        if (sourceSha1 == null || !SHA1_PATTERN.matcher(sourceSha1).matches()) {
            throw new IllegalArgumentException("Source SHA-1 must be 40 hex characters");
        }
        return sourceSha1.toLowerCase(Locale.ROOT);
    }

    private static boolean fitsFormatNumber(String number) {
        try {
            Integer.parseInt(number);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String fingerprint(String rulesVersion) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return PathIo.hex(digest.digest(rulesVersion.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
