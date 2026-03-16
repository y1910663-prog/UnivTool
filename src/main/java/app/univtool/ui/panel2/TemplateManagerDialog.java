package app.univtool.ui.panel2;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

public class TemplateManagerDialog extends Dialog<Void> {
    private final Path tplDir;
    private final ListView<Path> list = new ListView<>();

    public TemplateManagerDialog(Path workspaceDir) {
        setTitle("テンプレートファイルの管理");
        getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        this.tplDir = (workspaceDir == null ? Path.of(".") : workspaceDir).resolve("template");
        try { Files.createDirectories(tplDir); } catch (Exception ignored) {}

        list.setPlaceholder(new Label("テンプレートがありません"));
        refreshList();
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFileName().toString());
            }
        });

        list.setOnMouseClicked(e -> { if (e.getClickCount() == 2) openSelected(); });

        list.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) openSelected(); });

        var btnOpen   = new Button("開く/編集");
        var btnRename = new Button("リネーム");
        var btnDelete = new Button("削除");

        btnOpen.setOnAction(e -> openSelected());
        btnRename.setOnAction(e -> renameSelected());
        btnDelete.setOnAction(e -> deleteSelected());

        var buttons = new HBox(8, btnOpen, btnRename, btnDelete);
        var root = new BorderPane(list, null, null, buttons, null);
        BorderPane.setMargin(list, new Insets(10));
        BorderPane.setMargin(buttons, new Insets(10));

        getDialogPane().setContent(root);
    }

    private void refreshList() {
        try (var s = Files.list(tplDir)) {
            list.getItems().setAll(
                s.filter(Files::isRegularFile)
                 .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                 .toList()
            );
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "テンプレート読み込みに失敗:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private Path selectedPath() {
        return list.getSelectionModel().getSelectedItem();
    }

    private void openSelected() {
        Path p = selectedPath(); if (p == null) return;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(p.toFile());
            } else {
                new Alert(Alert.AlertType.INFORMATION, "この環境では既定アプリで開けません。", ButtonType.OK).showAndWait();
            }
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "開けませんでした:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private void renameSelected() {
        Path p = selectedPath(); if (p == null) return;
        var dlg = new TextInputDialog(p.getFileName().toString());
        dlg.setTitle("リネーム");
        dlg.setHeaderText("新しいファイル名を入力してください（拡張子を含めてOK）");
        dlg.setContentText("ファイル名:");
        var res = dlg.showAndWait();
        if (res.isEmpty()) return;

        String newName = FileSaveOps.sanitize(res.get().trim());
        if (newName.isBlank()) return;

        try {
            Path target = FileSaveOps.ensureUnique(tplDir, newName);
            Files.move(p, target);
            refreshList();
            list.getSelectionModel().select(target);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "リネームに失敗:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private void deleteSelected() {
        Path p = selectedPath(); if (p == null) return;
        var conf = new Alert(Alert.AlertType.CONFIRMATION,
                "削除してよろしいですか？\n" + p.getFileName(), ButtonType.OK, ButtonType.CANCEL).showAndWait();
        if (conf.isEmpty() || conf.get() != ButtonType.OK) return;

        try {
            Files.delete(p);
            refreshList();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "削除に失敗:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }
}
