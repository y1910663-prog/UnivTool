package app.univtool.ui.panel2;

import java.util.Optional;

import app.univtool.model.Course;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class RenameDialog extends Dialog<RenameDialog.Result> {
    public record Result(String filename) {}

    private final Course course; private final int nth; private final ClassifyDialog.Kind kind; private final String ext;

    public RenameDialog(Course course, int nth, ClassifyDialog.Kind kind, String ext){
        this.course=course; this.nth=nth; this.kind=kind; this.ext = (ext==null?"":ext);

        setTitle("ファイル名のリネーム");
        var save = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("キャンセル", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(save, cancel);

        var rbUseOrig = new RadioButton("元のファイル名を使用");
        var rbRename  = new RadioButton("ファイル名をリネームする");
        var tg = new ToggleGroup(); rbUseOrig.setToggleGroup(tg); rbRename.setToggleGroup(tg);
        rbRename.setSelected(true);

        var cbPreset = new ComboBox<String>();
        var btnPresetEdit = new Button("プリセットの追加/編集");
        loadPresets(cbPreset);

        var tfPreview = new TextField(); tfPreview.setEditable(false);

        var rowPreset = new HBox(8, cbPreset, btnPresetEdit);

        var gp = new GridPane(); gp.setHgap(10); gp.setVgap(8); gp.setPadding(new Insets(12));
        gp.addRow(0, rbUseOrig);
        gp.addRow(1, rbRename);
        gp.addRow(2, new Label("プリセット"), rowPreset);
        gp.addRow(3, new Label("生成プレビュー"), tfPreview);
        getDialogPane().setContent(gp);

        tfPreview.setText(buildByPreset(cbPreset.getValue()));

        rbUseOrig.selectedProperty().addListener((o, a, b) -> tfPreview.setDisable(b));
        cbPreset.valueProperty().addListener((o, a, b) -> tfPreview.setText(buildByPreset(b)));

        btnPresetEdit.setOnAction(e -> {
            var dlg = new RenamePresetEditorDialog();
            var res = dlg.showAndWait();
            if (res.isPresent()) {
                loadPresets(cbPreset);
                tfPreview.setText(buildByPreset(cbPreset.getValue()));
            }
        });

        setResultConverter(bt -> {
            if (bt != save) return null;
            if (rbUseOrig.isSelected()) {
                return new Result("" /* 未使用 */);
            } else {
                return new Result(tfPreview.getText());
            }
        });
    }

    private void loadPresets(ComboBox<String> cb) {
        var presets = FilenamePresetStore.load();
        if (!presets.hasPreset("デフォルトプリセット")) {
            presets.upsertPreset("デフォルトプリセット", "{course}_{nth}_{kind}{ext}");
            FilenamePresetStore.save(presets);
        }
        cb.getItems().setAll(presets.names());
        cb.getSelectionModel().select("デフォルトプリセット");
    }

    private String buildByPreset(String presetName) {
        var presets = FilenamePresetStore.load();
        Optional<String> patOpt = presets.patternOf(presetName);
        String pattern = patOpt.orElse("{course}_{nth}_{kind}{ext}");
        String kindLabel = kind.toString();
        String safeCourse = FileSaveOps.sanitize(course.folder != null && !course.folder.isBlank() ? course.folder : course.name);
        return pattern
                .replace("{course}", safeCourse)
                .replace("{nth}", String.valueOf(nth))
                .replace("{kind}", kindLabel)
                .replace("{ext}", ext!=null?ext:"");
    }
}
