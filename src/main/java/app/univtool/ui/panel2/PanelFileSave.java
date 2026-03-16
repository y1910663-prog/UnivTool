// =============================================
// File: app/univtool/ui/panel2/PanelFileSave.java
// =============================================
package app.univtool.ui.panel2;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import app.univtool.core.SettingsStore;
import app.univtool.core.WorkspaceService;
import app.univtool.files.SavedFileIndex;
import app.univtool.model.Course;
import app.univtool.repo.CourseRepository;
import app.univtool.ui.panel1.AddCourseDialog;
import app.univtool.util.Dialogs;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;


public class PanelFileSave extends BorderPane {
    private final CourseRepository courseRepo = new CourseRepository();
    private Path workspaceDir;
    private final javafx.scene.control.ListView<String> logList = new javafx.scene.control.ListView<>();
    private static final int LOG_MAX = 500;
    private FollowupDefaults followup;
    
    private boolean followupLockCourse = false;
    private boolean followupLockNth = false;
    private java.util.List<ClassifyDialog.Kind> followupAllowedKinds = null;

    public PanelFileSave() {
        setPadding(new Insets(16));
        var s = SettingsStore.load();
        if (s.workspaceDir == null || s.workspaceDir.isBlank()) {
            var wp = WorkspaceService.ensureDefault();
            workspaceDir = wp.workspace();
        } else {
            workspaceDir = Path.of(s.workspaceDir);
            try { Files.createDirectories(workspaceDir); } catch (Exception ignored) {}
        }

        var title = new Label("ファイル保存");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        var btnNew = new Button("ファイルの新規作成");
        var btnDrop = new Button("ファイルを選択");
        btnNew.setMaxWidth(Double.MAX_VALUE);
        btnDrop.setMaxWidth(Double.MAX_VALUE);
        btnNew.setPrefHeight(48);
        btnDrop.setPrefHeight(48);
        btnDrop.setOnAction(e -> pickFilesViaChooser(getScene() == null ? null : getScene().getWindow()));

        var info = new Label("ここにファイルをドロップしてもOK（複数可）\n※ ドロップ/新規作成後に分類ダイアログが開きます");
        info.setStyle("-fx-text-fill: #666;");

     // 小さめのテンプレ関連ボタン
        var btnTplReg = new Button("テンプレートファイルを登録");
        btnTplReg.setOnAction(ev -> registerTemplateFiles(getScene() == null ? null : getScene().getWindow()));
        btnTplReg.setPrefHeight(28);
        btnTplReg.setMaxWidth(Region.USE_PREF_SIZE);
        btnTplReg.setStyle("-fx-font-size: 11px; -fx-padding: 4 10 4 10;");

        var btnTplManage = new Button("テンプレートファイルの管理");
        btnTplManage.setOnAction(ev -> manageTemplates(getScene() == null ? null : getScene().getWindow()));
        btnTplManage.setPrefHeight(28);
        btnTplManage.setMaxWidth(Region.USE_PREF_SIZE);
        btnTplManage.setStyle("-fx-font-size: 11px; -fx-padding: 4 10 4 10;");

        var center = new VBox(12, title, btnNew, btnDrop, info, btnTplReg, btnTplManage);
        center.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(btnDrop, Priority.NEVER);
        setCenter(center);

        
        var btnDetect = new Button("未登録ファイルを検出して登録");
        btnDetect.setOnAction(ev -> detectUnregisteredFiles());

        var bottomBar = new HBox(8, btnDetect);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setPadding(new Insets(8, 0, 0, 0));

        // ログ
        var lblLog = new Label("ファイル追加ログ");
        lblLog.setStyle("-fx-font-weight: bold;");

        logList.setPlaceholder(new Label("（まだログはありません）"));
        logList.setFocusTraversable(false);
        logList.setPrefHeight(140);

        // ログブロック
        var logBlock = new VBox(6, lblLog, logList);
        logBlock.setPadding(new Insets(0, 0, 0, 0));

        // 下部
        var bottomWrapper = new VBox(8,
                new Separator(),   // 上と本文の区切り
                logBlock,          // ログ
                new Separator(),   // ログと操作の区切り
                bottomBar          // 検出ボタン
        );
        setBottom(bottomWrapper);

        // ドロップ領域
        this.setOnDragOver(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        this.setOnDragDropped(this::onFilesDropped);

        btnDrop.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        btnDrop.setOnDragDropped(this::onFilesDropped);

        btnNew.setOnAction(e -> openNewFileFlow(getScene() == null ? null : getScene().getWindow()));
        detectUnregisteredFiles();
    }
    
    public PanelFileSave(FollowupDefaults defaults) {
        this();
        this.followup = defaults;
    }

    private void onFilesDropped(DragEvent e) {
        Dragboard db = e.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;
            List<File> files = db.getFiles();
            for (File f : files) {
                if (followup != null) {
                    openClassifyThenSave(
                        Imported.newFromExisting(f.toPath()),
                        followup.course, followup.nth,
                        ClassifyDialog.Kind.課題_未提出,
                        /*期限チェック既定*/ followup.deadline != null,
                        followup.deadline
                    );
                } else {
                    openClassifyThenSave(Imported.newFromExisting(f.toPath()));
                }
            }
        }
        e.setDropCompleted(success);
        e.consume();
    }

    private void openNewFileFlow(Window owner) {
        var dialog = new SelectNewFileTypeDialog(workspaceDir);
        dialog.initOwner(owner);
        var res = dialog.showAndWait();
        if (res.isEmpty()) return;

        var r = res.get();
        if (r instanceof SelectNewFileTypeDialog.TypeResult tr) {
            var imp = Imported.newToBeCreated(tr.type);
            if (followup != null) {
                openClassifyThenSave(imp, followup.course, followup.nth,
                        ClassifyDialog.Kind.課題_未提出,
                        /*期限チェック既定*/ followup.deadline != null,
                        followup.deadline);
            } else {
                openClassifyThenSave(imp);
            }
        } else if (r instanceof SelectNewFileTypeDialog.TemplateResult tr) {
            var imp = Imported.newFromExisting(tr.templatePath);
            if (followup != null) {
                openClassifyThenSave(imp, followup.course, followup.nth,
                        ClassifyDialog.Kind.課題_未提出,
                        /*期限チェック既定*/ followup.deadline != null,
                        followup.deadline);
            } else {
                openClassifyThenSave(imp);
            }
        }
    }


    private void openClassifyThenSave(Imported imported) {
        // 分類ダイアログ
        var classify = new ClassifyDialog(fetchCoursesWithAddSlot());
        var res1 = classify.showAndWait();
        if (res1.isEmpty()) return;
        ClassifyDialog.Result csel = res1.get();

        // 講義の確定
        Course course = csel.chosenCourse();
        if (course == null) {
            var add = new AddCourseDialog();
            var added = add.showAndWait();
            if (added.isEmpty()) return;
            int id = courseRepo.insert(added.get());
            var cc = added.get();
            cc.id = id;

            String folder = FileSaveOps.buildFolderNameFromId(cc);
            try {
                Path dst = workspaceDir.resolve(folder);
                Files.createDirectories(dst);
            } catch (Exception ex) {
                Dialogs.error("エラー", "講義フォルダの作成に失敗: " + ex.getMessage());
                return;
            }
            cc.folder = folder;
            courseRepo.updateFolder(cc.id, cc.folder);
            course = cc;
        }

        // 保存処理本体へ
        proceedSave(imported, course, csel);
    }
    
    private void openClassifyThenSave(Imported imported, Course defaultCourse, int defaultNth,
                                      ClassifyDialog.Kind defaultKind, boolean defaultDeadlineOn,
                                      java.time.LocalDate defaultDeadline) {
        var courses = fetchCoursesWithAddSlot();
        var classify = new ClassifyDialog(
                courses,
                defaultCourse,
                defaultNth,
                defaultKind,
                defaultDeadlineOn,
                /* lockCourse */ true,
                /* lockNth    */ this.followupLockNth,
                /* allowed    */ this.followupAllowedKinds
        );

        var res1 = classify.showAndWait();
        if (res1.isEmpty()) return;
        ClassifyDialog.Result csel = res1.get();

        Course course = csel.chosenCourse();
        if (course == null) {
            var add = new app.univtool.ui.panel1.AddCourseDialog();
            var added = add.showAndWait();
            if (added.isEmpty()) return;
            int id = courseRepo.insert(added.get());
            var cc = added.get(); cc.id = id;
            String folder = FileSaveOps.buildFolderNameFromId(cc);
            try { java.nio.file.Files.createDirectories(workspaceDir.resolve(folder)); } catch (Exception ex) {
                Dialogs.error("エラー", "講義フォルダの作成に失敗: " + ex.getMessage()); return;
            }
            cc.folder = folder;
            courseRepo.updateFolder(cc.id, cc.folder);
            course = cc;
        }

        proceedSave(imported, course, csel, defaultDeadline);
    }


    private void openClassifyThenSave(Imported imported, Course preselectCourse) {
        var courses = fetchCoursesWithAddSlot();
        var classify = (preselectCourse == null)
                ? new ClassifyDialog(courses)
                : new ClassifyDialog(courses, preselectCourse, /* lockCourse = */ false);


        var res1 = classify.showAndWait();
        if (res1.isEmpty()) return;
        ClassifyDialog.Result csel = res1.get();
        Course course = csel.chosenCourse();
        if (course == null) {
            var add = new app.univtool.ui.panel1.AddCourseDialog();
            var added = add.showAndWait();
            if (added.isEmpty()) return;
            int id = courseRepo.insert(added.get());
            var cc = added.get(); cc.id = id;
            String folder = FileSaveOps.buildFolderNameFromId(cc);
            try { java.nio.file.Files.createDirectories(workspaceDir.resolve(folder)); } catch (Exception ex) {
                Dialogs.error("エラー", "講義フォルダの作成に失敗: " + ex.getMessage()); return;
            }
            cc.folder = FileSaveOps.buildFolderNameFromId(cc);
            courseRepo.updateFolder(cc.id, cc.folder);
            course = cc;
        }
        proceedSave(imported, course, csel);
    }

    
    private void pickFilesViaChooser(Window owner) {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle("ファイルを選択");
        chooser.getExtensionFilters().addAll(
        	    new javafx.stage.FileChooser.ExtensionFilter("すべてのファイル", "*.*"),
        	    new javafx.stage.FileChooser.ExtensionFilter("ドキュメント", "*.txt", "*.md", "*.rtf",
        	        "*.pdf", "*.doc", "*.docx", "*.xlsx", "*.xls", "*.ppt", "*.pptx")
        	);

        java.util.List<java.io.File> files = chooser.showOpenMultipleDialog(owner);
        if (files == null || files.isEmpty()) return;
        for (java.io.File f : files) {
            if (followup != null) {
                openClassifyThenSave(
                    Imported.newFromExisting(f.toPath()),
                    followup.course, followup.nth,
                    ClassifyDialog.Kind.課題_未提出,
                    /*期限チェック既定*/ followup.deadline != null,
                    followup.deadline
                );
            } else {
                openClassifyThenSave(Imported.newFromExisting(f.toPath()));
            }
        }
    }
    
    private void proceedSave(Imported imported, Course course, ClassifyDialog.Result csel) {
        proceedSave(imported, course, csel, null);
    }
    
    private void proceedSave(Imported imported, Course course, ClassifyDialog.Result csel,
            java.time.LocalDate preferredDeadline) {
    	Path courseFolder = workspaceDir.resolve(FileSaveOps.resolveCourseFolderName(course));
    	try { Files.createDirectories(courseFolder); } catch (Exception ignored) {}
    	
    	try {
    		java.time.LocalDate chosenDeadline = null;
    		if (csel.setDeadline()) {
    			var ddl = new DeadlineDialog(preferredDeadline);
    			var r = ddl.showAndWait();
    			if (r.isPresent()) chosenDeadline = r.get();
    			}
            var rename = new RenameDialog(course, csel.nth(), csel.kind(), imported.getSuggestedExt());
            var res2 = rename.showAndWait();
            if (res2.isEmpty()) return;
            String filename = res2.get().filename();

            if (filename == null || filename.isBlank()) {
                if (imported.mode() == Imported.Mode.EXISTING) {
                    filename = imported.existingPath().getFileName().toString();
                } else {
                    filename = "新規ファイル" + imported.getSuggestedExt();
                }
            }
            filename = FileSaveOps.sanitize(filename);

            Path finalPath;

            if (imported.mode() == Imported.Mode.EXISTING) {
                Path src = imported.existingPath();
                boolean sameFolder = (src != null && src.getParent() != null && src.getParent().equals(courseFolder));
                if (sameFolder) {
                    if (src.getFileName().toString().equals(filename)) {
                        finalPath = src;
                    } else {
                        Path target = FileSaveOps.ensureUnique(courseFolder, filename);
                        Files.move(src, target);
                        finalPath = target;
                    }
                } else {
                    Path target = FileSaveOps.ensureUnique(courseFolder, filename);
                    Files.copy(src, target);
                    finalPath = target;
                }
            } else {
                Path target = FileSaveOps.ensureUnique(courseFolder, filename);
                FileSaveOps.createNewFileByType(target, imported.newType());
                finalPath = target;
            }

            try {
                SavedFileIndex.get().addEntry(
                        course.id,
                        csel.nth(),
                        csel.kind().name(),
                        finalPath.toString(),
                        chosenDeadline
                );
            } catch (Exception ignored) {}

            String action;
            if (imported.mode() == Imported.Mode.EXISTING) {
                Path src = imported.existingPath();
                if (src != null && src.getParent() != null && src.getParent().equals(courseFolder)) {
                    action = src.getFileName().toString().equals(finalPath.getFileName().toString())
                            ? "既存（変更なし）"
                            : "リネーム";
                } else {
                    action = "取り込み（コピー）";
                }
            } else {
                action = "新規作成";
            }

            logAdded(finalPath, course, csel.nth(), csel.kind(), action, chosenDeadline);

            Dialogs.info("保存完了", "保存しました:\n" + finalPath);

            if (csel.kind() == ClassifyDialog.Kind.課題_テキスト) {
                var conf = new Alert(Alert.AlertType.CONFIRMATION,
                        "同時に「課題（未提出）」ファイルを作成しますか？",
                        ButtonType.YES, ButtonType.NO).showAndWait();
                if (conf.isPresent() && conf.get() == ButtonType.YES) {
                    var stage = new javafx.stage.Stage();
                    var pane = new PanelFileSave(new FollowupDefaults(course, csel.nth(), chosenDeadline));
                    stage.setTitle("ファイル保存（課題・未提出）");
                    stage.setScene(new javafx.scene.Scene(pane, 720, 520));
                    stage.initOwner(getScene() == null ? null : getScene().getWindow());
                    stage.initModality(javafx.stage.Modality.NONE);
                    stage.show();
                }
            }
        } catch (Exception ex) {
            Dialogs.error("保存エラー", ex.getMessage());
        }
    }
    
    private void detectUnregisteredFiles() {
        try {
            var idx = SavedFileIndex.get();
            var courses = courseRepo.findAll();
            var targets = new java.util.ArrayList<java.util.AbstractMap.SimpleEntry<Course, Path>>();

            for (Course c : courses) {
                Path folder = workspaceDir.resolve(FileSaveOps.resolveCourseFolderName(c));
                if (!java.nio.file.Files.isDirectory(folder)) continue;

                try (var stream = java.nio.file.Files.list(folder)) {
                    stream.filter(java.nio.file.Files::isRegularFile).forEach(p -> {
                        if (!idx.isRegistered(p)) {
                            targets.add(new java.util.AbstractMap.SimpleEntry<>(c, p));
                        }
                    });
                } catch (Exception ignore) {}
            }

            if (targets.isEmpty()) return;

            var alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "未登録のファイルが検出されました。登録しますか？ (" + targets.size() + " 件)",
                    ButtonType.OK, ButtonType.CANCEL);
            var res = alert.showAndWait();
            if (res.isEmpty() || res.get() != ButtonType.OK) return;

            for (var pair : targets) {
                Course course = pair.getKey();
                Path file = pair.getValue();
                openClassifyThenSave(Imported.newFromExisting(file), course);
            }
        } catch (Exception ignore) {
        }
    }
    
    private void registerTemplateFiles(Window owner) {
        try {
            Path tplDir = workspaceDir.resolve("template");
            Files.createDirectories(tplDir);

            var chooser = new FileChooser();
            chooser.setTitle("テンプレートに追加するファイルを選択");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("すべてのファイル", "*.*"),
                    new FileChooser.ExtensionFilter("Office / ドキュメント",
                            "*.docx","*.xlsx","*.pptx","*.txt","*.md","*.rtf","*.pdf")
            );
            var files = chooser.showOpenMultipleDialog(owner);
            if (files == null || files.isEmpty()) return;

            for (var f : files) {
                String newName = promptTemplateName(f.getName());
                if (newName == null || newName.isBlank()) newName = f.getName();
                newName = FileSaveOps.sanitize(newName);

                Path target = FileSaveOps.ensureUnique(tplDir, newName);
                Files.copy(f.toPath(), target); // 既存なら "(1)" などでユニーク名になる
            }

            Dialogs.info("登録完了", "テンプレートフォルダへ登録しました:\n" + tplDir);
        } catch (Exception ex) {
            Dialogs.error("テンプレート登録エラー", ex.getMessage());
        }
    }

    private String promptTemplateName(String defaultName) {
        var dlg = new TextInputDialog(defaultName);
        dlg.setTitle("テンプレート名の入力");
        dlg.setHeaderText("テンプレートのファイル名を入力してください");
        dlg.setContentText("ファイル名:");
        Optional<String> res = dlg.showAndWait();
        return res.orElse(null);
    }
    
    private void manageTemplates(Window owner) {
        var dlg = new TemplateManagerDialog(workspaceDir);
        dlg.initOwner(owner);
        dlg.showAndWait();
    }

    private List<Course> fetchCoursesWithAddSlot() {
        var list = new ArrayList<Course>();
        list.addAll(courseRepo.findAll());
        return list;
    }

  
    private void logAdded(java.nio.file.Path path, Course course, int nth, ClassifyDialog.Kind kind, String action) {
        logAdded(path, course, nth, kind, action, null);
    }
    
    private void logAdded(java.nio.file.Path path, Course course, int nth, ClassifyDialog.Kind kind, String action, java.time.LocalDate deadline) {
        String ts = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
        String courseLabel = (course == null || course.name == null) ? "(不明)" : course.name;
        String dl = (deadline == null) ? "期限未設定" : deadline.toString();
        String line = "[%s] %s  追加: %s  （授業科目名: %s / 第%d回 / 種類: %s / 期限: %s）"
                .formatted(ts, action, path.toString(), courseLabel, Math.max(1, nth), kind.toString(), dl);
        var items = logList.getItems();
        items.add(line);
        if (items.size() > LOG_MAX) items.remove(0);
        logList.scrollTo(items.size() - 1);
    }
    
    public static final class Imported {
        enum Mode { EXISTING, TO_BE_CREATED }
        private final Mode mode;
        private final Path existingPath;
        private final SelectNewFileTypeDialog.NewType newType;

        private Imported(Mode m, Path p, SelectNewFileTypeDialog.NewType t) { this.mode=m; this.existingPath=p; this.newType=t; }
        public static Imported newFromExisting(Path p) { return new Imported(Mode.EXISTING, p, null); }
        public static Imported newToBeCreated(SelectNewFileTypeDialog.NewType t) { return new Imported(Mode.TO_BE_CREATED, null, t); }
        public Mode mode() { return mode; }
        public Path existingPath() { return existingPath; }
        public SelectNewFileTypeDialog.NewType newType() { return newType; }
        public String getSuggestedExt() {
            if (mode == Mode.EXISTING) {
                String name = existingPath.getFileName().toString();
                int dot = name.lastIndexOf('.');
                return dot >= 0 ? name.substring(dot) : "";
            } else {
                return newType.defaultExtension();
            }
        }
    }
    
    private static final class FollowupDefaults {
    	final Course course;
    	final int nth;
    	final java.time.LocalDate deadline;
    	FollowupDefaults(Course c, int nth, java.time.LocalDate dl){ this.course=c; this.nth=nth; this.deadline=dl; }
    	
    }

    public PanelFileSave(app.univtool.model.Course fixedCourse) {
    	this(new FollowupDefaults(fixedCourse, 1, null));
    }
    
    public PanelFileSave(Course fixedCourse, int fixedNth,
                         boolean lockCourse, boolean lockNth,
                         java.util.List<ClassifyDialog.Kind> allowedKinds) {
        this();
        this.followup = new FollowupDefaults(fixedCourse, fixedNth, null);
        this.followupLockCourse = lockCourse;
        this.followupLockNth    = lockNth;
        this.followupAllowedKinds = allowedKinds;
    }

}