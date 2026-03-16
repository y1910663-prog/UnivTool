package app.univtool.ui.panel2;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class RenamePresetEditorDialog extends Dialog<Boolean> {
    public RenamePresetEditorDialog(){
        setTitle("リネームプリセットの追加/編集");
        var ok = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("閉じる", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);

        var list = new ListView<String>();
        var store = FilenamePresetStore.load();
        list.getItems().setAll(store.names());

        var tfName = new TextField(); tfName.setPromptText("プリセット名");
        var tfPattern = new TextField(); tfPattern.setPromptText("{course}_{nth}_{kind}{ext}");

        var btnAdd = new Button("追加");
        var btnEdit = new Button("名称/式を変更");
        var btnDel = new Button("削除");

        btnAdd.setOnAction(e -> {
            var sub = new Dialog<ButtonType>();
            sub.setTitle("プリセット追加");
            sub.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            var gp = new GridPane(); gp.setHgap(8); gp.setVgap(8); gp.setPadding(new Insets(10));
            var name = new TextField(); var pat = new TextField("{course}_{nth}_{kind}{ext}");
            gp.addRow(0, new Label("名前"), name);
            gp.addRow(1, new Label("式"), pat);
            sub.getDialogPane().setContent(gp);
            var r = sub.showAndWait().orElse(ButtonType.CANCEL);
            if (r==ButtonType.OK) {
                store.upsertPreset(name.getText().trim(), pat.getText().trim());
                FilenamePresetStore.save(store);
                list.getItems().setAll(store.names());
            }
        });
        btnEdit.setOnAction(e -> {
            String cur = list.getSelectionModel().getSelectedItem();
            if (cur==null) return;
            var sub = new Dialog<ButtonType>();
            sub.setTitle("プリセット編集");
            sub.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            var gp = new GridPane(); gp.setHgap(8); gp.setVgap(8); gp.setPadding(new Insets(10));
            var name = new TextField(cur); var pat = new TextField(FilenamePresetStore.load().patternOf(cur).orElse(""));
            gp.addRow(0, new Label("名前"), name);
            gp.addRow(1, new Label("式"), pat);
            sub.getDialogPane().setContent(gp);
            var r = sub.showAndWait().orElse(ButtonType.CANCEL);
            if (r==ButtonType.OK) {
                store.deletePreset(cur);
                store.upsertPreset(name.getText().trim(), pat.getText().trim());
                FilenamePresetStore.save(store);
                list.getItems().setAll(store.names());
            }
        });
        btnDel.setOnAction(e -> {
            String cur = list.getSelectionModel().getSelectedItem(); if (cur==null) return;
            store.deletePreset(cur);
            FilenamePresetStore.save(store);
            list.getItems().setAll(store.names());
        });

        var top = new GridPane(); top.setHgap(8); top.setVgap(8); top.setPadding(new Insets(10));
        top.addRow(0, new Label("使えるプレースホルダ: {course}, {nth}, {kind}, {ext}"));

        var buttons = new HBox(8, btnAdd, btnEdit, btnDel);
        var root = new BorderPane();
        root.setTop(top);
        root.setCenter(list);
        root.setBottom(buttons);
        BorderPane.setMargin(buttons, new Insets(10));
        getDialogPane().setContent(root);

        setResultConverter(bt -> bt==ok);
    }
}