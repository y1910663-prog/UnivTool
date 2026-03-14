package app.univtool.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** 講義ごとの自由記入欄の保存読込用ストア。 ~/.univtool/course-notes.json */
public final class CourseNoteStore {
    private static final Gson G = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "course-notes.json";
    private static CourseNoteStore INSTANCE;

    public static final class Data {
        public Map<Integer,String> notes = new LinkedHashMap<>();
    }

    private Data data;

    private CourseNoteStore(Data d) { this.data = (d == null ? new Data() : d); }

    public static synchronized CourseNoteStore get() {
        if (INSTANCE == null) INSTANCE = new CourseNoteStore(load());
        return INSTANCE;
    }

    public synchronized String loadNote(Integer courseId) {
        if (courseId == null) return "";
        String s = data.notes.get(courseId);
        return (s == null ? "" : s);
    }

    public synchronized void saveNote(Integer courseId, String text) {
        if (courseId == null) return;
        data.notes.put(courseId, text == null ? "" : text);
        save(data);
    }

    // ---------- IO ----------
    private static Data load() {
        try {
            Path dir = AppPaths.appHome();
            Files.createDirectories(dir);
            Path p = dir.resolve(FILE);
            if (Files.exists(p)) {
                String json = Files.readString(p);
                Data d = G.fromJson(json, Data.class);
                if (d != null && d.notes != null) return d;
            }
        } catch (Exception ignored) {}
        return new Data();
    }

    private static void save(Data d) {
        try {
            Path dir = AppPaths.appHome();
            Files.createDirectories(dir);
            Path p = dir.resolve(FILE);
            Files.writeString(p, G.toJson(d));
        } catch (Exception ignored) {}
    }
}
