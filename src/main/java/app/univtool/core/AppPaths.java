package app.univtool.core;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {
    private AppPaths() {}

    // アプリの設定/DBはユーザホーム直下の .univtool に置く
    public static Path appHome() {
        return Paths.get(System.getProperty("user.home"), ".univtool");
    }
    public static Path settingsJson() {
        return appHome().resolve("settings.json");
    }
    public static Path appDb() {
        return appHome().resolve("app.db");
    }

    // 初回起動時のデフォルトworkspace候補（ユーザホーム直下）
    public static Path defaultWorkspace() {
        return Paths.get(System.getProperty("user.home"), "UnivWorkspace");
    }
}
