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
package top.syshub.viarp;

import java.lang.reflect.Method;

/**
 * 跨版本日志门面。
 *
 * <p>为什么不直接用 SLF4J：Minecraft 1.14.4 – 1.16.5 的运行期**只有 log4j**，
 * {@code org.slf4j.Logger} 根本不在类路径上；1.17 才引入 slf4j-api。core 是这七个
 * 版本族共用的同一份字节码，任何一处硬引用都会让老版本 {@code NoClassDefFoundError}。
 *
 * <p>因此按 slf4j → log4j → {@code System.out} 的顺序反射解析一次，之后所有调用走
 * 同一个已解析的方法句柄。占位符风格统一用 SLF4J/log4j 都认的 <code>{}</code>；
 * 走到 {@code System.out} 兜底时由本类自己做替换。
 */
public final class ViaRpLog {

    private static final String NAME = "ViaResourcePack";

    private static final Backend BACKEND = resolve();

    private ViaRpLog() {
        throw new UnsupportedOperationException();
    }

    public static void debug(String message, Object... args) {
        BACKEND.log(Level.DEBUG, message, args);
    }

    public static void info(String message, Object... args) {
        BACKEND.log(Level.INFO, message, args);
    }

    public static void warn(String message, Object... args) {
        BACKEND.log(Level.WARN, message, args);
    }

    public static void error(String message, Object... args) {
        BACKEND.log(Level.ERROR, message, args);
    }

    private enum Level {
        DEBUG("debug"),
        INFO("info"),
        WARN("warn"),
        ERROR("error");

        final String slf4jName;

        Level(String slf4jName) {
            this.slf4jName = slf4jName;
        }
    }

    private interface Backend {
        void log(Level level, String message, Object[] args);
    }

    private static Backend resolve() {
        Backend slf4j = tryReflective(
                "org.slf4j.LoggerFactory", "getLogger", "org.slf4j.Logger");
        if (slf4j != null) {
            return slf4j;
        }
        Backend log4j = tryReflective(
                "org.apache.logging.log4j.LogManager", "getLogger", "org.apache.logging.log4j.Logger");
        if (log4j != null) {
            return log4j;
        }
        return new ConsoleBackend();
    }

    /**
     * 反射搭一个日志后端。
     *
     * <p>SLF4J 与 log4j2 的 API 在这里恰好同形：{@code Factory.getLogger(String)} 拿到
     * logger，再调 {@code debug/info/warn/error(String, Object...)}，占位符都是
     * <code>{}</code>，也都支持「最后一个参数是 Throwable 时打印堆栈」。所以一套反射
     * 代码同时覆盖两者。
     */
    private static Backend tryReflective(String factoryClass, String factoryMethod, String loggerClass) {
        try {
            ClassLoader loader = ViaRpLog.class.getClassLoader();
            Class<?> factory = Class.forName(factoryClass, true, loader);
            Class<?> logger = Class.forName(loggerClass, true, loader);
            final Object instance = factory.getMethod(factoryMethod, String.class).invoke(null, NAME);
            if (instance == null) {
                return null;
            }
            final Method[] methods = new Method[Level.values().length];
            for (Level level : Level.values()) {
                methods[level.ordinal()] = logger.getMethod(level.slf4jName, String.class, Object[].class);
            }
            return new Backend() {
                @Override
                public void log(Level level, String message, Object[] args) {
                    try {
                        methods[level.ordinal()].invoke(instance, message, args == null ? new Object[0] : args);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        ConsoleBackend.print(level, message, args);
                    }
                }
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** 两个日志框架都不可用时的兜底（正常游戏环境不会走到）。 */
    private static final class ConsoleBackend implements Backend {

        @Override
        public void log(Level level, String message, Object[] args) {
            print(level, message, args);
        }

        static void print(Level level, String message, Object[] args) {
            StringBuilder out = new StringBuilder();
            out.append('[').append(NAME).append("/").append(level.name()).append("] ");
            Throwable failure = format(out, message, args);
            java.io.PrintStream stream = level == Level.ERROR || level == Level.WARN
                    ? System.err
                    : System.out;
            stream.println(out);
            if (failure != null) {
                failure.printStackTrace(stream);
            }
        }

        /** 展开 <code>{}</code> 占位符，返回末尾多出来的 Throwable（若有）。 */
        private static Throwable format(StringBuilder out, String message, Object[] args) {
            int argIndex = 0;
            int cursor = 0;
            int total = args == null ? 0 : args.length;
            while (cursor < message.length()) {
                int placeholder = message.indexOf("{}", cursor);
                if (placeholder < 0 || argIndex >= total) {
                    out.append(message, cursor, message.length());
                    break;
                }
                out.append(message, cursor, placeholder);
                out.append(args[argIndex++]);
                cursor = placeholder + 2;
            }
            if (args != null && argIndex < args.length && args[args.length - 1] instanceof Throwable) {
                return (Throwable) args[args.length - 1];
            }
            return null;
        }
    }
}
