package app.univtool.ui.panel1;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import app.univtool.core.KindPresetStore;
import app.univtool.model.Course;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;


public class AddCourseDialog extends Dialog<Course> {

    private boolean childDialogShowing = false;

    public AddCourseDialog() { this(null, false); }          // ← 既定はロックOFF
    public AddCourseDialog(Course preset) { this(preset, false); } // ← 既存互換（ロックOFF）

    // ★ 追加：曜日・時限をロックするかを指定できる版
    public AddCourseDialog(Course preset, boolean lockDayPeriod) {
        setTitle(preset == null ? "講義を追加" : "講義を編集");

        var ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("キャンセル", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12));
        int r = 0;

        var tfName = new TextField();

        var cbDay = new ComboBox<String>();
        cbDay.getItems().addAll("MON","TUE","WED","THU","FRI","SAT","SUN","OTHER");
        cbDay.getSelectionModel().selectFirst();

        var cbPeriod = new ComboBox<String>();
        cbPeriod.getItems().addAll("1","2","3","4","5","6","OTHER");
        cbPeriod.getSelectionModel().selectFirst();

        int thisYear = java.time.Year.now().getValue();
        Spinner<Integer> spYear = new Spinner<>(thisYear - 1, thisYear + 5, thisYear);
        spYear.setEditable(true);

        g.addRow(r++, new Label("授業科目名*"), tfName);
        g.addRow(r++, new Label("曜日*"), cbDay);
        g.addRow(r++, new Label("時限*"), cbPeriod);
        g.addRow(r++, new Label("開講年度(西暦)"), spYear);

        ComboBox<String> cbTerm = new ComboBox<>();
        cbTerm.getItems().addAll("前期","後期","春ターム","夏ターム","秋ターム","冬ターム","集中講義");
        cbTerm.setPromptText("任意");
        g.addRow(r++, new Label("開講期"), cbTerm);

        var tfCode = new TextField();
        Spinner<Integer> spGrade = new Spinner<>(1, 4, 1); spGrade.setEditable(true);

        var cbKind = new ComboBox<String>();
        var btnPreset = new Button("大学プリセットを選択");
        btnPreset.setOnAction(ev -> openPresetMenu(cbKind));

        Spinner<Double> spCredit = new Spinner<>(0.0, 100.0, 2.0, 0.5); spCredit.setEditable(true);

        spYear.focusedProperty().addListener((obs, was, is) -> { if (!is) spYear.increment(0); });
        spGrade.focusedProperty().addListener((obs, was, is) -> { if (!is) spGrade.increment(0); });
        spCredit.focusedProperty().addListener((obs, was, is) -> { if (!is) spCredit.increment(0); });

        var tfTeacher = new TextField();
        var tfEmail = new TextField();
        var tfWeb = new TextField();
        var tfSyllabus = new TextField();

        var cbAttendance = new CheckBox("出席確認あり");
        var cbMidExam = new CheckBox("中間試験アリ");
        var cbFinalExam = new CheckBox("期末試験アリ");
        var cbMidReport = new CheckBox("中間レポートアリ");
        var cbFinalReport = new CheckBox("期末レポートアリ");

        g.addRow(r++, new Separator());
        g.addRow(r++, new Label("科目番号"), tfCode);
        g.addRow(r++, new Label("開講年次(1-4)"), spGrade);
        g.addRow(r++, new Label("科目区分"), new HBox(8, cbKind, btnPreset));
        g.addRow(r++, new Label("単位数"), spCredit);
        g.addRow(r++, new Label("担当教員名"), tfTeacher);
        g.addRow(r++, new Label("公開E-mail"), tfEmail);
        g.addRow(r++, new Label("授業関連Webページ"), tfWeb);
        g.addRow(r++, new Label("シラバスURL"), tfSyllabus);
        g.addRow(r++, new Label("出欠/試験・レポート"),
                new HBox(8, cbAttendance, cbMidExam, cbFinalExam, cbMidReport, cbFinalReport));

        // preset を反映
        if (preset != null) {
            tfName.setText(preset.name);
            if (preset.day != null)    cbDay.setValue(preset.day);
            if (preset.period != null) cbPeriod.setValue(preset.period);
            if (preset.year != null)   spYear.getValueFactory().setValue(preset.year);
            cbTerm.setValue(preset.term);
            tfCode.setText(nvl(preset.code));
            if (preset.grade != null)  spGrade.getValueFactory().setValue(preset.grade);
            cbKind.setValue(preset.kind);
            if (preset.credit != null) spCredit.getValueFactory().setValue(preset.credit);
            tfTeacher.setText(nvl(preset.teacher));
            tfEmail.setText(nvl(preset.email));
            tfWeb.setText(nvl(preset.webpage));
            tfSyllabus.setText(nvl(preset.syllabus));
            cbAttendance.setSelected(preset.attendanceRequired);
            
            String ex = (preset.examsCsv == null) ? "" : preset.examsCsv.replaceAll("\\s+", "");
            boolean midExam     = ex.contains("中間試験") || ex.contains("中間テスト");
            boolean finalExam   = ex.contains("期末試験") || ex.contains("期末テスト");
            boolean midReport   = ex.contains("中間レポート") || ex.contains("中間レポ");
            boolean finalReport = ex.contains("期末レポート") || ex.contains("期末レポ");

            cbMidExam.setSelected(midExam);
            cbFinalExam.setSelected(finalExam);
            cbMidReport.setSelected(midReport);
            cbFinalReport.setSelected(finalReport);
        }

        // 曜日/時限をロック
        if (lockDayPeriod) {
            cbDay.setDisable(true);
            cbPeriod.setDisable(true);
            cbDay.setStyle("-fx-opacity:0.8;");
            cbPeriod.setStyle("-fx-opacity:0.8;");
            cbDay.setTooltip(new javafx.scene.control.Tooltip("時間割のセルに合わせて固定されています"));
            cbPeriod.setTooltip(new javafx.scene.control.Tooltip("時間割のセルに合わせて固定されています"));
        }

        getDialogPane().setContent(g);

        var okBtn = (Button)getDialogPane().lookupButton(ok);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            if (tfName.getText().trim().isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "授業科目名は必須です。", ButtonType.OK).showAndWait();
                evt.consume();
            }
        });

        setResultConverter(bt -> {
            if (bt != ok) return null;
            Course c = new Course();
            if (preset != null) c.id = preset.id;
            c.name = tfName.getText().trim();
            c.day = cbDay.getValue();
            c.period = cbPeriod.getValue();
            c.year = spYear.getValue();
            c.term = cbTerm.getValue();
            c.code = emptyToNull(tfCode.getText());
            c.grade  = spGrade.getValue();
            c.kind = cbKind.getValue();
            c.credit = spCredit.getValue();
            c.teacher = emptyToNull(tfTeacher.getText());
            c.email = emptyToNull(tfEmail.getText());
            c.webpage = emptyToNull(tfWeb.getText());
            c.syllabus = emptyToNull(tfSyllabus.getText());
            c.attendanceRequired = cbAttendance.isSelected();
            c.examsCsv = java.util.Arrays.asList(
                    cbMidExam.isSelected() ? "中間試験アリ" : null,
                    cbFinalExam.isSelected() ? "期末試験アリ" : null,
                    cbMidReport.isSelected() ? "中間レポートアリ" : null,
                    cbFinalReport.isSelected() ? "期末レポートアリ" : null
            ).stream().filter(s -> s != null).collect(Collectors.joining(","));
            return c;
        });

        setOnShown(ev -> {
            Window w = getDialogPane().getScene().getWindow();
            w.focusedProperty().addListener((o, was, is) -> {
                if (!is && !childDialogShowing) close();
            });
        });
    }
	
        

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
    

    private void openPresetMenu(ComboBox<String> cbKind) {
        var use    = new ButtonType("プリセットを使用", ButtonBar.ButtonData.LEFT);
        var edit   = new ButtonType("プリセットを作成", ButtonBar.ButtonData.LEFT);
        var imp    = new ButtonType("JSONから読み込み…", ButtonBar.ButtonData.LEFT);
        var cancel = ButtonType.CANCEL;

        var a = new Alert(Alert.AlertType.NONE, "", use, edit, imp, cancel);
        Window owner = getDialogPane().getScene().getWindow();
        a.initOwner(owner);
        a.initModality(Modality.WINDOW_MODAL);
        a.setTitle("科目区分プリセット");
        a.setHeaderText("操作を選択してください");

        var res = showChildDialog(a).orElse(cancel);
        if (res == use) {
            onUsePreset(cbKind);
        } else if (res == edit) {
            onEditPreset(cbKind);
        } else if (res == imp) {
            onImportPresetThenChoose(cbKind);
        }
    }
    
    private void onUsePreset(ComboBox<String> cbKind) {
        List<String> names = KindPresetStore.presetNames();
        if (names.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "保存されているプリセットがありません。先に「プリセットを作成」を行ってください。", ButtonType.OK).showAndWait();
            return;
        }
                
        var dlg = new ChoiceDialog<>(names.get(0), names);
        Window owner = getDialogPane().getScene().getWindow();
        dlg.initOwner(owner);
        dlg.initModality(Modality.WINDOW_MODAL);
        
        dlg.setTitle("プリセットを使用");
        dlg.setHeaderText("使用するプリセットを選択してください");
        dlg.setContentText("プリセット名：");
        var chosen = showChildDialog(dlg);
        if (chosen.isEmpty()) return;

        KindPresetStore.findByName(chosen.get()).ifPresent(p -> {
            cbKind.getItems().setAll(p.kinds != null ? p.kinds : List.of());
            if (!cbKind.getItems().isEmpty()) cbKind.getSelectionModel().selectFirst();
        });
    }

    private void onEditPreset(ComboBox<String> cbKind) {
        var nameInput = new TextField();
                
        var ask = new Alert(Alert.AlertType.NONE, "", ButtonType.OK, ButtonType.CANCEL);
        Window owner = getDialogPane().getScene().getWindow();
        ask.initOwner(owner);
        ask.initModality(Modality.WINDOW_MODAL);
        
        ask.setTitle("プリセット作成");
        ask.setHeaderText("プリセット名を入力してください（例：○○大学）");
        ask.getDialogPane().setContent(nameInput);
        if (showChildDialog(ask).orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        String name = nameInput.getText().trim();
        if (name.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "プリセット名が空です。", ButtonType.OK).showAndWait();
            return;
        }

        var list = new ListView<String>();
        list.setPrefHeight(240);

        KindPresetStore.findByName(name).ifPresent(p -> {
            if (p.kinds != null) list.getItems().setAll(p.kinds);
        });

        var btnAdd = new Button("追加");
        var btnEdit = new Button("名称変更");
        var btnDel = new Button("削除");
        btnAdd.setOnAction(ev -> {
            var tf = new TextField();
            var ad = new Alert(Alert.AlertType.NONE, "", ButtonType.OK, ButtonType.CANCEL);
            ad.initOwner(getDialogPane().getScene().getWindow());
            ad.initModality(Modality.WINDOW_MODAL);
            
            ad.setTitle("区分の追加");
            ad.setHeaderText("追加する科目区分名を入力");
            ad.getDialogPane().setContent(tf);
            if (showChildDialog(ad).orElse(ButtonType.CANCEL) == ButtonType.OK) {
                String v = tf.getText().trim();
                if (!v.isEmpty()) list.getItems().add(v);
            }
        });
        btnEdit.setOnAction(ev -> {
            String cur = list.getSelectionModel().getSelectedItem();
            if (cur == null) return;
            var tf = new TextField(cur);
            var ad = new Alert(Alert.AlertType.NONE, "", ButtonType.OK, ButtonType.CANCEL);
            ad.initOwner(getDialogPane().getScene().getWindow());
            ad.initModality(Modality.WINDOW_MODAL);
            ad.setTitle("名称変更");
            ad.setHeaderText("科目区分名を編集");
            ad.getDialogPane().setContent(tf);
            if (ad.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                String v = tf.getText().trim();
                if (!v.isEmpty()) {
                    int idx = list.getSelectionModel().getSelectedIndex();
                    list.getItems().set(idx, v);
                }
            }
        });
        
        btnDel.setOnAction(ev -> {
            int idx = list.getSelectionModel().getSelectedIndex();
            if (idx >= 0) list.getItems().remove(idx);
        });

        var box = new GridPane();
        box.setHgap(8); box.setVgap(8); box.setPadding(new Insets(10));
        int r = 0;
        box.addRow(r++, new Label("プリセット名"), new Label(name));
        box.addRow(r++, new Label("科目区分一覧"), list);
        box.addRow(r++, new Label("操作"), new HBox(8, btnAdd, btnEdit, btnDel));

        var editDlg = new Alert(Alert.AlertType.NONE, "", ButtonType.OK, ButtonType.CANCEL);
        editDlg.initOwner(getDialogPane().getScene().getWindow());
        editDlg.initModality(Modality.WINDOW_MODAL);
        
        editDlg.setTitle("プリセット編集");
        editDlg.setHeaderText("科目区分の集合を編集してください");
        editDlg.getDialogPane().setContent(box);

        if (showChildDialog(editDlg).orElse(ButtonType.CANCEL) == ButtonType.OK) {
            var p = new KindPresetStore.Preset();
            p.name = name;
            p.kinds = new java.util.ArrayList<>(list.getItems());
            KindPresetStore.upsert(p);
            cbKind.getItems().setAll(p.kinds);
            if (!cbKind.getItems().isEmpty()) cbKind.getSelectionModel().selectFirst();
        }
    }

    private void onImportPresetThenChoose(ComboBox<String> cbKind) {
        var owner = getDialogPane().getScene().getWindow();

        var fc = new FileChooser();
        fc.setTitle("大学プリセット JSON を選択");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));

        childDialogShowing = true;
        java.io.File file;
        try {
            file = fc.showOpenDialog(owner);
        } finally {
            childDialogShowing = false;
        }
        if (file == null) return;

        try {
            var res = KindPresetStore.importFromFile(file.toPath());
            var info = new Alert(Alert.AlertType.INFORMATION,
                    "プリセットを取り込みました。\n"
                  + "新規: " + res.addedPresets + " 件\n"
                  + "更新: " + res.updatedPresets + " 件（追加科目区分 " + res.addedKinds + " 件）",
                    ButtonType.OK);
            showChildDialog(info);

            onUsePreset(cbKind);

        } catch (Exception ex) {
            var err = new Alert(Alert.AlertType.ERROR, "インポート失敗: " + ex.getMessage(), ButtonType.OK);
            showChildDialog(err);
        }
    }

    
    
    private <T> Optional<T> showChildDialog(javafx.scene.control.Dialog<T> dlg) {
        Window owner = getDialogPane().getScene().getWindow();
        dlg.initOwner(owner);
        dlg.initModality(Modality.WINDOW_MODAL);
        childDialogShowing = true;
        dlg.setOnHidden(e -> childDialogShowing = false);
        try {
            return dlg.showAndWait();
        } finally {
            childDialogShowing = false;
        }
    }
    
    


private static String nvl(String s){ return s==null?"":s; }
}
