package app.univtool.ui.panel1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
import java.util.stream.Collectors;

import app.univtool.core.CoursePresetStore;
import app.univtool.core.CoursePresetStore.CoursePreset;
import app.univtool.core.CreditStore;
import app.univtool.core.SettingsStore;
import app.univtool.core.TimetableStore;
import app.univtool.core.WorkspaceService;
import app.univtool.model.Course;
import app.univtool.repo.CourseRepository;
import app.univtool.util.Dialogs;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public class PanelCourses extends BorderPane {

    private final CourseRepository repo = new CourseRepository();
    private final ListView<Course> listAdded = new ListView<>();
    private final ListView<String> listArchived = new ListView<>();
    private Path workspaceDir;
    private Path archiveDir;
    private final Label lblPaths = new Label("WS: （未設定） / AR: （未設定）");
    
    private final CheckBox cbTimetable = new CheckBox("時間割登録状況"); // 三段回
    private final Set<Integer> assignedInTimetable = new HashSet<>();

    private final Label lblTTStatus = new Label("全件");

    private final TextField tfFilter = new TextField();
    private final ComboBox<String> cbSortKey = new ComboBox<>();
    private final CheckBox cbDesc = new CheckBox("降順");
    private List<Course> allCourses = new ArrayList<>();

    public PanelCourses() {
        setPadding(new Insets(12));
        
        var topBox = new VBox(10);
        var workspaceLabel = new Label("ワークスペース未設定です。初期化してください。");
        var btnUseDefault = new Button("デフォルトのフォルダを使用");
        var btnChoose = new Button("フォルダを選択");
        var initRow = new HBox(8, btnUseDefault, btnChoose);

        topBox.getChildren().addAll(workspaceLabel, initRow);
        setTop(topBox);

        var centerBox = new SplitPane();
        centerBox.setDividerPositions(0.65);

        var leftBox = new VBox(8);
        var headerRow = new HBox(8);
        var btnAddCourse   = new Button("講義を追加");
        var btnReload      = new Button("再読込");
        var btnRebase      = new Button("フォルダを再選択");
        var btnCoursePreset= new Button("プリセットから講義を追加");
        
        lblPaths.setStyle("-fx-font-size: 11px;");
        lblPaths.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        lblPaths.setMaxWidth(Double.MAX_VALUE);
        lblPaths.setTooltip(new Tooltip(lblPaths.getText()));
        lblPaths.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(lblPaths, Priority.ALWAYS);



        cbTimetable.setAllowIndeterminate(true);

        cbTimetable.setIndeterminate(false);
        cbTimetable.setSelected(false);

        cbTimetable.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            e.consume();
            if (!cbTimetable.isSelected() && !cbTimetable.isIndeterminate()) {
                cbTimetable.setSelected(true);
                cbTimetable.setIndeterminate(false);
            } else if (cbTimetable.isSelected()) {
                cbTimetable.setSelected(false);
                cbTimetable.setIndeterminate(true);
            } else {
                cbTimetable.setSelected(false);
                cbTimetable.setIndeterminate(false);
            }
            updateTTStatusLabel();
            applyFilterAndSort();
        });

        updateTTStatusLabel();

        headerRow.getChildren().setAll(
            btnAddCourse,
            btnCoursePreset,
            lblPaths,
            btnRebase
        );

        
        headerRow.setAlignment(Pos.BOTTOM_RIGHT);
        headerRow.setFillHeight(false);
        
        
        
        tfFilter.setPromptText("講義の検索（科目名/教員名/科目番号）");
        cbSortKey.getItems().addAll("科目名", "開講年度", "期", "開講年次", "曜日", "時限", "科目区分");
        cbSortKey.getSelectionModel().selectFirst();

     var lblSort = new Label("ソート:");
     var spacerSort = new Region();
     HBox.setHgrow(spacerSort, Priority.ALWAYS);


     lblTTStatus.setStyle("-fx-text-fill: #666;");

     var sortRow = new HBox(8,
             lblSort, cbSortKey, cbDesc,
             spacerSort,
             cbTimetable, lblTTStatus
     );
        
        
        leftBox.getChildren().addAll(headerRow, tfFilter, sortRow, listAdded);
        leftBox.setPadding(new Insets(8));
        
        VBox.setVgrow(listAdded, Priority.ALWAYS);
        var leftBottom = new HBox(8);
        var btnCondBulkArchive = new Button("条件で一括アーカイブ");
        
        var bottomSpacer = new javafx.scene.layout.Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);
        leftBottom.getChildren().setAll(btnCondBulkArchive, bottomSpacer, btnReload);

        leftBox.getChildren().add(leftBottom);

        btnCondBulkArchive.setOnAction(e -> openBulkArchiveDialog());
        btnReload.setOnAction(evt -> refreshAll());

        var rightBox = new VBox(8);
        rightBox.getChildren().addAll(new Label("アーカイブ済み講義"), listArchived);
        rightBox.setPadding(new Insets(8));

        centerBox.getItems().addAll(leftBox, rightBox);
        
        VBox.setVgrow(listArchived, Priority.ALWAYS);
        var rightBottom = new VBox(8);
        var btnBulkRestore = new Button("アーカイブを一括で登録に戻す");
        var btnBulkDelete  = new Button("アーカイブを削除");
        rightBottom.getChildren().addAll(btnBulkRestore, btnBulkDelete);
        rightBox.getChildren().add(rightBottom);
        
        var s = SettingsStore.load();
        if (s.workspaceDir != null) {
            Path ws = Path.of(s.workspaceDir);
            Path ar = (s.archiveDir != null) ? Path.of(s.archiveDir)
                                             : (ws.getParent() != null ? ws.getParent().resolve("archive")
                                                                       : ws.resolveSibling("archive"));
            if (Files.isDirectory(ws) && Files.isDirectory(ar)) {
                applyWorkspace(ws, ar);
                setTop(null);
                setCenter(centerBox);
            } else {
                setTop(topBox);
            }
        } else {
            setTop(topBox);
        }

        listAdded.setCellFactory(listView -> new ListCell<>() {
            @Override protected void updateItem(Course c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setText(null); return; }

                String name   = nullToEmpty(c.name);
                String year   = (c.year == null) ? "-" : String.valueOf(c.year);
                String term   = (c.term == null || c.term.isBlank()) ? "-" : c.term;
                String grade  = (c.grade == null) ? "-" : (c.grade + "年次");
                String credit = (c.credit == null) ? "-" :
                        (Math.floor(c.credit) == c.credit
                                ? (int)Math.floor(c.credit) + "単位"
                                : c.credit + "単位");
                String day    = jpDayLabel(nullToEmpty(c.day));
                String period = jpPeriodLabel(nullToEmpty(c.period));

                setText("「%s」 / %s / %s / %s / %s / %s・%s"
                        .formatted(name, year, term, grade, credit, day, period));
            }
        });
        listAdded.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                Course c = listAdded.getSelectionModel().getSelectedItem();
                if (c != null) {
                    var dlg = new CourseDetailsDialog(c, workspaceDir, archiveDir, this::refreshAll);
                    dlg.showAndWait();
                }
            } else if (e.getButton() == MouseButton.SECONDARY) {
                Course c = listAdded.getSelectionModel().getSelectedItem();
                if (c == null) return;

                var menu = new ContextMenu();

                var miOpenDetail = new MenuItem("詳細を表示");
                miOpenDetail.setOnAction(evt -> {
                    var dlg = new CourseDetailsDialog(c, workspaceDir, archiveDir, this::refreshAll);
                    dlg.showAndWait();
                });

                var miEdit = new MenuItem("講義の編集");
                miEdit.setOnAction(evt -> {
                    Course original = listAdded.getSelectionModel().getSelectedItem();
                    if (original == null) return;
                    var dlg = new AddCourseDialog(original);
                    dlg.getDialogPane().getScene();
                    var res = dlg.showAndWait();
                    res.ifPresent(updated -> {
                        updated.id = original.id;
                        repo.update(updated);
                        refreshAll();
                    });
                });

                var miArchive = new MenuItem("講義をアーカイブ");
                miArchive.setOnAction(evt -> archiveCourse(c));

                var miFileSearch = new MenuItem("ファイル検索");
                miFileSearch.setOnAction(evt -> {
                    Course sel = listAdded.getSelectionModel().getSelectedItem();
                    if (sel == null) return;
                    var pane = new app.univtool.ui.panel3.PanelFileSearch(sel);
                    var stage = new javafx.stage.Stage();
                    stage.setTitle("ファイル検索 - " + (sel.name == null ? "" : sel.name));
                    stage.initOwner(getScene() == null ? null : getScene().getWindow());
                    stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
                    stage.setScene(new javafx.scene.Scene(pane, 900, 600));
                    stage.show();    
                });
                var miAssignToTT = new MenuItem("時間割に登録");
                miAssignToTT.setOnAction(evt -> assignCourseToTimetable(c));
                
                var miOpenExplorer = new MenuItem("エクスプローラーでワークスペースを開く");
                miOpenExplorer.setOnAction(evt -> openExplorer(workspaceDir.resolve(resolveFolderName(c))));

             menu.getItems().setAll(
                 miOpenDetail, miEdit, miArchive,
                 new SeparatorMenuItem(),
                 miFileSearch,
                 miAssignToTT,
                 miOpenExplorer
             );
                menu.show(listAdded, e.getScreenX(), e.getScreenY());
            }

        });

        listArchived.setOnMouseClicked(e -> {
            String folder = listArchived.getSelectionModel().getSelectedItem();
            if (folder == null) return;

            final String selectedFolder = folder;

            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                findCourseByArchivedFolder(selectedFolder).ifPresentOrElse(c -> {
                    var dlg = new CourseDetailsDialog(c, workspaceDir, archiveDir, null, false);
                    dlg.showAndWait();
                }, () -> Dialogs.error("エラー", "このアーカイブに対応する講義がDBで見つかりませんでした。"));
            }

            if (e.getButton() == MouseButton.SECONDARY) {
                var menu = new ContextMenu();

                var miRestore = new MenuItem("追加済みの講義一覧に戻す");
                miRestore.setOnAction(evt -> {
                    try {
                        if (workspaceDir == null || archiveDir == null) {
                            Dialogs.error("エラー", "ワークスペース未設定です。");
                            return;
                        }
                        Path src = archiveDir.resolve(selectedFolder);
                        Path dst = workspaceDir.resolve(selectedFolder);
                        if (Files.exists(dst)) {
                            Dialogs.error("エラー", "戻し先に同名フォルダが存在します: " + dst);
                            return;
                        }
                        Files.move(src, dst);
                        refreshAll();
                    } catch (Exception ex) {
                        Dialogs.error("エラー", "復元に失敗: " + ex.getMessage());
                    }
                });

                var miDeleteArch = new MenuItem("講義を削除");
                miDeleteArch.setOnAction(evt -> {
                    var cf = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "アーカイブフォルダ「" + selectedFolder + "」を削除します。\n" +
                        "フォルダ内のファイル、対応する講義レコードも削除します。\nよろしいですか？",
                        ButtonType.OK, ButtonType.CANCEL
                    );
                    cf.setHeaderText("削除の確認");
                    if (cf.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

                    try {
                        if (archiveDir != null) {
                            Path p = archiveDir.resolve(selectedFolder);
                            if (Files.exists(p)) deleteDirectory(p);
                        }
                    } catch (Exception ex) {
                        Dialogs.error("削除エラー", "アーカイブフォルダ削除に失敗: " + ex.getMessage());
                        return;
                    }

                    findCourseByArchivedFolder(selectedFolder).ifPresent(c -> {
                        try { repo.delete(c.id); } catch (Exception ignore) {}
                    });

                    refreshAll();
                });

                var miOpenExplorer = new MenuItem("エクスプローラーで開く");
                miOpenExplorer.setOnAction(evt -> openExplorer(archiveDir.resolve(selectedFolder)));

                menu.getItems().addAll(miRestore, miDeleteArch, new SeparatorMenuItem(), miOpenExplorer);
                menu.show(listArchived, e.getScreenX(), e.getScreenY());
            }
        });


        tfFilter.textProperty().addListener((obs, o, n) -> applyFilterAndSort());
        cbSortKey.valueProperty().addListener((obs, o, n) -> applyFilterAndSort());
        cbDesc.selectedProperty().addListener((obs, o, n) -> applyFilterAndSort());

        btnAddCourse.setOnAction(evt -> {
            var dlg = new AddCourseDialog();
            var result = dlg.showAndWait();
            result.ifPresent(c -> {
                try {
                    int id = repo.insert(c);
                    c.id = id;

                    String folder = buildFolderNameFromId(c);
                    Path dst = workspaceDir.resolve(folder);
                    if (Files.exists(dst)) {
                        Dialogs.error("エラー", "同名フォルダが既に存在します: " + dst);
                        return;
                    }

                    Files.createDirectories(dst);

                    c.folder = folder;
                    repo.updateFolder(c.id, folder);

                    refreshAll();
                } catch (Exception ex) {
                    Dialogs.error("エラー", "講義の追加に失敗: " + ex.getMessage());
                }
            });
        });
       
        btnReload.setOnAction(evt -> refreshAll());
        
        btnCondBulkArchive.setOnAction(e -> openBulkArchiveDialog());
        btnBulkRestore.setOnAction(e -> bulkRestoreArchived());
        btnBulkDelete.setOnAction(e -> bulkDeleteArchived());
        
        
        btnUseDefault.setOnAction(evt -> {
            var wp = WorkspaceService.ensureDefault();
            java.nio.file.Path ws = wp.workspace();
            java.nio.file.Path ar = wp.archive();

            applyWorkspace(ws, ar);
        });

        btnChoose.setOnAction(evt -> {
            var dc = new DirectoryChooser();
            dc.setTitle("ワークスペースの親フォルダを選択");
            var dir = dc.showDialog(getScene() == null ? null : getScene().getWindow());
            if (dir != null) {
                var base = dir.toPath().toAbsolutePath();
                var wsWanted = base.resolve("UnivWorkspace");
                var wp = WorkspaceService.ensureWorkspace(wsWanted.toString());
                java.nio.file.Path ws = wp.workspace();
                java.nio.file.Path ar = wp.archive();

                applyWorkspace(ws, ar);
            }
        });
        
        btnCoursePreset.setOnAction(e -> openCoursePresetMenu());

        
        btnRebase.setOnAction(evt -> {
            if (workspaceDir == null) {
                Dialogs.error("エラー", "まずワークスペースを設定してください。");
                return;
            }
            var dc = new DirectoryChooser();
            dc.setTitle("新しいワークスペース親フォルダを選択");
            var dir = dc.showDialog(getScene() == null ? null : getScene().getWindow());
            if (dir == null) return;

            var destBase = dir.toPath().toAbsolutePath();

            try {
                Path newWs = uniquePath(destBase.resolve(workspaceDir.getFileName()));

                boolean arInsideWs = (archiveDir != null)
                        && archiveDir.normalize().startsWith(workspaceDir.normalize());

                Path newAr = arInsideWs
                        ? newWs.resolve(workspaceDir.relativize(archiveDir))
                        : (archiveDir != null ? uniquePath(destBase.resolve(archiveDir.getFileName())) : null);

                moveDirectory(workspaceDir, newWs);
                if (!arInsideWs && archiveDir != null && Files.exists(archiveDir)) {
                    moveDirectory(archiveDir, newAr);
                }

                applyWorkspace(newWs, newAr != null ? newAr : newWs.resolve("archive"));
                setTop(null);

            } catch (Exception ex) {
                Dialogs.error("移設エラー", "フォルダの移設に失敗しました: " + ex.getMessage());
            }
        });
        updatePathIndicator();

    }


//    private void setupWorkspaceDisplay(Label workspaceLabel, WorkspaceService.WorkspacePaths ws) {
//        this.workspaceDir = ws.workspace();
//        this.archiveDir = ws.archive();
//        workspaceLabel.setText("Workspace: " + workspaceDir + " / Archive: " + archiveDir);
//        refreshArchiveList();
//    }


    private void refreshAll() {
        this.allCourses = repo.findAll();
        applyFilterAndSort();
        refreshArchiveList();
    }

    private void applyFilterAndSort() {
        refreshAssignedIdsFromTimetable();
        updateTTStatusLabel();
        
        String q = tfFilter.getText() == null ? "" : tfFilter.getText().toLowerCase(Locale.ROOT).trim();

        List<Course> active = allCourses.stream()
                .filter(c -> !isArchived(c))
                .collect(Collectors.toList());

        boolean showAll        = !cbTimetable.isSelected() && !cbTimetable.isIndeterminate();
        boolean onlyRegistered =  cbTimetable.isSelected() && !cbTimetable.isIndeterminate();
        boolean onlyUnreg      =  cbTimetable.isIndeterminate();

        var filtered = active.stream()
                .filter(c -> {
                    if (showAll) return true;
                    boolean inTT = (c.id != null) && assignedInTimetable.contains(c.id);
                    if (onlyRegistered) return inTT;
                    if (onlyUnreg)     return !inTT;
                    return true;
                })
                .filter(c -> {
                    if (q.isEmpty()) return true;
                    String name = nullToEmpty(c.name).toLowerCase(Locale.ROOT);
                    String teacher = nullToEmpty(c.teacher).toLowerCase(Locale.ROOT);
                    String code = nullToEmpty(c.code).toLowerCase(Locale.ROOT);
                    return name.contains(q) || teacher.contains(q) || code.contains(q);
                })
                .sorted(buildComparator())
                .collect(Collectors.toList());

        listAdded.getItems().setAll(filtered);
    }


    private Comparator<Course> buildComparator() {
        Comparator<Course> byName   = Comparator.comparing(c -> nullToEmpty(c.name).toLowerCase(Locale.ROOT));
        Comparator<Course> byYear   = Comparator.comparing(c -> c.year   == null ? Integer.MAX_VALUE : c.year);
        Comparator<Course> byTerm   = Comparator.comparing(c -> termOrder(nullToEmpty(c.term)));
        Comparator<Course> byGrade  = Comparator.comparing(c -> c.grade  == null ? Integer.MAX_VALUE : c.grade);
        Comparator<Course> byCredit = Comparator.comparing(c -> c.credit == null ? Double.MAX_VALUE  : c.credit);
        Comparator<Course> byDayC   = Comparator.comparing(c -> orderDay(nullToEmpty(c.day)));
        Comparator<Course> byPeriodC= Comparator.comparing(c -> periodOrder(nullToEmpty(c.period)));
        Comparator<Course> byKind   = Comparator.comparing(c -> c.kind == null ? "" : c.kind);

        Comparator<Course> base = switch (String.valueOf(cbSortKey.getValue())) {
            case "科目名" ->
                byName.thenComparing(byYear).thenComparing(byTerm).thenComparing(byGrade)
                      .thenComparing(byCredit).thenComparing(byDayC).thenComparing(byPeriodC);

            case "開講年度" ->
                byYear.thenComparing(byTerm).thenComparing(byGrade).thenComparing(byCredit)
                      .thenComparing(byDayC).thenComparing(byPeriodC).thenComparing(byName);

            case "期" ->
                byTerm.thenComparing(byYear).thenComparing(byGrade).thenComparing(byCredit)
                      .thenComparing(byDayC).thenComparing(byPeriodC).thenComparing(byName);

            case "開講年次" ->
                byGrade.thenComparing(byYear).thenComparing(byTerm).thenComparing(byCredit)
                       .thenComparing(byDayC).thenComparing(byPeriodC).thenComparing(byName);

            case "曜日" ->
                byDayC.thenComparing(byYear).thenComparing(byTerm).thenComparing(byGrade)
                      .thenComparing(byCredit).thenComparing(byPeriodC).thenComparing(byName);

            case "時限" ->
                byPeriodC.thenComparing(byYear).thenComparing(byTerm).thenComparing(byGrade)
                         .thenComparing(byCredit).thenComparing(byDayC).thenComparing(byName);

            case "科目区分" ->
                byKind.thenComparing(byYear).thenComparing(byTerm).thenComparing(byGrade)
                      .thenComparing(byCredit).thenComparing(byDayC).thenComparing(byPeriodC).thenComparing(byName);

            default ->
                byName.thenComparing(byYear).thenComparing(byTerm).thenComparing(byGrade)
                      .thenComparing(byCredit).thenComparing(byDayC).thenComparing(byPeriodC);
        };

        return cbDesc.isSelected() ? base.reversed() : base;
    }

    private void refreshArchiveList() {
        listArchived.getItems().clear();
        if (archiveDir != null && Files.exists(archiveDir)) {
            try (var stream = Files.list(archiveDir)) {
                stream.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .forEach(listArchived.getItems()::add);
            } catch (Exception ignored) {}
        }
    }

    private boolean isArchived(Course c) {
        if (workspaceDir == null) return false;
        return !Files.exists(workspaceDir.resolve(resolveFolderName(c)));
    }


    private Optional<Course> findCourseByArchivedFolder(String folder) {
        var byFolder = allCourses.stream().filter(c -> folder.equals(c.folder)).findFirst();
        if (byFolder.isPresent()) return byFolder;

        return allCourses.stream()
                .filter(c -> sanitize(c.name).equals(folder) || folder.startsWith(c.name + "_"))
                .findFirst();
    }

    private static int orderDay(String day) {
        return switch (day) {
            case "SUN" -> 0; case "MON" -> 1; case "TUE" -> 2; case "WED" -> 3;
            case "THU" -> 4; case "FRI" -> 5; case "SAT" -> 6; default -> 7;
        };
    }
    private static int periodOrder(String p) {
        try { return Integer.parseInt(p); } catch (Exception ignored) { return 99; }
    }
    
    private static String jpDayLabel(String day) {
        return switch (day) {
            case "MON" -> "月"; case "TUE" -> "火"; case "WED" -> "水";
            case "THU" -> "木"; case "FRI" -> "金"; case "SAT" -> "土";
            case "SUN" -> "日"; case "OTHER" -> "その他"; default -> day;
        };
    }
    private static int termOrder(String term) {
        return switch (term) {
            case "前期"      -> 1;
            case "後期"      -> 2;
            case "春ターム"  -> 3;
            case "夏ターム"  -> 4;
            case "秋ターム"  -> 5;
            case "冬ターム"  -> 6;
            case "集中講義"  -> 7;
            default          -> 99;
        };
    }


    private static String jpPeriodLabel(String p) {
        try {
            int n = Integer.parseInt(p);
            return String.valueOf(n);
        } catch (Exception e) {
            return "その他";
        }
    }


    private void openExplorer(Path path) {
        try {
            Files.createDirectories(path);
            new ProcessBuilder("explorer.exe", path.toAbsolutePath().toString()).start();
        } catch (Exception ex) {
            Dialogs.error("エラー", "エクスプローラーを開けませんでした: " + ex.getMessage());
        }
    }
    
    
    private void applyWorkspace(Path ws, Path ar) {
        try {
            Files.createDirectories(ws);
            Files.createDirectories(ar);
        } catch (Exception e) {
            throw new RuntimeException("ワークスペースの作成に失敗: " + e.getMessage(), e);
        }

        this.workspaceDir = ws;
        this.archiveDir   = ar;

        var s = SettingsStore.load();
        s.workspaceDir = ws.toString();
        s.archiveDir   = ar.toString();
        SettingsStore.save(s);

        updatePathIndicator();
        refreshAll();
    }
    
    private void updatePathIndicator() {
        String ws = (workspaceDir == null) ? "（未設定）" : workspaceDir.toString();
        String ar = (archiveDir   == null) ? "（未設定）" : archiveDir.toString();
        String text = "WS: " + ws + " / AR: " + ar;
        lblPaths.setText(text);
        if (lblPaths.getTooltip() != null) {
            lblPaths.getTooltip().setText("Workspace: " + ws + "\nArchive: " + ar);
        }
    }


    private static Path uniquePath(Path target) throws IOException {
        if (!Files.exists(target)) return target;
        String name = target.getFileName().toString();
        Path parent = target.getParent();
        int i = 2;
        Path cand;
        do {
            cand = parent.resolve(name + "_" + i);
            i++;
        } while (Files.exists(cand));
        return cand;
    }

    private static void moveDirectory(Path src, Path dst) throws IOException {
        Files.createDirectories(dst.getParent());
        try {
            Files.move(src, dst);
        } catch (IOException moveFail) {
            copyDirectory(src, dst);
            deleteDirectory(src);
        }
    }

    private static void copyDirectory(Path src, Path dst) throws IOException {
        try (var paths = Files.walk(src)) {
            paths.forEach(p -> {
                Path rel = src.relativize(p);
                Path to = dst.resolve(rel);
                try {
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(to);
                    } else {
                        Files.createDirectories(to.getParent());
                        Files.copy(p, to, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException e) { /* ignore */ }
            });
        }
    }

    private void openCoursePresetMenu() {
        var use = new ButtonType("プリセットから一括登録", ButtonBar.ButtonData.LEFT);
        var edit = new ButtonType("プリセットを作成/編集", ButtonBar.ButtonData.LEFT);
        var imp = new ButtonType("JSONをインポート…", ButtonBar.ButtonData.LEFT);
        var cancel = ButtonType.CANCEL;

        var a = new Alert(Alert.AlertType.NONE, "", use, edit, imp, cancel);
        a.setTitle("講義プリセット");
        a.setHeaderText("操作を選択してください");
        var res = a.showAndWait().orElse(cancel);

        if (res == use) {
            bulkRegisterFromPreset();
        } else if (res == edit) {
            editCoursePreset();
        } else if (res == imp) {
            importCoursePresetJson();
        }
    }


    private void bulkRegisterFromPreset() {
        if (workspaceDir == null) {
            Dialogs.error("エラー", "先にワークスペースを設定してください。");
            return;
        }
        var names = CoursePresetStore.presetNames();
        if (names.isEmpty()) {
            Dialogs.error("エラー", "講義プリセットがありません。先に作成/インポートしてください。");
            return;
        }
        var cd = new ChoiceDialog<>(names.get(0), names);
        cd.setTitle("プリセットから一括登録");
        cd.setHeaderText("使用する講義プリセットを選んでください");
        cd.setContentText("プリセット名：");
        var chosen = cd.showAndWait().orElse(null);
        if (chosen == null) return;

        var opt = CoursePresetStore.findByName(chosen);
        if (opt.isEmpty() || opt.get().courses == null || opt.get().courses.isEmpty()) {
            Dialogs.error("エラー", "このプリセットには講義が登録されていません。");
            return;
        }

        int ok = 0, ng = 0;
        for (CoursePreset cp : opt.get().courses) {
            try {
                var c = toCourse(cp);

                int id = repo.insert(c);
                c.id = id;

                String folder = buildFolderNameFromId(c);
                Path dst = workspaceDir.resolve(folder);
                if (Files.exists(dst)) { ng++; continue; }

                Files.createDirectories(dst);
                c.folder = folder;
                repo.updateFolder(c.id, folder);

                ok++;
            } catch (Exception ex) {
                ng++;
            }
        }
        refreshAll();
        new Alert(Alert.AlertType.INFORMATION,
            "登録完了: 成功 " + ok + " 件 / 失敗 " + ng + " 件", ButtonType.OK).showAndWait();
    }


    private void editCoursePreset() {
        var names = CoursePresetStore.presetNames();
        String name = null;

        if (names.isEmpty()) {
            var tid = new TextInputDialog();
            tid.setTitle("新規プリセット作成");
            tid.setHeaderText("プリセット名（大学名など）を入力");
            name = tid.showAndWait().orElse("").trim();
            if (name.isEmpty()) return;
        } else {
            var ask = new Alert(Alert.AlertType.NONE, "", ButtonType.APPLY, ButtonType.OK, ButtonType.CANCEL);
            ask.setTitle("プリセット名の選択");
            ask.setHeaderText("既存プリセットを編集するか、新規を作成します。\n" +
                    "OK = 既存から選択 / 適用 = 新規作成");
            var res = ask.showAndWait().orElse(ButtonType.CANCEL);
            if (res == ButtonType.APPLY) {
                var tid = new TextInputDialog();
                tid.setTitle("新規プリセット作成");
                tid.setHeaderText("プリセット名（大学名など）を入力");
                name = tid.showAndWait().orElse("").trim();
                if (name.isEmpty()) return;
            } else if (res == ButtonType.OK) {
                var cd = new ChoiceDialog<>(names.get(0), names);
                cd.setTitle("既存プリセットを選択");
                cd.setHeaderText("編集するプリセットを選択してください");
                cd.setContentText("プリセット名：");
                name = cd.showAndWait().orElse(null);
                if (name == null) return;
            } else {
                return;
            }
        }

        var list = new ListView<CoursePreset>();
        list.setPrefHeight(300);

        CoursePresetStore.findByName(name).ifPresent(p -> {
            if (p.courses != null) list.getItems().setAll(p.courses);
        });

        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(CoursePreset it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) { setText(null); return; }
                String y = (it.year == null ? "-" : it.year.toString());
                String t = (it.term == null ? "-" : it.term);
                String d = (it.day == null ? "-" : it.day);
                String p = (it.period == null ? "-" : it.period);
                setText("「%s」 / %s / %s / %s・%s".formatted(it.name, y, t, d, p));
            }
        });

        var btnAdd = new Button("追加");
        var btnEdit = new Button("編集");
        var btnDel = new Button("削除");

        btnAdd.setOnAction(e -> {
            var dlg = new AddCourseDialog();
            var res = dlg.showAndWait();
            res.ifPresent(c -> list.getItems().add(fromCourse(c)));
        });

        btnEdit.setOnAction(e -> {
            var cur = list.getSelectionModel().getSelectedItem();
            if (cur == null) return;
            var dlg = new AddCourseDialog(toCourse(cur));
            var res = dlg.showAndWait();
            res.ifPresent(c -> {
                int idx = list.getSelectionModel().getSelectedIndex();
                list.getItems().set(idx, fromCourse(c));
            });
        });

        btnDel.setOnAction(e -> {
            int idx = list.getSelectionModel().getSelectedIndex();
            if (idx >= 0) list.getItems().remove(idx);
        });

        var box = new GridPane();
        box.setHgap(8); box.setVgap(8); box.setPadding(new Insets(10));
        int r = 0;
        box.addRow(r++, new Label("プリセット名"), new Label(name));
        box.addRow(r++, new Label("講義一覧"), list);
        box.addRow(r++, new Label("操作"), new HBox(8, btnAdd, btnEdit, btnDel));

        var dlg = new Alert(Alert.AlertType.NONE, "", ButtonType.OK, ButtonType.CANCEL);
        dlg.setTitle("講義プリセット編集");
        dlg.setHeaderText("講義の集合を編集してください");
        dlg.getDialogPane().setContent(box);
        if (dlg.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            var p = new CoursePresetStore.Preset();
            p.name = name;
            p.courses = new ArrayList<>(list.getItems());
            CoursePresetStore.upsert(p);
            new Alert(Alert.AlertType.INFORMATION, "保存しました。", ButtonType.OK).showAndWait();
        }
    }

    private void importCoursePresetJson() {
        var fc = new FileChooser();
        fc.setTitle("講義プリセット JSON を選択");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        var f = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (f == null) return;

        try {
            var result = CoursePresetStore.importFromFile(f.toPath());
            new Alert(Alert.AlertType.INFORMATION,
                "取り込み完了\n新規プリセット: " + result.addedPresets +
                " / 更新: " + result.updatedPresets +
                " / 追加講義数: " + result.addedCourses,
                ButtonType.OK).showAndWait();
        } catch (Exception ex) {
            Dialogs.error("エラー", "インポート失敗: " + ex.getMessage());
        }
    }

    private static Course toCourse(CoursePreset p) {
        var c = new Course();
        c.name = p.name;
        c.day = p.day;
        c.period = p.period;
        c.code = p.code;
        c.grade = p.grade;
        c.kind = (p.kind == null || p.kind.isBlank()) ? null : p.kind;
        c.credit = p.credit;
        c.teacher = p.teacher;
        c.email = p.email;
        c.webpage = p.webpage;
        c.syllabus = p.syllabus;
        c.attendanceRequired = Boolean.TRUE.equals(p.attendanceRequired);
        c.examsCsv = p.examsCsv;
        c.year = p.year;
        c.term = p.term;
        return c;
    }

    private static CoursePreset fromCourse(Course c) {
        var p = new CoursePreset();
        p.name = c.name;
        p.day = c.day;
        p.period = c.period;
        p.code = c.code;
        p.grade = c.grade;
        p.kind = (c.kind == null ? null : c.kind.toString());
        p.credit = c.credit;
        p.teacher = c.teacher;
        p.email = c.email;
        p.webpage = c.webpage;
        p.syllabus = c.syllabus;
        p.attendanceRequired = c.attendanceRequired;
        p.examsCsv = c.examsCsv;
        p.year = c.year;
        p.term = c.term;
        return p;
    }

//    private static Path uniqueName(Path target) throws IOException {
//        if (!Files.exists(target)) return target;
//        Path dir = target.getParent();
//        String base = target.getFileName().toString();
//        int i = 2;
//        Path cand;
//        do {
//            cand = dir.resolve(base + "_" + i);
//            i++;
//        } while (Files.exists(cand));
//        return cand;
//    }
    
    private static String buildFolderNameFromId(Course c) {
        String idPart = String.format("[%04d]", c.id);
        String title  = sanitize(c.name);
        String year   = (c.year == null ? "" : "_" + c.year);
        return idPart + title + year;
    }
//    private Path pathInWorkspace(Course c) {
//        String folder = (c.folder != null && !c.folder.isBlank()) ? c.folder : sanitize(c.name);
//        return workspaceDir.resolve(folder);
//    }
    
    private static String resolveFolderName(Course c) {
        return (c.folder != null && !c.folder.isBlank()) ? c.folder : sanitize(c.name);
    }
    private static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }
    
    
    
    
    
    

    private static final List<String> TERMS = List.of("前期","後期","春ターム","夏ターム","秋ターム","冬ターム","集中講義");

    private void archiveCourse(Course c) {
        try {
            if (c == null) return;
            if (workspaceDir == null || archiveDir == null) {
                Dialogs.error("エラー", "ワークスペース/アーカイブの場所が未設定です。");
                return;
            }

            CheckBox cbAdd = new CheckBox("アーカイブ後、この講義の単位を「現在の取得単位」に追加する");
            String kindLabel = (c.kind==null||c.kind.isBlank()? "（未分類）" : c.kind.trim());
            String crLabel = (c.credit==null? "不明" : String.format(java.util.Locale.ROOT,"%.1f", c.credit));
            Label info = new Label("区分: " + kindLabel + " / 単位: " + crLabel);
            VBox box = new VBox(8, info, cbAdd);
            box.setPadding(new Insets(8));
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.OK, ButtonType.CANCEL);
            confirm.setHeaderText("講義をアーカイブします");
            confirm.getDialogPane().setContent(box);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            String folder = resolveFolderName(c);
            Path src = workspaceDir.resolve(folder);
            Path dst = archiveDir.resolve(folder);

            if (!Files.exists(src)) {
                Dialogs.error("エラー", "元フォルダが見つかりません: " + src);
                return;
            }
            if (Files.exists(dst)) {
                Dialogs.error("エラー", "同名フォルダがアーカイブに既に存在します: " + dst);
                return;
            }
            Files.createDirectories(archiveDir);
            Files.move(src, dst);

            if (cbAdd.isSelected()) {
                boolean ok = true;
                String err = null;
                if (c.kind==null || c.kind.isBlank()) { ok=false; err="科目区分（kind）が空です。"; }
                else if (c.credit==null || c.credit<=0) { ok=false; err="単位数（credit）が不正です。"; }

                if (!ok) {
                    Dialogs.error("取得単位の加算エラー", err + "\nアーカイブは完了していますが、取得単位には追加されませんでした。");
                } else {
                    try {
                        CreditStore.get().addProgress(c.kind.trim(), c.credit);
                    } catch (Exception ex) {
                        Dialogs.error("加算失敗", "取得単位への追加に失敗: " + ex.getMessage());
                    }
                }
            }

            new Alert(Alert.AlertType.INFORMATION, "アーカイブへ移動しました:\n" + dst, ButtonType.OK).showAndWait();
            refreshAll();
        } catch (Exception ex) {
            Dialogs.error("アーカイブ失敗", ex.getMessage());
        }
    }


    private void openBulkArchiveDialog() {
        if (workspaceDir == null || archiveDir == null) {
            Dialogs.error("エラー", "先にワークスペース/アーカイブを設定してください。");
            return;
        }


        List<Course> active = allCourses.stream().filter(c -> !isArchived(c)).collect(Collectors.toList());

        var names = active.stream().map(c -> nullToEmpty(c.name)).filter(s -> !s.isBlank())
                .distinct().sorted().collect(Collectors.toList());
        var years = active.stream().map(c -> c.year).filter(Objects::nonNull)
                .distinct().sorted().collect(Collectors.toList());
        var grades = active.stream().map(c -> c.grade).filter(Objects::nonNull)
                .distinct().sorted().collect(Collectors.toList());
        var terms = new ArrayList<>(TERMS);

        active.stream().map(c -> nullToEmpty(c.term)).filter(s -> !s.isBlank())
                .filter(s -> !terms.contains(s)).sorted().forEach(terms::add);

        var dlg = new Alert(Alert.AlertType.NONE, "", ButtonType.OK, ButtonType.CANCEL);
        dlg.setTitle("条件で一括アーカイブ");
        dlg.setHeaderText("条件に一致する登録済み講義をアーカイブに移動します");

        var cbName  = new ComboBox<String>();
        var cbYear  = new ComboBox<Integer>();
        var cbTerm  = new ComboBox<String>();
        var cbGrade = new ComboBox<Integer>();

        cbName.getItems().add( "（指定なし）" ); cbName.getItems().addAll(names);  cbName.getSelectionModel().selectFirst();
        cbYear.getItems().add( null ); cbYear.getItems().addAll(years);            cbYear.getSelectionModel().selectFirst();
        cbTerm.getItems().add( "（指定なし）" ); cbTerm.getItems().addAll(terms);  cbTerm.getSelectionModel().selectFirst();
        cbGrade.getItems().add( null ); cbGrade.getItems().addAll(grades);         cbGrade.getSelectionModel().selectFirst();

        var grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8); grid.setPadding(new Insets(10));
        int r = 0;
        grid.addRow(r++, new Label("授業科目名"), cbName);
        grid.addRow(r++, new Label("開講年度"), cbYear);
        grid.addRow(r++, new Label("開講期"),   cbTerm);
        grid.addRow(r++, new Label("開講年次"), cbGrade);

        dlg.getDialogPane().setContent(grid);

        if (dlg.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        String nameSel  = cbName.getValue();
        Integer yearSel = cbYear.getValue();
        String termSel  = cbTerm.getValue();
        Integer gradeSel= cbGrade.getValue();

        List<Course> target = active.stream().filter(c -> {
            if (nameSel  != null && !"（指定なし）".equals(nameSel) && !Objects.equals(c.name, nameSel)) return false;
            if (yearSel  != null && !Objects.equals(c.year, yearSel)) return false;
            if (termSel  != null && !"（指定なし）".equals(termSel) && !Objects.equals(c.term, termSel)) return false;
            if (gradeSel != null && !Objects.equals(c.grade, gradeSel)) return false;
            return true;
        }).collect(Collectors.toList());

        if (target.isEmpty()) {
            Dialogs.info("結果", "条件に一致する講義はありませんでした。");
            return;
        }

        if (!Dialogs.confirm("確認", "一致した " + target.size() + " 件をアーカイブへ移動します。よろしいですか？")) return;

        int ok = 0, skip = 0, ng = 0;
        try { Files.createDirectories(archiveDir); } catch (IOException ignored) {}

        for (Course c : target) {
            try {
                String folder = resolveFolderName(c);
                Path src = workspaceDir.resolve(folder);
                Path dst = archiveDir.resolve(folder);

                if (!Files.exists(src)) { skip++; continue; }
                if (Files.exists(dst))  { skip++; continue; }
                Files.move(src, dst);
                ok++;
            } catch (Exception ex) {
                ng++;
            }
        }

        refreshAll();
        Dialogs.info("結果", "移動 完了: " + ok + " 件 / スキップ: " + skip + " 件 / 失敗: " + ng + " 件");
    }
    
    private void bulkRestoreArchived() {
        if (workspaceDir == null || archiveDir == null) {
            Dialogs.error("エラー", "先にワークスペース/アーカイブを設定してください。");
            return;
        }
        List<String> folders = listArchived.getItems();
        if (folders.isEmpty()) {
            Dialogs.info("情報", "アーカイブに対象がありません。");
            return;
        }
        if (!Dialogs.confirm("確認", "アーカイブ内の " + folders.size() + " 件を登録に戻します。よろしいですか？")) return;

        int ok = 0, skip = 0, ng = 0;
        for (String folder : folders) {
            try {
                Path src = archiveDir.resolve(folder);
                Path dst = workspaceDir.resolve(folder);
                if (!Files.exists(src)) { skip++; continue; }
                if (Files.exists(dst))  { skip++; continue; }
                Files.move(src, dst);
                ok++;
            } catch (Exception ex) { ng++; }
        }
        refreshAll();
        Dialogs.info("結果", "復元 完了: " + ok + " 件 / スキップ: " + skip + " 件 / 失敗: " + ng + " 件");
    }

    private void bulkDeleteArchived() {
        if (archiveDir == null) {
            Dialogs.error("エラー", "アーカイブの場所が未設定です。");
            return;
        }
        List<String> folders = listArchived.getItems();
        if (folders.isEmpty()) {
            Dialogs.info("情報", "アーカイブに対象がありません。");
            return;
        }
        if (!Dialogs.confirm("確認", "アーカイブ内の " + folders.size() + " 件を削除します。\n対応する講義もDBから削除します。よろしいですか？")) return;

        int ok = 0, skip = 0, ng = 0;
        for (String folder : folders) {
            try {
                Path p = archiveDir.resolve(folder);
                if (!Files.exists(p)) { skip++; continue; }
                deleteDirectory(p);
                findCourseByArchivedFolder(folder).ifPresent(c -> {
                    try { repo.delete(c.id); } catch (Exception ignore) {}
                });
                ok++;
            } catch (Exception ex) { ng++; }
        }
        refreshAll();
        Dialogs.info("結果", "削除 完了: " + ok + " 件 / スキップ: " + skip + " 件 / 失敗: " + ng + " 件");
    }
    private void refreshAssignedIdsFromTimetable() {
        assignedInTimetable.clear();
        TimetableStore.loadLastOpened().ifPresent(t -> {
            if (t.slots != null) assignedInTimetable.addAll(t.slots.values());
            if (t.sunSlots != null) {
                t.sunSlots.values().forEach(list -> { if (list != null) assignedInTimetable.addAll(list); });
            }
        });
    }
    private void updateTTStatusLabel() {
        if (cbTimetable.isIndeterminate()) {
            lblTTStatus.setText("未登録");
        } else if (cbTimetable.isSelected()) {
            lblTTStatus.setText("登録済み");
        } else {
            lblTTStatus.setText("全件");
        }
    }
    
    private void assignCourseToTimetable(Course c) {
        if (c == null) return;

        if (c.day == null || c.day.isBlank() || c.period == null || c.period.isBlank()) {
            Dialogs.error("エラー", "この講義には「曜日」または「時限」が設定されていません。\n講義情報を編集してから再度お試しください。");
            return;
        }

        var ttOpt = TimetableStore.loadLastOpened();
        TimetableStore.Timetable tt = ttOpt.orElse(null);
        if (tt == null) {
            if (c.year == null || c.term == null || c.term.isBlank()) {
                Dialogs.error("エラー", "時間割がありませんが、この講義の「開講年度 / 開講期」が未設定のため自動作成できません。");
                return;
            }
            String main = mainTermOf(c.term);
            if (main == null) {
                Dialogs.error("エラー", "この講義の開講期「" + c.term + "」から前期/後期が判定できないため、時間割を自動作成できません。");
                return;
            }
            if (!Dialogs.confirm("確認", "時間割がありません。\n" + c.year + "年 " + main + " の時間割を新規作成しますか？")) {
                return;
            }
            tt = new TimetableStore.Timetable();
            tt.year = c.year;
            tt.termMain = main;
            tt.name = "時間割_" + tt.year + "_" + tt.termMain;
            TimetableStore.upsert(tt);
            TimetableStore.saveLastOpened(tt.id);
        }

        if (!isTermCompatible(tt, c)) {
            String msg =
                "この講義は現在の時間割に登録できません。\n" +
                "講義情報を修正するか、時間割を新規作成してください。\n\n" +
                "（" + nullToEmpty(c.name) + "） 開講年度 " + nullToDash(c.year) + "年　開講期 " + nullToEmpty(c.term) + "\n" +
                "現在の時間割　開講年度 " + nullToDash(tt.year) + "年　開講期 " + nullToEmpty(tt.termMain);
            Dialogs.error("登録不可", msg);
            return;
        }

        if ("OTHER".equals(c.period)) {
            var candidates = candidateKeysForOtherPeriod(c, tt);
            if (candidates.isEmpty()) {
                Dialogs.info("情報", "登録できる空きコマが見つかりませんでした。");
                return;
            }
            var labels = new ArrayList<String>();
            for (String k : candidates) {
                String[] dp = k.split("-");
                labels.add(jpLabelOf(dp[0], Integer.parseInt(dp[1])));
            }

            var dlg = new ChoiceDialog<>(labels.get(0), labels);
            dlg.setTitle("登録先の選択");
            dlg.setHeaderText("空いているコマから登録先を選んでください（時限=その他）");
            dlg.setContentText("コマ：");
            var chosen = dlg.showAndWait().orElse(null);
            if (chosen == null) return;

            int idx = labels.indexOf(chosen);
            String key = candidates.get(idx);
            String[] dp = key.split("-");
            String daySel = dp[0];
            int periodSel = Integer.parseInt(dp[1]);

            removeCourseFromAllSlots(tt, c.id);

            if (tt.slots == null) tt.slots = new java.util.LinkedHashMap<>();
            tt.slots.put(key, c.id);
            TimetableStore.upsert(tt);

            Dialogs.info("登録完了", "「" + nullToEmpty(c.name) + "」を " + jpLabelOf(daySel, periodSel) + " に登録しました。");
            return;
        }

        String day = normalizeDayForAssign(c.day);
        int period = safeInt(c.period);
        if (period <= 0) {
            Dialogs.error("エラー", "不正な時限値です: " + c.period);
            return;
        }

        if (tt.slots == null) tt.slots = new java.util.LinkedHashMap<>();
        String key = day + "-" + period;
        Integer existsId = tt.slots.get(key);

        if (existsId != null && Objects.equals(existsId, c.id)) {
            Dialogs.info("情報", "このコマには既に登録済みです。");
            return;
        }

        if (existsId == null) {
            removeCourseFromAllSlots(tt, c.id);
            tt.slots.put(key, c.id);
            TimetableStore.upsert(tt);
            Dialogs.info("登録完了", "「" + nullToEmpty(c.name) + "」を " + jpLabelOf(day, period) + " に登録しました。");
            return;
        }

        String occupiedName = "(不明)";
        Course occupied = findById(existsId);
        if (occupied != null && occupied.name != null && !occupied.name.isBlank()) occupiedName = occupied.name;

        var removePrev = new ButtonType("前の講義を外す");
        var cancel = new ButtonType("キャンセル");
        var alert = new Alert(Alert.AlertType.WARNING,
                "移動先 " + jpLabelOf(day, period) + " は「" + occupiedName + "」で埋まっています。",
                removePrev, cancel);
        alert.setTitle("移動先が埋まっています");
        var choice = alert.showAndWait().orElse(cancel);

        if (choice == removePrev) {
            tt.slots.remove(key);
            if (tt.memos != null && existsId != null) tt.memos.remove(key + "#" + existsId);

            removeCourseFromAllSlots(tt, c.id);

            tt.slots.put(key, c.id);
            TimetableStore.upsert(tt);
            Dialogs.info("登録完了", "「" + nullToEmpty(c.name) + "」を " + jpLabelOf(day, period) + " に登録しました（置換）。");
        }
    }
    
    private List<String> candidateKeysForOtherPeriod(Course c, TimetableStore.Timetable tt) {
        var out = new ArrayList<String>();
        if (tt == null) return out;

        String day = c.day == null ? "" : c.day.trim();
        boolean weekday =
                "MON".equals(day) || "TUE".equals(day) || "WED".equals(day) ||
                "THU".equals(day) || "FRI".equals(day) || "SAT".equals(day);

        if (weekday) {
            addIfOpen(out, tt, day, 6);
        }
        for (int p = 1; p <= 6; p++) addIfOpen(out, tt, "SUN", p);

        return out;
    }

    private void addIfOpen(List<String> out, TimetableStore.Timetable tt, String day, int period) {
        if (period <= 0) return;
        String key = day + "-" + period;
        Integer exists = (tt.slots == null) ? null : tt.slots.get(key);
        if (exists == null) out.add(key);
    }




 private Course findById(Integer id) {
     if (id == null) return null;
     for (Course x : repo.findAll()) if (Objects.equals(x.id, id)) return x;
     return null;
 }

 private static String normalizeDayForAssign(String courseDay) {
     return "OTHER".equals(courseDay) ? "SUN" : courseDay;
 }

 private static int safeInt(String s) {
     try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
 }

 private static String nullToEmpty(String s) { return s == null ? "" : s; }
 private static String nullToDash(Object o)   { return o == null ? "-" : o.toString(); }

 private static boolean isTermCompatible(TimetableStore.Timetable tt, Course c) {
     if (tt == null) return false;
     if (c == null) return false;
     if (tt.year == null || c.year == null) return false;
     if (!Objects.equals(tt.year, c.year)) return false;

     var ok = allowedTerms(tt.termMain);
     String term = c.term == null ? "" : c.term;
     return ok.contains(term);
 }

 private static String mainTermOf(String term) {
     if (term == null) return null;
     switch (term) {
         case "前期": return "前期";
         case "後期": return "後期";
         case "春ターム":
         case "夏ターム": return "前期";
         case "秋ターム":
         case "冬ターム": return "後期";
         default: return null;
     }
 }

 private static java.util.Set<String> allowedTerms(String main) {
     if ("前期".equals(main)) return java.util.Set.of("前期","春ターム","夏ターム");
     if ("後期".equals(main)) return java.util.Set.of("後期","秋ターム","冬ターム");
     return java.util.Set.of();
 }

 private static void removeCourseFromAllSlots(TimetableStore.Timetable tt, int courseId) {
     if (tt == null || tt.slots == null) return;
     var it = tt.slots.entrySet().iterator();
     while (it.hasNext()) {
         var e = it.next();
         if (Objects.equals(e.getValue(), courseId)) {
             if (tt.memos != null) tt.memos.remove(e.getKey() + "#" + courseId);
             it.remove();
         }
     }
 }

 private static String jpLabelOf(String day, int period) {
     String d = switch (day) {
         case "MON" -> "月";
         case "TUE" -> "火";
         case "WED" -> "水";
         case "THU" -> "木";
         case "FRI" -> "金";
         case "SAT" -> "土";
         case "SUN" -> "日（＋曜日指定なし）";
         default    -> day;
     };
     return d + " / " + period + "限";
 }

}

