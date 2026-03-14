package app.univtool.ui.credit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import app.univtool.core.CreditStore;
import app.univtool.core.KindPresetStore;
import app.univtool.util.Dialogs;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;




public final class CreditDashboardWindow extends Stage {

    private final CreditStore store = CreditStore.get();

    private ComboBox<CreditStore.BorderSet> cbSet;
    private TableView<RowReq> tableReq;
    private TableView<RowProg> tableProg;
    private TableView<RowWhatIf> tableWhat;
    private ComboBox<String> cbCatAdd;
    private TabPane tabs;

    private static final String RED_STYLE = "-fx-text-fill: #c62828; -fx-font-weight: bold;";

    public CreditDashboardWindow() {
        setTitle("単位ダッシュボード");
        initModality(Modality.WINDOW_MODAL);

        this.tabs = new TabPane(
            new Tab("ボーダーラインを選択", buildBorderTab()),
            new Tab("取得単位の追加", buildProgressTab()),
            new Tab("単位取得状況", buildWhatIfTab())
        );


        tabs.getTabs().forEach(t -> t.setClosable(false));

        setScene(new Scene(tabs, 900, 620));
        refreshAll();
    }

    public static void open(Stage owner) {
        CreditDashboardWindow w = new CreditDashboardWindow();
        if (owner != null) w.initOwner(owner);
        w.show();
    }

    // 単位取得状況タブを選択して開く
    public static void openWhatIf(Stage owner) {
        CreditDashboardWindow w = new CreditDashboardWindow();
        if (owner != null) w.initOwner(owner);
        w.show();
        w.selectWhatIfTab();
    }


    // ボーダーライン
    private BorderPane buildBorderTab() {
        cbSet = new ComboBox<>();
        cbSet.setPrefWidth(300);
        cbSet.setConverter(new StringConverter<>() {
            @Override public String toString(CreditStore.BorderSet s) { return s==null? "（なし）" : s.name; }
            @Override public CreditStore.BorderSet fromString(String s) { return null; }
        });
        cbSet.valueProperty().addListener((o,ov,nv)-> {
            refreshReqTable();
            refreshProgressTable();
            refreshWhatIfTable();
            if (cbCatAdd != null) refreshCatPulldown(cbCatAdd);
        });

        Button btnNew = new Button("新規セット");
        btnNew.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog("新しいボーダー");
            d.setHeaderText("ボーダーラインセット名を入力");
            d.showAndWait().ifPresent(name -> {
                CreditStore.BorderSet s = store.createSet(name, null);
                store.setActiveSet(s.id);
                refreshAll();
                cbSet.getSelectionModel().select(s);
            });
        });


        Button btnDelete = new Button("削除");
        btnDelete.setOnAction(e -> {
            var sel = cbSet.getValue();
            if (sel==null) return;
            if (!Dialogs.confirm("確認", "セット「"+sel.name+"」を削除します。よろしいですか？")) return;
            store.deleteSet(sel.id);
            refreshAll();
        });

        Button btnImportKinds = new Button("科目区分から読み込み");
        btnImportKinds.setOnAction(e -> {
            var sel = cbSet.getValue(); 
            if (sel==null) return;

            var presets = KindPresetStore.loadAll();
            if (presets == null || presets.isEmpty()) {
                Dialogs.error("エラー", "科目区分プリセットが見つかりません。");
                return;
            }

            List<String> names = presets.stream()
                    .map(p -> (p.name==null || p.name.isBlank()) ? "(名称未設定)" : p.name)
                    .collect(Collectors.toList());

            ChoiceDialog<String> ch = new ChoiceDialog<>(names.get(0), names);
            ch.setHeaderText("読み込む『科目区分』プリセットを選択");
            ch.setContentText("プリセット：");
            var opt = ch.showAndWait();
            if (opt.isEmpty()) return;

            int idx = names.indexOf(opt.get());
            var picked = presets.get(Math.max(0, idx));

            List<String> kinds = (picked.kinds==null? List.<String>of() : picked.kinds).stream()
                    .filter(s2 -> s2!=null && !s2.isBlank())
                    .map(String::trim).distinct().sorted()
                    .collect(Collectors.toList());
            for (String k : kinds) {
                double keep = sel.required.getOrDefault(k, 0.0);
                store.upsertRequired(sel.id, k, keep);
            }
            refreshReqTable();
        });

        HBox top = new HBox(8, new Label("セット:"), cbSet, btnNew, btnDelete, btnImportKinds);
        top.setPadding(new Insets(8));
        HBox.setHgrow(cbSet, Priority.ALWAYS);

        tableReq = new TableView<>();
        tableReq.setEditable(true);
        TableColumn<RowReq, String> colCat = new TableColumn<>("カテゴリ");
        colCat.setCellValueFactory(c -> c.getValue().category);
        colCat.setCellFactory(TextFieldTableCell.forTableColumn());
        colCat.setOnEditCommit(ev -> {
            RowReq r = ev.getRowValue();
            String newCat = ev.getNewValue()==null? "" : ev.getNewValue().trim();
            if (newCat.isBlank()) return;
            var s = cbSet.getValue(); if (s==null) return;
            store.removeCategory(s.id, r.category.get());
            store.upsertRequired(s.id, newCat, r.required.get());
            refreshReqTable();
        });
        colCat.setPrefWidth(300);

        TableColumn<RowReq, Double> colReq = new TableColumn<>("所要単位");
        colReq.setCellValueFactory(c -> c.getValue().required.asObject());
        colReq.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter1()));
        colReq.setOnEditCommit(ev -> {
            RowReq r = ev.getRowValue();
            var s = cbSet.getValue(); if (s==null) return;
            store.upsertRequired(s.id, r.category.get(), ev.getNewValue()==null? 0.0 : ev.getNewValue());
            refreshReqTable();
        });

        Button btnAddRow = new Button("行を追加");
        btnAddRow.setOnAction(e -> {
            var s = cbSet.getValue(); if (s==null) return;
            TextInputDialog d = new TextInputDialog();
            d.setHeaderText("カテゴリ名を入力");
            d.showAndWait().ifPresent(cat -> { if (!cat.isBlank()) { store.upsertRequired(s.id, cat.trim(), 0.0); refreshReqTable(); }});
        });

        Button btnSetActive = new Button("このセットをアクティブにする");
        btnSetActive.setOnAction(e -> {
            var s = cbSet.getValue();
            if (s!=null) {
                store.setActiveSet(s.id);
                Dialogs.info("設定","アクティブセットを切り替えました。");
                refreshWhatIfTable();
                refreshProgressTable();
                if (cbCatAdd != null) refreshCatPulldown(cbCatAdd);
            }
        });

        HBox bottom = new HBox(8, btnAddRow, btnSetActive);
        bottom.setPadding(new Insets(8));

        tableReq.getColumns().setAll(java.util.List.of(colCat, colReq));
        tableReq.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        BorderPane pane = new BorderPane();
        pane.setTop(top);
        pane.setCenter(tableReq);
        pane.setBottom(bottom);
        return pane;
    }

    //  現在の取得
    private BorderPane buildProgressTab() {
        tableProg = new TableView<>();
        tableProg.setEditable(true);

        TableColumn<RowProg, String> cCat = new TableColumn<>("カテゴリ");
        cCat.setCellValueFactory(c -> c.getValue().category);
        cCat.setCellFactory(TextFieldTableCell.forTableColumn());
        cCat.setOnEditCommit(ev -> {
            RowProg r = ev.getRowValue();
            String newCat = ev.getNewValue()==null? "" : ev.getNewValue().trim();
            if (newCat.isBlank()) return;
            double v = r.value.get();
            store.setProgress(newCat, v);
            refreshProgressTable();
        });
        cCat.setPrefWidth(300);

        TableColumn<RowProg, Double> cVal = new TableColumn<>("取得済み単位");
        cVal.setCellValueFactory(c -> c.getValue().value.asObject());
        cVal.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter1()));
        cVal.setOnEditCommit(ev -> {
            RowProg r = ev.getRowValue();
            store.setProgress(r.category.get(), ev.getNewValue()==null? 0.0 : ev.getNewValue());
            refreshProgressTable();
        });

     // アクティブセットのカテゴリから選ぶ
        cbCatAdd = new ComboBox<>();
        cbCatAdd.setPrefWidth(260);

        TextField tfValAdd = new TextField();
        tfValAdd.setPromptText("単位数");
        tfValAdd.setPrefWidth(120);

        Button btnAdd = new Button("追加");
        btnAdd.setOnAction(e -> {
            String cat = cbCatAdd.getValue();
            if (cat == null || cat.isBlank()) { Dialogs.error("エラー","カテゴリを選択してください"); return; }
            double v;
            try { v = Double.parseDouble(tfValAdd.getText().trim()); }
            catch (Exception ex) { Dialogs.error("エラー","単位数が不正です"); return; }
            if (v < 0) { Dialogs.error("エラー","単位数は0以上で入力してください"); return; }

            double cur = store.getProgress().getOrDefault(cat, 0.0);
            store.setProgress(cat, cur + v);
            tfValAdd.clear();
            refreshProgressTable();
        });

        Button btnRecalc = new Button("再読み込み");
        btnRecalc.setOnAction(e -> {
            refreshProgressTable();
            refreshCatPulldown(cbCatAdd);
        });

        refreshCatPulldown(cbCatAdd);

        HBox top = new HBox(8, new Label("カテゴリ"), cbCatAdd, new Label("＋"), tfValAdd, btnAdd, btnRecalc);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(8));
 
        tableProg.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tableProg.getColumns().setAll(java.util.List.of(cCat, cVal));

        tableProg.setRowFactory(tv -> {
            TableRow<RowProg> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem miDel = new MenuItem("削除");
            miDel.setOnAction(ev -> {
                RowProg r = row.getItem();
                if (r == null) return;
                if (!Dialogs.confirm("確認", "「" + r.category.get() + "」を削除しますか？")) return;
                store.removeProgress(r.category.get());
                refreshProgressTable();
                refreshCatPulldown(cbCatAdd);
            });
            menu.getItems().add(miDel);
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu)null).otherwise(menu));
            return row;
        });


        BorderPane p = new BorderPane();
        p.setTop(top);
        p.setCenter(tableProg);
        return p;
    }

    //  見込み
    private BorderPane buildWhatIfTab() {
        tableWhat = new TableView<>();
        TableColumn<RowWhatIf, String> cCat = new TableColumn<>("科目区分/カテゴリ");
        cCat.setCellValueFactory(c -> c.getValue().category);

        TableColumn<RowWhatIf, Double> cReq = new TableColumn<>("所要単位ボーダーライン");
        cReq.setCellValueFactory(c -> c.getValue().required.asObject());

        TableColumn<RowWhatIf, Double> cCur = new TableColumn<>("現在の単位");
        cCur.setCellValueFactory(c -> c.getValue().current.asObject());

        TableColumn<RowWhatIf, Double> cPln = new TableColumn<>("時間割の単位");
        cPln.setCellValueFactory(c -> c.getValue().planned.asObject());

        TableColumn<RowWhatIf, Double> cTot = new TableColumn<>("合計");
        cTot.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(
                c.getValue().current.get() + c.getValue().planned.get()
        ).asObject());

        TableColumn<RowWhatIf, Double> cDef = new TableColumn<>("不足");
        cDef.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(
                Math.max(0.0, c.getValue().required.get() - (c.getValue().current.get() + c.getValue().planned.get()))
        ).asObject());
        cDef.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else { setText(String.format(Locale.ROOT, "%.1f", item)); setStyle(RED_STYLE); }

            }
        });

        tableWhat.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tableWhat.getColumns().setAll(java.util.List.of(cCat, cReq, cCur, cPln, cTot, cDef));

        Button btnRefresh = new Button("再計算");
        btnRefresh.setOnAction(e -> refreshWhatIfTable());
        HBox top = new HBox(8, new Label("所要単位と現在＋時間割の見込みを比較します（不足は赤字）。"), btnRefresh);

        top.setPadding(new Insets(8));

        BorderPane p = new BorderPane();
        p.setTop(top);
        p.setCenter(tableWhat);
        return p;
    }

    //  履修提案
    private void refreshAll() {
        var sets = store.listSets();
        cbSet.getItems().setAll(sets);
        if (!sets.isEmpty()) {
            String active = store.getActiveSetId();
            var pick = sets.stream().filter(s -> Objects.equals(s.id, active)).findFirst().orElse(sets.get(0));
            cbSet.getSelectionModel().select(pick);
        }
        refreshReqTable();
        refreshProgressTable();
        refreshWhatIfTable();
        if (cbCatAdd != null) refreshCatPulldown(cbCatAdd);
    }


    private void refreshReqTable() {
        var s = cbSet.getValue();
        List<RowReq> rows = new ArrayList<>();
        if (s != null && s.required != null) {
            for (var e : s.required.entrySet()) rows.add(new RowReq(e.getKey(), e.getValue()));
        }
        rows.sort(Comparator.comparing(r -> r.category.get()));
        tableReq.getItems().setAll(rows);
    }
    private void refreshProgressTable() {
        var p = store.getProgress();
        List<RowProg> rows = new ArrayList<>();
        for (var e : p.entrySet()) rows.add(new RowProg(e.getKey(), e.getValue()));
        rows.sort(Comparator.comparing(r -> r.category.get()));
        tableProg.getItems().setAll(rows);
    }
    private void refreshWhatIfTable() {
        String sid = store.getActiveSetId();
        if (sid == null) { tableWhat.getItems().clear(); return; }
        var list = store.computeWhatIf(sid);
        List<RowWhatIf> rows = new ArrayList<>();
        for (var r : list) rows.add(new RowWhatIf(r.category, r.required, r.current, r.planned));
        tableWhat.getItems().setAll(rows);
    }

    private static final class RowReq {
        final javafx.beans.property.SimpleStringProperty category = new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleDoubleProperty required = new javafx.beans.property.SimpleDoubleProperty();
        RowReq(String c, double r) { category.set(c); required.set(r); }
    }
    private static final class RowProg {
        final javafx.beans.property.SimpleStringProperty category = new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleDoubleProperty value = new javafx.beans.property.SimpleDoubleProperty();
        RowProg(String c, double v) { category.set(c); value.set(v); }
    }
    private static final class RowWhatIf {
        final javafx.beans.property.SimpleStringProperty category = new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleDoubleProperty required = new javafx.beans.property.SimpleDoubleProperty();
        final javafx.beans.property.SimpleDoubleProperty current = new javafx.beans.property.SimpleDoubleProperty();
        final javafx.beans.property.SimpleDoubleProperty planned = new javafx.beans.property.SimpleDoubleProperty();
        RowWhatIf(String c, double r, double cu, double p) { category.set(c); required.set(r); current.set(cu); planned.set(p); }
    }

    private static final class DoubleStringConverter1 extends StringConverter<Double> {
        @Override public String toString(Double d) { return d==null? "" : String.format(Locale.ROOT,"%.1f", d); }
        @Override public Double fromString(String s) {
            if (s==null || s.isBlank()) return 0.0;
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 0.0; }
        }
    }

    private void refreshCatPulldown(ComboBox<String> cb) {
        var sid = store.getActiveSetId();
        List<String> cats = (sid==null)? List.of() : store.listCategories(sid);
        cb.getItems().setAll(cats);
        if (!cats.isEmpty()) cb.getSelectionModel().select(0);
    }
    
    private void selectWhatIfTab() {
        if (tabs == null) return;
        tabs.getTabs().stream()
            .filter(t -> "単位取得状況".equals(t.getText()))
            .findFirst()
            .ifPresent(t -> tabs.getSelectionModel().select(t));
    }


}
