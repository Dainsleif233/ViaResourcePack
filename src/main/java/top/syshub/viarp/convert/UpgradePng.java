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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** 在 JDK PNG 解码前验证文件、尺寸与布局；不重采样图像，也不覆盖目标。 */
final class UpgradePng {
    private static final int MAX_SIDE = 4096;
    private static final long MAX_PIXELS = (long) MAX_SIDE * MAX_SIDE;
    private static final long MAX_IMAGE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_PNG_AUX_BYTES = 2L * 1024 * 1024;
    private static final int MAX_PNG_CHUNKS = 8192;
    private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;
    private static final int PNG_IHDR = 0x49484452;
    private static final int PNG_IDAT = 0x49444154;
    private static final int PNG_IEND = 0x49454E44;

    private UpgradePng() {
    }

    static Png dimensions(Path path) throws IOException {
        return read(path, null);
    }

    static BufferedImage atlas(Path path, int columns, int rows) throws IOException {
        return read(path, (width, height) -> width % columns == 0 && height % rows == 0).image;
    }

    static BufferedImage scaledSheet(Path path, int baseWidth, int baseHeight) throws IOException {
        return read(path, (width, height) -> width % baseWidth == 0 && height == width / baseWidth * baseHeight).image;
    }

    /** ImageReader 只读第零帧；先验证 PNG 文件大小、两个边长与像素乘积，再准许像素解压。 */
    private static Png read(Path path, PixelLayout layout) throws IOException {
        long length = Files.size(path);
        if (length < 33 || length > MAX_IMAGE_BYTES) {
            throw new IOException("PNG 文件长度无效或超过限制");
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("PNG");
        if (!readers.hasNext()) {
            throw new IOException("JDK 未提供 PNG 读取器");
        }
        ImageReader reader = readers.next();
        try (FileImageInputStream input = new FileImageInputStream(path.toFile())) {
            if (input.readLong() != PNG_SIGNATURE) {
                throw new IOException("不是 PNG 图像");
            }
            checkPngChunks(input, length);
            input.seek(0);
            // 跳过压缩文本等不需要的元数据；读取首个图像的尺寸不解压 IDAT。
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE
                    || (long) width * height > MAX_PIXELS) {
                throw new IOException("PNG 的尺寸超过限制：" + width + "×" + height);
            }
            if (layout != null && !layout.accepts(width, height)) {
                throw new IOException("PNG 尺寸不符合图集布局：" + width + "×" + height);
            }
            return new Png(width, height, layout == null ? null : reader.read(0));
        } catch (RuntimeException e) {
            throw new IOException("PNG 结构无效", e);
        } finally {
            reader.dispose();
        }
    }

    /** 预扣声明长度，避免索引色 PNG 的辅助块让 ImageReader 先按伪造长度分配。 */
    private static void checkPngChunks(FileImageInputStream input, long length) throws IOException {
        long offset = 8;
        long auxiliaryBytes = 0;
        boolean imageData = false;
        for (int chunks = 0; chunks < MAX_PNG_CHUNKS && offset <= length - 12; chunks++) {
            input.seek(offset);
            long size = input.readUnsignedInt();
            int type = input.readInt();
            if (size > length - offset - 12 || (chunks == 0 && (type != PNG_IHDR || size != 13))) {
                throw new IOException("PNG 块长度不正确");
            }
            if (type == PNG_IDAT) {
                imageData = true;
            } else {
                auxiliaryBytes += size;
                if (auxiliaryBytes > MAX_PNG_AUX_BYTES) {
                    throw new IOException("PNG 辅助块超过大小限制");
                }
            }
            offset += size + 12;
            if (type == PNG_IEND && size == 0 && imageData && offset == length) {
                return;
            }
        }
        throw new IOException("PNG 缺少完整结束块或块过多");
    }

    static void writeNew(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream bytes = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes)) {
            if (!ImageIO.write(image, "PNG", stream)) {
                throw new IOException("JDK 未提供 PNG 写入器");
            }
        }
    }

    @FunctionalInterface
    private interface PixelLayout {
        boolean accepts(int width, int height);
    }

    static final class Png {
        final int width;
        final int height;
        final BufferedImage image;

        Png(int width, int height, BufferedImage image) {
            this.width = width;
            this.height = height;
            this.image = image;
        }
    }
}
