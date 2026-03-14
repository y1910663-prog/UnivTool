package app.univtool.ui.panel2;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;

/** 提出期限の入力 */
public class DueDateDialog extends Dialog<DueDateDialog.Result> {
    public record Result(LocalDate dueDate, boolean dueNone) {}

    public DueDateDialog() {
        setTitle("提出期限の設定");
        var ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("スキップ（未設定）", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);

        var rbUnset = new RadioButton("未設定のままにする");
        var rbNone  = new RadioButton("期限なし");
        var rbSet   = new RadioButton("期限あり（プルダウン or カレンダーから選択）");
        var tg = new ToggleGroup();
        rbUnset.setToggleGroup(tg);
        rbNone.setToggleGroup(tg);
        rbSet.setToggleGroup(tg);
        rbUnset.setSelected(true);

        var cbQuick = new ComboBox<String>();
        cbQuick.setPromptText("（〇月〇日を選択）");
        // 直近90日の候補を「M月d日(曜)」で生成
        var fmt = DateTimeFormatter.ofPattern("M月d日");
        var today = LocalDate.now();
        var items = FXCollections.<String>observableArrayList();
        for (int i = 0; i < 90; i++) {
            var d = today.plusDays(i);
            var s = fmt.format(d) + "（" + d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.JAPANESE) + "）";
            items.add(s);
        }
        cbQuick.setItems(items);

        var dp = new DatePicker();
        dp.setEditable(false);

        // プルダウン選択 → DatePicker
        cbQuick.valueProperty().addListener((o, a, b) -> {
            if (b == null) return;
            try {
                // M月d日(曜) → LocalDate
                var base = b.split("（")[0];
                int m = Integer.parseInt(base.substring(0, base.indexOf('月')));
                int d = Integer.parseInt(base.substring(base.indexOf('月') + 1, base.indexOf('日')));
                var cand = LocalDate.of(today.getYear(), m, d);
                if (cand.isBefore(today)) cand = cand.plusYears(1);
                dp.setValue(cand);
                rbSet.setSelected(true);
            } catch (Exception ignore) {}
        });

        // 期限ありなのに日付未選択の時は OK 無効
        var btnOk = getDialogPane().lookupButton(ok);
        btnOk.disableProperty().bind(
                rbSet.selectedProperty().and(dp.valueProperty().isNull())
        );

        var gp = new GridPane();
        gp.setHgap(10); gp.setVgap(8); gp.setPadding(new Insets(12));
        gp.addRow(0, rbUnset);
        gp.addRow(1, rbNone);
        gp.addRow(2, rbSet);
        gp.addRow(3, new Label("プルダウン"), cbQuick);
        gp.addRow(4, new Label("カレンダー"), dp);

        getDialogPane().setContent(gp);

        setResultConverter(bt -> {
            if (bt != ok) return null; // スキップ＝未設定
            if (rbNone.isSelected())   return new Result(null, true);
            if (rbSet.isSelected())    return new Result(dp.getValue(), false);
            return null; // 未設定
        });
    }
}
