package app.univtool.ui.panel2;

import java.util.List;

import app.univtool.model.Course;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;



public class ClassifyDialog extends Dialog<ClassifyDialog.Result> {
    public record Result(Course chosenCourse, int nth, Kind kind, boolean setDeadline) {}
    public enum Kind { 教材, 課題_テキスト, 課題_未提出, 課題_提出済み, 試験問題, その他; 
        @Override public String toString(){
            return switch (this){
                case 教材 -> "教材"; case 課題_テキスト -> "課題（テキスト）"; case 課題_未提出 -> "課題（未提出）";
                case 課題_提出済み -> "課題（提出済み）"; case 試験問題 -> "試験問題"; case その他 -> "その他";};}
    }
    private static final String OPTION_ADD_COURSE = "（講義の追加…）";

    public ClassifyDialog(List<Course> courses){
        setTitle("分類");
        var ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("キャンセル", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);

        var cbCourse = new ComboBox<String>();
        for (Course c : courses) cbCourse.getItems().add(c.name);
        cbCourse.getItems().add(OPTION_ADD_COURSE);
        cbCourse.getSelectionModel().selectFirst();

        var spNth = new Spinner<Integer>(1, 1000, 1); spNth.setEditable(true);
        var cbKind = new ComboBox<Kind>(FXCollections.observableArrayList(Kind.values()));
        cbKind.getSelectionModel().select(Kind.教材);

        var cbDeadline = new CheckBox("提出期限を設定する"); cbDeadline.setSelected(false);

        var gp = new GridPane(); gp.setHgap(10); gp.setVgap(8); gp.setPadding(new Insets(12));
        gp.addRow(0, new Label("授業科目名"), cbCourse);
        gp.addRow(1, new Label("第何回"), spNth);
        gp.addRow(2, new Label("種類"), cbKind);
        gp.addRow(3, new Label("提出期限"), cbDeadline);
        getDialogPane().setContent(gp);

        setResultConverter(bt -> {
            if (bt != ok) return null;
            String sel = cbCourse.getValue();
            Course chosen = null;
            if (OPTION_ADD_COURSE.equals(sel)) {
                return new Result(null, spNth.getValue(), cbKind.getValue(), cbDeadline.isSelected());
            } else {
                chosen = courses.stream().filter(c -> sel.equals(c.name)).findFirst().orElse(null);
            }
            return new Result(chosen, spNth.getValue(), cbKind.getValue(), cbDeadline.isSelected());
        });
    }


    public ClassifyDialog(List<Course> courses, Course defaultCourse, boolean lockCourse) {
        this(courses, defaultCourse, 1, Kind.教材, false, lockCourse);
    }

    public ClassifyDialog(List<Course> courses, Course defaultCourse, int defaultNth,
                          Kind defaultKind, boolean defaultDeadlineOn, boolean lockCourse) {
        this(courses, defaultCourse, defaultNth, defaultKind, defaultDeadlineOn, lockCourse, /*lockNth*/ false, /*allowedKinds*/ null);
    }
    
    public ClassifyDialog(List<Course> courses, Course defaultCourse, int defaultNth,
                          Kind defaultKind, boolean defaultDeadlineOn,
                          boolean lockCourse, boolean lockNth,
                          java.util.List<Kind> allowedKinds) {
        setTitle("分類");
        var ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("キャンセル", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);

        var cbCourse = new ComboBox<String>();
        for (Course c : courses) cbCourse.getItems().add(c.name);
        cbCourse.getItems().add(OPTION_ADD_COURSE);

        if (defaultCourse != null) {
            int idx = -1;
            for (int i = 0; i < courses.size(); i++) {
                Course ci = courses.get(i);
                if (defaultCourse.id != null && defaultCourse.id.equals(ci.id)) { idx = i; break; }
            }
            if (idx < 0) {
                for (int i = 0; i < courses.size(); i++) {
                    Course ci = courses.get(i);
                    if (ci.name != null && ci.name.equals(defaultCourse.name)) { idx = i; break; }
                }
            }
            if (idx >= 0) cbCourse.getSelectionModel().select(idx);
            else cbCourse.getSelectionModel().selectFirst();
        } else {
            cbCourse.getSelectionModel().selectFirst();
        }

        var spNth = new Spinner<Integer>(1, 1000, Math.max(1, defaultNth)); spNth.setEditable(true);

        var kinds = (allowedKinds == null || allowedKinds.isEmpty())
                ? FXCollections.observableArrayList(Kind.values())
                : FXCollections.observableArrayList(allowedKinds);
        var cbKind = new ComboBox<Kind>(kinds);
        cbKind.getSelectionModel().select(defaultKind == null
                ? (kinds.isEmpty()? null : kinds.get(0))
                : defaultKind);
        if (cbKind.getValue() == null && !kinds.isEmpty()) cbKind.getSelectionModel().selectFirst();

        var cbDeadline = new CheckBox("提出期限を設定する"); cbDeadline.setSelected(defaultDeadlineOn);

        var gp = new GridPane(); gp.setHgap(10); gp.setVgap(8); gp.setPadding(new Insets(12));
        gp.addRow(0, new Label("授業科目名"), cbCourse);
        gp.addRow(1, new Label("第何回"), spNth);
        gp.addRow(2, new Label("種類"), cbKind);
        gp.addRow(3, new Label("提出期限"), cbDeadline);
        getDialogPane().setContent(gp);

        if (lockCourse && defaultCourse != null) cbCourse.setDisable(true);
        if (lockNth) spNth.setDisable(true);

        setResultConverter(bt -> {
            if (bt != ok) return null;
            String sel = cbCourse.getValue();
            Course chosen = null;
            if (OPTION_ADD_COURSE.equals(sel)) {
                return new Result(null, spNth.getValue(), cbKind.getValue(), cbDeadline.isSelected());
            } else {
                chosen = courses.stream().filter(c -> sel.equals(c.name)).findFirst().orElse(null);
            }
            return new Result(chosen, spNth.getValue(), cbKind.getValue(), cbDeadline.isSelected());
        });
    }


}
