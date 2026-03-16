package app.univtool.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import app.univtool.model.Course;
import app.univtool.repo.CourseRepository;

/**
 * 単位ボーダーライン＆進捗のストア
 * JSON: ~/.univtool/credit_store.json
 * バグ取り中
 */
public final class CreditStore {

    public static final class BorderSet {
        public String id;                 // UUID
        public String name;               // 可読名
        /** カテゴリ→ 所要単位 */
        public Map<String, Double> required = new LinkedHashMap<>();
    }
    public static final class Data {
        /** 複数の所要単位ボーダーライン定義 */
        public List<BorderSet> borderSets = new ArrayList<>();
        /** アクティブBorderSet ID */
        public String activeSetId;
        /** 現在の取得単位（カテゴリ→合計） */
        public Map<String, Double> progress = new LinkedHashMap<>();
    }

    private static final Gson G = new GsonBuilder().setPrettyPrinting().create();
    private static CreditStore INSTANCE;
    private Data data;

    private CreditStore(Data d) { this.data = (d == null ? new Data() : d); }

    public static synchronized CreditStore get() {
        if (INSTANCE == null) INSTANCE = new CreditStore(load());
        return INSTANCE;
    }

    private static Path file() { return AppPaths.appHome().resolve("credit_store.json"); }

    private static Data load() {
        try {
            Files.createDirectories(AppPaths.appHome());
            Path p = file();
            if (Files.exists(p)) {
                String json = Files.readString(p);
                Data d = G.fromJson(json, Data.class);
                return d != null ? d : new Data();
            }
        } catch (IOException ignored) {}
        return new Data();
    }

    private synchronized void save() {
        try {
            Files.createDirectories(AppPaths.appHome());
            Files.writeString(file(), G.toJson(data),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("CreditStore の保存に失敗: " + e.getMessage(), e);
        }
    }

    // ---- BorderSet 操作 ----
    public synchronized BorderSet createSet(String name, Collection<String> initialCategories) {
        BorderSet s = new BorderSet();
        s.id = UUID.randomUUID().toString();
        s.name = (name == null || name.isBlank()) ? ("ボーダー_" + s.id.substring(0, 8)) : name;
        s.required = new LinkedHashMap<>();
        data.borderSets.add(s);
        if (data.activeSetId == null) data.activeSetId = s.id;
        save();
        return s;
    }


    public synchronized List<BorderSet> listSets() {
        return data.borderSets.stream()
                .sorted(Comparator.comparing(b -> (b.name == null ? "" : b.name)))
                .collect(Collectors.toList());
    }

    public synchronized Optional<BorderSet> findSet(String id) {
        return data.borderSets.stream().filter(x -> Objects.equals(x.id, id)).findFirst();
    }

    public synchronized void renameSet(String id, String newName) {
        findSet(id).ifPresent(s -> { s.name = newName; save(); });
    }

    public synchronized void deleteSet(String id) {
        data.borderSets.removeIf(s -> Objects.equals(s.id, id));
        if (Objects.equals(data.activeSetId, id)) data.activeSetId = data.borderSets.isEmpty() ? null : data.borderSets.get(0).id;
        save();
    }

    public synchronized void setActiveSet(String id) {
        if (findSet(id).isPresent()) { data.activeSetId = id; save(); }
    }

    public synchronized String getActiveSetId() { return data.activeSetId; }

    public synchronized void upsertRequired(String setId, String category, double required) {
        findSet(setId).ifPresent(s -> {
            String key = category == null ? "" : category.trim();
            if (!key.isBlank()) s.required.put(key, Math.max(0.0, required));
            save();
        });
    }

    public synchronized void removeCategory(String setId, String category) {
        findSet(setId).ifPresent(s -> { s.required.remove(category); save();});
    }

    // ---- Progress（現在の取得単位） ----
    public synchronized Map<String, Double> getProgress() {
        return new LinkedHashMap<>(data.progress);
    }
    public synchronized void setProgress(String category, double value) {
        if (category == null || category.isBlank()) return;
        data.progress.put(category.trim(), Math.max(0.0, value));
        save();
    }
    public synchronized void addProgress(String category, double delta) {
        if (category == null || category.isBlank()) return;
        String key = category.trim();
        double v = data.progress.getOrDefault(key, 0.0);
        data.progress.put(key, Math.max(0.0, v + delta));
        save();
    }

    // ---- 集計（見込み） ----
    public static final class WhatIfRow {
        public String category;
        public double required;  // ボーダー
        public double current;   // 取得済み
        public double planned;   // 時間割に登録済み分（赤字で表示）
    }

    public synchronized List<WhatIfRow> computeWhatIf(String setId) {
        BorderSet s = findSet(setId).orElse(null);
        if (s == null) return List.of();

        // 現在の時間割を採用
        Map<String, Double> plannedByKind = computePlannedByKind();

        List<WhatIfRow> rows = new ArrayList<>();
        Set<String> catAll = new LinkedHashSet<>();
        catAll.addAll(s.required.keySet());
        catAll.addAll(data.progress.keySet());
        catAll.addAll(plannedByKind.keySet());

        for (String cat : catAll) {
            WhatIfRow r = new WhatIfRow();
            r.category = cat;
            r.required = s.required.getOrDefault(cat, 0.0);
            r.current  = data.progress.getOrDefault(cat, 0.0);
            r.planned  = plannedByKind.getOrDefault(cat, 0.0);
            rows.add(r);
        }
        rows.sort(Comparator.comparing(x -> x.category));
        return rows;
    }

    private Map<String, Double> computePlannedByKind() {
        Map<String, Double> out = new LinkedHashMap<>();
        Optional<TimetableStore.Timetable> ttOpt = TimetableStore.loadLastOpened(); // 既存API
        if (ttOpt.isEmpty()) return out;

        TimetableStore.Timetable t = ttOpt.get();
        Set<Integer> ids = new LinkedHashSet<>();
        if (t.slots != null) ids.addAll(t.slots.values());
        if (t.sunSlots != null) {
            for (List<Integer> li : t.sunSlots.values()) if (li != null) ids.addAll(li);
        }
        if (ids.isEmpty()) return out;

        CourseRepository repo = new CourseRepository();
        List<Course> all = repo.findAll();
        Map<Integer, Course> byId = new HashMap<>();
        for (Course c : all) byId.put(c.id, c);

        for (Integer id : ids) {
            Course c = byId.get(id);
            if (c == null) continue;
            String kind = (c.kind == null || c.kind.isBlank()) ? "（未分類）" : c.kind.trim();
            double cr = (c.credit == null ? 0.0 : c.credit);
            if (cr <= 0) continue;
            out.put(kind, out.getOrDefault(kind, 0.0) + cr);
        }
        return out;
    }

    // 進捗の削除
    public synchronized void removeProgress(String category) {
        if (category == null || category.isBlank()) return;
        data.progress.remove(category.trim());
        save();
    }
    
    // 指定セットのカテゴリ一覧
    public synchronized List<String> listCategories(String setId) {

        return findSet(setId)
                .map(s -> s.required.keySet().stream()
                        .filter(k -> k != null && !k.isBlank())
                        .map(String::trim)
                        .distinct()
                        .sorted()
                        .collect(java.util.stream.Collectors.toList()))
                .orElseGet(java.util.ArrayList::new);
    }
}
