// PanelFileSearch.java（新規）
package app.univtool.ui.panel3;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
//（ファイル先頭の import 群の末尾あたりに追記）
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import app.univtool.core.SettingsStore;
import app.univtool.files.SavedFileIndex;
import app.univtool.model.Course;
import app.univtool.repo.CourseRepository;
import app.univtool.ui.panel2.ClassifyDialog;
import app.univtool.ui.panel2.FileSaveOps;
import app.univtool.ui.panel2.RenameDialog;
import app.univtool.util.Dialogs;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;


public class PanelFileSearch extends BorderPane {

    private final CourseRepository courseRepo = new CourseRepository();
    private final TableView<Row> table = new TableView<>();
    private final ComboBox<String> cbCourse = new ComboBox<>();
    private final ComboBox<String> cbKind = new ComboBox<>();
    private final ComboBox<String> cbNth = new ComboBox<>();
    private final Button btnSearch = new Button("検索");
    private final Button btnReset  = new Button("リセット");
    private final Label lblCount = new Label("0件");

    private final Map<Integer, Course> courseById = new HashMap<>();
    private Path workspaceDir;
    private Path archiveDir;

    public PanelFileSearch() {
        setPadding(new Insets(12));

        var s = SettingsStore.load();
        if (s.workspaceDir != null && !s.workspaceDir.isBlank()) {
            workspaceDir = Path.of(s.workspaceDir);
            try { Files.createDirectories(workspaceDir); } catch (Exception ignore) {}
        }
        if (s.archiveDir != null && !s.archiveDir.isBlank()) {
            archiveDir = Path.of(s.archiveDir);
        }

        cbNth.getItems().add("（指定なし）");
        for (int i = 1; i <= 1000; i++) cbNth.getItems().add(String.valueOf(i));
        cbNth.getSelectionModel().selectFirst();
        var kinds = Arrays.stream(ClassifyDialog.Kind.values()).map(ClassifyDialog.Kind::toString).toList();
        cbKind.getItems().add( "（指定なし）" );
        cbKind.getItems().addAll(kinds);
        cbKind.getSelectionModel().selectFirst();

        cbCourse.getItems().add("（指定なし）");
        cbCourse.getSelectionModel().selectFirst();

        btnSearch.setOnAction(e -> applyFilter());
        btnReset.setOnAction(e -> {
            cbCourse.getSelectionModel().selectFirst();
            cbKind.getSelectionModel().selectFirst();
            cbNth.getSelectionModel().selectFirst();
            loadRows();
            refreshNthOptions();
        });
        
        cbCourse.valueProperty().addListener((o, a, b) -> refreshNthOptions());
        cbKind.valueProperty().addListener((o, a, b) -> refreshNthOptions());

        var grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8); grid.setPadding(new Insets(8));
        int r = 0;
        grid.addRow(r++, new Label("授業科目名"), cbCourse);
        grid.addRow(r++, new Label("第何回"), cbNth);;
        grid.addRow(r++, new Label("種類"), cbKind);
        var spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        lblCount.setStyle("-fx-text-fill:#666;");
        var btns = new HBox(8, btnSearch, btnReset, spacer, lblCount);
        grid.add(btns, 1, r++);
        setTop(grid);

        buildTable();
        setCenter(table);

        refreshCoursesCache();
        refreshCourseFilterList();
        loadRows();
        refreshNthOptions();
        
    }

    private void buildTable() {
        TableColumn<Row, String> colCourse = new TableColumn<>("授業科目名");
        colCourse.setCellValueFactory(v -> v.getValue().courseName);
        colCourse.setPrefWidth(220);

        TableColumn<Row, String> colNth = new TableColumn<>("第何回");
        colNth.setCellValueFactory(v -> v.getValue().nthStr);
        colNth.setPrefWidth(80);

        TableColumn<Row, String> colKind = new TableColumn<>("種類");
        colKind.setCellValueFactory(v -> v.getValue().kindLabel);
        colKind.setPrefWidth(150);

        TableColumn<Row, String> colExt = new TableColumn<>("拡張子");
        colExt.setCellValueFactory(v -> v.getValue().ext);
        colExt.setPrefWidth(90);

        TableColumn<Row, String> colName = new TableColumn<>("ファイル名");
        colName.setCellValueFactory(v -> v.getValue().fileName);
        colName.setPrefWidth(420);
        colName.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
                Row row = (Row) getTableRow().getItem();
                if (row != null) setTooltip(new Tooltip(row.fullPath)); else setTooltip(null);
            }
        });

        java.util.List<TableColumn<Row, ?>> cols = new java.util.ArrayList<>();
        cols.add(colCourse);
        cols.add(colNth);
        cols.add(colKind);
        cols.add(colExt);
        cols.add(colName);
        table.getColumns().setAll(cols);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.setRowFactory(tv -> {
            TableRow<Row> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (row.isEmpty()) return;
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                    openWithDefaultApp(row.getItem().path);
                } else if (e.getButton() == MouseButton.SECONDARY) {
                    showContextMenu(row.getItem(), e.getScreenX(), e.getScreenY());
                }
            });
            return row;
        });

    }

    private static class Row {
        final Path path;
        final Integer courseId;
        final javafx.beans.property.SimpleStringProperty courseName;
        final javafx.beans.property.SimpleStringProperty nthStr;
        final javafx.beans.property.SimpleStringProperty kindLabel;
        final javafx.beans.property.SimpleStringProperty ext;
        final javafx.beans.property.SimpleStringProperty fileName;
        final String fullPath;
        final int nth;
        final String kindEnumName;
        final String deadlineIso;

        Row(Path p, Course c, int nth, String kindEnumName, String deadlineIso) {
            this.path = p;
            this.courseId = (c == null ? null : c.id);
            this.nth = nth;
            this.kindEnumName = kindEnumName;
            this.deadlineIso = deadlineIso;
            String labelKind = toKindLabel(kindEnumName);

            this.courseName = new javafx.beans.property.SimpleStringProperty(c == null ? "(不明)" : String.valueOf(c.name));
            this.nthStr = new javafx.beans.property.SimpleStringProperty(String.valueOf(nth));
            this.kindLabel = new javafx.beans.property.SimpleStringProperty(labelKind);

            String fname = p.getFileName().toString();
            this.fileName = new javafx.beans.property.SimpleStringProperty(fname);
            this.ext = new javafx.beans.property.SimpleStringProperty(extractExt(fname));
            this.fullPath = p.toAbsolutePath().toString();
        }
        private static String extractExt(String name) {
            int dot = name.lastIndexOf('.');
            return (dot >= 0 ? name.substring(dot) : "");
        }
        private static String toKindLabel(String enumName) {
            try {
                var k = ClassifyDialog.Kind.valueOf(enumName);
                return k.toString();
            } catch (Exception e) { return enumName; }
        }
    }

    private void refreshCoursesCache() {
        courseById.clear();
        for (Course c : courseRepo.findAll()) courseById.put(c.id, c);
    }

    private void refreshCourseFilterList() {
        var names = SavedFileIndex.get().list().stream()
                .map(e -> courseById.get(e.courseId))
                .filter(Objects::nonNull)
                .filter(this::isActiveCourse)
                .map(c -> c.name)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        cbCourse.getItems().setAll(new ArrayList<>(List.of("（指定なし）")));
        cbCourse.getItems().addAll(names);
        cbCourse.getSelectionModel().selectFirst();
    }


    private void loadRows() {
        refreshCoursesCache();
        var idx = SavedFileIndex.get().list();
        var rows = new ArrayList<Row>();
        for (var e : idx) {
            try {
                Course c = courseById.get(e.courseId);
                if (!isActiveCourse(c)) continue;
                Path p = Path.of(e.path);
                rows.add(new Row(p, c, e.nth == null ? 0 : e.nth, e.kind, e.deadline));
            } catch (Exception ignore) {}
        }
        table.getItems().setAll(rows.stream()
                .sorted(Comparator.comparing(r -> r.fileName.get()))
                .toList());
        updateCount();
    }


    private void applyFilter() {
        String courseSel = cbCourse.getValue();
        String kindSel   = cbKind.getValue();
        String nthSelStr = cbNth.getValue();

        var all = new ArrayList<Row>();
        for (var e : SavedFileIndex.get().list()) {
            try {
                Course c = courseById.get(e.courseId);
                if (!isActiveCourse(c)) continue;
                Path p = Path.of(e.path);
                all.add(new Row(p, c, e.nth == null ? 0 : e.nth, e.kind, e.deadline));
            } catch (Exception ignore) {}
        }

        var filtered = all.stream().filter(r -> {
            boolean ok = true;
            if (courseSel != null && !"（指定なし）".equals(courseSel)) ok &= courseSel.equals(r.courseName.get());
            if (kindSel   != null && !"（指定なし）".equals(kindSel))   ok &= kindSel.equals(r.kindLabel.get());
            if (nthSelStr != null && !"（指定なし）".equals(nthSelStr)) {
                try { ok &= Integer.parseInt(nthSelStr) == r.nth; } catch (NumberFormatException ignore) {}
            }
            return ok;
        }).collect(Collectors.toList());

        table.getItems().setAll(filtered);
        updateCount();
    }

    private void showContextMenu(Row sel, double x, double y) {
        var menu = new ContextMenu();

        var miOpenWith = new MenuItem("アプリから開く…");
        miOpenWith.setOnAction(e -> openWithChosenApp(sel.path));

        var miExplorer = new MenuItem("エクスプローラーから開く");
        miExplorer.setOnAction(e -> openInExplorerSelect(sel.path));

        var miEditClass = new MenuItem("分類を編集");
        miEditClass.setOnAction(e -> editClassification(sel));

        var miRename = new MenuItem("ファイル名を編集");
        miRename.setOnAction(e -> renameFile(sel));

        var miDelete = new MenuItem("ファイルを削除");
        miDelete.setOnAction(e -> deleteFile(sel));

        var miEditDeadline = new MenuItem("提出期限の編集");
        miEditDeadline.setOnAction(e -> editDeadline(sel));

        menu.getItems().addAll(
                miOpenWith, miExplorer,
                new SeparatorMenuItem(),
                miEditClass, miRename, miEditDeadline,
                new SeparatorMenuItem(),
                miDelete
        );
        menu.show(table, x, y);
    }

    private void openWithDefaultApp(Path p) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(p.toFile());
        } catch (Exception ex) {
            Dialogs.error("エラー", "ファイルを開けませんでした: " + ex.getMessage());
        }
    }

    private void openWithChosenApp(Path p) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                var fc = new FileChooser();
                fc.setTitle("アプリケーションを選択（.exe）");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("実行ファイル", "*.exe"));
                File exe = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
                if (exe == null) return;
                new ProcessBuilder(exe.getAbsolutePath(), p.toAbsolutePath().toString()).start();
            } else if (os.contains("mac")) {
                var fc = new FileChooser();
                fc.setTitle("アプリケーションを選択（.app の中の実行ファイルも可）");
                File app = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
                if (app == null) return;

                new ProcessBuilder("open", "-a", app.getAbsolutePath(), p.toAbsolutePath().toString()).start();
            } else {
                Dialogs.info("情報", "このOSでは既定アプリで開きます（xdg-open）。");
                new ProcessBuilder("xdg-open", p.toAbsolutePath().toString()).start();
            }
        } catch (Exception ex) {
            Dialogs.error("エラー", "起動に失敗: " + ex.getMessage());
        }
    }

    private void openInExplorerSelect(Path p) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select,", p.toAbsolutePath().toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", p.toAbsolutePath().toString()).start();
            } else {

                new ProcessBuilder("xdg-open", p.getParent().toAbsolutePath().toString()).start();
            }
        } catch (Exception ex) {
            Dialogs.error("エラー", "エクスプローラーを開けませんでした: " + ex.getMessage());
        }
    }

    private void editClassification(Row row) {
        try {
            Course currentCourse = (row.courseId == null ? null : courseById.get(row.courseId));
            Integer oldCourseId = (currentCourse == null ? null : currentCourse.id);
            int currentNth = row.nth;
            String currentKindEnum = row.kindEnumName;
            ClassifyDialog.Kind currentKind = safeKind(currentKindEnum);

            var dlg = new Dialog<ButtonType>();
            dlg.setTitle("分類を編集");

            var cbCourseSel = new ComboBox<String>();
            var courses = courseRepo.findAll().stream()
                    .filter(this::isActiveCourse)
                    .toList();
            var courseNames = courses.stream()
                    .map(c -> c.name)
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
            cbCourseSel.getItems().addAll(courseNames);
            if (currentCourse != null) cbCourseSel.getSelectionModel().select(currentCourse.name);
            else if (!courseNames.isEmpty()) cbCourseSel.getSelectionModel().select(0);

            var spNthSel = new Spinner<Integer>(1, 1000, Math.max(1, currentNth));
            spNthSel.setEditable(true);

            var cbKindSel = new ComboBox<String>();
            for (var k : ClassifyDialog.Kind.values()) cbKindSel.getItems().add(k.toString());
            cbKindSel.getSelectionModel().select(currentKind.toString());

            var grid = new GridPane();
            grid.setHgap(10); grid.setVgap(8); grid.setPadding(new Insets(10));
            int r = 0;
            grid.addRow(r++, new Label("授業科目名"), cbCourseSel);
            grid.addRow(r++, new Label("第何回"), spNthSel);
            grid.addRow(r++, new Label("種類"), cbKindSel);

            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dlg.getDialogPane().setContent(grid);
            var res = dlg.showAndWait().orElse(ButtonType.CANCEL);
            if (res != ButtonType.OK) return;

            String newCourseName = cbCourseSel.getValue();
            Integer newCourseId = courses.stream()
                    .filter(c -> Objects.equals(c.name, newCourseName))
                    .map(c -> c.id)
                    .findFirst().orElse(null);
            int newNth = spNthSel.getValue();
            ClassifyDialog.Kind newKind = Arrays.stream(ClassifyDialog.Kind.values())
                    .filter(k -> k.toString().equals(cbKindSel.getValue()))
                    .findFirst().orElse(ClassifyDialog.Kind.その他);

            Path curPath = row.path;
            boolean courseChanged = !Objects.equals(oldCourseId, newCourseId);
            Course newCourse = (newCourseId == null ? null : courseById.get(newCourseId));
            if (newCourse == null && courseChanged) {

                refreshCoursesCache();
                newCourse = courseById.get(newCourseId);
            }

            boolean usedCourse = currentCourse != null && containsToken(row.fileName.get(), tokenCourse(currentCourse));
            boolean usedNth    = containsToken(row.fileName.get(), String.valueOf(currentNth));
            boolean usedKind   = containsToken(row.fileName.get(), currentKind.toString());

            if (usedCourse || usedNth || usedKind) {
                var conf = new Alert(Alert.AlertType.CONFIRMATION,
                        "ファイル名に「授業科目名 / 第何回 / 種類」の要素が含まれています。\n" +
                        "新しい分類に合わせてファイル名も変更しますか？",
                        ButtonType.YES, ButtonType.NO);
                var ans = conf.showAndWait().orElse(ButtonType.NO);
                if (ans == ButtonType.YES) {
                    var ren = new RenameDialog(newCourse, newNth, newKind, Row.extractExt(row.fileName.get()));
                    var rr = ren.showAndWait();
                    if (rr.isPresent()) {
                        String newName = rr.get().filename();
                        if (newName != null && !newName.isBlank()) {
                            Path target = FileSaveOps.ensureUnique(curPath.getParent(), FileSaveOps.sanitize(newName));
                            Files.move(curPath, target);
                            SavedFileIndex.get().updatePath(curPath, target);
                            curPath = target;
                        }
                    }
                }
            }

            if (courseChanged) {
                if (workspaceDir == null) {
                    Dialogs.error("エラー", "ワークスペース未設定のため、ファイル移動ができません。");
                    newCourseId = oldCourseId;
                    courseChanged = false;
                } else {
                    try {
                        Path dstDir = workspaceDir.resolve(resolveFolderName(newCourse));
                        Files.createDirectories(dstDir);

                        String baseName = curPath.getFileName().toString();
                        Path dstCandidate = FileSaveOps.ensureUnique(dstDir, FileSaveOps.sanitize(baseName));

                        var cf = new Alert(Alert.AlertType.CONFIRMATION,
                                "授業科目名を変更しました。\n" +
                                "ファイルを新しい講義フォルダへ移動します。\n\n" +
                                "元: " + curPath.toAbsolutePath() + "\n" +
                                "先: " + dstCandidate.toAbsolutePath() + "\n\n" +
                                "よろしいですか？",
                                ButtonType.OK, ButtonType.CANCEL);
                        cf.setHeaderText("移動の確認");
                        var ansMove = cf.showAndWait().orElse(ButtonType.CANCEL);

                        if (ansMove == ButtonType.OK) {
                            Files.move(curPath, dstCandidate);
                            SavedFileIndex.get().updatePath(curPath, dstCandidate);
                            curPath = dstCandidate;
                        } else {
                            newCourseId = oldCourseId;
                            courseChanged = false;
                        }
                    } catch (Exception moveEx) {
                        Dialogs.error("エラー", "フォルダ移動に失敗: " + moveEx.getMessage());
                        newCourseId = oldCourseId;
                        courseChanged = false;
                    }
                }
            }

            SavedFileIndex.get().updateClassification(curPath, newCourseId, newNth, newKind.name());

            refreshCoursesCache();
            refreshCourseFilterList();
            applyFilter();

        } catch (Exception ex) {
            Dialogs.error("エラー", "分類の更新に失敗: " + ex.getMessage());
        }
    }

    private void refreshNthOptions() {
        String courseSel = cbCourse.getValue();
        String kindSel   = cbKind.getValue();

        java.util.Set<Integer> set = new java.util.TreeSet<>();
        var list = SavedFileIndex.get().list();
        for (var e : list) {
            var c = courseById.get(e.courseId);
            if (!isActiveCourse(c)) continue;

            if (courseSel != null && !"（指定なし）".equals(courseSel)) {
                if (c == null || c.name == null || !courseSel.equals(c.name)) continue;
            }
            if (kindSel != null && !"（指定なし）".equals(kindSel)) {
                String labelOfEntry = toKindLabelSafe(e.kind);
                if (!kindSel.equals(labelOfEntry)) continue;
            }
            if (e.nth != null && e.nth > 0) set.add(e.nth);
        }

        String keep = cbNth.getValue();
        cbNth.getItems().setAll("（指定なし）");
        for (Integer n : set) cbNth.getItems().add(String.valueOf(n));
        if (keep != null && cbNth.getItems().contains(keep)) cbNth.getSelectionModel().select(keep);
        else cbNth.getSelectionModel().selectFirst();
    }

    private static String toKindLabelSafe(String enumName) {
        try {
            return app.univtool.ui.panel2.ClassifyDialog.Kind.valueOf(enumName).toString();
        } catch (Exception e) {
            return enumName == null ? "" : enumName;
        }
    }


    private static boolean containsToken(String name, String token) {
        if (token == null || token.isBlank() || name == null) return false;
        return name.contains(token);
    }
    private static String tokenCourse(Course c) {
        if (c == null) return null;
        String base = (c.folder != null && !c.folder.isBlank()) ? c.folder : c.name;
        return FileSaveOps.sanitize(base);
    }
    private static ClassifyDialog.Kind safeKind(String enumName) {
        try { return ClassifyDialog.Kind.valueOf(enumName); } catch (Exception e) { return ClassifyDialog.Kind.その他; }
    }

    private void renameFile(Row row) {
        var td = new TextInputDialog(row.fileName.get());
        td.setTitle("ファイル名の編集");
        td.setHeaderText("新しいファイル名を入力してください");
        td.setContentText("ファイル名:");
        var res = td.showAndWait();
        if (res.isEmpty()) return;

        String newName = FileSaveOps.sanitize(res.get().trim());
        if (newName.isBlank()) return;

        try {
            Path target = FileSaveOps.ensureUnique(row.path.getParent(), newName);
            Files.move(row.path, target);
            SavedFileIndex.get().updatePath(row.path, target);
            applyFilter();
        } catch (Exception ex) {
            Dialogs.error("エラー", "リネームに失敗: " + ex.getMessage());
        }
    }

    private void deleteFile(Row row) {
        var cf = new Alert(Alert.AlertType.CONFIRMATION,
                "次のファイルを削除します。\n" + row.fullPath, ButtonType.OK, ButtonType.CANCEL).showAndWait();
        if (cf.isEmpty() || cf.get() != ButtonType.OK) return;

        try {
            Files.deleteIfExists(row.path);
            SavedFileIndex.get().deleteByPath(row.path);
            applyFilter();
        } catch (Exception ex) {
            Dialogs.error("エラー", "削除に失敗: " + ex.getMessage());
        }
    }

    public PanelFileSearch(app.univtool.model.Course defaultCourse) {
        this();
        if (defaultCourse != null && defaultCourse.name != null && !defaultCourse.name.isBlank()) {
            if (!cbCourse.getItems().contains(defaultCourse.name)) {
                cbCourse.getItems().add(defaultCourse.name);
            }
            cbCourse.getSelectionModel().select(defaultCourse.name);
            refreshNthOptions();
            applyFilter();
        }
    }
    
    private static String resolveFolderName(Course c) {
        return (c.folder != null && !c.folder.isBlank()) ? c.folder : sanitize(c.name);
    }
    private static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }
    private boolean isActiveCourse(Course c) {
        if (c == null) return false;
        if (workspaceDir == null) return true;
        try {
            return Files.exists(workspaceDir.resolve(resolveFolderName(c)));
        } catch (Exception e) {
            return false;
        }
    }

    private void editDeadline(Row row) {
        LocalDate current = null;
        try { if (row.deadlineIso != null && !row.deadlineIso.isBlank()) current = LocalDate.parse(row.deadlineIso); } catch (Exception ignore) {}

        var dlg = new Dialog<ButtonType>();
        dlg.setTitle("提出期限の編集");

        var ok = new ButtonType("保存", ButtonType.OK.getButtonData());
        var clear = new ButtonType("期限をクリア", javafx.scene.control.ButtonBar.ButtonData.OTHER);
        dlg.getDialogPane().getButtonTypes().addAll(ok, clear, ButtonType.CANCEL);

        var fmt = DateTimeFormatter.ofPattern("M月d日");
        String nowLabel = (current == null ? "未設定" : "（" + fmt.format(current) + "）");
        var lbCur = new Label("現在: " + nowLabel);

        var dp = new DatePicker(current == null ? LocalDate.now() : current);
        dp.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || item.isBefore(LocalDate.now()));
            }
        });

        var grid = new GridPane(); grid.setHgap(10); grid.setVgap(8); grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("提出期限"), dp);
        grid.add(lbCur, 0, 1);
        dlg.getDialogPane().setContent(grid);

        var res = dlg.showAndWait();
        if (res.isEmpty()) return;

        if (res.get() == ok) {
            LocalDate chosen = dp.getValue();
            SavedFileIndex.get().updateDeadline(row.path, chosen);
        } else if (res.get() == clear) {
            SavedFileIndex.get().updateDeadline(row.path, null);
        } else {
            return;
        }

        applyFilter();
    }

    private void updateCount() {
        lblCount.setText(table.getItems().size() + "件");
    }
    
    public void revealFile(java.nio.file.Path focusFile) {
        if (focusFile == null) return;
        Platform.runLater(() -> {
            var items = table.getItems();
            if (items == null || items.isEmpty()) return;

            String target = normalizePath(focusFile);
            for (int i = 0; i < items.size(); i++) {
                var r = items.get(i);
                if (r == null || r.path == null) continue;
                if (normalizePath(r.path).equals(target)) {
                    table.getSelectionModel().select(i);
                    table.scrollTo(i);
                    break;
                }
            }
        });
    }

    private static String normalizePath(java.nio.file.Path p) {
        try { return p.toAbsolutePath().normalize().toString(); }
        catch (Exception e) { return String.valueOf(p); }
    }

}
