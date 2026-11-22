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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import top.syshub.viarp.ViaRpLog;
import top.syshub.viarp.convert.ConversionBatch;
import top.syshub.viarp.convert.ConversionService;

/** 原版选包提交的准备阶段。仅客户端线程更改屏幕和发起原版重载。 */
public final class LocalPackReload {
    private static LocalPackReload active;

    private final Minecraft game;
    private final ConversionService service;
    private final ConversionBatch batch;
    private final CompletableFuture<Void> ready;
    private final CompletableFuture<Void> result = new CompletableFuture<Void>();
    private ReloadUi ui;
    private boolean chinese;
    private boolean reloading;
    private boolean summary;
    private String shownTitle = "";
    private String shownLine = "";
    private int shownPercent = -1;

    private LocalPackReload(Minecraft game, ConversionService service, ConversionBatch batch) {
        this.game = game;
        this.service = service;
        this.batch = batch;
        this.ready = batch.completion();
    }

    public static CompletableFuture<Void> submit(Minecraft game) {
        final ConversionService service;
        final ConversionBatch batch;
        try {
            service = ConversionService.get();
            batch = service.prepareLocalReload();
        } catch (RuntimeException | LinkageError failure) {
            ViaRpLog.error("Could not start parallel resource pack preparation; using the vanilla reload", failure);
            return game.reloadResourcePacks();
        }
        if (batch.entries().isEmpty() && active == null) {
            return game.reloadResourcePacks();
        }
        LocalPackReload previous = active;
        LocalPackReload next = new LocalPackReload(game, service, batch);
        active = next;
        if (previous != null && !previous.reloading) {
            previous.close();
            // 如果提交在同一原版导航回调内重复发生，较早调用也观察最终这次重载。
            next.result.whenComplete((ignored, failure) -> complete(previous.result, failure));
        }
        ReloadUi.enqueue(game, next::open);
        return next.result;
    }

    private void open() {
        if (active != this) {
            return;
        }
        try {
            chinese = ReloadUi.chinese(game);
            ui = ReloadUi.open(game, this::tick, this::dismiss);
            showProgress();
            // 如果窗口在任务完成前被其他原版界面替换，不抢回这个界面。
            ready.whenComplete((ignored, failure) -> ReloadUi.enqueue(game, () -> {
                if (active == this && !reloading && !ui.isCurrent()) {
                    if (ui.vanillaReloadBusy()) {
                        // 其他重载已开始，不能把这次仅限当前调用的准备结果传给原版的 pendingReload。
                        active = null;
                        result.completeExceptionally(new CancellationException("Resource reload superseded"));
                    } else {
                        beginVanilla();
                    }
                }
            }));
        } catch (RuntimeException | LinkageError failure) {
            ViaRpLog.error("Could not display resource pack preparation", failure);
            ready.whenComplete((ignored, workerFailure) -> ReloadUi.enqueue(game, this::beginVanilla));
        }
    }

    private void tick() {
        if (active != this) {
            return;
        }
        if (summary) {
            showFailures();
        } else if (!reloading) {
            showProgress();
            if (ready.isDone() && !ui.vanillaReloadBusy()) {
                beginVanilla();
            }
        }
    }

    private void beginVanilla() {
        if (active != this || reloading) {
            return;
        }
        reloading = true;
        try {
            show(chinese ? "正在重载资源" : "Reloading resources",
                    chinese ? "已准备资源包，交给 Minecraft 加载" : "Preparation complete; handing packs to Minecraft", 100);
            CompletableFuture<Void> vanilla = ready.isCompletedExceptionally()
                    ? game.reloadResourcePacks()
                    : service.withPreparedLocalPacks(batch, game::reloadResourcePacks);
            vanilla.whenComplete((ignored, failure) -> ReloadUi.enqueue(game, () -> finish(failure)));
        } catch (RuntimeException | LinkageError failure) {
            finish(failure);
        }
    }

    private void finish(Throwable failure) {
        complete(result, failure);
        if (active != this) {
            return;
        }
        if (ui != null && ui.isCurrent() && (failure != null || !failed().isEmpty())) {
            summary = true;
            ui.allowDismiss();
            showFailures();
        } else {
            close();
            active = null;
        }
    }

    private void dismiss() {
        if (active == this && summary) {
            close();
            active = null;
        }
    }

    private void close() {
        if (ui != null) {
            ui.close();
        }
    }

    private void showProgress() {
        List<ConversionBatch.Entry> busy = new ArrayList<ConversionBatch.Entry>();
        int complete = 0;
        int failed = 0;
        int count = batch.entries().size();
        for (ConversionBatch.Entry entry : batch.entries()) {
            ConversionBatch.Stage stage = entry.stage();
            if (stage.finished()) {
                complete++;
                if (stage == ConversionBatch.Stage.FAILED) {
                    failed++;
                }
            } else if (stage != ConversionBatch.Stage.QUEUED) {
                busy.add(entry);
            }
        }
        String title = (chinese ? "准备资源包：" : "Preparing packs: ") + complete + "/" + count;
        if (failed > 0) {
            title += (chinese ? "，失败 " : "; failed ") + failed;
        }
        String line = chinese ? "等待后台任务" : "Waiting for workers";
        if (!busy.isEmpty()) {
            ConversionBatch.Entry entry = busy.get(page(busy.size()));
            line = stageName(entry.stage()) + ": " + safeName(entry.name());
        }
        int percent = count == 0 ? 100 : (int) (100L * complete / count);
        show(title, line, percent);
    }

    private void showFailures() {
        List<ConversionBatch.Entry> failures = failed();
        if (failures.isEmpty()) {
            show(chinese ? "资源重载失败" : "Resource reload failed",
                    chinese ? "请查看日志，按 Esc 返回" : "See the game log; press Esc to return", 100);
            return;
        }
        int index = page(failures.size());
        show(chinese ? "资源包转换失败（按 Esc 返回）" : "Pack conversion failed (Esc to return)",
                (index + 1) + "/" + failures.size() + "  " + safeName(failures.get(index).name()), 100);
    }

    private List<ConversionBatch.Entry> failed() {
        List<ConversionBatch.Entry> failed = new ArrayList<ConversionBatch.Entry>();
        for (ConversionBatch.Entry entry : batch.entries()) {
            if (entry.stage() == ConversionBatch.Stage.FAILED) {
                failed.add(entry);
            }
        }
        return failed;
    }

    private String stageName(ConversionBatch.Stage stage) {
        switch (stage) {
            case READING: return chinese ? "读取" : "Reading";
            case MIGRATING: return chinese ? "升级" : "Upgrading";
            case SAVING: return chinese ? "保存" : "Saving";
            default: return chinese ? "检查" : "Checking";
        }
    }

    private void show(String title, String line, int percent) {
        if (ui != null && (!title.equals(shownTitle) || !line.equals(shownLine) || percent != shownPercent)) {
            ui.show(title, line, percent);
            shownTitle = title;
            shownLine = line;
            shownPercent = percent;
        }
    }

    private static int page(int count) {
        return (int) Math.floorMod(System.nanoTime() / 2_000_000_000L, (long) count);
    }

    private static String safeName(String name) {
        String visible = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", "?");
        if (visible.codePointCount(0, visible.length()) > 45) {
            int head = visible.offsetByCodePoints(0, 30);
            int tail = visible.offsetByCodePoints(visible.length(), -12);
            return visible.substring(0, head) + "…" + visible.substring(tail);
        }
        return visible;
    }

    private static void complete(CompletableFuture<Void> future, Throwable failure) {
        if (failure == null) {
            future.complete(null);
        } else {
            future.completeExceptionally(failure);
        }
    }
}
