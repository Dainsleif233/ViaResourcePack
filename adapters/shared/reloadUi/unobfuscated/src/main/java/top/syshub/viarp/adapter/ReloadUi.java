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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.syshub.viarp.ViaRpLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/** 只在客户端线程维护界面；enqueue 是从任意线程返回客户端的入口。 */
final class ReloadUi {
    private static Field pendingReload = findPendingReload();

    private final Minecraft game;
    private final Screen previous;
    private final ConversionScreen screen;
    private boolean dismissAllowed;
    private String title;
    private String line;
    private int percent;
    private int lastWidth;

    private ReloadUi(Minecraft game, Runnable tick, Runnable dismiss) {
        this.game = game;
        this.previous = currentScreen(game);
        this.screen = new ConversionScreen(tick, dismiss);
    }

    static void enqueue(Minecraft game, Runnable action) {
        // schedule 无论调用线程都只入队；execute 在客户端线程可能立即执行。
        game.schedule(action);
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

    private void refreshText() {
        lastWidth = screen.width;
        int available = Math.max(0, lastWidth - 16);
        screen.progressStartNoAbort(Component.literal(fit(title, available)));
        // 原版在阶段行末尾拼接百分比，给后缀预留宽度。
        int suffix = screenFont().width(" " + percent + "%");
        screen.progressStage(Component.literal(fit(line, Math.max(0, available - suffix))));
        screen.progressStagePercentage(percent);
    }

    private String fit(String text, int maxWidth) {
        if (screenFont().width(text) <= maxWidth) return text;
        String ellipsis = "…";
        if (screenFont().width(ellipsis) > maxWidth) return "";
        // 按 code point 截断，避免切开包名中的 surrogate pair。二分避免长包名的 O(n²) 测量。
        int low = 0;
        int high = text.codePointCount(0, text.length());
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            int end = text.offsetByCodePoints(0, mid);
            if (screenFont().width(text.substring(0, end) + ellipsis) <= maxWidth) low = mid;
            else high = mid - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + ellipsis;
    }

    private static Field findPendingReload() {
        try {
            Field field;
            field = Minecraft.class.getDeclaredField("pendingReload");
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

    private net.minecraft.client.gui.Font screenFont() {
        return screen.vanillaFont();
    }

    private static Screen currentScreen(Minecraft game) {
        return (Screen) ScreenAccess.INSTANCE.current(game);
    }

    private static void setScreen(Minecraft game, Screen screen) {
        ScreenAccess access = ScreenAccess.INSTANCE;
        access.invoke(access.setScreen, game, screen);
    }

    private static Object overlay(Minecraft game) {
        ScreenAccess access = ScreenAccess.INSTANCE;
        return access.invoke(access.overlay, game);
    }

    /** 26.1 屏幕由 Minecraft 管理；26.2 起屏幕与遮罩一起迁到 Gui。 */
    private static final class ScreenAccess {
        private static final ScreenAccess INSTANCE = find();

        private final Field screenField;
        private final Method screenGetter;
        private final Method setScreen;
        private final Method overlay;

        private ScreenAccess(Field screenField, Method screenGetter, Method setScreen, Method overlay) {
            this.screenField = screenField;
            this.screenGetter = screenGetter;
            this.setScreen = setScreen;
            this.overlay = overlay;
        }

        private static ScreenAccess find() {
            try {
                Class<?> gui = Minecraft.class.getField("gui").getType();
                try {
                    Method getter = gui.getMethod("screen");
                    return new ScreenAccess(null, getter, gui.getMethod("setScreen", Screen.class),
                            gui.getMethod("overlay"));
                } catch (NoSuchMethodException minecraftOwnsScreen) {
                    return new ScreenAccess(Minecraft.class.getField("screen"), null,
                            Minecraft.class.getMethod("setScreen", Screen.class), Minecraft.class.getMethod("getOverlay"));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法识别原版屏幕接口", e);
            }
        }

        private Object current(Minecraft game) {
            if (screenGetter != null) return invoke(screenGetter, game);
            try {
                return screenField.get(game);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("无法读取原版屏幕", e);
            }
        }

        private Object invoke(Method method, Minecraft game, Object... args) {
            try {
                return method.invoke(screenGetter == null ? game : game.gui, args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法调用原版屏幕接口", e);
            }
        }
    }

    private final class ConversionScreen extends ProgressScreen {
        private final Runnable tick;
        private final Runnable dismiss;

        private ConversionScreen(Runnable tick, Runnable dismiss) {
            super(false);
            this.tick = tick;
            this.dismiss = dismiss;
        }

        @Override
        public void tick() {
            if (!isCurrent()) return;
            tick.run();
            if (isCurrent() && ReloadUi.this.title != null && width != lastWidth) refreshText();
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return dismissAllowed;
        }

        @Override
        public void onClose() {
            if (dismissAllowed && isCurrent()) dismiss.run();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        private net.minecraft.client.gui.Font vanillaFont() {
            return font;
        }
    }
}
