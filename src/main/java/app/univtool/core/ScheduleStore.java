package app.univtool.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** 時間割IDごとの開講期間を保存 */
public class ScheduleStore {
    public static class Range {
        public String timetableId; // null可
        public String startIso;    // yyyy-MM-dd
        public String endIso;      // yyyy-MM-dd
    }
    public static class Data { public Map<String, Range> byTimetableId = new LinkedHashMap<>(); }

    private static final Gson G = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "schedule-config.json";
    private static ScheduleStore INSTANCE;
    private Data data;

    private ScheduleStore(Data d) { this.data = (d == null ? new Data() : d); }
    public static synchronized ScheduleStore get() {
        if (INSTANCE == null) INSTANCE = new ScheduleStore(load());
        return INSTANCE;
    }

    public synchronized void setRange(String timetableId, LocalDate start, LocalDate end) {
        if (timetableId == null) timetableId = "(default)";
        Range r = new Range();
        r.timetableId = timetableId;
        r.startIso = (start == null ? null : start.toString());
        r.endIso   = (end   == null ? null : end.toString());
        data.byTimetableId.put(timetableId, r);
        save(data);
    }
    public synchronized Range getRange(String timetableId) {
        if (timetableId == null) timetableId = "(default)";
        return data.byTimetableId.get(timetableId);
    }

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
