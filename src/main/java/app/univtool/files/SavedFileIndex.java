package app.univtool.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import app.univtool.core.AppPaths;
import app.univtool.core.Database;



/**
 * ファイル保存時の情報を記録するインデックス。
 *  - 保存時に addEntry() で1レコード追加
 *  - 講義詳細ダイアログから countsByKind(courseId) で種類別個数集計
 *
 * ~/.univtool/saved-files.json
 */
public class SavedFileIndex {
    public static class Entry {
        public Integer courseId;
        public Integer nth;
        public String kind;
        public String path;
        public long savedAt;
        public String deadline;
    }

    public static class Data {
        public List<Entry> entries = new ArrayList<>();
    }

    private static final Gson G = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "saved-files.json";
    private static SavedFileIndex INSTANCE;

    private Data data;

    private SavedFileIndex(Data d) { this.data = (d == null ? new Data() : d); }

    public static synchronized SavedFileIndex get() {
        if (INSTANCE == null) {
            INSTANCE = new SavedFileIndex(load());
        }
        return INSTANCE;
    }

    public synchronized void addEntry(Integer courseId, Integer nth, String kindName, String path) {
        addEntry(courseId, nth, kindName, path, null);
    }
    
    public synchronized void addEntry(Integer courseId, Integer nth, String kindName, String path, java.time.LocalDate deadline) {
        Entry e = new Entry();
        e.courseId = courseId;
        e.nth = nth;
        e.kind = kindName;
        e.path = path;
        e.savedAt = System.currentTimeMillis();
        e.deadline = (deadline == null ? null : deadline.toString());

        data.entries.add(e);
        save(data);

        try { upsertDb(e); } catch (Exception ignore) {}
    }
    
    
    

    public synchronized Map<String, Long> countsByKind(Integer courseId) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Entry e : data.entries) {
            if (Objects.equals(e.courseId, courseId)) {
                m.put(e.kind, m.getOrDefault(e.kind, 0L) + 1L);
            }
        }
        return m;
    }

    // ---- IO ----
    private static Data load() {
        try {
            Path p = AppPaths.appHome().resolve(FILE);
            if (Files.exists(p)) {
                String json = Files.readString(p);
                Data d = G.fromJson(json, Data.class);
                if (d != null && d.entries != null) return d;
            }
        } catch (Exception ignored) {}
        return new Data();
    }

    private static void save(Data d) {
        try {
            Files.createDirectories(AppPaths.appHome());
            Path p = AppPaths.appHome().resolve(FILE);
            Files.writeString(p, G.toJson(d));
        } catch (Exception e) {
        }
    }
    
    private static String norm(Path p) {
        try { return p.toAbsolutePath().normalize().toString(); }
        catch (Exception e) { return String.valueOf(p); }
    }

    public synchronized boolean isRegistered(Path p) {
        String target = norm(p);
        for (Entry e : data.entries) {
            try {
                if (target.equals(norm(Path.of(e.path)))) return true;
            } catch (Exception ignore) {}
        }
        return false;
    }
    
    public synchronized java.util.List<Entry> list() {
        return new java.util.ArrayList<>(data.entries);
    }

    public synchronized void deleteByPath(Path p) {
        String target = norm(p);
        data.entries.removeIf(e -> {
            try { return target.equals(norm(Path.of(e.path))); } catch (Exception ignore) { return false; }
        });
        save(data);

        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement("DELETE FROM saved_file WHERE path=?")) {
            ps.setString(1, target);
            ps.executeUpdate();
        } catch (Exception ignore) {}
    }

    public synchronized void updateClassification(Path p, Integer courseId, Integer nth, String kindName) {
        String target = norm(p);
        for (Entry e : data.entries) {
            try {
                if (target.equals(norm(Path.of(e.path)))) {
                    e.courseId = courseId;
                    e.nth = nth;
                    e.kind = kindName;
                    save(data);

                    try (Connection con = Database.get();
                         PreparedStatement ps = con.prepareStatement(
                                "UPDATE saved_file SET course_id=?, nth=?, kind=? WHERE path=?")) {
                        if (courseId == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, courseId);
                        if (nth == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, nth);
                        ps.setString(3, kindName);
                        ps.setString(4, target);
                        ps.executeUpdate();
                    } catch (Exception ignore2) {}
                    return;
                }
            } catch (Exception ignore) {}
        }
    }

    public synchronized void updatePath(Path oldPath, Path newPath) {
        String oldN = norm(oldPath);
        String newN = norm(newPath);
        for (Entry e : data.entries) {
            try {
                if (oldN.equals(norm(Path.of(e.path)))) {
                    e.path = newN;
                    save(data);
                    try (Connection con = Database.get();
                         PreparedStatement ps = con.prepareStatement(
                                "UPDATE saved_file SET path=? WHERE path=?")) {
                        ps.setString(1, newN);
                        ps.setString(2, oldN);
                        ps.executeUpdate();
                    } catch (Exception ignore2) {}
                    return;
                }
            } catch (Exception ignore) {}
        }
    }
    
    public synchronized void updateDeadline(Path p, java.time.LocalDate deadline) {
        String target = norm(p);
        String iso = (deadline == null ? null : deadline.toString());
        for (Entry e : data.entries) {
            try {
                if (target.equals(norm(Path.of(e.path)))) {
                    e.deadline = iso;
                    save(data);
                    break;
                }
            } catch (Exception ignore) {}
        }
        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE saved_file SET deadline_date=? WHERE path=?")) {
            if (iso == null) ps.setNull(1, java.sql.Types.VARCHAR); else ps.setString(1, iso);
            ps.setString(2, target);
            ps.executeUpdate();
        } catch (Exception ignore) {}
    }
    
    private void upsertDb(Entry e) throws SQLException {
        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement("""
                INSERT INTO saved_file (course_id, nth, kind, path, saved_at, deadline_date)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(path) DO UPDATE SET
                  course_id      = excluded.course_id,
                  nth            = excluded.nth,
                  kind           = excluded.kind,
                  saved_at       = excluded.saved_at,
                  deadline_date  = excluded.deadline_date
            """)) {
            if (e.courseId == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, e.courseId);
            if (e.nth == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, e.nth);
            ps.setString(3, e.kind);
            ps.setString(4, norm(Path.of(e.path)));
            ps.setLong(5, e.savedAt);
            if (e.deadline == null) ps.setNull(6, java.sql.Types.VARCHAR); else ps.setString(6, e.deadline);
            ps.executeUpdate();
        }
    }
    
}

