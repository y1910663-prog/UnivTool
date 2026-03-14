package app.univtool.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * 時間割をJSONに保存/読込するストア。
 * ~/.univtool/timetables.json

 */
public final class TimetableStore {

    public static final class Timetable {
        public String id;               // UUID
        public String name;             // 時間割_2025_前期
        public Integer year;            // 開講年度
        /** 前期後期 */
        public String termMain;

        /** 月〜土の単一登録コマ */
        public Map<String, Integer> slots = new LinkedHashMap<>();

        /** 日曜（自由枠 */
        public Map<String, List<Integer>> sunSlots = new LinkedHashMap<>();

        /** 自由記入メモ */
        public Map<String, String> memos = new LinkedHashMap<>();
        
        public Double creditLimit;
    }

    private static final Gson G = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return AppPaths.appHome().resolve("timetables.json"); }
    private static Path lastFile() { return AppPaths.appHome().resolve("timetable-current.txt"); }

    
    public static synchronized List<Timetable> loadAll() {
        try {
            Files.createDirectories(AppPaths.appHome());
            Path p = file();
            if (Files.exists(p)) {
                String json = Files.readString(p);
                List<Timetable> list = G.fromJson(json, new TypeToken<List<Timetable>>(){}.getType());
                return (list != null) ? list : new ArrayList<>();
            }
        } catch (IOException ignored) {}
        return new ArrayList<>();
    }

    public static synchronized void saveAll(List<Timetable> list) {
        try {
            Files.createDirectories(AppPaths.appHome());
            Files.writeString(file(), G.toJson(list),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("時間割の保存に失敗: " + e.getMessage(), e);
        }
    }

    public static synchronized Optional<Timetable> findById(String id) {
        return loadAll().stream().filter(t -> Objects.equals(t.id, id)).findFirst();
    }

    public static synchronized Optional<Timetable> findByName(String name) {
        return loadAll().stream().filter(t -> Objects.equals(t.name, name)).findFirst();
    }

    /** 追加 or 上書き（id一致で置換）。idが無ければUUIDを付与。 */
    public static synchronized Timetable upsert(Timetable t) {
        if (t.id == null || t.id.isBlank()) t.id = UUID.randomUUID().toString();
        List<Timetable> all = loadAll();
        all.removeIf(x -> Objects.equals(x.id, t.id));
        all.add(t);
        saveAll(all);
        return t;
    }

    public static synchronized List<String> names() {
        var n = new ArrayList<String>();
        for (var t : loadAll()) n.add(t.name);
        n.sort(String::compareTo);
        return n;
    }
    
    
    
    /** 最後に開いた時間割次回起動時に自動で開く */
    public static synchronized void saveLastOpened(String id) {
        try {
            Files.createDirectories(AppPaths.appHome());
            Files.writeString(lastFile(), id == null ? "" : id,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {}
    }
    /** 最後に開いた時間割を読み込み */
    public static synchronized Optional<Timetable> loadLastOpened() {
        try {
            Files.createDirectories(AppPaths.appHome());
            Path p = lastFile();
            if (Files.exists(p)) {
                String id = Files.readString(p).trim();
                if (!id.isEmpty()) return findById(id);
            }
        } catch (IOException ignored) {}
        return Optional.empty();
    }
    
    /** 最後に開いた時間割をクリア */
    public static synchronized void clearLastOpened() {
        try { Files.deleteIfExists(lastFile()); } catch (IOException ignored) {}
    }
    
    /** 指定IDの時間割を削除。
     *  timetables.json から該当要素を除去、最後に開いた時間割が同一なら記録もクリア */
    public static synchronized boolean delete(String id) {
        if (id == null || id.isBlank()) return false;

        List<Timetable> all = loadAll();
        boolean removed = all.removeIf(t -> Objects.equals(t.id, id));
        if (!removed) return false;

        saveAll(all);

        try {
            Files.createDirectories(AppPaths.appHome());
            Path p = lastFile();
            if (Files.exists(p)) {
                String last = Files.readString(p).trim();
                if (Objects.equals(last, id)) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException ignored) {}

        return true;
    }

    /** 名前で削除  */
    public static synchronized boolean deleteByName(String name) {
        return findByName(name).map(t -> delete(t.id)).orElse(false);
    }

    
}
