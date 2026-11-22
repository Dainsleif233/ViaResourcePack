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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** 一批独立的路径准备任务；结果顺序与原版请求相同，单项失败不取消其余任务。 */
public final class ConversionBatch {

    public enum Stage {
        QUEUED, CHECKING, READING, MIGRATING, SAVING, UNCHANGED, CACHED, CONVERTED, FAILED;

        public boolean finished() {
            return ordinal() >= UNCHANGED.ordinal();
        }
    }

    /** 单包状态；供原版渲染线程读取，不在工作线程调用游戏接口。 */
    public static final class Entry {
        private final Path source;
        private volatile Stage stage = Stage.QUEUED;
        private Path path;

        Entry(Path source) {
            this.source = source;
            this.path = source;
        }

        public String name() {
            return source == null || source.getFileName() == null ? "?" : source.getFileName().toString();
        }

        public Stage stage() {
            return stage;
        }

        void stage(Stage value) {
            stage = value;
        }

        void finish(Path path, Stage finished) {
            this.path = path;
            stage = finished;
        }
    }

    private final List<Entry> entries;
    private final CompletableFuture<Void> completion = new CompletableFuture<Void>();

    private ConversionBatch(List<Path> paths) {
        List<Entry> pending = new ArrayList<Entry>(paths.size());
        for (Path path : paths) {
            pending.add(new Entry(path));
        }
        entries = Collections.unmodifiableList(pending);
    }

    static ConversionBatch start(List<Path> paths, Executor executor, int parallelism,
                                 BiConsumer<Path, Entry> resolve) {
        ConversionBatch batch = new ConversionBatch(paths);
        if (paths.isEmpty()) {
            batch.completion.complete(null);
            return batch;
        }
        int lanes = Math.min(parallelism, paths.size());
        AtomicInteger cursor = new AtomicInteger();
        AtomicInteger working = new AtomicInteger(lanes);
        // 只排队少量 lane；每处理完一份包就回队尾，让另一批（如服务器请求）有机会执行。
        Runnable lane = new Runnable() {
            @Override
            public void run() {
                boolean handedOff = false;
                try {
                    int index;
                    while (!batch.completion.isDone() && (index = cursor.getAndIncrement()) < batch.entries.size()) {
                        Entry entry = batch.entries.get(index);
                        try {
                            resolve.accept(entry.source, entry);
                        } catch (RuntimeException failure) {
                            entry.finish(entry.source, Stage.FAILED);
                        }
                        if (cursor.get() >= batch.entries.size()) {
                            break;
                        }
                        try {
                            executor.execute(this);
                            handedOff = true;
                            return;
                        } catch (RejectedExecutionException full) {
                            // 此 lane 已被接纳，队列满时推进当前批，不把正常资源包错误标成失败。
                        }
                    }
                } catch (Error fatal) {
                    batch.completion.completeExceptionally(fatal);
                    throw fatal;
                } finally {
                    if (!handedOff) {
                        batch.finishLane(working);
                    }
                }
            }
        };
        for (int i = 0; i < lanes; i++) {
            try {
                executor.execute(lane);
            } catch (RejectedExecutionException full) {
                batch.finishLane(working);
            }
        }
        return batch;
    }

    private void finishLane(AtomicInteger working) {
        if (working.decrementAndGet() == 0) {
            for (Entry entry : entries) {
                if (!entry.stage().finished()) {
                    // 没有任何 lane 被接纳或没有给出结果时，保留原路径。
                    entry.finish(entry.source, Stage.FAILED);
                }
            }
            completion.complete(null);
        }
    }

    public List<Entry> entries() {
        return entries;
    }

    public CompletableFuture<Void> completion() {
        // 调用方可组合或取消它自己的 future，但不能提前结束整个批次。
        return completion.thenApply(ignored -> null);
    }

    List<Path> resolvedPaths() {
        completion.join();
        List<Path> result = new ArrayList<Path>(entries.size());
        for (Entry entry : entries) {
            result.add(entry.path);
        }
        return result;
    }

    Map<Path, Path> resolvedMap() {
        List<Path> resolved = resolvedPaths();
        Map<Path, Path> result = new LinkedHashMap<Path, Path>();
        for (int i = 0; i < entries.size(); i++) {
            Path source = entries.get(i).source;
            if (source != null) {
                result.put(source.toAbsolutePath().normalize(), resolved.get(i));
            }
        }
        return result;
    }
}
