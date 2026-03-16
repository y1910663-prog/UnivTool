package app.univtool.ui.panel1;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import app.univtool.core.CourseNoteStore;
import app.univtool.files.SavedFileIndex;
import app.univtool.model.Course;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;




public class CourseDetailsDialog extends Dialog<Void> {

    public interface ArchiveHandler {
        void onArchived();
    }

    private final Runnable onRefreshParent;  

    public CourseDetailsDialog(Course c, Path workspaceDir, Path archiveDir, ArchiveHandler afterArchive) {
        this(c, workspaceDir, archiveDir, afterArchive, true);
    }

    public CourseDetailsDialog(Course c, Path workspaceDir, Path archiveDir, ArchiveHandler afterArchive, boolean showArchiveButton) {
        this.onRefreshParent = (afterArchive != null ? afterArchive::onArchived : () -> {});

        setTitle("講義の詳細");

//        var closeBtn = new ButtonType("閉じる", ButtonBar.ButtonData.CANCEL_CLOSE);
//        var archiveBtn = new ButtonType("この講義をアーカイブ", ButtonBar.ButtonData.LEFT);
//        if (showArchiveButton) {
//            getDialogPane().getButtonTypes().addAll(archiveBtn, closeBtn);
//        } else {
//            getDialogPane().getButtonTypes().addAll(closeBtn);
//        }

        getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        {
            var hiddenCloseBtn = (javafx.scene.control.Button) getDialogPane().lookupButton(javafx.scene.control.ButtonType.CLOSE);
            if (hiddenCloseBtn != null) {
                hiddenCloseBtn.setVisible(false);
                hiddenCloseBtn.setManaged(false);
            }
        }



        GridPane g = new GridPane();
        g.setHgap(8); g.setVgap(8); g.setPadding(new Insets(12));

        {
            var cc1 = new javafx.scene.layout.ColumnConstraints();
            cc1.setHgrow(javafx.scene.layout.Priority.NEVER);
            cc1.setFillWidth(false);
            cc1.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

            var cc2 = new javafx.scene.layout.ColumnConstraints();
            cc2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
            cc2.setFillWidth(true);

            g.getColumnConstraints().setAll(cc1, cc2);
        }

        populateInfoGrid(g, c);
        

        Map<String, Long> counts = SavedFileIndex.get().countsByKind(c.id);

        var memoTitle = new Label("自由記入欄");
        memoTitle.setStyle("-fx-font-weight: bold;");
        var memoArea = new TextArea(CourseNoteStore.get().loadNote(c.id));
        memoArea.setEditable(false);
        memoArea.setWrapText(true);
        memoArea.setPrefRowCount(6);

        var btnEditMemo = new Button("自由記入欄の編集");
        btnEditMemo.setOnAction(ev -> {
            var ed = new Dialog<ButtonType>();
            ed.setTitle("自由記入欄の編集");
            ed.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            var ta = new TextArea(CourseNoteStore.get().loadNote(c.id));
            ta.setWrapText(true);
            ta.setPrefRowCount(10);
            ed.getDialogPane().setContent(ta);

            var dlgResult = ed.showAndWait().orElse(ButtonType.CANCEL);
            if (dlgResult == ButtonType.OK) {
                CourseNoteStore.get().saveNote(c.id, ta.getText());
                memoArea.setText(ta.getText());
            }

        });

        var btnEditCourse = new Button("講義情報の編集");
        btnEditCourse.setOnAction(ev -> {
            var dlg = new app.univtool.ui.panel1.AddCourseDialog(c);
            var res = dlg.showAndWait();
            res.ifPresent(updated -> {
                updated.id = c.id;
                new app.univtool.repo.CourseRepository().update(updated);

                c.name = updated.name;
                c.day = updated.day;
                c.period = updated.period;
                c.code = updated.code;
                c.grade = updated.grade;
                c.kind = updated.kind;
                c.credit = updated.credit;
                c.teacher = updated.teacher;
                c.email = updated.email;
                c.webpage = updated.webpage;
                c.syllabus = updated.syllabus;
                c.attendanceRequired = updated.attendanceRequired;
                c.examsCsv = updated.examsCsv;
                c.year = updated.year;
                c.term = updated.term;

                populateInfoGrid(g, c);

                new Alert(Alert.AlertType.INFORMATION, "講義情報を更新しました。", ButtonType.OK).showAndWait();
            });
        });


        var spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        var savedRightTitle = new Label("保存済みのファイル");
        savedRightTitle.setStyle("-fx-font-weight: bold;");

        var savedListBox = new VBox(6);

     rebuildSavedList(savedListBox, c);

        var statsTitle = new Label("種類別件数");
        statsTitle.setStyle("-fx-font-weight: bold;");
        var statsBox = new VBox(6);
        if (counts == null || counts.isEmpty()) {
            statsBox.getChildren().add(new Label("（統計なし）"));
        } else {
            for (var e : counts.entrySet()) {
                statsBox.getChildren().add(new HBox(8,
                        new Label(prettyKind(e.getKey())),
                        new Label(String.valueOf(e.getValue()))
                ));
            }
        }

        var btnArchiveRight = new Button("この講義をアーカイブ");

        btnArchiveRight.setDisable(!showArchiveButton);
        btnArchiveRight.setOnAction(evt -> {
            try {
                if (!showArchiveButton) return;
                if (workspaceDir == null || archiveDir == null) {
                    showError("ワークスペース未設定です。先にワークスペースを設定してください。");
                    return;
                }
                String folder = resolveFolderName(c);
                Path src = workspaceDir.resolve(folder);
                Path dst = archiveDir.resolve(folder);

                if (!Files.exists(src)) { showError("元フォルダがありません: " + src); return; }
                if (Files.exists(dst))  { showError("同名フォルダがアーカイブ側に既にあります: " + dst); return; }

                Files.createDirectories(archiveDir);
                Files.move(src, dst);
                new Alert(Alert.AlertType.INFORMATION, "アーカイブへ移動しました:\n" + dst, ButtonType.OK).showAndWait();
                if (afterArchive != null) afterArchive.onArchived();
            } catch (Exception ex) {
                showError("アーカイブに失敗: " + ex.getMessage());
            }
        });

        var btnCloseRight = new Button("閉じる");
        btnCloseRight.setOnAction(e -> {
            var hidden = (javafx.scene.control.Button) getDialogPane().lookupButton(javafx.scene.control.ButtonType.CLOSE);
            if (hidden != null) hidden.fire();
            else close();
        });

        var spacerButtons = new javafx.scene.layout.Region();
        HBox.setHgrow(spacerButtons, javafx.scene.layout.Priority.ALWAYS);
        var rightButtons = new HBox(8, btnArchiveRight, spacerButtons, btnCloseRight);
        rightButtons.setPadding(new Insets(8, 0, 0, 0));

        var sp = new javafx.scene.control.ScrollPane(savedListBox);
        sp.setFitToWidth(true);
        sp.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPrefViewportHeight(420);
        sp.setMaxHeight(420);
        javafx.scene.layout.VBox.setVgrow(sp, javafx.scene.layout.Priority.NEVER);

        var growR = new javafx.scene.layout.Region();
        javafx.scene.layout.VBox.setVgrow(growR, javafx.scene.layout.Priority.ALWAYS);

        var btnAddFile = new Button("ファイルを追加");
        btnAddFile.setOnAction(ev -> {
            openFileSavePanelFixedCourse(c, () -> {
                rebuildSavedList(savedListBox, c);
                if (onRefreshParent != null) onRefreshParent.run();
            });
        });

        var rightPane = new VBox(
                10,
                savedRightTitle,
                sp,
                btnAddFile,
                new Separator(),
                statsTitle,
                statsBox,
                growR,
                rightButtons
        );
        rightPane.setPadding(new Insets(12));
        rightPane.setMaxWidth(520);

        var leftPane = new VBox(10,
                g,
                btnEditCourse,
                new Separator(),
                memoTitle,
                memoArea,
                btnEditMemo
        );
        leftPane.setPadding(new Insets(12));
        javafx.scene.layout.VBox.setVgrow(memoArea, javafx.scene.layout.Priority.ALWAYS);

        var split = new javafx.scene.control.SplitPane();
        split.getItems().addAll(leftPane, rightPane);
        split.setDividerPositions(0.55);

        getDialogPane().setContent(split);
        setOnHidden(ev -> onRefreshParent.run());

     setOnShown(ev -> {
         var w = getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow();
         if (w != null) {
             w.setOnHidden(e -> {
                 if (onRefreshParent != null) onRefreshParent.run();
             });
         }
     });



        


        
//        setOnShown(ev -> {
//            var w = getDialogPane().getScene().getWindow();
//            w.focusedProperty().addListener((o, was, is) -> { if (!is) close(); });
//        });

//        if (showArchiveButton) {
//        	var archiveNode = (Button) getDialogPane().lookupButton(archiveBtn);
//        	archiveNode.setOnAction(evt -> {
//        	    try {
//        	        if (workspaceDir == null || archiveDir == null) {
//        	            showError("ワークスペース未設定です。先にワークスペースを設定してください。");
//        	            return;
//        	        }
//        	        String folder = resolveFolderName(c);              // ★ 追加：folder優先のフォールバック
//        	        Path src = workspaceDir.resolve(folder);
//        	        Path dst = archiveDir.resolve(folder);
//
//        	        if (!Files.exists(src)) { showError("元フォルダがありません: " + src); return; }
//        	        if (Files.exists(dst))  { showError("同名フォルダがアーカイブ側に既にあります: " + dst); return; }
//
//        	        Files.createDirectories(archiveDir);
//        	        Files.move(src, dst);                              // ★ リネームなしでそのまま移動
//        	        new Alert(Alert.AlertType.INFORMATION, "アーカイブへ移動しました:\n" + dst, ButtonType.OK).showAndWait();
//        	        if (afterArchive != null) afterArchive.onArchived();
//        	    } catch (Exception ex) {
//        	        showError("アーカイブに失敗: " + ex.getMessage());
//        	    }
//        	});
//        }
    }
    

    private static void populateInfoGrid(GridPane g, Course c) {
        g.getChildren().clear();
        int r = 0;

        g.addRow(r++, keyLabel("授業科目名"), new Label(nv(c.name)));
        g.addRow(r++, keyLabel("曜日"), new Label(nv(c.day)));
        g.addRow(r++, keyLabel("時限"), new Label(nv(c.period)));
        g.addRow(r++, keyLabel("開講年度"), new Label(c.year == null ? "" : String.valueOf(c.year)));
        g.addRow(r++, keyLabel("開講期"), new Label(nv(c.term)));


        g.add(new Separator(), 0, r++, 2, 1);

        g.addRow(r++, keyLabel("科目番号"), new Label(nv(c.code)));
        g.addRow(r++, keyLabel("開講年次"), new Label(c.grade == null ? "" : String.valueOf(c.grade)));
        g.addRow(r++, keyLabel("科目区分"), new Label(c.kind == null ? "" : c.kind));
        g.addRow(r++, keyLabel("単位数"), new Label(c.credit == null ? "" : String.valueOf(c.credit)));
        g.addRow(r++, keyLabel("担当教員名"), new Label(nv(c.teacher)));
        g.addRow(r++, keyLabel("公開E-mail"), buildCopyCell(nv(c.email)));
        g.addRow(r++, keyLabel("授業関連Webページ"), buildUrlCell(nv(c.webpage)));
        g.addRow(r++, keyLabel("シラバスURL"), buildUrlCell(nv(c.syllabus)));
        g.addRow(r++, keyLabel("出席確認の有無"), new Label(c.attendanceRequired ? "出席有" : "出席無"));
        g.addRow(r++, keyLabel("定期試験・レポートの有無"), new Label(nv(c.examsCsv)));
    }

    

    private static Label keyLabel(String text) {
        var l = new Label(text);
        l.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return l;
    }
    
    private static HBox buildUrlCell(String url) {
        boolean empty = (url == null || url.isBlank());
        String label = empty ? "（未設定）" : url.trim();

        var link = new Hyperlink(label);
        link.setDisable(empty);
        link.setOnAction(e -> openExternal(url));

        link.setTextOverrun(OverrunStyle.ELLIPSIS);
        link.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(link, Priority.ALWAYS);
        if (!empty) link.setTooltip(new Tooltip(label));

        var copyBtn = new Button("コピー");
        copyBtn.setDisable(empty);
        copyBtn.setOnAction(e -> copyToClipboard(label));

        var box = new HBox(8, link, copyBtn);
        return box;
    }


    private static void copyToClipboard(String s) {
        if (s == null || s.isBlank()) return;
        Clipboard cb = Clipboard.getSystemClipboard();
        ClipboardContent cc = new ClipboardContent();
        cc.putString(s);
        cb.setContent(cc);
        new Alert(Alert.AlertType.INFORMATION, "URLをコピーしました。", ButtonType.OK).showAndWait();
    }

    private static void openExternal(String raw) {
        try {
            if (raw == null || raw.isBlank()) return;
            String u = raw.trim();
            if (!u.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
                u = "https://" + u;
            }
            URI uri = new URI(u);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(uri);
            } else {
                copyToClipboard(u);
                new Alert(Alert.AlertType.INFORMATION, "ブラウザで開けない環境のため、URLをコピーしました。", ButtonType.OK).showAndWait();
            }
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "URLを開けませんでした:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private static HBox buildCopyCell(String text) {
        boolean empty = (text == null || text.isBlank());
        String v = empty ? "（未設定）" : text.trim();

        var lbl = new Label(v);
        lbl.setTextOverrun(OverrunStyle.ELLIPSIS);
        lbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lbl, Priority.ALWAYS);
        if (!empty) lbl.setTooltip(new Tooltip(v));

        var copyBtn = new Button("コピー");
        copyBtn.setDisable(empty);
        copyBtn.setOnAction(e -> copyToClipboard(v));

        return new HBox(8, lbl, copyBtn);
    }

    
    private static String resolveFolderName(app.univtool.model.Course c) {
        if (c.folder != null && !c.folder.isBlank()) return c.folder;
        return sanitize(c.name);
    }
    private static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    private static String nv(String s) { return (s == null ? "" : s); }

    private static void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
    private static String prettyKind(String name) {

        return switch (name) {
            case "課題_テキスト" -> "課題（テキスト）";
            case "課題_未提出" -> "課題（未提出）";
            case "課題_提出済み" -> "課題（提出済み）";
            default -> name;
        };
    }
    
    private static void openFileDirect(java.nio.file.Path p) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(p.toFile());
            } else {
                String os = System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT);
                if (os.contains("mac")) {
                    new ProcessBuilder("open", p.toString()).start();
                } else if (os.contains("win")) {
                    new ProcessBuilder("cmd","/c","start","","", p.toString()).start();
                } else {
                    new ProcessBuilder("xdg-open", p.toString()).start();
                }
            }
        } catch (Exception ex) {
            showError("ファイルを開けませんでした:\n" + ex.getMessage());
        }
    }
    
    private void rebuildSavedList(VBox savedListBox, Course c) {
        savedListBox.getChildren().clear();

        var entries = app.univtool.files.SavedFileIndex.get().list().stream()
                .filter(e -> java.util.Objects.equals(e.courseId, c.id))
                .toList();

        if (entries.isEmpty()) {
            savedListBox.getChildren().add(new Label("（この講義のファイルはまだ保存されていません）"));
            return;
        }

        for (var e : entries) {
            String fname;
            try { fname = java.nio.file.Path.of(e.path).getFileName().toString(); }
            catch (Exception ex) { fname = e.path; }

            var kindLbl = new Label(prettyKind(e.kind));

            var nameLbl = new Label(fname);
            nameLbl.setTooltip(new Tooltip(e.path));
            nameLbl.setTextOverrun(OverrunStyle.ELLIPSIS);
            nameLbl.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nameLbl, Priority.ALWAYS);

            nameLbl.setStyle("-fx-text-fill:#3367d6; -fx-underline:true; -fx-cursor:hand;");
            final String baseStyle = nameLbl.getStyle();
            final String HOVER_CSS =
                    "; -fx-background-color: rgba(25,118,210,0.08);" +
                    "  -fx-background-radius: 6;" +
                    "  -fx-padding: 1 4;" +
                    "  -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 6, 0, 0, 1);";
            nameLbl.setOnMouseEntered(ev -> nameLbl.setStyle(baseStyle + HOVER_CSS));
            nameLbl.setOnMouseExited (ev -> nameLbl.setStyle(baseStyle));

            final String pathStr = e.path;
            nameLbl.setOnMouseClicked(ev -> {
                if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) {
                    java.nio.file.Path p = java.nio.file.Paths.get(pathStr);
                    if (!java.nio.file.Files.exists(p)) {
                        showError("ファイルが見つかりません:\n" + pathStr);
                        return;
                    }
                    openFileDirect(p);
                }
            });

            savedListBox.getChildren().add(new HBox(8, kindLbl, nameLbl));
        }
    }

    private void openFileSavePanelFixedCourse(Course course, Runnable afterClose) {
        try {
        	final Class<?> panelClass = Class.forName("app.univtool.ui.panel2.PanelFileSave");
        	final Object pane = createPanelFileSave(panelClass, course);

        	var st = new Stage();
        	st.setTitle("ファイルを保存 - " + nv(course.name));
        	st.initOwner(getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow());
        	st.initModality(Modality.WINDOW_MODAL);
        	st.setScene(new Scene((javafx.scene.Parent) pane, 900, 600));
        	st.show();


            st.setOnHidden(ev -> { if (afterClose != null) afterClose.run(); });

        } catch (Exception ex) {
            showError("ファイル保存パネルを開けませんでした:\n" + ex.getMessage());
        }
    }

    private static Object createPanelFileSave(Class<?> panelClass, Course course) throws Exception {
        try {
            return panelClass.getConstructor(app.univtool.model.Course.class).newInstance(course);
        } catch (NoSuchMethodException e) {
            return panelClass.getConstructor().newInstance();
        }
    }
        
}
