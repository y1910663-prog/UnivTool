package app.univtool.ui.panel5;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import app.univtool.core.AttendanceStore;
import app.univtool.core.AttendanceStore.Attendance;
import app.univtool.core.ReminderStore;
import app.univtool.core.ReminderStore.Priority;
import app.univtool.core.ScheduleStore;
import app.univtool.core.SettingsStore;
import app.univtool.core.TimetableStore;
import app.univtool.core.TimetableSyncBus;
import app.univtool.core.TodoReminderLinker;
import app.univtool.core.TodoStore;
import app.univtool.files.SavedFileIndex;
import app.univtool.model.Course;
import app.univtool.repo.CourseRepository;
import app.univtool.ui.panel1.CourseDetailsDialog;
import app.univtool.ui.panel2.FileSaveOps;
import app.univtool.ui.panel3.PanelFileSearch;
import app.univtool.util.Dialogs;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;


/** パネル５スケジュール＋ToDo */
public class PanelScheduleTodo extends BorderPane {

    private final CourseRepository courseRepo = new CourseRepository();

    private final SplitPane split = new SplitPane();

    private final BorderPane schedulePane = new BorderPane();
    private final Button btnSetRange   = new Button("開講期間を設定");
    private final Button btnToday      = new Button("今日へ戻る");
    private final Button btnJumpDate   = new Button("日付を選択");
    private final Button btnResetRange = new Button("開講期間を再設定");
    private final Button btnAddRem     = new Button("リマインダーを追加");

    private final ScrollPane scheduleScroll = new ScrollPane();
    private final VBox scheduleRoot = new VBox();
    private final GridPane headerGrid = new GridPane();
    private final VBox dayList = new VBox();

 // 右：ToDo
    private final VBox todoPane  = new VBox(8);
    private final ScrollPane todoScroll = new ScrollPane();
    private final Button btnAddTodo = new Button("新規ToDo");
    private final Button btnRemoveDone = new Button("完了一括削除");

    // スケジュール状態
    private TimetableStore.Timetable timetable;        // 現在の時間割
    private final Map<Integer, Course> courseById = new HashMap<>();
    private final Map<LocalDate, Boolean> highlightExamBg = new HashMap<>(); // 試験日背景
    private final Map<LocalDate, GridPane> dayRowMap = new HashMap<>();
    private final Map<LocalDate, VBox> dateCellMap = new HashMap<>();
    private LocalDate minLoadedDate;
    private LocalDate maxLoadedDate;
    private LocalDate jumpedDate = null;
    
    private final TimetableSyncBus.Listener ttListener = new TimetableSyncBus.Listener() {
        @Override public void onTimetableChanged(String id) { Platform.runLater(() -> { reloadTimetable(); requestRefresh(); }); }
        @Override public void onRangeChanged(String id)     { Platform.runLater(() -> { reloadTimetable(); requestRefresh(); }); }
        @Override public void onTimetableDeleted(String id) { Platform.runLater(() -> { reloadTimetable(); requestRefresh(); }); }
        @Override public void onTimetableOpened(String id)  { Platform.runLater(() -> { reloadTimetable(); requestRefresh(); }); }
    };

    private Path workspaceDir;
    private Path archiveDir;

    private static final DateTimeFormatter DF_MD = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPANESE);
    private static final DateTimeFormatter DF_YMD = DateTimeFormatter.ISO_LOCAL_DATE;

 // === Layout constants ===
    private static final double DATE_COL_WIDTH = 120;
    private static final double ROW_HEIGHT     = 108;
    private static final String OUT_OF_RANGE_BG = "#d0d0d0";
    
    
    
 // 強調表示
    private String    highlightTodoId = null;
    private LocalDate highlightRemDate = null;
    private String    highlightRemId = null;
    
    private final Map<String, Boolean> todoExpandStates = new HashMap<>();
    
 // 分類ごとの開閉状態
    private final Map<String, Boolean> todoGroupExpandStates = new HashMap<>();
    private static final String TODO_BG_WARN    = "#fff3cd"; // オレンジ（期限一週間前）
    private static final String TODO_BG_OVERDUE = "#ffd6d6"; // 薄赤（期限切れ）
    
    private boolean isAutoAdjusting = false;
    
    private boolean refreshPending = false;
    private boolean suppressAutoScrollOnce = false;
    
 // スクロール位置のアンカー
    private LocalDate preservedAnchorDate = null;
    private double   preservedAnchorRatio = 0.0;
    private static final double TODO_GROUP_INDENT = 18;




    public PanelScheduleTodo() {
        setPadding(new Insets(10));

        restoreWorkspace();
        reloadTimetable();
        refreshCourseCache();

        // 右：ToDo
        var todoBar = new HBox(8, btnAddTodo, btnRemoveDone);
        todoBar.setAlignment(Pos.CENTER_LEFT);
        todoPane.getChildren().addAll(todoBar, new Separator());
        btnAddTodo.setOnAction(e -> openAddTodoDialog(null, null));
        btnRemoveDone.setOnAction(e -> {
            if (!Dialogs.confirm("確認", "完了済みToDoを一括削除します。よろしいですか？")) return;
            var dones = TodoStore.get().listAll().stream().filter(x -> x.done).toList();
            for (var t : dones) TodoReminderLinker.removeWithReminder(t);
            renderTodo();
        });
        
        // 左：スケジュール
        var topBar = new HBox(10);
        topBar.getChildren().addAll(btnToday, btnJumpDate);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(4,0,6,0));
        schedulePane.setTop(topBar);

        var bottomBar = new HBox(10, btnResetRange, btnAddRem);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setPadding(new Insets(6,0,0,0));
        schedulePane.setBottom(bottomBar);

        buildHeaderGrid();

        dayList.setSpacing(0);
        dayList.setFillWidth(true);

        scheduleRoot.getChildren().setAll(headerGrid, dayList);
        scheduleScroll.setFitToWidth(true);
        scheduleScroll.setContent(scheduleRoot);
        schedulePane.setCenter(scheduleScroll);
        
        scheduleRoot.setFillWidth(true);

        scheduleScroll.vvalueProperty().addListener((o, ov, nv) -> {
            if (isAutoAdjusting) return;
            if (minLoadedDate == null || maxLoadedDate == null) return;

            double v = nv.doubleValue();
            if (v < 0.02) {
                isAutoAdjusting = true;
                final double viewportH = scheduleScroll.getViewportBounds().getHeight();
                final double contentH0 = scheduleScroll.getContent().getBoundsInLocal().getHeight();
                final double v0        = scheduleScroll.getVvalue();
                final int    addDays   = 30;

                prependDays(addDays);

                Platform.runLater(() -> {
                    double contentH1 = scheduleScroll.getContent().getBoundsInLocal().getHeight();
                    double deltaH    = Math.max(0, contentH1 - contentH0);
                    double y0        = v0 * Math.max(0, contentH0 - viewportH);
                    double y1        = y0 + deltaH;
                    double v1        = (contentH1 <= viewportH) ? 0 : (y1 / (contentH1 - viewportH));
                    scheduleScroll.setVvalue(Math.max(0, Math.min(1, v1)));
                    isAutoAdjusting = false;
                });

            } else if (v > 0.98) {
                isAutoAdjusting = true;
                appendDays(30);
                Platform.runLater(() -> isAutoAdjusting = false);
            }
        });

        btnSetRange.setOnAction(e ->
        Dialogs.info("操作", "開講期間の設定はパネル４（時間割）の「開講期間を編集」から行ってください。")
        );
        btnResetRange.setOnAction(btnSetRange.getOnAction());
        btnSetRange.setVisible(false);
        btnResetRange.setVisible(false);
        btnAddRem.setOnAction(e -> openCustomReminderDialog(null, null));
        btnToday.setOnAction(e -> {
            jumpedDate = null;
            ensureDateLoaded(LocalDate.now());
            scrollToDate(LocalDate.now());
            refreshDateCellStyles();
        });
        btnJumpDate.setOnAction(e -> openJumpDialog());
        
        todoScroll.setContent(todoPane);
        todoScroll.setFitToWidth(true);
        todoScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        todoScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        todoScroll.setPannable(true);

        // スプリット
        split.getItems().addAll(schedulePane, todoScroll);
        split.setDividerPositions(0.55);
        setCenter(split);

        renderAll();
        
        TimetableSyncBus.add(ttListener);
    }

    // ================== 初期化/ユーティリティ ==================

    private void restoreWorkspace() {
        var s = SettingsStore.load();
        if (s.workspaceDir != null && !s.workspaceDir.isBlank()) {
            workspaceDir = java.nio.file.Path.of(s.workspaceDir);
            try { Files.createDirectories(workspaceDir); } catch (Exception ignore) {}
        }
        if (s.archiveDir != null && !s.archiveDir.isBlank()) {
            archiveDir = java.nio.file.Path.of(s.archiveDir);
        } else if (workspaceDir != null) {
            archiveDir = (workspaceDir.getParent()!=null ? workspaceDir.getParent().resolve("archive") : workspaceDir.resolveSibling("archive"));
        }
        try { if (archiveDir!=null) Files.createDirectories(archiveDir); } catch (Exception ignore) {}
    }

    private void reloadTimetable() {
        TimetableStore.loadLastOpened().ifPresent(t -> timetable = t);
        refreshCourseCache();
        sweepMismatchedAttendanceForCurrentTimetable();
    }


    private void refreshCourseCache() {
        courseById.clear();
        for (Course c : courseRepo.findAll()) courseById.put(c.id, c);
    }

    private List<Course> coursesOfDay(DayOfWeek dow) {
        if (timetable == null || timetable.slots == null) return List.of();
        String d = switch (dow) {
            case MONDAY -> "MON"; case TUESDAY -> "TUE"; case WEDNESDAY -> "WED";
            case THURSDAY -> "THU"; case FRIDAY -> "FRI"; case SATURDAY -> "SAT";
            default -> "SUN";
        };
        List<Course> out = new ArrayList<>();
        for (var e : timetable.slots.entrySet()) {
            Integer cid = e.getValue();
            Course c = courseById.get(cid);
            if (c == null) continue;
            if (Objects.equals(c.day, d) || ("SUN".equals(d) && "OTHER".equals(c.day))) {
                out.add(c);
            }
        }
        out.sort(Comparator.comparing(x -> safe(x.period)));
        return out;
    }

    private static String safe(String s){return s==null?"":s;}

    // ==== 週ビュー「第n回」計算 ====
    private int nthOf(Course c, LocalDate date) {
        var rng = ScheduleStore.get().getRange(timetable==null?null:timetable.id);
        if (rng == null || rng.startIso == null || rng.endIso == null) return 0;

        LocalDate start = LocalDate.parse(rng.startIso);
        LocalDate end   = LocalDate.parse(rng.endIso);
        // 開講期間外の日付は換算ゼロ
        if (date.isBefore(start) || date.isAfter(end)) return 0;

        DayOfWeek targetDow = dowOfCourse(c);
        if (targetDow == null) return 0;

        int nth = 0;
        for (LocalDate d = start; !d.isAfter(date) && !d.isAfter(end); d = d.plusDays(1)) {
            if (matchesDow(c, d)) {
                var rec = AttendanceStore.get().find(c.id, d).orElse(null);
                if (rec != null && rec.cancelled) continue;
                nth++;
            }
        }
        return nth;
    }

    private boolean matchesDow(Course c, LocalDate d) {
        DayOfWeek dd = d.getDayOfWeek();
        if ("OTHER".equals(safe(c.day))) return dd==DayOfWeek.SUNDAY;
        DayOfWeek cw = dowOfCourse(c);
        return cw != null && cw==dd;
    }
    private static DayOfWeek dowOfCourse(Course c) {
        return switch (safe(c.day)) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN","OTHER" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    // ================== Render ==================

    private void renderAll() {
        renderTodo();
        renderSchedule();
    }

    private void renderTodo() {
        if (todoPane.getChildren().size() > 2) {
            todoPane.getChildren().remove(2, todoPane.getChildren().size());
        }
        ensureUndatedAssignmentTodos();

        List<TodoStore.TodoItem> all = TodoStore.get().listAll();
        var visible = all.stream()
                .filter(t -> t.courseId == null || !isArchivedCourseId(t.courseId))
                .toList();

        LocalDate today = LocalDate.now();
        LocalDate in7   = today.plusDays(6);

        var withDueNotDone = visible.stream()
                .filter(t -> !t.done && t.dueIso != null && !t.dueIso.isBlank())
                .toList();

        var overdue = withDueNotDone.stream()
                .filter(t -> {
                    LocalDate d = parseDue(t.dueIso);
                    return d != null && d.isBefore(today);
                })
                .sorted(Comparator.comparing(t -> parseDue(safe(t.dueIso))))
                .toList();

        var withinWeek = withDueNotDone.stream()
                .filter(t -> {
                    LocalDate d = parseDue(t.dueIso);
                    return d != null && (!d.isBefore(today) && !d.isAfter(in7));
                })
                .sorted(Comparator.comparing(t -> parseDue(safe(t.dueIso))))
                .toList();

        var later = withDueNotDone.stream()
                .filter(t -> {
                    LocalDate d = parseDue(t.dueIso);
                    return d != null && d.isAfter(in7);
                })
                .sorted(Comparator.comparing(t -> parseDue(safe(t.dueIso))))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        var noDueNotDone = visible.stream()
                .filter(t -> !t.done && (t.dueIso == null || t.dueIso.isBlank()))
                .toList();

        var undatedAssignments = noDueNotDone.stream()
                .filter(this::isAssignmentTodo)
                .sorted(Comparator.comparing((TodoStore.TodoItem t) -> {
                    Course c = (t.courseId==null? null : courseById.get(t.courseId));
                    String cn = (c==null? "" : safe(c.name));
                    return cn;
                }).thenComparing(t -> t.nth==null? 0 : t.nth))
                .toList();

        var otherNoDue = noDueNotDone.stream()
                .filter(t -> !isAssignmentTodo(t))
                .toList();

        later.addAll(otherNoDue);

        var done = visible.stream()
                .filter(t -> t.done)
                .sorted(Comparator.comparing(t -> parseDue(safe(t.dueIso)), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();

        if (withinWeek.isEmpty() && later.isEmpty() && overdue.isEmpty() && done.isEmpty() && undatedAssignments.isEmpty()) {
            todoPane.getChildren().add(new Label("（ToDoなし）"));
            return;
        }

     // ========== グループ ==========
     // 1) 期限一週間前
     addTodoGroup("期限一週間前", withinWeek, true, tp -> paintTodoHeaderBackground(tp, TODO_BG_WARN));

     // 2) それ以降
     addTodoGroup("それ以降",       later,      true, null);

     // 3) 期限切れ
     addTodoGroup("期限切れ",       overdue,    true, tp -> paintTodoHeaderBackground(tp, TODO_BG_OVERDUE));

     // 4) 完了済み
     addTodoGroup("完了済み",       done,       true, null, /*completedGroup*/ true);

        todoPane.getChildren().add(new Separator());

        // 5) 期限未設定課題一覧
        addTodoGroup("期限未設定課題一覧", undatedAssignments, false, null);

    }

    private void addTodoGroup(String groupName,
                              java.util.List<TodoStore.TodoItem> items,
                              boolean defaultExpand,
                              java.util.function.Consumer<TitledPane> perItemStyler) {
        addTodoGroup(groupName, items, defaultExpand, perItemStyler, false);
    }

    private void addTodoGroup(String groupName,
            java.util.List<TodoStore.TodoItem> items,
            boolean defaultExpand,
            java.util.function.Consumer<TitledPane> perItemStyler,
            boolean completedGroup) {

    	var box = new VBox(6);
    	boolean containsHighlight = false;
    	for (var t : items) {
    		var tp = buildTodoNode(t, completedGroup || t.done);
    		if (perItemStyler != null) perItemStyler.accept(tp);
    		if (java.util.Objects.equals(t.id, highlightTodoId)) containsHighlight = true;
    		box.getChildren().add(tp);
    		}
    	if (box.getChildren().isEmpty()) {
    		var empty = new Label("（該当なし）");
    		empty.setStyle("-fx-text-fill:#999;");
    		box.getChildren().add(empty);
    		}

    	boolean expanded = todoGroupExpandStates.getOrDefault(groupName, defaultExpand);
    	if (containsHighlight) expanded = true;
    	// 見出し
    	String headerText = groupName + "　" + items.size() + "個";
    	Label chevron = new Label(expanded ? "▼" : "▶");
    	chevron.setStyle("-fx-font-weight:bold; -fx-text-fill:#444;");
    	Label title = new Label(headerText);
    	title.setStyle("-fx-font-weight:bold; -fx-text-fill:#444;");
    	
    	var header = new HBox(8, chevron, title);
		header.setAlignment(Pos.CENTER_LEFT);
		header.setPadding(new Insets(6, 8, 6, 8));
		header.setStyle(
		"-fx-background-color: linear-gradient(#f7f7f7, #efefef);"
		+ "-fx-border-color: #dddddd transparent #dddddd transparent;"
		+ "-fx-border-width: 1 0 1 0;"
		);
		header.setCursor(javafx.scene.Cursor.HAND);


		var indentContainer = new VBox(box);
		indentContainer.setPadding(new Insets(4, 0, 6, TODO_GROUP_INDENT));
		indentContainer.setStyle(
		"-fx-border-color: transparent transparent transparent #e0e0e0;"
		+ "-fx-border-width: 0 0 0 2;"
		+ "-fx-border-insets: 0 0 0 6;"
		);
		indentContainer.setFillWidth(true);
		indentContainer.setManaged(expanded);
		indentContainer.setVisible(expanded);
		
		// クリックで開閉
		header.setOnMouseClicked(ev -> {
		boolean next = !indentContainer.isVisible();
		indentContainer.setManaged(next);
		indentContainer.setVisible(next);
		chevron.setText(next ? "▼" : "▶");
		todoGroupExpandStates.put(groupName, next);
		});
		
		// まとめて追加
		var groupRoot = new VBox(2, header, indentContainer);
		groupRoot.setFillWidth(true);
		todoPane.getChildren().add(groupRoot);
		}


    private TitledPane buildTodoNode(TodoStore.TodoItem t, boolean completed) {
        var title = new HBox(8);
        var cb = new CheckBox(); cb.setSelected(completed);
        var lbl = new Label(todoTitleText(t));
        lbl.setStyle(completed ? "-fx-text-fill:#888;" : "-fx-font-weight:bold;");
        title.getChildren().addAll(cb, lbl);
        title.setAlignment(Pos.CENTER_LEFT);

        cb.setOnAction(e -> {
            boolean toDone = cb.isSelected();

            String msg = toDone ? "このToDoを完了にしますか？" : "このToDoを未完了に戻しますか？";
            if (!Dialogs.confirm("確認", msg)) {
                cb.setSelected(!toDone);
                return;
            }

            t.done = toDone;
            TodoReminderLinker.updateAndLink(t);
            if (t.filePaths != null && !t.filePaths.isEmpty()) {
                final String kindTo = toDone ? "課題_提出済み" : "課題_未提出";
                final String fromLabel = toDone ? "課題（未提出）" : "課題（提出済み）";
                final String toLabel   = toDone ? "課題（提出済み）" : "課題（未提出）";

                for (int i=0; i<t.filePaths.size(); i++) {
                    Path oldPath = Path.of(t.filePaths.get(i));
                    SavedFileIndex.get().updateClassification(oldPath, t.courseId, t.nth, kindTo);
                }
                for (int i=0; i<t.filePaths.size(); i++) {
                    Path oldPath = Path.of(t.filePaths.get(i));
                    Path newPath = renameFileByClassificationLabel(oldPath, fromLabel, toLabel);
                    if (!Objects.equals(oldPath, newPath)) {
                        tryUpdateSavedIndexPath(oldPath, newPath);
                        t.filePaths.set(i, newPath.toString());
                    }
                }
                TodoReminderLinker.updateAndLink(t);
            }

            requestRefresh();
        });


        VBox content = new VBox(6);
        content.setPadding(new Insets(4));

        if (t.detail != null && !t.detail.isBlank()) {
            Label detailLbl = new Label(t.detail);
            detailLbl.setWrapText(true);
            detailLbl.setStyle(completed ? "-fx-text-fill:#999;" : "-fx-text-fill:#333;");
            content.getChildren().add(detailLbl);
        }

        if (t.filePaths != null && !t.filePaths.isEmpty()) {
            if (t.detail != null && !t.detail.isBlank()) {
                content.getChildren().add(new Separator());
            }
            for (String p : t.filePaths) {
                String name;
                try { name = Path.of(p).getFileName().toString(); } catch (Exception ex) { name = p; }
                var link = new Label(name);
                link.setStyle(completed ? "-fx-text-fill:#999; -fx-underline:true;"
                                        : "-fx-text-fill:#0066cc; -fx-underline:true;");
                final String base = link.getStyle();
                final Path pathObj = Path.of(p);

                link.setOnMouseEntered(ev -> link.setStyle(base + HOVER_CSS));
                link.setOnMouseExited (ev -> link.setStyle(base));
                link.setCursor(javafx.scene.Cursor.HAND);

                link.setOnMouseClicked(ev -> {
                    if (ev.getButton()==MouseButton.PRIMARY && ev.getClickCount()==2) {
                        openFileDirect(pathObj);
                    }
                });

                content.getChildren().add(link);
            }
            content.getChildren().add(new Separator());
            Button openAll = new Button("全ファイルを一括で開く");
            openAll.setOnAction(ev -> {
                for (String pp : t.filePaths) openFileDirect(Path.of(pp));
            });
            content.getChildren().add(openAll);
        }


        var tp = new TitledPane(todoHeaderText(t), content);
        tp.setGraphic(title);

        tp.setExpanded(todoExpandStates.getOrDefault(t.id, false));
        tp.expandedProperty().addListener((o, ov, nv) -> todoExpandStates.put(t.id, nv));

        if (completed) {
            tp.setStyle("-fx-background-color:#f0f0f0;");
            tp.setContextMenu(new ContextMenu(new MenuItem("削除"){
                { setOnAction(e->{
                    if (Dialogs.confirm("削除", "この完了済みToDoを削除しますか？")) {
                        TodoStore.get().remove(t.id);
                        renderTodo();
                    }
                });}
            }));
        }

        if (Objects.equals(t.id, highlightTodoId)) {
            tp.setExpanded(true);
            todoExpandStates.put(t.id, true);

            tp.setStyle(tp.getStyle() + "; -fx-background-color:#fff3cd; -fx-border-color:#ffb300; -fx-border-width:2; -fx-background-radius:6; -fx-border-radius:6;");
            Platform.runLater(() -> {
                new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2), ev -> {
                        highlightTodoId = null;
                        tp.setStyle(completed ? "-fx-background-color:#f0f0f0;" : "");
                    })
                ).play();
            });
        }


        return tp;
    }
    private static String todoHeaderText(TodoStore.TodoItem t) {
        String due = (t.dueIso==null || t.dueIso.isBlank()) ? "期限:（未設定）" : "期限:"+t.dueIso;
        String cn  = (t.courseId==null? "" : " / 第"+(t.nth==null? "-" : t.nth)+"回");
        return t.title + cn + "  ・" + due;
    }
    private String todoTitleText(TodoStore.TodoItem t) {
        Course c = (t.courseId==null? null : courseById.get(t.courseId));
        String name = (c==null? "" : c.name);
        return (name.isBlank()? "" : "【"+name+"】 ") + t.title;
    }

    private void renderSchedule() {
    	sweepMismatchedAttendanceForCurrentTimetable();
        dayList.getChildren().clear();
        headerGrid.toFront();
        dayRowMap.clear();
        dateCellMap.clear();
        highlightExamBg.clear();

        if (timetable == null || timetable.slots==null || timetable.slots.isEmpty()) {
            sweepAllAutoAndExamReminders();
            dayList.getChildren().add(msgRow("（時間割がありません。パネル４で作成してください）"));
            renderTodo();
            return;
        }

        var rng = ScheduleStore.get().getRange(timetable.id);
        if (rng == null || rng.startIso == null || rng.endIso == null) {
            dayList.getChildren().add(
                msgRow("開講期間が未設定です。パネル４（時間割）の「開講期間を編集」から設定してください。")
            );
            renderTodo();
            return;
        }

        LocalDate today = LocalDate.now();
        minLoadedDate = today.minusDays(7);
        maxLoadedDate = today.plusDays(7);
        for (LocalDate d = minLoadedDate; !d.isAfter(maxLoadedDate); d=d.plusDays(1)) {
            addDayRow(d);
        }

        if (!suppressAutoScrollOnce) {
            Platform.runLater(() -> scrollToDate(today));
        }
        suppressAutoScrollOnce = false;
    }

    private Region msgRow(String text) {
        var box = new HBox(new Label(text));
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12));
        return box;
    }
    
    private static final String HOVER_CSS =
    	    "; -fx-background-color: rgba(25,118,210,0.08);"
    	  + "  -fx-background-radius: 6;"
    	  + "  -fx-padding: 2 6;"
    	  + "  -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 6, 0, 0, 1);";

    private void buildHeaderGrid() {
        headerGrid.getChildren().clear();
        configureColumns(headerGrid);
        headerGrid.setMaxWidth(Double.MAX_VALUE);
        headerGrid.prefWidthProperty().bind(scheduleRoot.widthProperty());

        Label hDate = new Label("日付");
        Label hLec  = new Label("講義予定");
        Label hRem  = new Label("リマインダー");
        hDate.setStyle("-fx-font-weight:bold;");
        hLec.setStyle("-fx-font-weight:bold;");
        hRem.setStyle("-fx-font-weight:bold;");

        VBox cellD = wrapHeaderCell(hDate, true);
        VBox cellL = wrapHeaderCell(hLec, true);
        VBox cellR = wrapHeaderCell(hRem, false);

        GridPane.setHgrow(cellL, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(cellR, javafx.scene.layout.Priority.ALWAYS);

        headerGrid.add(cellD, 0, 0);
        headerGrid.add(cellL, 1, 0);
        headerGrid.add(cellR, 2, 0);
    }

    private VBox wrapHeaderCell(Label l, boolean drawRight) {
        var box = new VBox(l);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6));
        String vr = drawRight ? "-fx-border-color: transparent #dddddd transparent transparent; -fx-border-width: 0 1 0 0; -fx-border-style: none dashed none none;" : "";
        box.setStyle(vr);
        VBox.setVgrow(box, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private void addDayRow(LocalDate date) {
        var row = new GridPane();
        configureColumns(row);
        row.setHgap(0);
        row.setPrefWidth(Double.MAX_VALUE);
        row.setMaxWidth(Double.MAX_VALUE);
        row.prefWidthProperty().bind(dayList.widthProperty());

        row.setMinHeight(ROW_HEIGHT);
        row.setPrefHeight(ROW_HEIGHT);
        row.setMaxHeight(ROW_HEIGHT);

        row.setStyle("-fx-border-color:#bdbdbd transparent transparent transparent; -fx-border-width:2 0 0 0;");

        boolean isSunday = date.getDayOfWeek()==DayOfWeek.SUNDAY;
        var dateLbl = new Label(DF_MD.format(date));
        var dateBox = new VBox(4);
        dateBox.getChildren().add(dateLbl);
        dateBox.setPadding(new Insets(8));
        dateBox.setAlignment(Pos.TOP_CENTER);
        if (date.equals(LocalDate.now())) {
            var today = new Label("今日");
            today.setStyle("-fx-text-fill:#1976d2; -fx-font-size:11px;");
            dateBox.getChildren().add(today);
        }
        dateBox.setMinHeight(ROW_HEIGHT);
        dateBox.setPrefHeight(ROW_HEIGHT);
        dateBox.setMaxHeight(ROW_HEIGHT);
        styleDateCell(dateBox, date, isSunday);
        dateCellMap.put(date, dateBox);

        var lecContent = new VBox(6);
        lecContent.setPadding(new Insets(8));
        lecContent.setFillWidth(true);

        var lecScroll = new ScrollPane(lecContent);
        lecScroll.setFitToWidth(true);
        lecScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        lecScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        lecScroll.setPannable(true);
        lecScroll.setMinHeight(ROW_HEIGHT);
        lecScroll.setPrefHeight(ROW_HEIGHT);
        lecScroll.setMaxHeight(ROW_HEIGHT);
        lecScroll.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: transparent #dddddd transparent transparent;" +
            "-fx-border-width: 0 1 0 0;" +
            "-fx-border-style: none dashed none none;"
        );
        GridPane.setHgrow(lecScroll, javafx.scene.layout.Priority.ALWAYS);

        List<Course> courses = isInRange(date) ? coursesOfDay(date.getDayOfWeek()) : List.of();

        var expectedAutoWarnKeys = new java.util.HashSet<String>();
        
        for (Course c : courses) {
            int nth = nthOf(c, date);
            String text = (safe(c.period).isBlank() ? "" : c.period + "限：") + safe(c.name) + (nth>0? ("（第"+nth+"回）"):"");
            Label lab = new Label(text);
            styleLectureLabelAndGenerateWarnings(lab, c, date, nth, expectedAutoWarnKeys);

            lab.setOnContextMenuRequested(e -> {
                ContextMenu m = buildLectureMenu(c, date, nth);
                m.show(lab, e.getScreenX(), e.getScreenY());
            });

            final String baseStyle = lab.getStyle();

            lab.setOnMouseEntered(ev -> lab.setStyle(baseStyle + HOVER_CSS));
            lab.setOnMouseExited (ev -> lab.setStyle(baseStyle));
            lab.setCursor(javafx.scene.Cursor.HAND);

            lab.setOnDragOver(ev -> {
                if (ev.getDragboard().hasFiles()) {
                    ev.acceptTransferModes(TransferMode.COPY);
                    lab.setStyle(baseStyle + HOVER_CSS + "; -fx-background-color:#e8f0ff;");
                }
                ev.consume();
            });
            lab.setOnDragExited(ev -> { lab.setStyle(baseStyle); });
            lab.setOnDragDropped(ev -> {
                List<File> fs = ev.getDragboard().getFiles();
                if (fs!=null && !fs.isEmpty()) { addFilesQuick(c, nth, fs); ev.setDropCompleted(true); }
                lab.setStyle(baseStyle);
                ev.consume();
            });

            lab.setOnMouseClicked(ev -> {
                if (ev.getButton()==MouseButton.PRIMARY && ev.getClickCount()==2) openPerSessionDialog(c, date, nth);
            });


            lecContent.getChildren().add(lab);
        }
        
        reconcileAutoWarnings(date, expectedAutoWarnKeys);
        TodoReminderLinker.syncFromFilesForDate(date);

        var remContent = new VBox(6);
        remContent.setPadding(new Insets(8));
        remContent.setFillWidth(true);

        var remScroll = new ScrollPane(remContent);
        remScroll.setFitToWidth(true);
        remScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        remScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        remScroll.setPannable(true);
        remScroll.setMinHeight(ROW_HEIGHT);
        remScroll.setPrefHeight(ROW_HEIGHT);
        remScroll.setMaxHeight(ROW_HEIGHT);
        GridPane.setHgrow(remScroll, javafx.scene.layout.Priority.ALWAYS);

        boolean inRange = isInRange(date);
        if (!inRange) {
            lecScroll.setStyle(
                "-fx-background-color:" + OUT_OF_RANGE_BG + ";" +
                "-fx-background:" + OUT_OF_RANGE_BG + ";" +
                "-fx-control-inner-background:" + OUT_OF_RANGE_BG + ";" +
                "-fx-border-color: transparent #dddddd transparent transparent;" +
                "-fx-border-width: 0 1 0 0;" +
                "-fx-border-style: none dashed none none;"
            );
            lecContent.setStyle("-fx-background-color:" + OUT_OF_RANGE_BG + ";");

            remScroll.setStyle(
                "-fx-background-color:" + OUT_OF_RANGE_BG + ";" +
                "-fx-background:" + OUT_OF_RANGE_BG + ";" +
                "-fx-control-inner-background:" + OUT_OF_RANGE_BG + ";"
            );
            remContent.setStyle("-fx-background-color:" + OUT_OF_RANGE_BG + ";");
        }

        List<ReminderStore.Reminder> rems = new ArrayList<>();

        addHolidayReminders(date, rems);

        addDeadlineReminders(date, rems);

        rems.addAll(
        	    ReminderStore.get().on(date).stream()
        	        .filter(r ->
        	            (r.priority == Priority.HOLIDAY)
        	         || (r.courseId == null && r.priority == Priority.CUSTOM)
        	         || (r.courseId != null && isCourseInTimetable(r.courseId))
        	        )
        	        .filter(r -> r.courseId == null || !isArchivedCourseId(r.courseId))
        	        .filter(r -> !(r.title!=null && r.title.contains("出席/欠席が未選択")
        	                    && (r.nth==null || r.nth<=0)))
        	        .toList()
        	);



        boolean exam = false;
        for (var r : rems.stream()
                .sorted(Comparator.comparingInt(x -> priorityRank(x.priority)))
                .toList()) {

            boolean isDeadlineAuto = (r.autoGenerated && r.priority == Priority.WARNING_MED);
            boolean isUnregistered = (r.courseId == null) || !isCourseInTimetable(r.courseId);

            Node lineNode;

            if (isDeadlineAuto && isUnregistered) {
                Text badge = new Text("（未登録科目） ");
                badge.setFill(Color.BLACK);

                Text body = new Text(remText(r));
                if (r.colorHex != null && !r.colorHex.isBlank()) {
                    body.setFill(Color.web(r.colorHex));
                }
                TextFlow flow = new TextFlow(badge, body);
                lineNode = flow;
            } else {
                Label line = new Label(remText(r));
                if (r.colorHex != null && !r.colorHex.isBlank()) {
                    line.setTextFill(Color.web(r.colorHex));
                }
                lineNode = line;
            }
            final String baseR = (lineNode.getStyle() == null ? "" : lineNode.getStyle());
            lineNode.setOnMouseEntered(ev -> lineNode.setStyle(baseR + HOVER_CSS));
            lineNode.setOnMouseExited (ev -> lineNode.setStyle(baseR));
            lineNode.setCursor(javafx.scene.Cursor.HAND);
            lineNode.setOnMouseClicked(ev -> { if (ev.getButton()==MouseButton.PRIMARY) onReminderClick(r); });

            if (r.priority == Priority.CUSTOM && !r.autoGenerated && r.id != null) {
                var edit = new MenuItem("編集…");
                edit.setOnAction(ev -> openEditCustomReminderDialog(r));
                var del = new MenuItem("リマインダーを削除");
                del.setOnAction(ev -> {
                    if (Dialogs.confirm("確認", "このリマインダーを削除しますか？")) {
                        ReminderStore.get().remove(r.id);
                        TodoReminderLinker.onReminderDeleted(r);
                        requestRefresh();
                    }
                });
                var cm = new ContextMenu(edit, del);
                lineNode.setOnContextMenuRequested(ev ->
                    cm.show(lineNode, ev.getScreenX(), ev.getScreenY())
                );
            }

            if (r.priority == Priority.EXAM && r.id != null) {
                var editExam = new MenuItem("試験日を編集…");
                editExam.setOnAction(ev -> openEditExamReminderDialog(r));
                var delExam = new MenuItem("試験日を削除");
                delExam.setOnAction(ev -> {
                    if (Dialogs.confirm("確認", "この試験日リマインダーを削除しますか？")) {
                        ReminderStore.get().remove(r.id);
                        TodoReminderLinker.onReminderDeleted(r);
                        requestRefresh();
                    }
                });
                var cmExam = new ContextMenu(editExam, delExam);
                lineNode.setOnContextMenuRequested(ev ->
                    cmExam.show(lineNode, ev.getScreenX(), ev.getScreenY())
                );
            }



            
            remContent.getChildren().add(lineNode);

            if (r.id != null && Objects.equals(r.id, highlightRemId) && Objects.equals(date, highlightRemDate)) {
                lineNode.setStyle(baseR + "; -fx-background-color:#fff3cd; -fx-border-color:#ffb300; -fx-border-width:1; -fx-background-radius:6; -fx-border-radius:6;");
                Platform.runLater(() -> {
                    new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2), ev -> {
                            lineNode.setStyle(baseR);
                            highlightRemId = null;
                            highlightRemDate = null;
                        })
                    ).play();
                });
            }

            if (r.priority == Priority.EXAM) exam = true;
        }

        if (exam) highlightExamBg.put(date, Boolean.TRUE);

        row.add(dateBox,  0, 0);
        row.add(lecScroll, 1, 0);
        row.add(remScroll, 2, 0);

        if (highlightExamBg.getOrDefault(date, false)) {
            row.setStyle(row.getStyle() + "; -fx-background-color:#ffe0e0;");
        } else if (inRange && hasDeadlineOn(date)) {
            row.setStyle(row.getStyle() + "; -fx-background-color:#fff3e0;");
        }

        
        if (isInOneWeekFromToday(date)) {
            row.setStyle(
                row.getStyle()
                + "; -fx-border-color:#bdbdbd transparent transparent #64b5f6;"
                + "  -fx-border-width:2 0 0 4;"
            );
        }

        dayRowMap.put(date, row);
        dayList.getChildren().add(row);
    }


    private void styleDateCell(VBox dateBox, LocalDate date, boolean isSunday) {
    	if (!isInRange(date)) {
    	    dateBox.setStyle(
    	        "-fx-background-color:" + OUT_OF_RANGE_BG + ";" +
    	        "-fx-border-color: transparent #dddddd transparent transparent;" +
    	        "-fx-border-width: 0 1 0 0;" +
    	        "-fx-border-style: none dashed none none;"
    	    );
    	    return;
    	}

        boolean isToday  = date.equals(LocalDate.now());
        boolean isJumped = (jumpedDate != null && jumpedDate.equals(date));

        String base = isSunday ? "#ffe6ea" : "#f5f5f5";
        String todayAccent  = "#fffde7";
        String jumpedAccent = "#e8f4ff";

        String bg = base;
        if (isToday)  bg = todayAccent;
        if (isJumped) bg = jumpedAccent;

        dateBox.setStyle(
            "-fx-background-color:"+bg+";" +
            "-fx-border-color: transparent #dddddd transparent transparent;" +
            "-fx-border-width: 0 1 0 0;" +
            "-fx-border-style: none dashed none none;"
        );
    }


    private void refreshDateCellStyles() {
        for (var e : dateCellMap.entrySet()) {
            LocalDate d = e.getKey();
            VBox cell = e.getValue();
            boolean isSun = d.getDayOfWeek()==DayOfWeek.SUNDAY;
            styleDateCell(cell, d, isSun);
        }
    }

    private void configureColumns(GridPane g) {
        if (!g.getColumnConstraints().isEmpty()) return;

        var c1 = new javafx.scene.layout.ColumnConstraints();
        c1.setMinWidth(DATE_COL_WIDTH);
        c1.setPrefWidth(DATE_COL_WIDTH);
        c1.setMaxWidth(DATE_COL_WIDTH);

        var c2 = new javafx.scene.layout.ColumnConstraints();
        c2.setPercentWidth(25);
        var c3 = new javafx.scene.layout.ColumnConstraints();
        c3.setPercentWidth(75);

        c2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        c3.setHgrow(javafx.scene.layout.Priority.ALWAYS);

        g.getColumnConstraints().setAll(c1, c2, c3);
    }

    private void prependDays(int days) {
        LocalDate from = minLoadedDate.minusDays(days);
        for (LocalDate d = minLoadedDate.minusDays(1); !d.isBefore(from); d = d.minusDays(1)) {
            var row = buildDayRowDetached(d);
            dayRowMap.put(d, row);
            dayList.getChildren().add(0, row);
        }
        minLoadedDate = from;
        refreshDateCellStyles();
    }



    private void appendDays(int days) {
        LocalDate to = maxLoadedDate.plusDays(days);
        for (LocalDate d = maxLoadedDate.plusDays(1); !d.isAfter(to); d = d.plusDays(1)) {
            addDayRow(d);
        }
        maxLoadedDate = to;
        refreshDateCellStyles();
    }
    private GridPane buildDayRowDetached(LocalDate d) {
        highlightExamBg.remove(d);
        var rng = ScheduleStore.get().getRange(timetable==null?null:timetable.id);
        if (rng == null) rng = new ScheduleStore.Range();

        var oldSize = dayList.getChildren().size();
        addDayRow(d);
        var row = dayRowMap.get(d);
        dayList.getChildren().remove(dayList.getChildren().size()-1);
        return row;
    }
    private void ensureDateLoaded(LocalDate d) {
        if (minLoadedDate==null || maxLoadedDate==null) return;
        if (d.isBefore(minLoadedDate)) {
            int diff = (int) java.time.temporal.ChronoUnit.DAYS.between(d, minLoadedDate);
            prependDays(diff + 7);
        } else if (d.isAfter(maxLoadedDate)) {
            int diff = (int) java.time.temporal.ChronoUnit.DAYS.between(maxLoadedDate, d);
            appendDays(diff + 7);
        }
    }
    private void scrollToDate(LocalDate d) {
        ensureDateLoaded(d);
        var node = dayRowMap.get(d);
        if (node == null) return;
        Platform.runLater(() -> {
            isAutoAdjusting = true;
            try {
                Bounds nb = node.localToScene(node.getBoundsInLocal());
                Bounds cb = scheduleScroll.getContent().localToScene(scheduleScroll.getContent().getBoundsInLocal());
                double y = nb.getMinY() - cb.getMinY();
                double contentH = scheduleScroll.getContent().getBoundsInLocal().getHeight();
                double viewportH = scheduleScroll.getViewportBounds().getHeight();
                double v = (contentH <= viewportH) ? 0 : y / (contentH - viewportH);
                scheduleScroll.setVvalue(Math.max(0, Math.min(1, v)));
            } finally {
                Platform.runLater(() -> isAutoAdjusting = false);
            }
        });
    }

    private void styleLectureLabelAndGenerateWarnings(Label lab, Course c, LocalDate date, int nth,
                                                      java.util.Set<String> expectedKeys) {
        lab.setStyle("-fx-font-size: 12.5px;");
        var rec = AttendanceStore.get().find(c.id, date).orElse(null);

        if (rec != null && rec.cancelled) {
            lab.setStyle("-fx-text-fill:#999; -fx-strikethrough:true;");
            lab.setText(lab.getText()+"（休講）");
            return;
        }

        if (date.isBefore(LocalDate.now())) {
            if (nth <= 0) return; // 第0回は作らない
            boolean needWarn = (rec == null || rec.attendance==Attendance.UNKNOWN);
            if (needWarn) {
                lab.setTextFill(Color.web("#ff8c00"));
                String title = safe(c.name)+"（第"+nth+"回）の出席/欠席が未選択です";
                addOrTrackAutoWarning(
                    date, c.id, nth,
                    title,
                    "講義当日の出欠状態が未設定です。クリックして設定してください。",
                    Priority.WARNING_LOW, "#ff8c00",
                    expectedKeys
                );
            }
        }

        var files = SavedFileIndex.get().list().stream()
                .filter(e -> Objects.equals(e.courseId, c.id) && Objects.equals(e.nth, nth))
                .toList();

        boolean hasActiveTask = files.stream().anyMatch(e ->
                "課題_テキスト".equals(e.kind) || "課題_未提出".equals(e.kind));
        boolean hasSubmitted   = files.stream().anyMatch(e ->
                "課題_提出済み".equals(e.kind));
        boolean hasAnyTaskRegistered = hasActiveTask || hasSubmitted;

        boolean hasDeadlineForActive = files.stream()
                .filter(e -> "課題_テキスト".equals(e.kind) || "課題_未提出".equals(e.kind))
                .anyMatch(e -> e.deadline != null && !e.deadline.isBlank());

        var aRec = AttendanceStore.get().find(c.id, date).orElse(null);
        if (aRec != null && Boolean.TRUE.equals(aRec.hasAssignment)) {
            if (!hasAnyTaskRegistered) {
                lab.setTextFill(Color.web("#d32f2f"));
                addOrTrackAutoWarning(
                    date, c.id, nth,
                    "課題が登録されていません",
                    "パネル２で課題ファイルを登録してください。",
                    Priority.WARNING_HIGH, "#d32f2f",
                    expectedKeys
                );
            } else if (hasActiveTask && !hasDeadlineForActive) {
                lab.setTextFill(Color.web("#d32f2f"));
                addOrTrackAutoWarning(
                    date, c.id, nth,
                    "課題の提出期限が設定されていません",
                    "課題ファイルの提出期限を設定してください。",
                    Priority.WARNING_HIGH, "#d32f2f",
                    expectedKeys
                );
            }
        }
    }


 // PanelScheduleTodo 内
    private void addDeadlineReminders(LocalDate date, List<ReminderStore.Reminder> out) {
        final String day = date.toString();

        TodoReminderLinker.syncFromFilesForDate(date);

        var seen = new java.util.HashSet<String>();

        for (var e : SavedFileIndex.get().list()) {
            if (e.deadline == null || !Objects.equals(e.deadline, day)) continue;
            if (e.courseId != null && isArchivedCourseId(e.courseId)) continue;
            if (!"課題_未提出".equals(e.kind) && !"課題_テキスト".equals(e.kind)) continue;

            Integer cid = e.courseId;
            Integer nth = e.nth;
            String key = (cid==null?"-":cid) + "|" + (nth==null?"-":nth) + "|" + day;
            if (!seen.add(key)) continue;

            if (isTodoDoneFor(cid, nth, day)) continue;

            var r = new ReminderStore.Reminder();
            r.dateIso  = day;
            r.courseId = cid;
            r.nth      = nth;
            r.title    = (nth==null ? "提出期限です" : "第"+nth+"回の提出期限です");
            r.detail   = "課題の提出期限です。";
            r.priority = ReminderStore.Priority.WARNING_MED;
            r.colorHex = "#ff8c00";
            r.autoGenerated = true;

            TodoReminderLinker.ensureTodoForReminder(r);

            out.add(r);
        }
    }

    private boolean isTodoDoneFor(Integer courseId, Integer nth, String dueIso) {
        return app.univtool.core.TodoStore.get().listAll().stream()
            .filter(t -> Objects.equals(t.courseId, courseId))
            .filter(t -> Objects.equals(t.nth, nth))
            .filter(t -> Objects.equals(
                (t.dueIso==null?"":t.dueIso),
                (dueIso==null?"":dueIso)))
            .filter(t -> {
                String ttl = (t.title==null?"":t.title).trim();
                return ttl.equalsIgnoreCase("課題");
            })
            .anyMatch(t -> t.done);
    }

    private void addHolidayReminders(LocalDate date, List<ReminderStore.Reminder> out) {
        String holidayName = null;
        try { holidayName = JpHolidays.getName(date); } catch (Exception ignore) {}
        if (holidayName != null && !holidayName.isBlank()) {
            var r = new ReminderStore.Reminder();
            r.dateIso = date.toString();
            r.title = holidayName;
            r.detail = null;
            r.priority = Priority.HOLIDAY;
            r.colorHex = null;
            r.autoGenerated = true;
            out.add(r);
            return;
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            var r = new ReminderStore.Reminder();
            r.dateIso = date.toString();
            r.title = "日曜日";
            r.detail = null;
            r.priority = Priority.HOLIDAY;
            r.colorHex = null;
            r.autoGenerated = true;
            out.add(r);
        }
    }


    
    private boolean hasDeadlineOn(LocalDate d) {
        for (var e : SavedFileIndex.get().list()) {
            if (e.deadline==null) continue;
            if (Objects.equals(e.deadline, d.toString())) return true;
        }
        return false;
    }

    private String remText(ReminderStore.Reminder r) {
        String base = (r == null || r.title == null || r.title.isBlank()) ? "(無題)" : r.title;

        if (r.courseId!=null) {
            Course c = courseById.get(r.courseId);
            if (c!=null && c.name!=null) base = "【"+c.name+"】 "+base;
        }
        return base;
    }
    
    private boolean isInOneWeekFromToday(LocalDate d) {
        LocalDate t = LocalDate.now();
        return !d.isBefore(t) && !d.isAfter(t.plusDays(6));
    }


    private void openJumpDialog() {
        var dlg = new Dialog<ButtonType>();
        hookAutoRefresh(dlg);
        dlg.setTitle("日付へジャンプ");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        var dp = new DatePicker(LocalDate.now());
        var box = new VBox(8, new Label("表示する日付を選択"), dp);
        box.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(box);
        if (dlg.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) {
            jumpedDate = dp.getValue();
            ensureDateLoaded(jumpedDate);
            scrollToDate(jumpedDate);
            refreshDateCellStyles();
        }
    }

    private void openCustomReminderDialog(Integer courseId, Integer nthPreset) {
    	var dlg = new Dialog<ButtonType>();
    	hookAutoRefresh(dlg);
    	dlg.setTitle("リマインダー追加");
    	dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    	dlg.initOwner(getScene()==null?null:getScene().getWindow());
    	dlg.initModality(Modality.WINDOW_MODAL);
    	dlg.setResizable(true);

        var dp = new DatePicker(LocalDate.now());

        var cbCourse = new ComboBox<String>();
        var timetableCourseIds = (timetable == null || timetable.slots == null)
                ? java.util.Set.<Integer>of()
                : new java.util.HashSet<>(timetable.slots.values());
        var courses = courseRepo.findAll().stream()
                .filter(c -> timetableCourseIds.contains(c.id))
                .filter(c -> !isArchived(c)) 
                .sorted(Comparator.comparing(c -> safe(c.name)))
                .toList();

        cbCourse.getItems().add("（紐づけなし）");
        for (Course c : courses) cbCourse.getItems().add(c.name);

        if (courseId != null && courseById.get(courseId) != null && timetableCourseIds.contains(courseId)) {
            cbCourse.getSelectionModel().select(courseById.get(courseId).name);
        } else {
            cbCourse.getSelectionModel().selectFirst();
        }

        var spNth = new Spinner<Integer>(1, 1000, nthPreset == null ? 1 : nthPreset);
        spNth.setEditable(true);

        java.util.function.Consumer<String> toggleNth = sel -> {
            boolean linked = sel != null && !"（紐づけなし）".equals(sel);
            spNth.setDisable(!linked);
        };
        toggleNth.accept(cbCourse.getValue());
        cbCourse.valueProperty().addListener((o, ov, nv) -> toggleNth.accept(nv));

        var tfTitle = new TextField();
        var taDetail = new TextArea(); taDetail.setPrefRowCount(4);

        var cbColor = new CheckBox("文字色を設定する");
        var colorPicker = new ColorPicker(Color.web("#3366cc"));
        colorPicker.setDisable(true);
        cbColor.selectedProperty().addListener((o,a,b)-> colorPicker.setDisable(!b));

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(10));
        int r=0;
        g.addRow(r++, new Label("日付"), dp);
        g.addRow(r++, new Label("授業科目名（任意）"), cbCourse);
        g.addRow(r++, new Label("第何回"), spNth);
        g.addRow(r++, new Label("タイトル"), tfTitle);
        g.addRow(r++, new Label("内容"), taDetail);

        var cbLinkTodo = new CheckBox("このリマインダーを ToDo に追加する");
        cbLinkTodo.setSelected(true);
        g.add(cbLinkTodo, 0, r, 2, 1); r++;

        g.add(cbColor, 0, r); g.add(colorPicker, 1, r++);


        dlg.getDialogPane().setContent(g);

        if (dlg.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            var rmd = new ReminderStore.Reminder();
            rmd.dateIso = dp.getValue().toString();

            String sel = cbCourse.getValue();
            if (sel != null && !"（紐づけなし）".equals(sel)) {
                var c = courses.stream().filter(x -> Objects.equals(x.name, sel)).findFirst().orElse(null);
                if (c != null) rmd.courseId = c.id;
            } else {
                rmd.courseId = null;
            }

            rmd.nth = (spNth.isDisabled() ? null : spNth.getValue());
            rmd.title = tfTitle.getText();
            rmd.detail = taDetail.getText();
            rmd.priority = Priority.CUSTOM;
            rmd.colorHex = cbColor.isSelected()? toHex(colorPicker.getValue()) : null;

            ReminderStore.get().add(rmd);

            if (cbLinkTodo.isSelected()) {
                TodoReminderLinker.ensureTodoForReminder(rmd);
                if (rmd.linkedTodoId != null) {
                    var t = TodoStore.get().listAll().stream()
                            .filter(x -> Objects.equals(x.id, rmd.linkedTodoId))
                            .findFirst().orElse(null);
                    if (t != null) {
                        t.detail = rmd.detail;
                        TodoReminderLinker.updateAndLink(t);
                    }
                }
            }

            requestRefresh();
        }
    }


    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",(int)(c.getRed()*255),(int)(c.getGreen()*255),(int)(c.getBlue()*255));
    }

    private void onReminderClick(ReminderStore.Reminder r) {
        if (r.priority == Priority.HOLIDAY) return;
        if (r.linkedTodoId != null) {
            highlightTodoId = r.linkedTodoId;
            split.setDividerPositions(0.55);
            renderTodo();
            return;
        }

        
        if (r.autoGenerated && r.priority == Priority.WARNING_MED
                && r.title != null && r.title.contains("提出期限")) {
            var target = TodoStore.get().listAll().stream()
                .filter(t -> Objects.equals(t.courseId, r.courseId))
                .filter(t -> Objects.equals(t.nth, r.nth))
                .filter(t -> Objects.equals(t.dueIso, r.dateIso))
                .findFirst()
                .or(() -> TodoStore.get().findByCourseNth(r.courseId, r.nth, "課題"))
                .orElse(null);

            if (target != null) {
                highlightTodoId = target.id;
                split.setDividerPositions(0.55);
                renderTodo();
                return;
            }
        }
        
    	if (r.priority == Priority.WARNING_HIGH && r.title != null && r.title.contains("課題が登録されていません")) {
    	    Course c = (r.courseId==null? null : courseById.get(r.courseId));
    	    int nth = (r.nth==null? 1 : r.nth);

    	    var onlyTasks = java.util.List.of(
    	        app.univtool.ui.panel2.ClassifyDialog.Kind.課題_テキスト,
    	        app.univtool.ui.panel2.ClassifyDialog.Kind.課題_未提出,
    	        app.univtool.ui.panel2.ClassifyDialog.Kind.課題_提出済み
    	    );
    	    openPanel2SavePopupFixed(c, nth, onlyTasks);
    	    return;
    	}

        if (r.priority==Priority.WARNING_LOW && r.title!=null && r.title.contains("出席/欠席")) {
            Course c = (r.courseId==null? null : courseById.get(r.courseId));
            if (c!=null) openPerSessionDialog(c, LocalDate.parse(r.dateIso), r.nth==null?0:r.nth);
            return;
        }
        if (r.priority==Priority.WARNING_HIGH && r.title!=null && r.title.contains("期限が設定されていません")) {
            openBulkDeadlineDialog(r.courseId, r.nth);
            return;
        }
        var a = new Alert(Alert.AlertType.INFORMATION, (r.detail==null||r.detail.isBlank()? "(詳細なし)":r.detail), ButtonType.OK);
        a.setHeaderText(r.title);
        a.showAndWait();
    }

    private void openBulkDeadlineDialog(Integer cid, Integer nth) {
        if (cid==null || nth==null) return;
        var targets = SavedFileIndex.get().list().stream()
                .filter(e -> Objects.equals(e.courseId,cid) && Objects.equals(e.nth,nth))
                .filter(e -> "課題_テキスト".equals(e.kind) || "課題_未提出".equals(e.kind))
                .filter(e -> e.deadline==null || e.deadline.isBlank())
                .toList();
        if (targets.isEmpty()) { Dialogs.info("情報","期限未設定の課題ファイルはありません。"); return; }

        var dlg = new Dialog<ButtonType>();
        hookAutoRefresh(dlg);
        dlg.setTitle("提出期限の一括設定");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        var dp = new DatePicker(LocalDate.now().plusDays(7));

        VBox box = new VBox(6);
        box.getChildren().addAll(new Label("以下のファイルの期限を一括で設定します。"), new Label("件数: "+targets.size()),
                new Label("期限日:"), dp);
        dlg.getDialogPane().setContent(box);

        if (dlg.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) {
            for (var e : targets) SavedFileIndex.get().updateDeadline(Path.of(e.path), dp.getValue());
            requestRefresh();
        }
    }

    private ContextMenu buildLectureMenu(Course c, LocalDate date, int nth) {
        var m = new ContextMenu();

        var add = new MenuItem("ファイル追加");
        add.setOnAction(e -> openPanel2SavePopupFixed(c, nth, /*allowedKinds*/ null));
        var search = new MenuItem("ファイル検索");
        search.setOnAction(e -> openFileSearchPopup(c.id, null));

        var attend = new MenuItem("出席に変更する");
        var absent = new MenuItem("欠席に変更する");
        var rec = AttendanceStore.get().getOrCreate(c.id, date);
        if (rec.attendance==Attendance.PRESENT || rec.attendance==Attendance.EXCUSED) attend.setDisable(true);
        if (rec.attendance==Attendance.ABSENT) absent.setDisable(true);
        attend.setOnAction(e -> {
            if (Dialogs.confirm("確認","出席に変更しますか？")) {
                rec.attendance = Attendance.PRESENT; AttendanceStore.get().save(); requestRefresh();
            }
        });
        absent.setOnAction(e -> {
            if (Dialogs.confirm("確認","欠席に変更しますか？")) {
                rec.attendance = Attendance.ABSENT; AttendanceStore.get().save(); requestRefresh();
            }
        });

        var cancelOn = new MenuItem("休講状態にする");
        var cancelOff = new MenuItem("休講状態の解除");
        cancelOn.setOnAction(e -> {
            if (Dialogs.confirm("確認", "この回を休講として扱いますか？")) {
                rec.cancelled = true; AttendanceStore.get().save(); requestRefresh();
            }
        });
        cancelOff.setOnAction(e -> {
            if (Dialogs.confirm("確認", "休講状態を解除しますか？")) {
                rec.cancelled = false; AttendanceStore.get().save(); requestRefresh();
            }
        });

        cancelOn.setDisable(rec.cancelled);
        cancelOff.setDisable(!rec.cancelled);

        var detail = new MenuItem("講義の詳細を見る");
        detail.setOnAction(e -> {
            var dlg = new CourseDetailsDialog(c, workspaceDir, archiveDir, ()->renderSchedule());
            hookAutoRefresh(dlg);
            dlg.initOwner(getScene()==null?null:getScene().getWindow());
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.showAndWait();
        });

        var openWs = new MenuItem("エクスプローラーでワークスペースを開く");
        openWs.setOnAction(e -> {
            if (workspaceDir == null) {
                Dialogs.error("エラー", "ワークスペースが未設定です。設定から指定してください。");
                return;
            }
            Path folder = workspaceDir.resolve(FileSaveOps.resolveCourseFolderName(c));
            try {
                Files.createDirectories(folder);
                String os = System.getProperty("os.name","").toLowerCase(Locale.ROOT);
                if (os.contains("win")) new ProcessBuilder("explorer.exe", folder.toAbsolutePath().toString()).start();
                else if (os.contains("mac")) new ProcessBuilder("open", folder.toAbsolutePath().toString()).start();
                else new ProcessBuilder("xdg-open", folder.toAbsolutePath().toString()).start();
            } catch (Exception ex) {
                Dialogs.error("エラー","フォルダを開けませんでした: "+ex.getMessage());
            }
        });

        m.getItems().addAll(add, search, new SeparatorMenuItem(),
                attend, absent, new SeparatorMenuItem(),
                rec.cancelled? cancelOff : cancelOn, new SeparatorMenuItem(),
                detail, openWs);
        return m;
    }

    private List<File> askFiles() {
        var fc = new FileChooser();
        fc.setTitle("追加するファイルを選択");
        return fc.showOpenMultipleDialog(getScene()==null?null:getScene().getWindow());
    }

    private void addFilesQuick(Course c, int nth, List<File> files) {
        if (files==null || files.isEmpty()) return;
        if (workspaceDir == null) {
            Dialogs.error("エラー", "ワークスペースが未設定です。設定から指定してください。");
            return;
        }
        try {
            Path dstDir = workspaceDir.resolve(FileSaveOps.resolveCourseFolderName(c));
            Files.createDirectories(dstDir);

            for (File f : files) {
                Path src = f.toPath();
                Path target = FileSaveOps.ensureUnique(dstDir, src.getFileName().toString());
                Files.copy(src, target);
                SavedFileIndex.get().addEntry(c.id, nth, "課題_未提出", target.toString(), null);
                var todoOpt = TodoStore.get().findByCourseNth(c.id, nth, "課題");
                TodoStore.TodoItem t = todoOpt.orElseGet(() -> {
                    var nt = new TodoStore.TodoItem();
                    nt.courseId = c.id; nt.nth = nth; nt.title = "課題"; nt.detail = "";
                    return TodoReminderLinker.addAndLink(nt);
                });
                if (!t.filePaths.contains(target.toString())) {
                    t.filePaths.add(target.toString());
                    TodoReminderLinker.updateAndLink(t);
                }

            }
            renderTodo();
            renderSchedule();
        } catch (Exception ex) {
            Dialogs.error("エラー","ファイル追加に失敗: "+ex.getMessage());
        }
    }

    private void openFileSearchPopup(Integer courseId, String focusPath) {
        Course c = (courseId==null? null : courseById.get(courseId));
        var pane = (c==null? new PanelFileSearch() : new PanelFileSearch(c));
        var st = new Stage();
        hookAutoRefresh(st); 
        st.setTitle("ファイル検索"+(c==null? "" : " - "+c.name));
        st.initOwner(getScene()==null?null:getScene().getWindow());
        st.initModality(Modality.WINDOW_MODAL);
        st.setScene(new Scene(pane, 900, 600));
        st.show();
        if (focusPath!=null) {
            try {
                var m = PanelFileSearch.class.getMethod("revealFile", java.nio.file.Path.class);
                Platform.runLater(()-> {
                    try { m.invoke(pane, Path.of(focusPath)); } catch (Exception ignore) {}
                });
            } catch (Exception ignore) {}
        }
    }

    private void openPerSessionDialog(Course c, LocalDate date, int nth) {
        var rec = AttendanceStore.get().getOrCreate(c.id, date);

        boolean needStep1 =
                rec.attendance==Attendance.UNKNOWN ||
                rec.hasAssignment==null;

        if (needStep1) {
            if (!openStep1Dialog(c, date, nth, rec)) return;
        }
        openStep2Dialog(c, date, nth, rec);
    }

    private boolean openStep1Dialog(Course c, LocalDate date, int nth, AttendanceStore.Rec rec) {
        var dlg = new Dialog<ButtonType>();
        hookAutoRefresh(dlg); 
        dlg.setTitle("【"+c.name+" 第"+nth+"回】 出席/課題 確認");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        var tgAtt = new ToggleGroup();
        var rbP = new RadioButton("出席"); rbP.setToggleGroup(tgAtt);
        var rbE = new RadioButton("出席（公欠）"); rbE.setToggleGroup(tgAtt);
        var rbA = new RadioButton("欠席"); rbA.setToggleGroup(tgAtt);
        if (rec.attendance==Attendance.PRESENT) rbP.setSelected(true);
        else if (rec.attendance==Attendance.EXCUSED) rbE.setSelected(true);
        else if (rec.attendance==Attendance.ABSENT) rbA.setSelected(true);

        var tgTask = new ToggleGroup();
        var rbHas = new RadioButton("課題あり"); rbHas.setToggleGroup(tgTask);
        var rbNone= new RadioButton("課題なし"); rbNone.setToggleGroup(tgTask);
        if (rec.hasAssignment!=null) (rec.hasAssignment?rbHas:rbNone).setSelected(true);

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(10));
        g.addRow(0, new Label("出席確認"), new HBox(10, rbP, rbE, rbA));
        g.addRow(1, new Label("課題確認"), new HBox(10, rbHas, rbNone));
        dlg.getDialogPane().setContent(g);

        if (dlg.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK) return false;

        if (tgAtt.getSelectedToggle()==rbP) rec.attendance = Attendance.PRESENT;
        else if (tgAtt.getSelectedToggle()==rbE) rec.attendance = Attendance.EXCUSED;
        else if (tgAtt.getSelectedToggle()==rbA) rec.attendance = Attendance.ABSENT;
        else rec.attendance = Attendance.UNKNOWN;

        if (tgTask.getSelectedToggle()==rbHas) rec.hasAssignment = true;
        else if (tgTask.getSelectedToggle()==rbNone) rec.hasAssignment = false;

        AttendanceStore.get().save();
        return true;
    }

    private void openStep2Dialog(Course c, LocalDate date, int nth, AttendanceStore.Rec rec) {
    	var dlg = new Dialog<ButtonType>();
    	hookAutoRefresh(dlg); 
    	dlg.setTitle("【"+c.name+" 第"+nth+"回】 情報");
    	dlg.setResizable(true);
    	var owner = (getScene()==null? null : getScene().getWindow());
    	if (owner != null) {
    	    double maxW = owner.getWidth();
    	    dlg.getDialogPane().setMaxWidth(maxW);
    	    dlg.getDialogPane().setPrefWidth(Math.min(maxW, 900));
    	    dlg.getDialogPane().setMaxHeight(owner.getHeight()*0.9);
    	}

    	var close = new ButtonType("閉じる", ButtonData.CANCEL_CLOSE);
    	var addRem = new ButtonType("リマインダーを追加", ButtonData.LEFT);
    	var setExam = new ButtonType("試験日を設定", ButtonData.LEFT);
    	var addFile = new ButtonType("ファイル追加", ButtonData.LEFT);

    	if (courseAllowsExam(c)) {
    	    dlg.getDialogPane().getButtonTypes().addAll(addRem, setExam, addFile, close);
    	} else {
    	    dlg.getDialogPane().getButtonTypes().addAll(addRem, addFile, close);
    	}

        var left = new VBox(8);
        left.getChildren().addAll(
                new Label("出席情報: "+ attendanceLabel(rec)),
                new Label("課題状況: "+ (rec.hasAssignment==null? "（未設定）": (rec.hasAssignment? "課題あり":"課題なし")))
        );

        var hist = AttendanceStore.get().listByCourse(c.id);
        long total = hist.stream().filter(x->!x.cancelled).count();
        long present = hist.stream().filter(x->!x.cancelled && (x.attendance==Attendance.PRESENT || x.attendance==Attendance.EXCUSED)).count();
        long excused = hist.stream().filter(x->!x.cancelled && x.attendance==Attendance.EXCUSED).count();
        String rate = (total==0? "0%" : String.format(Locale.JAPAN,"%.0f%%", present*100.0/total));
        left.getChildren().add(new Label("累計出席回数 "+present+"回"+(excused>0? "（うち公欠"+excused+"回）":"")+" 出席率"+rate));

        var right = new VBox(8);
        var filesTitle = new Label("ファイル一覧"); filesTitle.setStyle("-fx-font-weight:bold;");
        var fileBox = new VBox(4);
        var files = SavedFileIndex.get().list().stream()
                .filter(e -> Objects.equals(e.courseId, c.id) && Objects.equals(e.nth, nth))
                .sorted(Comparator.comparing(e->{
                    try { return Path.of(e.path).getFileName().toString(); } catch(Exception ex){ return e.path;}
                }))
                .toList();
        if (files.isEmpty()) fileBox.getChildren().add(new Label("（該当ファイルなし）"));
        for (var e : files) {
            String name;
            try { name = Path.of(e.path).getFileName().toString(); } catch(Exception ex){ name = e.path; }
            var lb = new Label(name);
            lb.setStyle("-fx-text-fill:#0066cc; -fx-underline:true;");
            final String p = e.path;
            final String base = lb.getStyle();
            lb.setCursor(javafx.scene.Cursor.HAND);
            lb.setOnMouseEntered(ev -> lb.setStyle(base + HOVER_CSS));
            lb.setOnMouseExited (ev -> lb.setStyle(base));
            lb.setOnMouseClicked(ev -> {
                if (ev.getButton()==MouseButton.PRIMARY) {
                    openFileDirect(Path.of(p));
                }
            });
            fileBox.getChildren().add(lb);
        }


        var todoTitle = new Label("ToDoリスト"); todoTitle.setStyle("-fx-font-weight:bold;");
        var tbox = new VBox(4);
        var todos = TodoStore.get().listAll().stream()
                .filter(t->Objects.equals(t.courseId,c.id)&&Objects.equals(t.nth,nth))
                .toList();
        if (todos.isEmpty()) tbox.getChildren().add(new Label("（該当ToDoなし）"));
        for (var t : todos) {
            var l = new Label((t.done?"[完] ":"")+ t.title + " / 期限:"+(t.dueIso==null?"未設定":t.dueIso));
            final String base2 = l.getStyle();
            l.setCursor(javafx.scene.Cursor.HAND);
            l.setOnMouseEntered(ev -> l.setStyle(base2 + HOVER_CSS));
            l.setOnMouseExited (ev -> l.setStyle(base2));
            l.setOnMouseClicked(ev -> {
                if (ev.getButton()==MouseButton.PRIMARY) {
                    highlightTodoId = t.id;
                    dlg.close();
                    split.setDividerPositions(0.55);
                    renderTodo();
                }
            });
            tbox.getChildren().add(l);
        }

        var remTitle = new Label("リマインダー"); remTitle.setStyle("-fx-font-weight:bold;");
        var rbox = new VBox(4);
        for (var r : ReminderStore.get().byCourse(c.id)) {
            var rl = new Label(r.dateIso+" "+(r.title==null?"":r.title));
            final String base = rl.getStyle();
            rl.setCursor(javafx.scene.Cursor.HAND);
            rl.setOnMouseEntered(ev -> rl.setStyle(base + HOVER_CSS));
            rl.setOnMouseExited (ev -> rl.setStyle(base));
            rl.setOnMouseClicked(ev -> {
                if (ev.getButton()==MouseButton.PRIMARY && r.dateIso!=null) {
                	highlightRemId   = r.id;
                	highlightRemDate = LocalDate.parse(r.dateIso);
                	dlg.close();
                	requestRefresh();
                	Platform.runLater(() -> {
                	    ensureDateLoaded(highlightRemDate);
                	    scrollToDate(highlightRemDate);
                	});
                }
            });
            rbox.getChildren().add(rl);
        }


        var allSessions = AttendanceStore.get().listByCourse(c.id).stream()
                .filter(x -> !x.cancelled && Boolean.TRUE.equals(x.hasAssignment))
                .filter(x -> { try { return isInRange(LocalDate.parse(x.dateIso)); } catch(Exception e){ return false; }})
                .toList();
        long doneCount = allSessions.stream().filter(x->Boolean.TRUE.equals(x.assignmentDone)).count();
        String prate = (allSessions.isEmpty()? "0%" : String.format(Locale.JAPAN,"%.0f%%", doneCount*100.0/allSessions.size()));
        var sumTask = new Label("累計課題提出状況: "+doneCount+"/"+allSessions.size()+" ("+prate+")");

        var remain = new VBox(2);
        var remainList = allSessions.stream().filter(x->!Boolean.TRUE.equals(x.assignmentDone)).toList();
        if (remainList.isEmpty()) remain.getChildren().add(new Label("未完了なし"));
        else {
            for (var r : remainList) {
                int nthX = nthOf(c, LocalDate.parse(r.dateIso));
                String due = SavedFileIndex.get().list().stream()
                        .filter(e->Objects.equals(e.courseId,c.id) && Objects.equals(e.nth, nthX))
                        .map(e->e.deadline).filter(Objects::nonNull).findFirst().orElse("未設定");
                remain.getChildren().add(new Label("第"+nthX+"回 / 期限:"+due));
            }
        }

        var leftBox = new VBox(8, left, new Separator(), sumTask, new Label("未完了一覧:"), remain);
        leftBox.setPadding(new Insets(8));
        var filesArea = wrapListArea(fileBox, 180);
        var todoArea  = wrapListArea(tbox,   180);
        var remArea   = wrapListArea(rbox,   180);

        var rightBox = new VBox(8,
            filesTitle, filesArea, new Separator(),
            todoTitle,  todoArea,  new Separator(),
            remTitle,   remArea
        );
        rightBox.setPadding(new Insets(8));

        var split = new SplitPane();
        split.getItems().addAll(leftBox, rightBox);
        split.setDividerPositions(0.45);

        dlg.getDialogPane().setContent(split);

        var nAddRem = (Button) dlg.getDialogPane().lookupButton(addRem);
        nAddRem.setOnAction(e -> {
            dlg.close();
            Platform.runLater(() -> openCustomReminderDialog(c.id, nth));
        });

        var nSetExam = (Button) dlg.getDialogPane().lookupButton(setExam);
        nSetExam.setOnAction(e -> {
            dlg.close();
            Platform.runLater(() -> openExamReminderDialog(c, date, nth));
        });

        var nAddFile = (Button) dlg.getDialogPane().lookupButton(addFile);
        nAddFile.setOnAction(e -> {
            openPanel2SavePopupFixed(c, nth, /*allowedKinds*/ null);
            dlg.close();
        });

        dlg.initOwner(getScene()==null?null:getScene().getWindow());
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.showAndWait();
    }

    private String attendanceLabel(AttendanceStore.Rec r) {
        if (r.cancelled) return "休講";
        return switch (r.attendance) {
            case PRESENT -> "出席";
            case EXCUSED -> "出席（公欠）";
            case ABSENT  -> "欠席";
            default -> "（未設定）";
        };
    }

    private void openExamReminderDialog(Course c, LocalDate date, int nth) {
        var dlg = new Dialog<ButtonType>();
        hookAutoRefresh(dlg);
        dlg.setTitle("試験日を設定");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.initOwner(getScene()==null?null:getScene().getWindow());
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.setResizable(true);

        var dp = new DatePicker(date);
        var note = new Label("※タイトルは『試験日』で固定、文字色は赤、最優先で表示。");
        VBox box = new VBox(8, new Label("日付"), dp, note);
        box.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(box);

        if (dlg.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) {
            var r = new ReminderStore.Reminder();
            r.dateIso = dp.getValue().toString();
            r.courseId = c.id; r.nth = nth;
            r.title = "試験日";
            r.detail = "試験日です。";
            r.priority = Priority.EXAM;
            r.colorHex = "#d32f2f";
            ReminderStore.get().add(r);
            requestRefresh();
        }
    }


    private void openAddTodoDialog(Integer courseId, Integer nth) {
        var dlg = new Dialog<ButtonType>();
        hookAutoRefresh(dlg);
        dlg.setTitle("新規ToDo");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        var cbCourse = new ComboBox<String>();
        var courses = courseRepo.findAll().stream()
        	    .filter(c -> !isArchived(c))
        	    .sorted(Comparator.comparing(c->safe(c.name)))
        	    .toList();

        cbCourse.getItems().add("（紐づけなし）");
        for (Course c : courses) cbCourse.getItems().add(c.name);
        cbCourse.getSelectionModel().selectFirst();
        if (courseId!=null && courseById.get(courseId)!=null) cbCourse.getSelectionModel().select(courseById.get(courseId).name);

        var spNth = new Spinner<Integer>(1, 1000, nth==null?1:nth);
        spNth.setEditable(true);

        java.util.function.Consumer<String> toggleNth = sel -> {
            boolean linked = sel != null && !"（紐づけなし）".equals(sel);
            spNth.setDisable(!linked);
        };
        toggleNth.accept(cbCourse.getValue());
        cbCourse.valueProperty().addListener((o,ov,nv) -> toggleNth.accept(nv));

        var tfTitle = new TextField();
        var ta = new TextArea(); ta.setPrefRowCount(3);
        var dpDue = new DatePicker(LocalDate.now());
        var cbRemColor = new CheckBox("リマインダーの文字色を設定する");
        var remColorPicker = new ColorPicker(Color.web("#3366cc"));
        remColorPicker.setDisable(true);
        cbRemColor.selectedProperty().addListener((o,a,b)-> remColorPicker.setDisable(!b));

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(10));
        int r=0;
        g.addRow(r++, new Label("授業科目名"), cbCourse);
        g.addRow(r++, new Label("第何回"), spNth);
        g.addRow(r++, new Label("タイトル"), tfTitle);
        g.addRow(r++, new Label("内容"), ta);
        g.addRow(r++, new Label("期限"), dpDue);
        g.add(cbRemColor, 0, r); g.add(remColorPicker, 1, r++);

        dlg.getDialogPane().setContent(g);


        if (dlg.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) {
            var t = new TodoStore.TodoItem();
            String sel = cbCourse.getValue();
            if (sel!=null && !"（紐づけなし）".equals(sel)) {
                var c = courses.stream().filter(x->Objects.equals(x.name, sel)).findFirst().orElse(null);
                if (c!=null) t.courseId = c.id;
            }
            t.nth = spNth.getValue();
            t.title = tfTitle.getText();
            t.detail = ta.getText();
            t.dueIso = (dpDue.getValue()==null? null : dpDue.getValue().toString());
            t = TodoReminderLinker.addAndLink(t);

         if (cbRemColor.isSelected() && t.dueIso != null) {
             try {
                 LocalDate due = LocalDate.parse(t.dueIso);
                 var list = ReminderStore.get().on(due);
                 for (var rmd : list) {
                     if (Objects.equals(rmd.linkedTodoId, t.id)) {
                         rmd.colorHex = toHex(remColorPicker.getValue());
                         ReminderStore.get().save();
                         break;
                     }
                 }
             } catch (Exception ignore) {}
         }

         requestRefresh();

        }
    }
    
    /** 日付が開講期間内か */
    private boolean isInRange(LocalDate d) {
        var rng = ScheduleStore.get().getRange(timetable==null?null:timetable.id);
        if (rng == null || rng.startIso == null || rng.endIso == null) return false;
        LocalDate s = LocalDate.parse(rng.startIso);
        LocalDate e = LocalDate.parse(rng.endIso);
        return !(d.isBefore(s) || d.isAfter(e));
    }
    
    /** この courseId が現在の時間割に含まれているか */
    private boolean isCourseInTimetable(Integer courseId) {
        if (courseId == null || timetable == null || timetable.slots == null) return false;
        return timetable.slots.values().stream().anyMatch(id -> Objects.equals(id, courseId));
    }
    
    /** 時間割が無い時に、自動警告を全消し */
    private void sweepAllAutoAndExamReminders() {
        LocalDate start = LocalDate.now().minusYears(1);
        LocalDate end   = LocalDate.now().plusYears(1);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            var list = new ArrayList<>(ReminderStore.get().on(d));
            for (var r : list) {
                boolean isAutoWarn = r.autoGenerated &&
                        (r.priority == Priority.WARNING_LOW
                      || r.priority == Priority.WARNING_MED
                      || r.priority == Priority.WARNING_HIGH);
                boolean isExam = (r.priority == Priority.EXAM);
                if (isAutoWarn || isExam) {
                    if (r.id != null) ReminderStore.get().remove(r.id);
                }
            }
        }
    }

    /** 現在の時間割と不整合な 自動警告を掃除 */
    private void sweepMismatchedAutoAndExamForCurrentTimetable() {
        var ttIds = (timetable == null || timetable.slots == null)
                ? java.util.Set.<Integer>of()
                : new java.util.HashSet<>(timetable.slots.values());

        var rng = ScheduleStore.get().getRange(timetable==null?null:timetable.id);
        LocalDate start = (rng!=null && rng.startIso!=null) ? LocalDate.parse(rng.startIso).minusWeeks(2) : LocalDate.now().minusMonths(6);
        LocalDate end   = (rng!=null && rng.endIso!=null) ? LocalDate.parse(rng.endIso).plusWeeks(2)  : LocalDate.now().plusMonths(6);

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            var list = new ArrayList<>(ReminderStore.get().on(d));
            for (var r : list) {
                boolean isAutoWarn = r.autoGenerated &&
                        (r.priority == Priority.WARNING_LOW
                      || r.priority == Priority.WARNING_MED
                      || r.priority == Priority.WARNING_HIGH);
                boolean isExam = (r.priority == Priority.EXAM);

                boolean invalidNth    = (r.nth == null || r.nth <= 0);
                boolean invalidCourse = (r.courseId != null && !ttIds.contains(r.courseId));

                if ((isAutoWarn && (invalidNth || invalidCourse))
                 || (isExam     && (invalidNth || invalidCourse))) {
                    if (r.id != null) ReminderStore.get().remove(r.id);
                }
            }
        }
    }
    
    /** 自動警告の同一性を表すキー */
    private String autoKey(LocalDate date, Integer cid, Integer nth, String title) {
        return (date==null?"":date.toString()) + "|" + (cid==null?"-":cid) + "|" + (nth==null?"-":nth) + "|" + (title==null?"":title);
    }
    private String autoKey(ReminderStore.Reminder r) {
        LocalDate d = (r.dateIso==null? null : LocalDate.parse(r.dateIso));
        return autoKey(d, r.courseId, r.nth, r.title);
    }

    private void addOrTrackAutoWarning(LocalDate date, Integer cid, Integer nth,
                                       String title, String detail,
                                       Priority p, String colorHex,
                                       java.util.Set<String> expectedKeys) {
        String k = autoKey(date, cid, nth, title);
        if (expectedKeys != null) expectedKeys.add(k);
        boolean exist = ReminderStore.get().on(date).stream().anyMatch(x ->
            x.autoGenerated
         && (x.priority==Priority.WARNING_LOW || x.priority==Priority.WARNING_MED || x.priority==Priority.WARNING_HIGH)
         && Objects.equals(x.courseId, cid)
         && Objects.equals(x.nth, nth)
         && Objects.equals(x.title, title)
        );
        if (!exist) {
            var r = new ReminderStore.Reminder();
            r.dateIso = date.toString(); r.courseId = cid; r.nth = nth;
            r.title = title; r.detail = detail; r.priority = p;
            r.colorHex = colorHex; r.autoGenerated = true;
            ReminderStore.get().add(r);
        }
    }

    private void reconcileAutoWarnings(LocalDate date, java.util.Set<String> expectedKeys) {
        var list = new ArrayList<>(ReminderStore.get().on(date));
        for (var r : list) {
            boolean isAutoWarn = r.autoGenerated &&
                    (r.priority==Priority.WARNING_LOW
                  || r.priority==Priority.WARNING_MED
                  || r.priority==Priority.WARNING_HIGH);
            if (isAutoWarn) {
                String k = autoKey(r);
                if (expectedKeys==null || !expectedKeys.contains(k)) {
                    if (r.id != null) ReminderStore.get().remove(r.id);
                }
            }
        }
    }

    private void openFileDirect(Path p) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(p.toFile());
            } else {
                String os = System.getProperty("os.name","").toLowerCase(Locale.ROOT);
                if (os.contains("mac")) new ProcessBuilder("open", p.toString()).start();
                else if (os.contains("win")) new ProcessBuilder("cmd","/c","start","","", p.toString()).start();
                else new ProcessBuilder("xdg-open", p.toString()).start();
            }
        } catch (Exception ex) {
            Dialogs.error("エラー", "ファイルを開けませんでした: "+ex.getMessage());
        }
    }

    private ScrollPane wrapListArea(Region content, double prefHeight) {
        var sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setPrefViewportHeight(prefHeight);
        sp.setMaxHeight(prefHeight);
        return sp;
    }
    
    /** 現在の時間割と不整合な状態（AttendanceStore）を掃除 */
    private void sweepMismatchedAttendanceForCurrentTimetable() {
        if (timetable == null || timetable.slots == null) return;

        refreshCourseCache();

        var ttIds = timetable.slots.values().stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        var rng = ScheduleStore.get().getRange(timetable.id);
        LocalDate start = null, end = null;
        if (rng != null) {
            try { if (rng.startIso != null) start = LocalDate.parse(rng.startIso); } catch (Exception ignore) {}
            try { if (rng.endIso   != null) end   = LocalDate.parse(rng.endIso);   } catch (Exception ignore) {}
        }

        var all = AttendanceStore.get().listAll();

        for (var r : all) {
            if (r.courseId == null || !ttIds.contains(r.courseId)) {
                AttendanceStore.get().removeWhere(x ->
                    Objects.equals(x.courseId, r.courseId) && Objects.equals(x.dateIso, r.dateIso)
                );
                continue;
            }

            LocalDate d = null;
            try { d = LocalDate.parse(r.dateIso); } catch (Exception ex) {
                AttendanceStore.get().removeWhere(x ->
                    Objects.equals(x.courseId, r.courseId) && Objects.equals(x.dateIso, r.dateIso)
                );
                continue;
            }

            Course c = courseById.get(r.courseId);
            if (c == null) {
                AttendanceStore.get().removeWhere(x ->
                    Objects.equals(x.courseId, r.courseId) && Objects.equals(x.dateIso, r.dateIso)
                );
                continue;
            }

            if (start != null && end != null && (d.isBefore(start) || d.isAfter(end))) {
                AttendanceStore.get().removeWhere(x ->
                    Objects.equals(x.courseId, r.courseId) && Objects.equals(x.dateIso, r.dateIso)
                );
                continue;
            }

            String day = safe(c.day);
            DayOfWeek real = d.getDayOfWeek();
            boolean mismatch;
            if ("OTHER".equals(day) || "SUN".equals(day)) {
                mismatch = (real != DayOfWeek.SUNDAY);
            } else {
                DayOfWeek courseDow = dowOfCourse(c);
                mismatch = (courseDow != null && courseDow != real);
            }
            if (mismatch) {
                AttendanceStore.get().removeWhere(x ->
                    Objects.equals(x.courseId, r.courseId) && Objects.equals(x.dateIso, r.dateIso)
                );
            }
        }
    }
    
    private void requestRefresh() {
        if (refreshPending) return;
        refreshPending = true;

        captureScrollAnchor();

        Platform.runLater(() -> {
            try {
                isAutoAdjusting = true;
                suppressAutoScrollOnce = true;

                refreshCourseCache();
                sweepMismatchedAttendanceForCurrentTimetable();
                sweepMismatchedAutoAndExamForCurrentTimetable();
                TodoReminderLinker.fullResync();

                ensureUndatedAssignmentTodos();

                renderSchedule();
                renderTodo();

                restoreScrollAnchor();

            } finally {
                Platform.runLater(() -> {
                    isAutoAdjusting = false;
                    refreshPending = false;
                });
            }
        });
    }

    private boolean isArchived(Course c) {
        if (c == null || workspaceDir == null) return false;
        Path p = workspaceDir.resolve(FileSaveOps.resolveCourseFolderName(c));
        return !Files.exists(p);
    }
    
    private boolean isArchivedCourseId(Integer courseId) {
        if (courseId == null) return false;
        Course c = courseById.get(courseId);
        return c == null || isArchived(c);
    }

    private void hookAutoRefresh(Dialog<?> dlg) {
        dlg.setOnHidden(ev -> requestRefresh());
    }
    private void hookAutoRefresh(Stage st) {
        st.setOnHidden(ev -> requestRefresh());
    }
    
    private void captureScrollAnchor() {
        if (minLoadedDate == null || maxLoadedDate == null) return;

        double contentH = scheduleScroll.getContent().getBoundsInLocal().getHeight();
        double viewportH = scheduleScroll.getViewportBounds().getHeight();
        double v = scheduleScroll.getVvalue();
        double yOffset = v * Math.max(0, contentH - viewportH);
        double headerH = headerGrid.getBoundsInLocal().getHeight();
        double yInDays = Math.max(0, yOffset - headerH);

        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(minLoadedDate, maxLoadedDate) + 1;
        if (totalDays <= 0) return;

        int idx = (int) Math.floor(yInDays / ROW_HEIGHT);
        if (idx < 0) idx = 0;
        if (idx >= totalDays) idx = (int) totalDays - 1;

        preservedAnchorDate  = minLoadedDate.plusDays(idx);
        double offsetInRow   = yInDays - idx * ROW_HEIGHT;
        preservedAnchorRatio = Math.max(0, Math.min(1, offsetInRow / ROW_HEIGHT));
    }

    private void restoreScrollAnchor() {
        if (preservedAnchorDate == null) return;
        LocalDate anchor = preservedAnchorDate;
        double ratio     = preservedAnchorRatio;
        preservedAnchorDate = null;

        ensureDateLoaded(anchor);

        Platform.runLater(() -> {
            isAutoAdjusting = true;
            try {
                double contentH = scheduleScroll.getContent().getBoundsInLocal().getHeight();
                double viewportH = scheduleScroll.getViewportBounds().getHeight();
                double headerH = headerGrid.getBoundsInLocal().getHeight();

                long daysFromMin = java.time.temporal.ChronoUnit.DAYS.between(minLoadedDate, anchor);
                if (daysFromMin < 0) daysFromMin = 0;

                double y = headerH + daysFromMin * ROW_HEIGHT + ratio * ROW_HEIGHT;
                double v = (contentH <= viewportH) ? 0 : (y / (contentH - viewportH));
                scheduleScroll.setVvalue(Math.max(0, Math.min(1, v)));
            } finally {
                Platform.runLater(() -> isAutoAdjusting = false);
            }
        });
    }

    private void openPanel2SavePopupFixed(Course c, int nth, java.util.List<app.univtool.ui.panel2.ClassifyDialog.Kind> allowedKinds) {
        try {
            var pane = new app.univtool.ui.panel2.PanelFileSave(
                    c, nth,
                    /*lockCourse*/ true,
                    /*lockNth*/   true,
                    allowedKinds
            );
            var st = new Stage();
            hookAutoRefresh(st);
            st.setTitle("ファイルを保存 - " + (c==null?"":c.name) + " 第" + nth + "回");
            st.initOwner(getScene()==null?null:getScene().getWindow());
            st.initModality(Modality.WINDOW_MODAL);
            st.setScene(new Scene(pane, 900, 600));
            st.show();
        } catch (Exception ex) {
            Dialogs.error("エラー", "パネル２を開けませんでした: " + ex.getMessage());
        }
    }
    
    private void openEditCustomReminderDialog(ReminderStore.Reminder r) {
        var dlg = new Dialog<ButtonType>();
        hookAutoRefresh(dlg);
        dlg.setTitle("リマインダーを編集");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.initOwner(getScene()==null?null:getScene().getWindow());
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.setResizable(true);

        LocalDate initDate;
        try { initDate = (r.dateIso==null? LocalDate.now() : LocalDate.parse(r.dateIso)); }
        catch (Exception ex) { initDate = LocalDate.now(); }

        var dp       = new DatePicker(initDate);
        var tfTitle  = new TextField(safe(r.title));
        var taDetail = new TextArea(safe(r.detail)); taDetail.setPrefRowCount(4);

        var cbColor     = new CheckBox("文字色を設定する");
        var colorPicker = new ColorPicker();
        if (r.colorHex != null && !r.colorHex.isBlank()) {
            try { colorPicker.setValue(Color.web(r.colorHex)); cbColor.setSelected(true); }
            catch (Exception ignore) { /* 色文字列不正は無視 */ }
        } else {
            cbColor.setSelected(false);
            colorPicker.setDisable(true);
        }
        cbColor.selectedProperty().addListener((o,a,b)-> colorPicker.setDisable(!b));

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(10));
        int row=0;
        g.addRow(row++, new Label("日付"),   dp);
        g.addRow(row++, new Label("タイトル"), tfTitle);
        g.addRow(row++, new Label("内容"),     taDetail);
        g.add(cbColor, 0, row); g.add(colorPicker, 1, row++);

        dlg.getDialogPane().setContent(g);

        if (dlg.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            r.dateIso  = (dp.getValue()==null ? r.dateIso : dp.getValue().toString());
            r.title    = tfTitle.getText();
            r.detail   = taDetail.getText();
            r.colorHex = cbColor.isSelected() ? toHex(colorPicker.getValue()) : null;

            ReminderStore.get().save();
            TodoReminderLinker.onReminderEdited(r);
            requestRefresh();
        }
    }
    
    private Path renameFileByClassificationLabel(Path oldPath, String fromLabel, String toLabel) {
        try {
            String fileName = oldPath.getFileName().toString();
            if (!fileName.contains(fromLabel)) return oldPath;

            Path parent = oldPath.getParent();
            String newName = fileName.replace(fromLabel, toLabel);
            Path candidate = parent.resolve(newName);
            Path target = FileSaveOps.ensureUnique(parent, candidate.getFileName().toString());

            Files.move(oldPath, target);
            return target;
        } catch (Exception ex) {

            return oldPath;
        }
    }

    private void tryUpdateSavedIndexPath(Path oldPath, Path newPath) {
        try {
            SavedFileIndex.get().updatePath(oldPath, newPath);
        } catch (Exception ignore) {
        }
    }

    private static int priorityRank(Priority p) {
        return switch (p) {
            case EXAM          -> 0;
            case WARNING_HIGH  -> 1;
            case WARNING_MED   -> 2;
            case WARNING_LOW   -> 3;
            case CUSTOM        -> 4;
            case HOLIDAY       -> 5;
            default            -> 99;
        };
    }
    
 // 「講義詳細」での中間/期末の有効可否を Course から推測
    private boolean courseAllowsExam(Course c) {
        if (c == null) return false;
        Boolean mid = readCourseBoolean(c,
            "hasMidtermExam", "midtermExam", "midtermEnabled", "hasMidterm", "midExam");
        Boolean fin = readCourseBoolean(c,
            "hasFinalExam", "finalExam", "finalEnabled", "hasFinal", "finExam");
        if (mid == null && fin == null) return true;
        return Boolean.TRUE.equals(mid) || Boolean.TRUE.equals(fin);
    }
    private Boolean readCourseBoolean(Object obj, String... names) {
        for (String n : names) {
            try {
                var f = obj.getClass().getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof Boolean) return (Boolean) v;
            } catch (Exception ignore) {}
        }
        for (String n : names) {
            String base = n.substring(0,1).toUpperCase(Locale.ROOT) + n.substring(1);
            String[] methods = new String[] {"is"+base, "get"+base};
            for (String m : methods) {
                try {
                    var md = obj.getClass().getMethod(m);
                    Object v = md.invoke(obj);
                    if (v instanceof Boolean) return (Boolean) v;
                } catch (Exception ignore) {}
            }
        }
        return null;
    }

    private void openEditExamReminderDialog(ReminderStore.Reminder r) {
        var dlg = new Dialog<ButtonType>();
        hookAutoRefresh(dlg);
        dlg.setTitle("試験日を編集");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.initOwner(getScene()==null?null:getScene().getWindow());
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.setResizable(true);

        LocalDate initDate;
        try { initDate = (r.dateIso==null? LocalDate.now() : LocalDate.parse(r.dateIso)); }
        catch (Exception ex) { initDate = LocalDate.now(); }

        var dp = new DatePicker(initDate);
        var fixed = new Label("※タイトルは『試験日』、優先度は最優先（赤）で固定です。");
        VBox box = new VBox(8, new Label("日付"), dp, fixed);
        box.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(box);

        if (dlg.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            r.dateIso  = (dp.getValue()==null ? r.dateIso : dp.getValue().toString());
            r.title    = "試験日";
            r.detail   = "試験日です。";
            r.priority = Priority.EXAM;
            r.colorHex = "#d32f2f";
            ReminderStore.get().save();
            TodoReminderLinker.onReminderEdited(r);
            requestRefresh();
        }
    }
    
    private void ensureUndatedAssignmentTodos() {
        refreshCourseCache();
        var undatedMap = new java.util.HashMap<String, java.util.List<SavedFileIndex.Entry>>();
        for (var e : SavedFileIndex.get().list()) {
            if (e.deadline != null && !e.deadline.isBlank()) continue;
            if (!"課題_未提出".equals(e.kind) && !"課題_テキスト".equals(e.kind)) continue;
            if (e.courseId != null && isArchivedCourseId(e.courseId)) continue;

            String key = (e.courseId==null? "-" : e.courseId) + "|" + (e.nth==null? "-" : e.nth);
            undatedMap.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(e);
        }

        if (undatedMap.isEmpty()) return;

        for (var ent : undatedMap.entrySet()) {
            String[] sp = ent.getKey().split("\\|", -1);
            Integer cid = "-".equals(sp[0]) ? null : Integer.valueOf(sp[0]);
            Integer nth = "-".equals(sp[1]) ? null : Integer.valueOf(sp[1]);

            var opt = TodoStore.get().findByCourseNth(cid, nth, "課題")
                    .or(() -> TodoStore.get().listAll().stream()
                        .filter(t -> java.util.Objects.equals(t.courseId, cid))
                        .filter(t -> java.util.Objects.equals(t.nth, nth))
                        .filter(t -> "課題".equals((t.title==null?"":t.title).trim()))
                        .findFirst()
                    );

            TodoStore.TodoItem t = opt.orElseGet(() -> {
                var nt = new TodoStore.TodoItem();
                nt.courseId = cid;
                nt.nth      = nth==null? 1 : nth;
                nt.title    = "課題";
                nt.detail   = "";
                nt.dueIso   = null;
                return TodoReminderLinker.addAndLink(nt);
            });

            for (var f : ent.getValue()) {
                if (t.filePaths == null) t.filePaths = new java.util.ArrayList<>();
                if (f.path != null && !t.filePaths.contains(f.path)) {
                    t.filePaths.add(f.path);
                }
            }
            TodoReminderLinker.updateAndLink(t);
        }
    }

    private LocalDate parseDue(String iso) {
        try { return (iso==null||iso.isBlank())? null : LocalDate.parse(iso); }
        catch(Exception ex) { return null; }
    }

    private boolean isAssignmentTodo(TodoStore.TodoItem t) {
        String ttl = (t.title==null? "" : t.title).trim();
        return "課題".equals(ttl);
    }

    private void paintTodoHeaderBackground(TitledPane tp, String bgColor) {
        if (tp == null) return;

        Runnable apply = () -> {
            Node header = tp.lookup(".title");
            if (header == null) header = tp.lookup(".header");
            if (header instanceof Region r) {
                boolean expanded = tp.isExpanded();
                String radius = expanded ? "6 6 0 0" : "6";
                String base = (r.getStyle() == null ? "" : r.getStyle());
                r.setStyle(base
                    + (base.endsWith(";") ? "" : ";")
                    + "-fx-background-color:" + bgColor + ";"
                    + "-fx-background-insets: 0;"
                    + "-fx-background-radius:" + radius + ";"
                );
            }
        };

        if (tp.getScene() == null) {
            tp.sceneProperty().addListener((o, ov, nv) -> {
                if (nv != null) Platform.runLater(apply);
            });
        } else {
            Platform.runLater(apply);
        }

        tp.expandedProperty().addListener((o, ov, nv) -> Platform.runLater(apply));
    }
    
        public void jumpToReminder(ReminderStore.Reminder r) {
            if (r == null || r.dateIso == null || r.dateIso.isBlank()) return;
            try {
                this.highlightRemId   = r.id;
                this.highlightRemDate = LocalDate.parse(r.dateIso);
                this.jumpedDate       = this.highlightRemDate;
                requestRefresh();
                Platform.runLater(() -> {
                    ensureDateLoaded(this.highlightRemDate);
                    scrollToDate(this.highlightRemDate);
                    refreshDateCellStyles();
                });
            } catch (Exception ignore) {}
        }
    
        public void jumpToReminder(String reminderId) {
            var r = ReminderStore.get().find(reminderId);
            if (r != null) jumpToReminder(r);
        }   
        
        private static final class JpHolidays {
            private static final HttpClient CLIENT = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            private static final Map<Integer, Map<LocalDate, String>> YEAR_CACHE = new HashMap<>();

            static synchronized String getName(LocalDate date) {
                int y = date.getYear();
                Map<LocalDate, String> map = YEAR_CACHE.get(y);
                if (map == null) {
                    map = fetchYear(y);
                    YEAR_CACHE.put(y, map);
                }
                return map.get(date);
            }

            private static Map<LocalDate, String> fetchYear(int year) {
                String[] urls = new String[] {
                    "https://holidays-jp.github.io/api/v1/" + year + "/date.json",
                    "https://holidays-jp.github.io/api/v1/date.json"
                };
                for (String url : urls) {
                    try {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                                .timeout(Duration.ofSeconds(8))
                                .header("User-Agent", "UnivTool/1.0 (+holidays)")
                                .GET().build();
                        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                        if (res.statusCode() != 200) continue;

                        JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
                        Map<LocalDate, String> map = new HashMap<>();
                        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                            try {
                                LocalDate d = LocalDate.parse(e.getKey());
                                String name = e.getValue().getAsString();
                                if (d.getYear() == year) map.put(d, name);
                            } catch (Exception ignore) {}
                        }
                        if (!map.isEmpty()) return map;
                    } catch (Exception ignore) {
                    }
                }
                return java.util.Collections.emptyMap();
            }
        }
    
}
