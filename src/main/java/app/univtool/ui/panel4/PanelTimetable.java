package app.univtool.ui.panel4;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import app.univtool.core.SettingsStore;
import app.univtool.core.TimetableStore;
import app.univtool.core.WorkspaceService;
import app.univtool.model.Course;
import app.univtool.repo.CourseRepository;
import app.univtool.ui.credit.CreditDashboardWindow;
import app.univtool.ui.panel1.AddCourseDialog;
import app.univtool.ui.panel2.FileSaveOps;
import app.univtool.ui.panel3.PanelFileSearch;
import app.univtool.util.Dialogs;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;


/**
 * パネル４「時間割」
 *
 * ・初期は「時間割を作成」＋空のカレンダー
 * ・右ダブルクリック（空マス）で該当コマに入れられる講義一覧（＋末尾「講義を追加」）
 * ・右ダブルクリック（登録済み）で「講義詳細＋自由記入メモ」ダイアログ
 * ・左クリックでコンテキスト操作（空ける / スケジュール表示(Stub) / ファイル検索 / Explorer / アーカイブ）
 * ・画面下「講義のフィルタリング」で背景色を切替
 */
public class PanelTimetable extends BorderPane {

    private static final List<String> DAYS = List.of("MON","TUE","WED","THU","FRI","SAT","SUN");
    private static final Map<String,String> DAY_LABEL = Map.of(
            "MON","月","TUE","火","WED","水","THU","木","FRI","金","SAT","土","SUN","日（＋曜日指定なし）"
    );

    private final CourseRepository repo = new CourseRepository();
    private final GridPane grid = new GridPane();
    private final Label lblTitle = new Label("（未作成）");
    private final Button btnCreate = new Button("時間割を作成");
    private final Button btnClear  = new Button("時間割をクリア");
    private final HBox filterBar = new HBox(12);
    private final Map<String, Cell> cellMap = new HashMap<>(); // key="DAY-PERIOD"
    
    private final java.util.Set<Integer> selGrades     = new java.util.HashSet<>();
    private final java.util.Set<Double>  selCredits    = new java.util.HashSet<>();
    private final java.util.Set<Boolean> selAttendance = new java.util.HashSet<>();
    private boolean examRequireAll = false;
    
    private enum ExamFlag { MID_EXAM, FINAL_EXAM, MID_REPORT, FINAL_REPORT }
    private final java.util.Set<ExamFlag> selExamFlags = new java.util.HashSet<>();
    private boolean selExamNone = false;

    private final Label lblCreditInfo = new Label("現在: 0単位");  // 下部に出す単位情報
    private final Button btnOpenCreditStatus = new Button("単位取得状況を開く…");

    private final Button btnSaveHighlight  = new Button("強調表示を保存");
    private final Button btnClearHighlight = new Button("強調表示をクリア");

    private final Button btnDeleteTimetable = new Button("時間割の削除");
    
    // 保存ファイルの読み込みを一度だけ行うためのフラグ
    private String loadedHighlightForTimetableId = null;

    
    private TimetableStore.Timetable current;
    private Path workspaceDir;
    private Path archiveDir;

    public PanelTimetable() {
        setPadding(new Insets(12));

        restoreWorkspace();

        var topBar = new HBox(10);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        lblTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        topBar.getChildren().addAll(btnCreate, btnClear, spacer, new Label("時間割名:"), lblTitle, btnDeleteTimetable);
        setTop(topBar);

        buildBlankGrid();
        setCenter(grid);

        var bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        var spacerBottom = new Region();
        HBox.setHgrow(spacerBottom, Priority.ALWAYS);
        bottomBar.getChildren().addAll(filterBar, spacerBottom, btnSaveHighlight, btnClearHighlight);
        lblCreditInfo.setStyle("-fx-font-weight: bold; -fx-text-fill: #333;");
        var bottom = new VBox(6,
                lblCreditInfo,
                btnOpenCreditStatus,
                bottomBar
        );
        setBottom(bottom);

        // イベント
        btnCreate.setOnAction(e -> createTimetableDialog());
        btnClear.setOnAction(e -> {
            if (current == null) {
                Dialogs.info("情報", "先に時間割を作成してください。");
                return;
            }
            if (!Dialogs.confirm("確認", "この時間割の全てのマスから講義を外します。よろしいですか？")) return;
            clearAllAssignments();
        });
        btnOpenCreditStatus.setOnAction(e -> {
            Stage owner = (Stage) (getScene() == null ? null : getScene().getWindow());
            CreditDashboardWindow.openWhatIf(owner);
        });

        btnDeleteTimetable.setOnAction(e -> {
            if (current == null) {
                Dialogs.info("情報", "削除できる時間割がありません。");
                return;
            }
            final String deletedId = current.id;

            var warn = new Alert(Alert.AlertType.WARNING,
                    "この時間割を削除します。元に戻せません。\nよろしいですか？",
                    ButtonType.OK, ButtonType.CANCEL);
            warn.setTitle("時間割の削除");
            var res = warn.showAndWait().orElse(ButtonType.CANCEL);
            if (res != ButtonType.OK) return;

            boolean doArchive = Dialogs.confirm("確認", "登録されていた講義を一括でアーカイブしますか？");

            if (doArchive) {
                int failed = archiveAllCoursesOfCurrent(); // ★新規メソッド
                if (failed > 0) {
                    Dialogs.error("一部失敗", failed + "件の講義フォルダの移動に失敗しました。手動でご確認ください。");
                }
            }

            try { deleteHighlightStateOnDisk(); } catch (Exception ignore) {}
            try {
                TimetableStore.delete(deletedId);
            } catch (Throwable t) {
            }
            TimetableStore.clearLastOpened();

            if (deletedId != null && !deletedId.isBlank()) {
                app.univtool.core.TimetableSyncBus.fireTimetableDeleted(deletedId);
            }

            current = null;
            lblTitle.setText("（未作成）");
            buildBlankGrid();
            clearHighlightSelection();
            applyHighlighting();
        });



        btnSaveHighlight.setOnAction(e -> {
            if (current == null) return;
            try {
                saveHighlightStateToDisk();
                Dialogs.info("保存", "着色状態を保存しました。");
            } catch (Exception ex) {
                Dialogs.error("エラー", "着色状態の保存に失敗しました: " + ex.getMessage());
            }
        });

        btnClearHighlight.setOnAction(e -> {
            clearHighlightSelection();
            applyHighlighting(); 
            if (current != null) {
                try {
                    deleteHighlightStateOnDisk();
                } catch (Exception ex) {
                    Dialogs.error("エラー", "保存済みの着色状態ファイルを削除できませんでした: " + ex.getMessage());
                }
            }
        });

        // 前回開いていた時間割を復元
        TimetableStore.loadLastOpened().ifPresent(t -> {
            current = t;
            lblTitle.setText(current.name);
            renderAll();
        });
    }

    // ------------------ 初期化 ------------------

    private void restoreWorkspace() {
        var s = SettingsStore.load();
        if (s.workspaceDir == null || s.workspaceDir.isBlank()) {
            var wp = WorkspaceService.ensureDefault();
            workspaceDir = wp.workspace();
            archiveDir = wp.archive();
        } else {
            workspaceDir = Path.of(s.workspaceDir);
            if (s.archiveDir != null && !s.archiveDir.isBlank()) {
                archiveDir = Path.of(s.archiveDir);
            } else {
                var ws = workspaceDir;
                archiveDir = (ws.getParent() != null) ? ws.getParent().resolve("archive") : ws.resolveSibling("archive");
            }
            try { Files.createDirectories(workspaceDir); } catch (Exception ignored) {}
            try { Files.createDirectories(archiveDir); } catch (Exception ignored) {}
        }
    }

    private void buildBlankGrid() {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();
        cellMap.clear();

        var firstCol = new ColumnConstraints();
        firstCol.setHalignment(HPos.CENTER);
        firstCol.setMinWidth(70);
        firstCol.setPrefWidth(80);
        grid.getColumnConstraints().add(firstCol);

        for (int c = 0; c < DAYS.size(); c++) {
            var cc = new ColumnConstraints();
            cc.setHalignment(HPos.CENTER);
            cc.setHgrow(Priority.ALWAYS);
            cc.setPercentWidth( (100.0 - 8) / DAYS.size() );
            grid.getColumnConstraints().add(cc);
        }

        var headerRow = new RowConstraints();
        headerRow.setValignment(VPos.CENTER);
        headerRow.setMinHeight(32);
        grid.getRowConstraints().add(headerRow);

        for (int r = 0; r < 6; r++) {
            var rc = new RowConstraints();
            rc.setValignment(VPos.TOP);
            rc.setMinHeight(80);
            rc.setVgrow(Priority.ALWAYS);
            grid.getRowConstraints().add(rc);
        }

        var corner = new Label("時限");
        corner.setStyle("-fx-font-weight: bold;");
        grid.add(wrap(corner), 0, 0);

        for (int i = 0; i < DAYS.size(); i++) {
            String d = DAYS.get(i);
            Label h = new Label(DAY_LABEL.getOrDefault(d, d));
            h.setStyle("-fx-font-weight: bold;");
            grid.add(wrap(h), i+1, 0);
        }

        for (int p = 1; p <= 6; p++) {
            Label h = new Label(String.valueOf(p));
            h.setStyle("-fx-font-weight: bold;");
            grid.add(wrap(h), 0, p);
        }

        for (int p = 1; p <= 6; p++) {
            for (int i = 0; i < DAYS.size(); i++) {
                String day = DAYS.get(i);
                String key = key(day, p);
                Cell cell = new Cell(day, p);
                cellMap.put(key, cell);
                grid.add(cell.root, i+1, p);
            }
        }
    }

    private static StackPane wrap(Label l) {
        StackPane sp = new StackPane(l);
        sp.setPadding(new Insets(6));
        sp.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd;");
        return sp;
    }

    // ------------------ Timetable 作成 ------------------

    private void createTimetableDialog() {
        var dlg = new Dialog<ButtonType>();
        dlg.setTitle("時間割を作成");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        int thisYear = Year.now().getValue();
        var spYear = new Spinner<Integer>(thisYear - 1, thisYear + 5, thisYear); spYear.setEditable(true);

        var cbTerm = new ComboBox<String>();
        cbTerm.getItems().addAll("前期","後期");
        cbTerm.getSelectionModel().selectFirst();

        // 開講期間
        var dpStart = new javafx.scene.control.DatePicker();
        var dpEnd   = new javafx.scene.control.DatePicker();
        // それっぽい初期値
        cbTerm.valueProperty().addListener((o,ov,nv)->{
            var guess = guessRange(spYear.getValue(), nv);
            dpStart.setValue(guess.start); dpEnd.setValue(guess.end);
        });
        { var g = guessRange(spYear.getValue(), cbTerm.getValue()); dpStart.setValue(g.start); dpEnd.setValue(g.end); }

        var cbLimit = new CheckBox("登録可能単位数の上限を設定する");
        var spLimit = new Spinner<Double>(
            new SpinnerValueFactory.DoubleSpinnerValueFactory(0.5, 100.0, 20.0, 0.5));
        spLimit.setEditable(true); spLimit.setDisable(true);
        cbLimit.selectedProperty().addListener((obs,o,on)-> spLimit.setDisable(!on));

        var g = new GridPane(); g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12));
        g.addRow(0, new Label("開講年度"), spYear);
        g.addRow(1, new Label("開講期"),   cbTerm);
        g.addRow(2, new Label("開講開始日"), dpStart);
        g.addRow(3, new Label("開講終了日"), dpEnd);
        g.addRow(4, new Label("単位上限"),   new HBox(8, cbLimit, spLimit, new Label("単位")));
        dlg.getDialogPane().setContent(g);

        var res = dlg.showAndWait().orElse(ButtonType.CANCEL);
        if (res != ButtonType.OK) return;

        var t = new TimetableStore.Timetable();
        t.year = spYear.getValue();
        t.termMain = cbTerm.getValue();
        t.name = "時間割_" + t.year + "_" + t.termMain;
        t.creditLimit = cbLimit.isSelected()? spLimit.getValue() : null;

        current = TimetableStore.upsert(t);
        TimetableStore.saveLastOpened(current.id);
        lblTitle.setText(current.name);

        // 開講期間を ScheduleStore に保存
        if (dpStart.getValue()!=null && dpEnd.getValue()!=null) {
            app.univtool.core.ScheduleStore.get().setRange(current.id, dpStart.getValue(), dpEnd.getValue());
            app.univtool.core.TimetableSyncBus.fireRangeChanged(current.id);
        }

        renderAll();
        app.univtool.core.TimetableSyncBus.fireTimetableOpened(current.id);
    }

 
    private void clearTimetable() {
        clearAllAssignments();
    }

    private void clearAllAssignments() {
        if (current == null) return;
        if (current.slots != null) current.slots.clear();
        if (current.memos != null) current.memos.clear();
        TimetableStore.upsert(current);
        renderAll();
        app.univtool.core.TimetableSyncBus.fireTimetableChanged(current.id);
    }


 // ------------------ 描画/更新 ------------------
    private void renderAll() {
        buildBlankGrid();
        if (current == null) { 
            rebuildFilterUI();         // 空のUIにする
            applyHighlighting();        // 強調リセット
            updateCreditInfo(); 
            return; 
        }
        
        // 時間割が変わったときだけロード
        if (loadedHighlightForTimetableId == null || !Objects.equals(loadedHighlightForTimetableId, current.id)) {
            loadHighlightStateFromDisk();                // セット群・モードを復元
            loadedHighlightForTimetableId = current.id;
        }

        boolean changed1 = autoUnassignArchivedCourses();
        if (changed1) {
            TimetableStore.upsert(current);
            app.univtool.core.TimetableSyncBus.fireTimetableChanged(current.id);
        }
   
        boolean changed2 = checkAndResolveScheduleMismatches();
        if (changed2) {
            TimetableStore.upsert(current);
            app.univtool.core.TimetableSyncBus.fireTimetableChanged(current.id);
        }


        if (current.slots != null) {
            for (var e : current.slots.entrySet()) {
                String[] dp = e.getKey().split("-");
                if (dp.length != 2) continue;
                String day = dp[0];
                int period = safeInt(dp[1]);
                var cell = cellMap.get(key(day, period));
                if (cell != null && e.getValue() != null) {
                    Course c = findById(e.getValue());
                    if (c != null) cell.setCourses(List.of(c));
                }
            }
        }

        // UIを現在の登録状況から生成し直し反映
        rebuildFilterUI();
        applyHighlighting();
        updateCreditInfo();
    }



    private Course findById(Integer id) {
        if (id == null) return null;
        for (Course c : repo.findAll()) if (Objects.equals(c.id, id)) return c;
        return null;
    }

    // ------------------ Cell ------------------

    private final class Cell {
        final String day;
        final int period;
        final VBox root = new VBox(4);
        final VBox listBox = new VBox(2);

        Cell(String day, int period) {
            this.day = day; this.period = period;

            root.setPadding(new Insets(6));
            root.setStyle("-fx-background-color: white; -fx-border-color: #ddd;");
            var title = new Label(DAY_LABEL.getOrDefault(day, day) + " / " + period + "限");
            title.setStyle("-fx-text-fill:#666; -fx-font-size:11px;");
            root.getChildren().addAll(title, listBox);
            VBox.setVgrow(listBox, Priority.ALWAYS);

            root.setOnMouseClicked(e -> {
            	if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                    onRightDouble();
            	} else if (e.getButton() == MouseButton.SECONDARY && e.getClickCount() == 1) {
                    onLeftClick();
                }
            });

            refreshView(List.of());
        }

        void setCourses(List<Course> cs) {
            refreshView(cs);
        }

        @SuppressWarnings("unchecked")
        List<Course> getCourses() {
            List<Course> out = new ArrayList<>();
            for (var n : listBox.getChildren()) {
                Object tag = n.getUserData();
                if (tag instanceof Course c) out.add(c);
            }
            return out;
        }

        private void refreshView(List<Course> courses) {
            listBox.getChildren().clear();
            if (courses == null || courses.isEmpty()) {
                var empty = new Label("（未登録）");
                empty.setStyle("-fx-text-fill:#aaa;");
                listBox.getChildren().add(empty);
            } else {
            	// Cell#refreshView 内
            	for (Course c : courses) {
            	    var l = new Label(safe(c.name));
            	    l.setUserData(c);
            	    l.setWrapText(true);
            	    // ★強調表示
            	    l.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #333;");
            	    listBox.getChildren().add(l);
            	}
            }
        }

        // -------- 操作 --------

        private void onRightDouble() {
            List<Course> now = getCourses();
            if (now.isEmpty()) {
                chooseAndAssign();
            } else {
                Course target = now.size() == 1 ? now.get(0) : chooseOne("どの講義を表示しますか？", now);
                if (target == null) return;
                showDetailsAndMemo(target);
            }
        }

        private void onLeftClick() {
            List<Course> now = getCourses();
            var menu = new ContextMenu();

            var miClear = new MenuItem("講義を外す");
            miClear.setOnAction(ev -> clearSlotConfirm());

            var miSearch = new MenuItem("ファイル検索");
            miSearch.setOnAction(ev -> openFileSearch(now));

            var miExplorer = new MenuItem("エクスプローラーでワークスペースを開く");
            miExplorer.setOnAction(ev -> openExplorerFor(now));

            menu.getItems().addAll(miClear, new SeparatorMenuItem(),
                    miSearch, miExplorer);
            menu.show(root, javafx.geometry.Side.RIGHT, 0, 0);
        }

        // ---- 割り当て ----
        private void chooseAndAssign() {
            if (current == null) return;

            List<Course> choices = candidateCoursesForSlot(day, period, current.termMain);
            final String ADD_NEW = "（講義を追加…）";
            List<String> names = new ArrayList<>(choices.stream().map(c -> c.name).filter(Objects::nonNull).toList());
            names.add(ADD_NEW);

            var dlg = new ChoiceDialog<>(names.get(0), names);
            dlg.setTitle("講義の選択");
            dlg.setHeaderText("このコマに登録する講義を選択してください");
            dlg.setContentText("授業科目名：");
            var chosen = dlg.showAndWait().orElse(null);
            if (chosen == null) return;

            Course selected = null;
            if (ADD_NEW.equals(chosen)) {
            	selected = addCourseInlineAndMaybeAssign(this.day, this.period);
            } else {
                for (Course c : choices) if (Objects.equals(c.name, chosen)) { selected = c; break; }
            }
            if (selected == null) return;
            assignToStore(selected);
            TimetableStore.upsert(current);
            renderAll();
            app.univtool.core.TimetableSyncBus.fireTimetableChanged(current.id);
        }

        private void assignToStore(Course c) {
            String k = key(day, period);
            if (current.slots == null) current.slots = new LinkedHashMap<>();
            current.slots.put(k, c.id);
        }
        private void showDetailsAndMemo(Course c) {
            var dlg = new app.univtool.ui.panel1.CourseDetailsDialog(
                    c,
                    workspaceDir,
                    archiveDir,
                    () -> renderAll()
            );
            dlg.initOwner(getScene() == null ? null : getScene().getWindow());
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.showAndWait();
            renderAll();
            app.univtool.core.TimetableSyncBus.fireTimetableChanged(current.id);
        }


        // ---- 左クリックメニュー ----

        private void clearSlotConfirm() {
            if (current == null) return;
            var now = getCourses();
            if (now.isEmpty()) return;
            if (!Dialogs.confirm("確認", "このコマの登録をクリアします。よろしいですか？")) return;

            String k = key(day, period);
            if (current.slots != null) current.slots.remove(k);
            if (current.memos != null) current.memos.keySet().removeIf(s -> s.startsWith(k + "#"));
            TimetableStore.upsert(current);
            renderAll();
            app.univtool.core.TimetableSyncBus.fireTimetableChanged(current.id);

        }

        private void openFileSearch(List<Course> now) {
            Course c = selectWhenMultiple("どの講義でファイル検索しますか？", now);
            if (c == null) return;
            var pane = new PanelFileSearch(c);
            var st = new Stage();
            st.setTitle("ファイル検索 - " + nv(c.name));
            st.initOwner(getScene() == null ? null : getScene().getWindow());
            st.initModality(Modality.WINDOW_MODAL);
            st.setScene(new Scene(pane, 900, 600));
            st.show();
        }

        private void openExplorerFor(List<Course> now) {
            Course c = selectWhenMultiple("どの講義のフォルダを開きますか？", now);
            if (c == null) return;
            Path folder = workspaceDir.resolve(FileSaveOps.resolveCourseFolderName(c));
            try {
                Files.createDirectories(folder);
                String os = System.getProperty("os.name","").toLowerCase(Locale.ROOT);
                if (os.contains("win")) new ProcessBuilder("explorer.exe", folder.toAbsolutePath().toString()).start();
                else if (os.contains("mac")) new ProcessBuilder("open", folder.toAbsolutePath().toString()).start();
                else new ProcessBuilder("xdg-open", folder.toAbsolutePath().toString()).start();
            } catch (Exception ex) {
                Dialogs.error("エラー", "フォルダを開けませんでした: " + ex.getMessage());
            }
        }
        private Course selectWhenMultiple(String title, List<Course> list) {
            if (list == null || list.isEmpty()) return null;
            if (list.size() == 1) return list.get(0);
            return chooseOne(title, list);
        }
    }

    // ------------------ 候補計算 ------------------
    private List<Course> candidateCoursesForSlot(String day, int period, String termMain) {
        var all = repo.findAll();
        Set<String> okTerms = allowedTerms(termMain);
        Set<Integer> already = assignedCourseIds();

        return all.stream()
            .filter(c -> !isArchived(c))
            .filter(c -> c.id != null && !already.contains(c.id))
            .filter(c -> c.term == null || okTerms.contains(c.term))
            .filter(c -> isCompatible(day, period, c))   // ★ここ
            .sorted(Comparator.comparing(c -> c.name == null ? "" : c.name))
            .collect(Collectors.toList());
    }



    private static Set<String> allowedTerms(String main) {
        if ("前期".equals(main)) return Set.of("前期","春ターム","夏ターム");
        if ("後期".equals(main)) return Set.of("後期","秋ターム","冬ターム");
        return Set.of();
    }

    private boolean isArchived(Course c) {
        if (c == null) return false;
        Path p = workspaceDir.resolve(FileSaveOps.resolveCourseFolderName(c));
        return !Files.exists(p);
    }

    // 「講義を追加」選択時の即割当
    private Course addCourseInlineAndMaybeAssign(String day, int period) {
    	var preset = new Course();
    	if (current != null) {
    	    preset.year = current.year;
    	    preset.term = current.termMain;
    	}
    	preset.day = day;
    	preset.period = String.valueOf(period);
    	var dlg = new AddCourseDialog(preset, true);
    	var res = dlg.showAndWait();
    	if (res.isEmpty()) return null;
    	Course cc = res.get();
        int id = repo.insert(cc);
        cc.id = id;
        
        String folder = FileSaveOps.buildFolderNameFromId(cc);
        try {
            Files.createDirectories(workspaceDir.resolve(folder));
        } catch (Exception ex) {
            Dialogs.error("エラー","講義フォルダの作成に失敗: " + ex.getMessage());
            return null;
        }
        cc.folder = folder;
        repo.updateFolder(cc.id, folder);
        return cc;
    }



    // ------------------ ヘルパ ------------------
    private static String key(String day, int period) { return day + "-" + period; }
    private static int safeInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String nv(String s) { return s == null ? "" : s; }


    private static Course chooseOne(String title, List<Course> list) {
        var names = list.stream().map(c -> c.name == null ? "(無名)" : c.name).toList();
        var dlg = new ChoiceDialog<>(names.get(0), names);
        dlg.setTitle(title);
        dlg.setHeaderText(title);
        dlg.setContentText("授業科目名：");
        var chosen = dlg.showAndWait().orElse(null);
        if (chosen == null) return null;
        for (Course c : list) if (Objects.equals(c.name, chosen)) return c;
        return null;
    }
    
    private boolean autoUnassignArchivedCourses() {
        if (current.slots == null) return false;
        boolean changed = false;
        var it = current.slots.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Course c = findById(e.getValue());
            if (c == null || isArchived(c)) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    private boolean checkAndResolveScheduleMismatches() {
        boolean changed = false;
        if (current.slots == null) return false;

        var entries = new ArrayList<>(current.slots.entrySet());
        for (var e : entries) {
            String curKey = e.getKey();
            Integer cid = e.getValue();
            Course c = findById(cid);
            if (c == null) continue;

            String[] dp = curKey.split("-");
            if (dp.length != 2) continue;
            String curDay = dp[0];
            int curPeriod = safeInt(dp[1]);

            if (!hasTargetInfo(c) || isCompatible(curDay, curPeriod, c)) continue;

            String toDay = normalizeTargetDayForMove(c.day);
            int toPeriod = normalizeTargetPeriodForMove(c.period, curPeriod);

            if (showMismatchDialogLoop(c, curDay, curPeriod, toDay, toPeriod)) {
                changed = true;
            }
        }
        return changed;
    }

    private boolean showMismatchDialogLoop(Course c, String curDay, int curPeriod, String toDay, int toPeriod) {

        boolean targetValid = toDay != null && !toDay.isBlank() && toPeriod > 0;

        while (true) {
            String msg =
                "講義「" + nv(c.name) + "」の登録位置が、講義情報の曜日・時限と一致していません。\n\n" +
                "現在の位置： " + labelOf(curDay, curPeriod) + "\n" +
                "講義情報：   " + (targetValid ? labelOf(toDay, toPeriod) : "（未設定/不正）") + "\n\n" +
                "どうしますか？";

            var move = new ButtonType("講義を移動させる");
            var remove = new ButtonType("講義を時間割から外す");
            var alert = new Alert(Alert.AlertType.WARNING, msg, move, remove);
            alert.setTitle("時間割と講義情報の不一致");

            var res = alert.showAndWait().orElse(remove);

            if (res == remove) {
                removeFromLocation(curDay, curPeriod, c.id);
                return true;
            }

            if (!targetValid) {
                Dialogs.error("エラー", "講義情報に曜日/時限が設定されていないため、移動できません。");
                continue;
            }

            if (tryMoveCourse(c.id, curDay, curPeriod, toDay, toPeriod)) {
                return true;
            } else {
                Integer occupiedId = getCourseIdAt(toDay, toPeriod);
                String occupiedName = (occupiedId == null) ? "(不明)" :
                        (findById(occupiedId) == null || nv(findById(occupiedId).name).isBlank() ? "(無名)" : nv(findById(occupiedId).name));

                String msg2 = "移動先 " + labelOf(toDay, toPeriod) + " は " + occupiedName + " で埋まっています";
                var removePrev = new ButtonType("前の講義を外す");
                var cancel = new ButtonType("キャンセル");
                var alert2 = new Alert(Alert.AlertType.WARNING, msg2, removePrev, cancel);
                alert2.setTitle("移動先が埋まっています");

                var choice = alert2.showAndWait().orElse(cancel);

                if (choice == removePrev) {
                    if (replaceAndMove(c.id, curDay, curPeriod, toDay, toPeriod)) {
                        return true;
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
            }
        }
    }

    private boolean tryMoveCourse(int courseId, String fromDay, int fromPeriod, String toDay, int toPeriod) {
        String targetKey = key(toDay, toPeriod);
        Integer exists = (current.slots == null) ? null : current.slots.get(targetKey);
        if (exists != null) return false;

        if (current.slots == null) current.slots = new LinkedHashMap<>();
        current.slots.put(targetKey, courseId);
        removeFromLocation(fromDay, fromPeriod, courseId);
        return true;
    }

    private void removeFromLocation(String day, int period, int courseId) {
        String k = key(day, period);
        if (current.slots != null) {
            Integer cur = current.slots.get(k);
            if (Objects.equals(cur, courseId)) current.slots.remove(k);
        }
    }

    private static String labelOf(String day, int period) {
        String jp = switch (day) {
            case "MON" -> "月";
            case "TUE" -> "火";
            case "WED" -> "水";
            case "THU" -> "木";
            case "FRI" -> "金";
            case "SAT" -> "土";
            case "SUN" -> "日（＋曜日指定なし）";
            default -> day;
        };
        return jp + " / " + period + "限";
    }
    
    private Set<Integer> assignedCourseIds() {
        var set = new java.util.HashSet<Integer>();
        if (current != null && current.slots != null) set.addAll(current.slots.values());
        return set;
    }
    
    private static final String DAY_OTHER = "OTHER";
    private static final String PERIOD_OTHER = "OTHER";

    private static boolean isCompatible(String cellDay, int cellPeriod, Course c) {
        if (c == null) return false;
        return matchesDay(cellDay, c.day) && matchesPeriod(cellPeriod, c.period);
    }

    private static boolean matchesDay(String cellDay, String courseDay) {
        if (courseDay == null || courseDay.isBlank()) return false;
        if ("SUN".equals(cellDay)) {
            return "SUN".equals(courseDay) || DAY_OTHER.equals(courseDay);
        }
        return Objects.equals(cellDay, courseDay);
    }

    private static boolean matchesPeriod(int cellPeriod, String coursePeriod) {
        if (coursePeriod == null || coursePeriod.isBlank()) return false;
        if (PERIOD_OTHER.equals(coursePeriod)) return true;
        return Objects.equals(String.valueOf(cellPeriod), coursePeriod);
    }

    private static String normalizeTargetDayForMove(String courseDay) {
        return DAY_OTHER.equals(courseDay) ? "SUN" : courseDay;
    }
    private static int normalizeTargetPeriodForMove(String coursePeriod, int currentPeriod) {
        return PERIOD_OTHER.equals(coursePeriod) ? currentPeriod : safeInt(coursePeriod);
    }

    private static boolean hasTargetInfo(Course c) {
        if (c == null) return false;
        boolean hasDay = c.day != null && !c.day.isBlank();
        boolean hasPeriod = c.period != null && !c.period.isBlank();
        return hasDay && hasPeriod;
    }
    
    private Integer getCourseIdAt(String day, int period) {
        if (current == null || current.slots == null) return null;
        return current.slots.get(key(day, period));
    }
    
    private boolean replaceAndMove(int movingCourseId, String fromDay, int fromPeriod, String toDay, int toPeriod) {
        if (current == null) return false;
        if (current.slots == null) current.slots = new LinkedHashMap<>();

        String toKey = key(toDay, toPeriod);
        Integer prev = current.slots.get(toKey);

        if (prev != null) {
            current.slots.remove(toKey);
            if (current.memos != null) current.memos.remove(toKey + "#" + prev);
        }

        current.slots.put(toKey, movingCourseId);

        removeFromLocation(fromDay, fromPeriod, movingCourseId);

        return true;
    }
    
    private void rebuildFilterUI() {
        filterBar.getChildren().clear();

        List<Course> cs = new ArrayList<>();
        if (current != null && current.slots != null) {
            for (Integer id : current.slots.values()) {
                Course c = findById(id);
                if (c != null) cs.add(c);
            }
        }

        var caption = new Label("強調（AND条件）：");
        caption.setStyle("-fx-text-fill:#444;");
        filterBar.getChildren().add(caption);

        java.util.function.Consumer<VBox> addGroup = box -> {
            box.setSpacing(4);
            box.setStyle("-fx-padding:4 8; -fx-background-color:#f9f9f9; -fx-border-color:#e0e0e0; -fx-border-radius:4; -fx-background-radius:4;");
            filterBar.getChildren().add(box);
        };

        var grades = cs.stream().map(c -> c.grade).filter(Objects::nonNull).distinct().sorted().toList();
        if (!grades.isEmpty()) {
            var box = new VBox();
            box.getChildren().add(new Label("開講年次"));
            for (Integer g : grades) {
                var cb = new CheckBox(g + "年次");
                cb.setSelected(selGrades.contains(g));
                cb.setOnAction(e -> { toggle(selGrades, g, cb.isSelected()); applyHighlighting(); });
                box.getChildren().add(cb);
            }
            addGroup.accept(box);
        }

        var credits = cs.stream().map(c -> c.credit).filter(Objects::nonNull).distinct()
                .sorted().toList();
        if (!credits.isEmpty()) {
            var box = new VBox();
            box.getChildren().add(new Label("単位数"));
            for (Double cr : credits) {
                var cb = new CheckBox(labelCredit(cr));
                cb.setSelected(selCredits.contains(cr));
                cb.setOnAction(e -> { toggle(selCredits, cr, cb.isSelected()); applyHighlighting(); });
                box.getChildren().add(cb);
            }
            addGroup.accept(box);
        }

        // --- 出席確認 ---
        var atts = cs.stream().map(c -> Boolean.valueOf(c.attendanceRequired))
                .distinct().sorted().toList();
        if (!atts.isEmpty()) {
            var box = new VBox();
            box.getChildren().add(new Label("出席確認"));
            for (Boolean b : atts) {
                var cb = new CheckBox(b ? "あり" : "なし");
                cb.setSelected(selAttendance.contains(b));
                cb.setOnAction(e -> { toggle(selAttendance, b, cb.isSelected()); applyHighlighting(); });
                box.getChildren().add(cb);
            }
            addGroup.accept(box);
        }

     // --- 試験/レポート ---
        {
            boolean anyNone = false;
            java.util.EnumSet<ExamFlag> union = java.util.EnumSet.noneOf(ExamFlag.class);
            for (Course c : cs) {
                var fs = parseExamFlags(c.examsCsv);
                if (fs.isEmpty()) anyNone = true;
                union.addAll(fs);
            }
            if (anyNone || !union.isEmpty()) {
                var box = new VBox();

                var header = new HBox(6);
                var lbl = new Label("試験/レポート");
                var cbMode = new ComboBox<String>();
                cbMode.getItems().addAll("もしくは(OR)","かつ(AND)");
                cbMode.getSelectionModel().select(examRequireAll ? 1 : 0);
                cbMode.setPrefWidth(110);
                cbMode.setMaxWidth(110);
                cbMode.setStyle("-fx-font-size: 11px;");
                cbMode.setOnAction(e -> { 
                    examRequireAll = (cbMode.getSelectionModel().getSelectedIndex() == 1);
                    applyHighlighting();
                });
                header.getChildren().addAll(lbl, new Label("一致:"), cbMode);
                box.getChildren().add(header);

                var flow = new FlowPane();
                flow.setHgap(8);
                flow.setVgap(4);

                if (anyNone) {
                    var cbNone = new CheckBox("なし");
                    cbNone.setSelected(selExamNone);
                    cbNone.setOnAction(e -> {
                        boolean on = cbNone.isSelected();
                        selExamNone = on;
                        if (on) selExamFlags.clear();
                        rebuildFilterUI();
                        applyHighlighting();
                    });
                    flow.getChildren().add(cbNone);
                }

                java.util.List<ExamFlag> order = java.util.List.of(
                        ExamFlag.MID_EXAM, ExamFlag.FINAL_EXAM,
                        ExamFlag.MID_REPORT, ExamFlag.FINAL_REPORT
                );
                for (ExamFlag f : order) {
                    if (!union.contains(f)) continue;
                    var cb = new CheckBox(shortLabel(f));
                    cb.setSelected(selExamFlags.contains(f));
                    cb.setOnAction(e -> {
                        if (cb.isSelected()) {
                            selExamFlags.add(f);
                            selExamNone = false;
                        } else {
                            selExamFlags.remove(f);
                        }
                        rebuildFilterUI();
                        applyHighlighting();
                    });
                    flow.getChildren().add(cb);
                }

                box.getChildren().add(flow);
                box.setSpacing(4);
                box.setStyle("-fx-padding:4 8; -fx-background-color:#f9f9f9; -fx-border-color:#e0e0e0; -fx-border-radius:4; -fx-background-radius:4;");
                filterBar.getChildren().add(box);
            }
        }
    }
    
    private static String shortLabel(ExamFlag f) {
        return switch (f) {
            case MID_EXAM     -> "中間試験";
            case FINAL_EXAM   -> "期末試験";
            case MID_REPORT   -> "中間レポ";
            case FINAL_REPORT -> "期末レポ";
        };
    }

    
    private static java.util.Set<ExamFlag> parseExamFlags(String csv) {
        var set = java.util.EnumSet.noneOf(ExamFlag.class);
        if (csv == null) return set;

        String s = csv.replaceAll("\\s+", "");

        if (s.contains("中間試験") || s.contains("中間テスト")) set.add(ExamFlag.MID_EXAM);
        if (s.contains("期末試験") || s.contains("期末テスト")) set.add(ExamFlag.FINAL_EXAM);
        if (s.contains("中間レポート") || s.contains("中間レポ")) set.add(ExamFlag.MID_REPORT);
        if (s.contains("期末レポート") || s.contains("期末レポ")) set.add(ExamFlag.FINAL_REPORT);

        return set;
    }

    private static <T> void toggle(java.util.Set<T> set, T v, boolean on) {
        if (on) set.add(v); else set.remove(v);
    }

    private static String labelCredit(Double cr) {
        if (cr == null) return "-";
        double f = Math.floor(cr);
        return (f == cr) ? String.format("%.0f単位", cr) : (cr + "単位");
    }
    
    private void applyHighlighting() {
    	boolean anySelected =
    	        !selGrades.isEmpty()
    	     || !selCredits.isEmpty()
    	     || !selAttendance.isEmpty()
    	     || selExamNone
    	     || !selExamFlags.isEmpty();
    	
        for (var cell : cellMap.values()) {
            var cs = cell.getCourses();
            if (cs.isEmpty()) {
                cell.root.setStyle("-fx-background-color: white; -fx-border-color: #ddd;");
                continue;
            }
            Course c = cs.get(0);

            boolean hit = anySelected && matchesAllFilters(c);
            if (hit) {
                cell.root.setStyle("-fx-background-color: #fff9c4; -fx-border-color: #bbb;"); // 淡黄色で強調
            } else {
                cell.root.setStyle("-fx-background-color: white; -fx-border-color: #ddd;");
            }
        }
    }

    private boolean matchesAllFilters(Course c) {
        if (c == null) return false;

        if (!selGrades.isEmpty()) {
            if (c.grade == null || !selGrades.contains(c.grade)) return false;
        }
        if (!selCredits.isEmpty()) {
            if (c.credit == null || !selCredits.contains(c.credit)) return false;
        }
        if (!selAttendance.isEmpty()) {
            boolean a = c.attendanceRequired;
            if (!selAttendance.contains(Boolean.valueOf(a))) return false;
        }
        if (selExamNone || !selExamFlags.isEmpty()) {
            var flags = parseExamFlags(c.examsCsv);

            if (selExamNone) {
                if (!flags.isEmpty()) return false;
            } else {
                if (examRequireAll) {
                    if (!flags.containsAll(selExamFlags)) return false;
                } else {
                    if (selExamFlags.stream().noneMatch(flags::contains)) return false;
                }
            }
        }
        return true;
    }
    
    private void clearHighlightSelection() {
        selGrades.clear();
        selCredits.clear();
        selAttendance.clear();
        selExamFlags.clear();
        selExamNone = false;
        examRequireAll = false;
        rebuildFilterUI();
    }
    
    private Path highlightStateFile() {
        if (workspaceDir == null || current == null || current.id == null) return null;
        return workspaceDir.resolve(".timetable_highlight_" + current.id + ".props");
    }
    
    private void saveHighlightStateToDisk() throws Exception {
        Path f = highlightStateFile();
        if (f == null) return;

        var p = new java.util.Properties();
        p.setProperty("grades", joinInt(selGrades));
        p.setProperty("credits", joinDouble(selCredits));
        p.setProperty("attendance", joinBool(selAttendance));
        p.setProperty("examRequireAll", String.valueOf(examRequireAll));
        p.setProperty("examNone", String.valueOf(selExamNone));
        p.setProperty("examFlags", selExamFlags.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(",")));

        try (var os = java.nio.file.Files.newOutputStream(f)) {
            p.store(os, "timetable highlight state");
        }
    }
    
    private void loadHighlightStateFromDisk() {
        try {
            Path f = highlightStateFile();
            if (f == null || !java.nio.file.Files.exists(f)) return;

            var p = new java.util.Properties();
            try (var is = java.nio.file.Files.newInputStream(f)) {
                p.load(is);
            }
            selGrades.clear();
            selCredits.clear();
            selAttendance.clear();
            selExamFlags.clear();
            selExamNone = false;
            examRequireAll = false;

            splitInt(p.getProperty("grades", "")).forEach(selGrades::add);
            splitDouble(p.getProperty("credits", "")).forEach(selCredits::add);
            splitBool(p.getProperty("attendance", "")).forEach(selAttendance::add);
            examRequireAll = Boolean.parseBoolean(p.getProperty("examRequireAll", "false"));
            selExamNone    = Boolean.parseBoolean(p.getProperty("examNone", "false"));

            var flagsCsv = p.getProperty("examFlags", "");
            if (!flagsCsv.isBlank()) {
                for (String s : flagsCsv.split(",")) {
                    try { selExamFlags.add(ExamFlag.valueOf(s)); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {
        }
    }
    
    private void deleteHighlightStateOnDisk() throws Exception {
        Path f = highlightStateFile();
        if (f != null && java.nio.file.Files.exists(f)) {
            java.nio.file.Files.delete(f);
        }
    }
    
    private static String joinInt(Set<Integer> s) {
        return s.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }
    private static String joinDouble(Set<Double> s) {
        return s.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }
    private static String joinBool(Set<Boolean> s) {
        return s.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static java.util.List<Integer> splitInt(String csv) {
        var out = new java.util.ArrayList<Integer>();
        for (String t : csv.split(",")) if (!t.isBlank()) {
            try { out.add(Integer.parseInt(t.trim())); } catch (Exception ignored) {}
        }
        return out;
    }
    private static java.util.List<Double> splitDouble(String csv) {
        var out = new java.util.ArrayList<Double>();
        for (String t : csv.split(",")) if (!t.isBlank()) {
            try { out.add(Double.valueOf(t.trim())); } catch (Exception ignored) {}
        }
        return out;
    }
    private static java.util.List<Boolean> splitBool(String csv) {
        var out = new java.util.ArrayList<Boolean>();
        for (String t : csv.split(",")) if (!t.isBlank()) {
            out.add(Boolean.parseBoolean(t.trim()));
        }
        return out;
    }
    
    private int archiveAllCoursesOfCurrent() {
        if (current == null || current.slots == null) return 0;
        int failed = 0;

        var ids = new java.util.HashSet<Integer>(current.slots.values());
        try { Files.createDirectories(archiveDir); } catch (Exception ignore) {}

        for (Integer id : ids) {
            Course c = findById(id);
            if (c == null) continue;
            try {
                Path src = workspaceDir.resolve(FileSaveOps.resolveCourseFolderName(c));
                if (!Files.exists(src)) continue;

                Path dst = archiveDir.resolve(src.getFileName());
                Path finalDst = uniqueDst(dst);
                Files.move(src, finalDst /*, StandardCopyOption.REPLACE_EXISTING*/);
            } catch (Exception ex) {
                failed++;
            }
        }
        return failed;
    }

    private static Path uniqueDst(Path base) throws Exception {
        if (!Files.exists(base)) return base;
        String name = base.getFileName().toString();
        String stem = name;
        String ext  = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { stem = name.substring(0, dot); ext = name.substring(dot); }
        Path dir = base.getParent();
        for (int i = 1; i <= 99; i++) {
            Path cand = dir.resolve(stem + "_" + i + ext);
            if (!Files.exists(cand)) return cand;
        }
        return dir.resolve(stem + "_" + System.currentTimeMillis() + ext);
    }
    private double calcRegisteredCredits() {
        if (current == null || current.slots == null || current.slots.isEmpty()) return 0.0;
        var ids = new java.util.HashSet<Integer>(current.slots.values());
        double sum = 0.0;
        for (Integer id : ids) {
            Course c = findById(id);
            if (c != null && c.credit != null) sum += c.credit.doubleValue();
        }
        return sum;
    }

    private static String fmtUnits(double v) {
        double f = Math.floor(v);
        return (f == v) ? String.format("%.0f", v) : String.valueOf(v);
    }

    private void updateCreditInfo() {
        double cur = calcRegisteredCredits();
        if (current == null || current.creditLimit == null) {
            lblCreditInfo.setText("現在: " + fmtUnits(cur) + "単位");
            lblCreditInfo.setStyle("-fx-font-weight: bold; -fx-text-fill: #333;");
            return;
        }
        double limit = current.creditLimit.doubleValue();
        double remain = limit - cur;

        if (remain >= 0) {
            lblCreditInfo.setText("現在: " + fmtUnits(cur) + "単位 / 上限: " + fmtUnits(limit) + "単位（残り " + fmtUnits(remain) + "単位）");
            lblCreditInfo.setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32;");
        } else {
            lblCreditInfo.setText("現在: " + fmtUnits(cur) + "単位 / 上限: " + fmtUnits(limit) + "単位（超過 " + fmtUnits(-remain) + "単位）");
            lblCreditInfo.setStyle("-fx-font-weight: bold; -fx-text-fill: #c62828;");
        }
    }
    
    private static final class Range { java.time.LocalDate start, end; Range(java.time.LocalDate s, java.time.LocalDate e){start=s;end=e;} }
    private Range guessRange(int year, String term){
        if ("前期".equals(term)) {
            return new Range(java.time.LocalDate.of(year, 4, 1), java.time.LocalDate.of(year, 9, 30));
        } else if ("後期".equals(term)) {
            return new Range(java.time.LocalDate.of(year, 10, 1), java.time.LocalDate.of(year + 1, 3, 31));
        }
        var s = java.time.LocalDate.of(year,1,1);
        var e = java.time.LocalDate.of(year,12,31);
        return new Range(s,e);
    }

    
}
