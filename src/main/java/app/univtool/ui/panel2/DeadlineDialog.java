package app.univtool.ui.panel2;

import java.time.LocalDate;
import java.time.YearMonth;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

/**
 * 提出期限の設定ダイアログ。
 */
public class DeadlineDialog extends Dialog<java.time.LocalDate> {

    public DeadlineDialog(LocalDate defaultDate){
        setTitle("提出期限の設定");
        var ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("キャンセル", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);

        LocalDate today = LocalDate.now();
        LocalDate initial = (defaultDate != null && !defaultDate.isBefore(today)) ? defaultDate : today;

        var rbCal = new RadioButton("カレンダーから選択");
        var rbMd  = new RadioButton("月日を選択");
        var tg = new ToggleGroup(); rbCal.setToggleGroup(tg); rbMd.setToggleGroup(tg);
        rbCal.setSelected(true);


     // カレンダー
        var dp = new DatePicker(initial);
        dp.setDayCellFactory(picker -> new javafx.scene.control.DateCell(){
            @Override
            public void updateItem(LocalDate item, boolean empty){
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setDisable(false);
                    setStyle("");
                    return;
                }

                boolean past = item.isBefore(java.time.LocalDate.now());
                setDisable(past);
                if (past) setStyle("-fx-opacity: 0.4;");
                else setStyle("");
            }
        });


        // 月日プルダウン
        var cbMonth = new ChoiceBox<Integer>();
        var cbDay   = new ChoiceBox<Integer>();

        // 月の選択肢
        for (int m = today.getMonthValue(); m <= 12; m++) cbMonth.getItems().add(m);
        cbMonth.getSelectionModel().select(Integer.valueOf(initial.getMonthValue()));

        // 日の選択肢
        java.util.function.Consumer<Integer> refreshDays = (month) -> {
            cbDay.getItems().clear();
            YearMonth ym = YearMonth.of(today.getYear(), month);
            int start = (month == today.getMonthValue()) ? today.getDayOfMonth() : 1;
            for (int d = start; d <= ym.lengthOfMonth(); d++) cbDay.getItems().add(d);
            if (!cbDay.getItems().isEmpty()) {
                int def = (month == initial.getMonthValue()) ? initial.getDayOfMonth() : cbDay.getItems().get(0);
                cbDay.getSelectionModel().select(Integer.valueOf(def));
            }
        };
        cbMonth.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) refreshDays.accept(b);
        });
        refreshDays.accept(initial.getMonthValue());

        var gp = new GridPane(); gp.setHgap(10); gp.setVgap(8); gp.setPadding(new Insets(12));
        gp.addRow(0, rbCal, new HBox(8, new Label(""), dp));
        gp.addRow(1, rbMd,  new HBox(8, new Label(" 月 / 日 "), cbMonth, cbDay));
        getDialogPane().setContent(gp);

        // 相互無効
        dp.disableProperty().bind(rbMd.selectedProperty());
        cbMonth.disableProperty().bind(rbCal.selectedProperty());
        cbDay.disableProperty().bind(rbCal.selectedProperty());

        setResultConverter(bt -> {
            if (bt != ok) return null;
            if (rbCal.isSelected()) {
                LocalDate d = dp.getValue();
                return (d != null && !d.isBefore(today)) ? d : today;
            } else {
                Integer m = cbMonth.getValue();
                Integer d = cbDay.getValue();
                if (m == null || d == null) return null;
                return LocalDate.of(today.getYear(), m, d);
            }
        });
    }
}
