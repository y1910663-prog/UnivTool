package app.univtool.ui.theme;

import javafx.application.Platform;
import javafx.scene.Scene;

/**
 * アプリ共通テーマ適用ユーティリティ。
 * 開発中
 */
public final class Theme {

    private Theme() {}

    public enum Mode { LIGHT, DARK, SYSTEM }

    private static Mode mode = Mode.SYSTEM;

    private static final String BASE_CSS = "/app/univtool/ui/theme/univtool-base.css";
    private static final String DARK_CSS = "/app/univtool/ui/theme/univtool-dark.css";

    public static void attach(Scene scene) {
        if (scene == null) return;

        var ss = scene.getStylesheets();
        ss.removeIf(s -> s != null && (s.contains("univtool-base.css") || s.contains("univtool-dark.css")));

        var baseUrl = Theme.class.getResource("univtool-base.css");
        if (baseUrl != null) ss.add(baseUrl.toExternalForm());

        boolean useDark = isDarkPreferred();
        var root = scene.getRoot();
        if (root != null) {
            if (useDark) root.getStyleClass().add("is-dark");
            else root.getStyleClass().remove("is-dark");
        }

        if (useDark) {
            var darkUrl = Theme.class.getResource("univtool-dark.css");
            if (darkUrl != null) ss.add(darkUrl.toExternalForm());
        }
    }

    public static void setMode(Mode newMode, Scene scene) {
        mode = (newMode == null ? Mode.SYSTEM : newMode);
        if (scene != null) {

            if (Platform.isFxApplicationThread()) attach(scene);
            else Platform.runLater(() -> attach(scene));
        }
    }

    public static Mode getMode() { return mode; }

    private static boolean isDarkPreferred() {
        return switch (mode) {
            case DARK   -> true;
            case LIGHT  -> false;
            case SYSTEM -> false;
        };
    }
}
