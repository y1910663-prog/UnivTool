package app.univtool.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** 1講義×特定日 */
public class AttendanceStore {
    public enum Attendance { UNKNOWN, PRESENT, EXCUSED, ABSENT }
    public static class Rec {
        public Integer courseId;
        public String  dateIso;     // yyyy-MM-dd
        public Attendance attendance = Attendance.UNKNOWN;
        public boolean cancelled = false;  // 休講
        public Boolean hasAssignment;      // 課題あり/なし
        public Boolean assignmentDone;     // 完了/未完
    }
    public static class Data { public List<Rec> list = new ArrayList<>(); }

    private static final Gson G = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "attendance.json";
    private static AttendanceStore INSTANCE;
    private Data data;

    private AttendanceStore(Data d) { this.data = (d == null ? new Data() : d); }

    public static synchronized AttendanceStore get() {
        if (INSTANCE == null) INSTANCE = new AttendanceStore(load());
        return INSTANCE;
    }

    public synchronized Rec getOrCreate(Integer cid, LocalDate d) {
        String iso = d.toString();
        for (Rec r : data.list) if (Objects.equals(r.courseId, cid) && Objects.equals(r.dateIso, iso)) return r;
        Rec r = new Rec(); r.courseId = cid; r.dateIso = iso; data.list.add(r); save();
        return r;
    }
    public synchronized Optional<Rec> find(Integer cid, LocalDate d) {
        String iso = d.toString();
        return data.list.stream().filter(x -> Objects.equals(x.courseId,cid) && Objects.equals(x.dateIso, iso)).findFirst();
    }
    public synchronized List<Rec> listByCourse(Integer cid) {
        List<Rec> out = new ArrayList<>();
        for (Rec r : data.list) if (Objects.equals(r.courseId, cid)) out.add(r);
        return out;
    }

    // 全レコードのコピーを返す
    public synchronized List<Rec> listAll() {
        return new ArrayList<>(data.list);
    }

    // 単一削除
    public synchronized boolean remove(Integer cid, LocalDate d) {
        String iso = d.toString();
        boolean changed = data.list.removeIf(r ->
            Objects.equals(r.courseId, cid) && Objects.equals(r.dateIso, iso)
        );
        if (changed) save();
        return changed;
    }

    // コース丸ごと削除
    public synchronized int removeByCourse(Integer cid) {
        int before = data.list.size();
        data.list.removeIf(r -> Objects.equals(r.courseId, cid));
        int removed = before - data.list.size();
        if (removed > 0) save();
        return removed;
    }

    // 任意条件で削除（未使用）
    public synchronized int removeWhere(Predicate<Rec> pred) {
        int before = data.list.size();
        data.list.removeIf(pred);
        int removed = before - data.list.size();
        if (removed > 0) save();
        return removed;
    }

    public synchronized void save() { save(data); }

    private static Data load() {
        try {
            Path p = AppPaths.appHome().resolve(FILE);
            if (Files.exists(p)) return G.fromJson(Files.readString(p), Data.class);
        } catch (Exception ignore) {}
        return new Data();
    }
    private static void save(Data d) {
        try {
            Files.createDirectories(AppPaths.appHome());
            Files.writeString(AppPaths.appHome().resolve(FILE), G.toJson(d));
        } catch (Exception ignore) {}
    }
}

