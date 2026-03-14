package app.univtool.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SettingsStore {
    public static class Settings {
        public String workspaceDir;
        public String archiveDir;

        // ポータブルモード設定（開発中）
        public Boolean portableEnabled;
        public String  portableRoot;
    }


    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Settings load() {
        try {
            Files.createDirectories(AppPaths.appHome());
            Path p = AppPaths.settingsJson();
            if (Files.exists(p)) {
                String json = Files.readString(p);
                Settings s = GSON.fromJson(json, Settings.class);
                return s != null ? s : new Settings();
            }
        } catch (IOException ignored) {}
        return new Settings();
    }

    public static void save(Settings s) {
        try {
            Files.createDirectories(AppPaths.appHome());
            Files.writeString(AppPaths.settingsJson(), GSON.toJson(s), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("設定保存に失敗: " + e.getMessage(), e);
        }
    }
}
