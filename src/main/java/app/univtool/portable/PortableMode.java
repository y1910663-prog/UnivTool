package app.univtool.portable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.time.Instant;

import app.univtool.core.Database;
import app.univtool.core.SettingsStore;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/**
 * 開発中
 */
public final class PortableMode {

    public static final class State {
        public final boolean enabled;
        public final Path root;      // ワークスペース親フォルダ
        public final Path workspace; // root/workspace
        public final Path archive;   // root/archive
        public final Path data;      // root/data
        public final Path locks;     // root/data/locks
        public final Path control;   // root/data/locks/control
        public final Path dbFile;    // root/data/univtool.db
        State(boolean enabled, Path root){
            this.enabled = enabled;
            this.root = root;
            this.workspace = enabled ? root.resolve("workspace") : null;
            this.archive   = enabled ? root.resolve("archive")   : null;
            this.data      = enabled ? root.resolve("data")      : null;
            this.locks     = enabled ? data.resolve("locks")     : null;
            this.control   = enabled ? locks.resolve("control")  : null;
            this.dbFile    = enabled ? data.resolve("univtool.db"): null;
        }
    }

    private static State current;
    private static FileChannel lockChannel;
    private static FileLock     lock;

    public static State current(){ return current; }

    public static void bootstrap(Stage stage) {
        var s = SettingsStore.load();
        boolean enabled = s.portableEnabled != null && s.portableEnabled.booleanValue()
                       && s.portableRoot   != null && !s.portableRoot.isBlank();
        Path root = enabled ? Path.of(s.portableRoot) : null;
        current = new State(enabled, root);

        if (!enabled) return;

        try {
            Files.createDirectories(current.workspace);
            Files.createDirectories(current.archive);
            Files.createDirectories(current.data);
            Files.createDirectories(current.locks);
            Files.createDirectories(current.control);
        } catch (IOException ioe) {
            new Alert(Alert.AlertType.ERROR, "ポータブルモードの初期化に失敗しました:\n" + ioe.getMessage(), ButtonType.OK).showAndWait();
            current = new State(false, null);
            return;
        }

        s.workspaceDir = current.workspace.toString();
        s.archiveDir   = current.archive.toString();
        SettingsStore.save(s);

        Database.configureBaseDir(current.data);

        acquireOrTakeover(stage);
        startControlWatcher();
    }

    private static void acquireOrTakeover(Stage stage) {
        Path lockFile = current.locks.resolve("instance.lock");
        try {
            lockChannel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
            if (lock != null) return;

            var ans = new Alert(Alert.AlertType.CONFIRMATION,
                    "このワークスペースは他のPCで開かれています。\n" +
                    "相手側で状態を保存して終了させ、引き継ぎますか？",
                    ButtonType.YES, ButtonType.NO).showAndWait().orElse(ButtonType.NO);

            if (ans != ButtonType.YES) {
                Platform.exit();
                return;
            }

            String ticket = "req_" + Instant.now().toEpochMilli();
            Path req = current.control.resolve("takeover_" + ticket + ".request");
            try (var ch = FileChannel.open(req, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                var who = System.getProperty("user.name","unknown") + "@" + getHost();
                ch.write(ByteBuffer.wrap(who.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }

            Path ack = current.control.resolve("ack_" + ticket + ".done");
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ack)) break;
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            }

            lock = lockChannel.tryLock();
            if (lock == null) {
                var force = new Alert(Alert.AlertType.CONFIRMATION,
                        "相手側の終了を確認できませんでした。\n" +
                        "強制的に引き継ぎますか？（データ競合の可能性があります）",
                        ButtonType.YES, ButtonType.NO).showAndWait().orElse(ButtonType.NO);
                if (force != ButtonType.YES) {
                    Platform.exit();
                    return;
                }
                lockChannel.close();
                lockChannel = FileChannel.open(lockFile,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                lock = lockChannel.tryLock();
            }

        } catch (IOException ioe) {
            new Alert(Alert.AlertType.ERROR, "単一起動ガードに失敗:\n" + ioe.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private static void startControlWatcher() {
        Thread t = new Thread(() -> {
            try (var ws = FileSystems.getDefault().newWatchService()) {
                current.control.register(ws, StandardWatchEventKinds.ENTRY_CREATE);
                while (true) {
                    WatchKey key = ws.take();
                    for (WatchEvent<?> ev : key.pollEvents()) {
                        Path name = (Path) ev.context();
                        if (name == null) continue;
                        if (!name.getFileName().toString().startsWith("takeover_")) continue;

                        String ticket = name.getFileName().toString()
                                .replace("takeover_", "").replace(".request", "");

                        try { Files.deleteIfExists(current.control.resolve(name)); } catch (Exception ignored) {}

                        Platform.runLater(() -> {
                            try {
                                app.univtool.core.SaveHooks.runAllQuietly();
                            } catch (Throwable ignored) {}
                            Path ack = current.control.resolve("ack_" + ticket + ".done");
                            try { Files.writeString(ack, "ok", StandardOpenOption.CREATE, StandardOpenOption.WRITE); }
                            catch (Exception ignored) {}
                            releaseLock();
                            Platform.exit();
                        });
                    }
                    if (!key.reset()) break;
                }
            } catch (Exception ignored) { /* 終了 */ }
        }, "Portable-Control-Watcher");
        t.setDaemon(true);
        t.start();
    }

    public static void releaseLock() {
        try { if (lock != null && lock.isValid()) lock.release(); } catch (IOException ignored) {}
        try { if (lockChannel != null) lockChannel.close(); } catch (IOException ignored) {}
    }

    private static String getHost() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "host"; }
    }
}
