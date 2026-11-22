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

package top.syshub.viarp.adapter;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import top.syshub.viarp.ViaRpLog;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** 只在客户端线程维护界面；enqueue 是从任意线程返回客户端的入口。 */
final class ReloadUi {
    private static Field pendingReload = findPendingReload();

    private final Minecraft game;
    private final Screen previous;
    private final ProgressScreen screen;
    private boolean dismissAllowed;
    private String title;
    private String line;
    private int percent;
    private int lastWidth;

    private ReloadUi(Minecraft game, Runnable tick, Runnable dismiss) {
        this.game = game;
        this.previous = currentScreen(game);
        this.screen = NativeScreen.create(() -> {
            if (!isCurrent()) return;
            tick.run();
            if (isCurrent() && title != null && widthChanged()) refreshText();
        }, () -> dismissAllowed, () -> {
            if (dismissAllowed && isCurrent()) dismiss.run();
        });
    }

    static void enqueue(Minecraft game, Runnable action) {
        // tell 即使在客户端线程也只入队；execute 有同步执行路径。
        game.tell(action);
    }

    static ReloadUi open(Minecraft game, Runnable tick, Runnable dismiss) {
        ReloadUi ui = new ReloadUi(game, tick, dismiss);
        setScreen(game, ui.screen);
        return ui;
    }

    static boolean chinese(Minecraft game) {
        String code = game.options.languageCode;
        return code != null && (code.equals("zh") || code.startsWith("zh_") || code.startsWith("zh-"));
    }

    boolean isCurrent() {
        return currentScreen(game) == screen;
    }

    boolean vanillaReloadBusy() {
        if (overlay(game) instanceof LoadingOverlay) return true;
        if (pendingReload != null) {
            try {
                // 原版会直接返回这个尚未启动的 reload，而不是启动一次新的加载。
                return pendingReload.get(game) != null;
            } catch (IllegalAccessException e) {
                pendingReload = null;
                warnPendingReload(e);
            }
        }
        return false;
    }

    void show(String title, String line, int percent) {
        this.title = title;
        this.line = line;
        // 原版在 0 时不绘制阶段行，因此最小显示值为 1。
        this.percent = Math.max(1, Math.min(100, percent));
        refreshText();
    }

    void allowDismiss() {
        dismissAllowed = true;
    }

    void close() {
        if (isCurrent()) setScreen(game, previous);
    }

    private boolean widthChanged() {
        return screen.width != lastWidth;
    }

    private void refreshText() {
        lastWidth = screen.width;
        int available = Math.max(0, lastWidth - 16);
        screen.progressStartNoAbort(LiteralText.create(fit(title, available)));
        // 原版在阶段行末尾拼接百分比，给后缀预留宽度。
        int suffix = game.font.width(" " + percent + "%");
        screen.progressStage(LiteralText.create(fit(line, Math.max(0, available - suffix))));
        screen.progressStagePercentage(percent);
    }

    private String fit(String text, int maxWidth) {
        if (game.font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        if (game.font.width(ellipsis) > maxWidth) return "";
        // 按 code point 截断，避免切开包名中的 surrogate pair。二分避免长包名的 O(n²) 测量。
        int low = 0;
        int high = text.codePointCount(0, text.length());
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            int end = text.offsetByCodePoints(0, mid);
            if (game.font.width(text.substring(0, end) + ellipsis) <= maxWidth) low = mid;
            else high = mid - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + ellipsis;
    }

    private static Field findPendingReload() {
        try {
            Field field;
            try {
                field = Minecraft.class.getDeclaredField("pendingReload");
            } catch (NoSuchFieldException namedMissing) {
                field = Minecraft.class.getDeclaredField("field_18174");
            }
            if (field.getType() != CompletableFuture.class) throw new NoSuchFieldException(field.getName());
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnPendingReload(e);
            return null;
        }
    }

    private static void warnPendingReload(Exception error) {
        ViaRpLog.warn("不能检测原版排队中的资源重载；仅检查原版加载遮罩：{}", error.toString());
    }

    private static Screen currentScreen(Minecraft game) {
        return game.screen;
    }

    private static void setScreen(Minecraft game, Screen screen) {
        game.setScreen(screen);
    }

    private static Object overlay(Minecraft game) {
        return game.getOverlay();
    }

    /** 1.19 用 Component.literal 替代 TextComponent(String)，不能硬链接到其中一侧。 */
    private static final class LiteralText {
        private static final MethodHandle FACTORY = find();

        private static MethodHandle find() {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                for (String name : new String[] {"literal", "method_43470"}) {
                    try {
                        Method method = Component.class.getMethod(name, String.class);
                        return lookup.unreflect(method).asType(MethodType.methodType(Component.class, String.class));
                    } catch (NoSuchMethodException ignored) {
                        // 旧版只有 TextComponent；依次尝试开发与生产命名。
                    }
                }
                String textClass = FabricLoader.getInstance().getMappingResolver()
                        .mapClassName("intermediary", "net.minecraft.class_2585");
                Constructor<?> ctor = Class.forName(textClass).getConstructor(String.class);
                return lookup.unreflectConstructor(ctor).asType(MethodType.methodType(Component.class, String.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法识别原版字面组件接口", e);
            }
        }

        private static Component create(String text) {
            try {
                return (Component) FACTORY.invokeExact(text);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException("无法创建原版字面组件", e);
            }
        }
    }

    /**
     * 1.17 起 ProgressScreen 的构造从 () 变为 (boolean)。JVM 的 super 构造调用必须写死描述符，
     * 不能通过 Java 反射调用另一签名；因此用 Loader 已提供的 ASM 生成一个仅适配构造和回调的子类。
     * 绘制、布局及按键分派仍全部由原版完成。只生成一次，并且不改写任何原版类。
     */
    private static final class NativeScreen extends ClassLoader implements Opcodes {
        private static final String NAME = "top.syshub.viarp.adapter.LegacyConversionScreen";
        private static final String INTERNAL_NAME = NAME.replace('.', '/');
        private static final String RUNNABLE = "Ljava/lang/Runnable;";
        private static final String BOOLEAN_SUPPLIER = "Ljava/util/function/BooleanSupplier;";
        private static final Constructor<? extends ProgressScreen> FACTORY = find();

        private NativeScreen() {
            super(ReloadUi.class.getClassLoader());
        }

        private static ProgressScreen create(Runnable tick, BooleanSupplier allowDismiss, Runnable dismiss) {
            try {
                return FACTORY.newInstance(tick, allowDismiss, dismiss);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法创建旧版原版进度界面", e);
            }
        }

        private static Constructor<? extends ProgressScreen> find() {
            try {
                boolean withCloseFlag;
                try {
                    ProgressScreen.class.getConstructor(boolean.class);
                    withCloseFlag = true;
                } catch (NoSuchMethodException oldRelease) {
                    ProgressScreen.class.getConstructor();
                    withCloseFlag = false;
                }
                byte[] bytes = generate(withCloseFlag);
                Class<? extends ProgressScreen> type = new NativeScreen()
                        .defineClass(NAME, bytes, 0, bytes.length).asSubclass(ProgressScreen.class);
                return type.getConstructor(Runnable.class, BooleanSupplier.class, Runnable.class);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法适配旧版原版进度界面构造或回调", e);
            }
        }

        private static byte[] generate(boolean withCloseFlag) throws NoSuchMethodException {
            String parent = Type.getInternalName(ProgressScreen.class);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, INTERNAL_NAME, null, parent, null);
            writer.visitField(ACC_PRIVATE | ACC_FINAL, "tick", RUNNABLE, null, null).visitEnd();
            writer.visitField(ACC_PRIVATE | ACC_FINAL, "allowDismiss", BOOLEAN_SUPPLIER, null, null).visitEnd();
            writer.visitField(ACC_PRIVATE | ACC_FINAL, "dismiss", RUNNABLE, null, null).visitEnd();

            MethodVisitor ctor = writer.visitMethod(ACC_PUBLIC, "<init>",
                    "(" + RUNNABLE + BOOLEAN_SUPPLIER + RUNNABLE + ")V", null, null);
            ctor.visitCode();
            ctor.visitVarInsn(ALOAD, 0);
            if (withCloseFlag) ctor.visitInsn(ICONST_0);
            ctor.visitMethodInsn(INVOKESPECIAL, parent, "<init>", withCloseFlag ? "(Z)V" : "()V", false);
            store(ctor, "tick", RUNNABLE, 1);
            store(ctor, "allowDismiss", BOOLEAN_SUPPLIER, 2);
            store(ctor, "dismiss", RUNNABLE, 3);
            finish(ctor, RETURN);

            callback(writer, methodName("tick", "method_25393"), "tick", RUNNABLE,
                    "java/lang/Runnable", "run", "()V", RETURN);
            callback(writer, methodName("shouldCloseOnEsc", "method_25422"), "allowDismiss", BOOLEAN_SUPPLIER,
                    "java/util/function/BooleanSupplier", "getAsBoolean", "()Z", IRETURN);
            callback(writer, methodName("onClose", "method_25419"), "dismiss", RUNNABLE,
                    "java/lang/Runnable", "run", "()V", RETURN);
            MethodVisitor pause = writer.visitMethod(ACC_PUBLIC, methodName("isPauseScreen", "method_25421"),
                    "()Z", null, null);
            pause.visitCode();
            pause.visitInsn(ICONST_0);
            finish(pause, IRETURN);
            writer.visitEnd();
            return writer.toByteArray();
        }

        private static String methodName(String named, String intermediary) throws NoSuchMethodException {
            try {
                return ProgressScreen.class.getMethod(named).getName();
            } catch (NoSuchMethodException ignored) {
                return ProgressScreen.class.getMethod(intermediary).getName();
            }
        }

        private static void store(MethodVisitor method, String field, String descriptor, int arg) {
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, arg);
            method.visitFieldInsn(PUTFIELD, INTERNAL_NAME, field, descriptor);
        }

        private static void callback(ClassWriter writer, String name, String field, String fieldDescriptor,
                                     String owner, String method, String descriptor, int returnOpcode) {
            MethodVisitor code = writer.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
            code.visitCode();
            code.visitVarInsn(ALOAD, 0);
            code.visitFieldInsn(GETFIELD, INTERNAL_NAME, field, fieldDescriptor);
            code.visitMethodInsn(INVOKEINTERFACE, owner, method, descriptor, true);
            finish(code, returnOpcode);
        }

        private static void finish(MethodVisitor method, int returnOpcode) {
            method.visitInsn(returnOpcode);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
    }
}
