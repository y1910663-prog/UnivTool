package app.univtool;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import app.univtool.core.Database;
import app.univtool.core.ReminderStore;
import app.univtool.core.SettingsStore;
import app.univtool.core.TodoStore;
import app.univtool.files.SavedFileIndex;
import app.univtool.model.Course;
import app.univtool.repo.CourseRepository;
import app.univtool.ui.credit.CreditDashboardWindow;
import app.univtool.ui.panel1.PanelCourses;
import app.univtool.ui.panel2.PanelFileSave;
import app.univtool.ui.panel3.PanelFileSearch;
import app.univtool.ui.panel4.PanelTimetable;
import app.univtool.ui.panel5.PanelScheduleTodo;
import app.univtool.ui.theme.Theme;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
    	app.univtool.portable.PortableMode.bootstrap(stage);
        Database.initSchema(); // SQLite

        var tabs = new TabPane();

     var tabCourses   = new Tab("講義を追加",  new PanelCourses());
     var tabFileSave  = new Tab("ファイル保存", new PanelFileSave());
     var tabFileSearch= new Tab("ファイル検索", new PanelFileSearch());
     var tabTimetable = new Tab("時間割",     new PanelTimetable());
     var tabSchedule  = new Tab("スケジュール", new PanelScheduleTodo());
     
     tabs.getTabs().addAll(tabCourses, tabFileSave, tabFileSearch, tabTimetable, tabSchedule);
     tabs.getTabs().forEach(t -> t.setClosable(false));
     
     var bellBtn = buildReminderBell(tabs, tabSchedule);
     var tabStack = new StackPane(tabs, bellBtn);
     
     bellBtn.setOnAction(e -> openTodayReminderList(stage, tabs, tabSchedule));
     
     StackPane.setAlignment(bellBtn, Pos.TOP_RIGHT);
     StackPane.setMargin(bellBtn, new Insets(6, 8, 0, 0)); 
     updateReminderBell(bellBtn);

     // 完全同期
     Map<Tab, Supplier<Node>> factories = new HashMap<>();
     factories.put(tabCourses,   PanelCourses::new);
     factories.put(tabFileSave,  PanelFileSave::new);
     factories.put(tabFileSearch,PanelFileSearch::new);
     factories.put(tabTimetable, PanelTimetable::new);
     factories.put(tabSchedule,  PanelScheduleTodo::new);

     tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
         if (newTab == null) return;
         Supplier<Node> f = factories.get(newTab);
         if (f != null) {
             newTab.setContent(f.get());
         }
         updateReminderBell(bellBtn);
     });
          

    
  // --- メニューバー---
     var menuBar = new MenuBar();
     var mMenu = new Menu("メニュー");
     var miOpenDashboard = new MenuItem("単位ダッシュボード…");
     miOpenDashboard.setOnAction(e -> CreditDashboardWindow.open(stage));

     // ポータブルモード切替
     var miTogglePortable = new MenuItem();
  {
      var s0 = SettingsStore.load();
      updatePortableMenuLabel(miTogglePortable, Boolean.TRUE.equals(s0.portableEnabled));
  }
     miTogglePortable.setOnAction(e -> {
         var s = SettingsStore.load();
         boolean isPortable = Boolean.TRUE.equals(s.portableEnabled);
         String msg = isPortable
                 ? "ポータブルモードを無効にし、通常モードへ戻しますか？\n（必要に応じてワークスペース親フォルダの位置も変更できます）"
                 : "ポータブルモードを有効にしますか？";
         var c1 = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
         c1.setTitle("ポータブルモード切替");
         c1.setHeaderText("ポータブルモードの切替");
         if (c1.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

         if (!isPortable) {
             var dc = new DirectoryChooser();
             dc.setTitle("ポータブル用のワークスペース親フォルダを選択");
             File chosen = dc.showDialog(stage);
             if (chosen == null) return;

             Path root = chosen.toPath();

             var c2 = new Alert(Alert.AlertType.CONFIRMATION,
                     "選択したフォルダ配下にワークスペース/アーカイブ/DBを配置しますか？\n" +
                     "（workspace / archive / data の各サブフォルダを使用）",
                     ButtonType.OK, ButtonType.CANCEL);
             c2.setTitle("親フォルダの切替");
             c2.setHeaderText("ワークスペース親フォルダの位置変更");
             boolean changeWs = (c2.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK);

             s.portableEnabled = true;
             s.portableRoot = root.toString();
             if (changeWs) {
                 s.workspaceDir = root.resolve("workspace").toString();
                 s.archiveDir   = root.resolve("archive").toString();
             }
             SettingsStore.save(s);

             Database.configureBaseDir(root.resolve("data"));
             try {
                 if (changeWs) {
                     Files.createDirectories(Path.of(s.workspaceDir));
                     Files.createDirectories(Path.of(s.archiveDir));
                 }
                 Files.createDirectories(root.resolve("data"));
             } catch (Exception ex) {
                 new Alert(Alert.AlertType.ERROR, "フォルダ作成に失敗しました:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
             }

             new Alert(Alert.AlertType.INFORMATION,
                     "ポータブルモードを有効にしました。\n必要に応じて再起動すると設定が確実に反映されます。",
                     ButtonType.OK).showAndWait();
             
             updatePortableMenuLabel(miTogglePortable, true);

         } else {
             var c2 = new Alert(Alert.AlertType.CONFIRMATION,
                     "通常モード向けにワークスペース親フォルダの位置も変更しますか？\n" +
                     "（変更する場合は、親フォルダを選択していただきます）",
                     ButtonType.OK, ButtonType.CANCEL);
             c2.setTitle("親フォルダの切替");
             c2.setHeaderText("ワークスペース親フォルダの位置変更");
             boolean changeWs = (c2.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK);

             if (changeWs) {
                 var dc = new DirectoryChooser();
                 dc.setTitle("通常モードのワークスペース親フォルダを選択");
                 File chosen = dc.showDialog(stage);
                 if (chosen != null) {
                     Path parent = chosen.toPath();
                     s.workspaceDir = parent.resolve("workspace").toString();
                     s.archiveDir   = parent.resolve("archive").toString();
                     try {
                         Files.createDirectories(Path.of(s.workspaceDir));
                         Files.createDirectories(Path.of(s.archiveDir));
                     } catch (Exception ex) {
                         new Alert(Alert.AlertType.ERROR, "フォルダ作成に失敗しました:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
                     }
                 }
             }
             s.portableEnabled = false;
             SettingsStore.save(s);
             Database.configureBaseDir(null);

             new Alert(Alert.AlertType.INFORMATION,
                     "ポータブルモードを無効にしました。\n必要に応じて再起動すると設定が確実に反映されます。",
                     ButtonType.OK).showAndWait();
         }
     });

     mMenu.getItems().addAll(miOpenDashboard, miTogglePortable);
     menuBar.getMenus().addAll(mMenu);

     var root = new VBox(menuBar, tabStack);
     root.getStyleClass().add("app-root");
     VBox.setVgrow(tabs, Priority.ALWAYS);

     stage.setTitle("UnivTool");
     var scene = new Scene(root, 1100, 700);
     Theme.attach(scene);
     stage.setScene(scene);
     stage.show();
     app.univtool.core.SaveHooks.register(() -> app.univtool.core.SettingsStore.save(app.univtool.core.SettingsStore.load()));


    }

    public static void main(String[] args) { launch(args); }
    private static void updatePortableMenuLabel(MenuItem item, boolean isPortable) {
        item.setText(isPortable ? "ポータブルモードを無効化…" : "ポータブルモードを有効化…");
    }
    
    private Button buildReminderBell(TabPane tabs, Tab tabSchedule) {
        var b = new Button("！");
        b.setFocusTraversable(false);
        b.setPickOnBounds(true);
        b.setTooltip(new Tooltip("今日のリマインダー"));
        b.setOnAction(ev -> openTodayRemindersDialog(tabs, tabSchedule));
        b.setStyle(bellStyle(false));
        return b;
    }

    private void updateReminderBell(Button b) {
        boolean has = !listTodayReminderTexts().isEmpty();
        b.setStyle(bellStyle(has));
        b.setOpacity( has ? 1.0 : 0.7);
        String tip = has ? "今日のリマインダー（あり）" : "今日のリマインダー（なし）";
        if (b.getTooltip()!=null) b.getTooltip().setText(tip);
    }

    private String bellStyle(boolean highlighted) {
        String bg = highlighted ? "#e53935" : "#bdbdbd";
        String fg = "#ffffff";
        return String.join("",
            "-fx-background-color:", bg, ";",
            "-fx-text-fill:", fg, ";",
            "-fx-font-weight:bold;",
            "-fx-background-radius: 9999;",
            "-fx-padding: 2 8 2 8;",
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 1);"
        );
    }

    /** 当日のリマインダー一覧 */
    private java.util.List<String> listTodayReminderTexts() {
        var list = new java.util.ArrayList<String>();
        var today = java.time.LocalDate.now();
        var todayIso = today.toString();

         var repo = new CourseRepository();
         java.util.Map<Integer, Course> idToCourse = new java.util.HashMap<>();
         for (Course cc : repo.findAll()) idToCourse.put(cc.id, cc);
        
         for (var r : ReminderStore.get().on(today)) {
             String t = (r.title==null||r.title.isBlank()) ? "(無題)" : r.title.trim();
             if (r.courseId != null) {
                 Course c = idToCourse.get(r.courseId);
                 if (c != null && c.name != null && !c.name.isBlank()) {
                     t = "【" + c.name + "】 " + t;
                 }
             }
             list.add(t);
         }
        var courseCache = new java.util.HashMap<Integer, String>();
        for (var e : SavedFileIndex.get().list()) {
            if (e.deadline == null || !todayIso.equals(e.deadline)) continue;
            if (!"課題_未提出".equals(e.kind) && !"課題_テキスト".equals(e.kind)) continue;
             String cname = null;
             if (e.courseId != null) {
                 Course c = idToCourse.get(e.courseId);
                 cname = (c==null ? null : c.name);
             }
            String nth = (e.nth==null? "" : " 第"+e.nth+"回");
            String t = (cname==null || cname.isBlank()) ? "提出期限" + nth : "【"+cname+"】 提出期限" + nth;
            list.add(t);
        }
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(list);
        return new java.util.ArrayList<>(set);
    }

    /** クリック時の一覧ダイアログ */
    private void openTodayRemindersDialog(TabPane tabs, Tab tabSchedule) {
        var items = listTodayReminderTexts();
        var dlg = new Dialog<ButtonType>();
        dlg.setTitle("今日のリマインダー");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
        var box = new VBox(8);
        box.setPadding(new Insets(12));
        if (items.isEmpty()) {
            box.getChildren().add(new Label("（本日のリマインダーはありません）"));
        } else {
            var lv = new ListView<String>();
            lv.getItems().setAll(items);
            lv.setPrefHeight(Math.min(320, Math.max(120, items.size()*32)));
            box.getChildren().addAll(new Label("本日のリマインダー"), lv, new Label("※ 詳細操作は「スケジュール」タブで行えます。"));
        }
        dlg.getDialogPane().setContent(box);
        dlg.showAndWait();

        if (!items.isEmpty()) {
            tabs.getSelectionModel().select(tabSchedule);
        }
        }
        
        /** ベルクリック時 */
    private void openTodayReminderList(Stage stage, TabPane tabs, Tab tabSchedule) {
    	    var list = collectTodaysReminders();
    	    var dlg = new javafx.scene.control.Dialog<ButtonType>();
    	    final String titleText = "今日のリマインダー";
    	    dlg.setTitle(titleText);
    	    
                dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        
                var box = new VBox(8);
                box.setPadding(new javafx.geometry.Insets(10));
        
                var idToCourse = new java.util.HashMap<Integer, Course>();
                for (Course c : new CourseRepository().findAll()) idToCourse.put(c.id, c);
        
                if (list.isEmpty()) {
                    box.getChildren().add(new javafx.scene.control.Label("（本日はリマインダーがありません）"));
                } else {
                    for (var r : list) {
                        String label = (r.title == null || r.title.isBlank()) ? "(無題)" : r.title;
                        if (r.courseId != null) {
                            Course c = idToCourse.get(r.courseId);
                            if (c != null && c.name != null && !c.name.isBlank()) {
                                label = "【" + c.name + "】 " + label;
                            }
                        }
                        var link = new Hyperlink(label);
                        final var rr = r; // effectively final
                        link.setOnAction(ev -> {
                            tabs.getSelectionModel().select(tabSchedule);
                            javafx.application.Platform.runLater(() -> {
                                var node = tabSchedule.getContent();
                                if (node instanceof PanelScheduleTodo pst) {
                                    pst.jumpToReminder(rr);
                                }
                            });
                            dlg.close();
                        });
                        box.getChildren().add(link);
                    }
                }
        
                var sp = new ScrollPane(box);
                sp.setFitToWidth(true);
                sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
                dlg.getDialogPane().setContent(sp);
                dlg.initOwner(stage);
                dlg.initModality(javafx.stage.Modality.WINDOW_MODAL);
                
                dlg.setResizable(true);
                {
                    javafx.scene.text.Text t = new javafx.scene.text.Text(titleText);
                    double titleW = t.getLayoutBounds().getWidth();
                    double minW = Math.max(360, titleW + 160);
                    dlg.getDialogPane().setMinWidth(minW);
                    dlg.getDialogPane().setPrefWidth(minW);
                    dlg.getDialogPane().setMinHeight(220);
                }
                
                
                dlg.show();
            }
        
            private java.util.List<ReminderStore.Reminder> collectTodaysReminders() {
                LocalDate today = LocalDate.now();
                var out = new ArrayList<ReminderStore.Reminder>();
        
                out.addAll(ReminderStore.get().on(today));
        
                if (today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                    var r = new ReminderStore.Reminder();
                    r.dateIso = today.toString();
                    r.title = "日曜日";
                    r.priority = ReminderStore.Priority.HOLIDAY;
                    r.autoGenerated = true;
                    out.add(r);
                }
        
                for (var e : SavedFileIndex.get().list()) {
                    if (e.deadline == null || !today.toString().equals(e.deadline)) continue;
                    if (!"課題_未提出".equals(e.kind) && !"課題_テキスト".equals(e.kind)) continue;
        
                    boolean done = TodoStore.get().listAll().stream()
                            .anyMatch(t -> java.util.Objects.equals(t.courseId, e.courseId)
                                        && java.util.Objects.equals(t.nth, e.nth)
                                        && java.util.Objects.equals(t.dueIso, e.deadline)
                                        && "課題".equals((t.title==null?"":t.title).trim())
                                        && t.done);
                    if (done) continue;
        
                    var r = new ReminderStore.Reminder();
                    r.dateIso = today.toString();
                    r.courseId = e.courseId;
                    r.nth = e.nth;
                    r.title = (e.nth == null ? "提出期限です" : "第" + e.nth + "回の提出期限です");
                    r.detail = "課題の提出期限です。";
                    r.priority = ReminderStore.Priority.WARNING_MED;
                    r.colorHex = "#ff8c00";
                    r.autoGenerated = true;
                    out.add(r);
                }
        
                out.sort(java.util.Comparator.comparingInt(a -> a.priority.ordinal()));
                return out;
            }
    }
     
