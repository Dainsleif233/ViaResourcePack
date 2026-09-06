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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import top.syshub.viarp.ViaRpLog;

/**
 * 把 RPC 的 {@code Logger}（一个全局静态 {@link PrintStream}）引到 mod 的日志上。
 *
 * <p>RPC 默认往 {@code System.out} 打印，转换大包时会刷出上千行，直接淹掉游戏日志。
 * 这里按行缓冲、逐行转发到 debug 级别，既保留了排查信息，也不污染正常日志。
 *
 * <p>走 {@link ViaRpLog} 而不是直接用 SLF4J：MC 1.16.5 及更早的运行期里没有
 * slf4j-api，只有 log4j。
 */
final class ConverterLogStream extends OutputStream {

    private final ByteArrayOutputStream line = new ByteArrayOutputStream(256);

    private ConverterLogStream() {
    }

    static PrintStream create() {
        try {
            return new PrintStream(new ConverterLogStream(), true, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // 每个 JRE 都必须支持 UTF-8。
            throw new IllegalStateException("UTF-8 is unavailable", e);
        }
    }

    @Override
    public void write(int b) {
        if (b == '\n') {
            flushLine();
            return;
        }
        if (b == '\r') {
            return;
        }
        line.write(b);
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        flushLine();
    }

    private void flushLine() {
        if (line.size() == 0) {
            return;
        }
        String text;
        try {
            text = new String(line.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            text = new String(line.toByteArray());
        }
        line.reset();
        String trimmed = text.trim();
        if (!trimmed.isEmpty()) {
            ViaRpLog.debug("[converter] {}", trimmed);
        }
    }
}
