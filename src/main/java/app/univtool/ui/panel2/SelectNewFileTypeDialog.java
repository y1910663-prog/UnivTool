// ==================================================
// File: app/univtool/ui/panel2/SelectNewFileTypeDialog.java
// ==================================================
package app.univtool.ui.panel2;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;


public class SelectNewFileTypeDialog extends Dialog<SelectNewFileTypeDialog.Result> {

    public sealed interface Result permits TypeResult, TemplateResult {}
    public static final class TypeResult implements Result {
        public final NewType type;
        public TypeResult(NewType t){ this.type=t; }
    }
    public static final class TemplateResult implements Result {
        public final Path templatePath;
        public TemplateResult(Path p){ this.templatePath=p; }
    }

    public enum NewType {
        WORD("ワードドキュメント (.docx)", ".docx"),
        EXCEL("エクセルワークブック (.xlsx)", ".xlsx"),
        POWERPOINT("パワーポイント (.pptx)", ".pptx"),
        TEXT("テキスト (.txt)", ".txt"),
        MARKDOWN("Markdown (.md)", ".md"),
        RTF("リッチテキスト形式 (.rtf)", ".rtf");

        private final String label; private final String ext;
        NewType(String label, String ext){ this.label=label; this.ext=ext; }
        public String label(){return label;}
        public String defaultExtension(){return ext;}
        @Override public String toString(){return label;}
    }

    private final Path workspaceDir;

    public SelectNewFileTypeDialog(Path workspaceDir){
        this.workspaceDir = workspaceDir;

        setTitle("新規ファイルの作成");
        var ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("キャンセル", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);

        var cb = new ChoiceBox<NewType>();
        cb.getItems().addAll(NewType.values());
        cb.getSelectionModel().selectFirst();

        var btnFromTpl = new Button("テンプレートから作成");

        btnFromTpl.setOnAction(e -> {
            try {
                Path tplDir = (workspaceDir == null ? Path.of(".") : workspaceDir).resolve("template");
                Files.createDirectories(tplDir);
                var chooser = new FileChooser();
                chooser.setTitle("テンプレートを選択");
                chooser.setInitialDirectory(tplDir.toFile());
                chooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("すべてのファイル", "*.*"),
                        new FileChooser.ExtensionFilter("Office / ドキュメント",
                                "*.docx","*.xlsx","*.pptx","*.txt","*.md","*.rtf","*.pdf")
                );
                File f = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
                if (f != null) {
                    setResult(new TemplateResult(f.toPath()));
                    close();
                }
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "テンプレート読み込みエラー: " + ex.getMessage(), ButtonType.OK).showAndWait();
            }
        });

        var gp = new GridPane();
        gp.setHgap(10); gp.setVgap(8); gp.setPadding(new Insets(12));
        gp.addRow(0, new Label("種類"), cb);
        gp.addRow(1, new Label(""), new HBox(8, btnFromTpl));
        getDialogPane().setContent(gp);

        setResultConverter(bt -> bt==ok ? new TypeResult(cb.getValue()) : null);
    }

    public SelectNewFileTypeDialog(){
        this(null);
    }
}



//public class SelectNewFileTypeDialog extends Dialog<SelectNewFileTypeDialog.NewType> {
//	public enum NewType {
//	    WORD("ワードドキュメント (.docx)", ".docx"),
//	    EXCEL("エクセルワークブック (.xlsx)", ".xlsx"),
//	    POWERPOINT("パワーポイント (.pptx)", ".pptx"),
//	    TEXT("テキスト (.txt)", ".txt"),
//	    MARKDOWN("Markdown (.md)", ".md"),
//	    RTF("リッチテキスト形式 (.rtf)", ".rtf");
//
//        private final String label; private final String ext;
//        NewType(String label, String ext){ this.label=label; this.ext=ext; }
//        public String label(){return label;}
//        public String defaultExtension(){return ext;}
//        @Override public String toString(){return label;}
//    }
//    public SelectNewFileTypeDialog(){
//        setTitle("新規ファイルの作成");
//        var ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
//        var cancel = new ButtonType("キャンセル", ButtonBar.ButtonData.CANCEL_CLOSE);
//        getDialogPane().getButtonTypes().addAll(ok, cancel);
//        var cb = new ChoiceBox<NewType>();
//        cb.getItems().addAll(NewType.values());
//        cb.getSelectionModel().selectFirst();
//        var gp = new GridPane();
//        gp.setHgap(10); gp.setVgap(8); gp.setPadding(new Insets(12));
//        gp.addRow(0, new Label("種類"), cb);
//        getDialogPane().setContent(gp);
//        setResultConverter(bt -> bt==ok ? cb.getValue() : null);
//    }
//}