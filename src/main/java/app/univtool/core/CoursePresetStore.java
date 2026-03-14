package app.univtool.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public final class CoursePresetStore {

    // 講義ひな型
    public static final class CoursePreset {
        public String name;
        public String day;
        public String period;
        public String code;
        public Integer grade;
        public String kind;
        public Double credit;
        public String teacher;
        public String email;
        public String webpage;
        public String syllabus;
        public Boolean attendanceRequired;
        public String examsCsv;
        public Integer year;
        public String term;
    }

    // 大学設定プリセット
    public static final class Preset {
        public String name;
        public List<CoursePreset> courses = new ArrayList<>();
    }

    public static final class ImportResult {
        public int addedPresets;
        public int updatedPresets;
        public int addedCourses;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path baseDir() {
        var s = SettingsStore.load();
        Path base = null;
        if (s.workspaceDir != null && !s.workspaceDir.isBlank()) {
            Path ws = Path.of(s.workspaceDir);
            base = (ws.getParent() != null) ? ws.getParent() : ws;
        }
        if (base == null && s.archiveDir != null && !s.archiveDir.isBlank()) {
            Path ar = Path.of(s.archiveDir);
            base = (ar.getParent() != null) ? ar.getParent() : ar;
        }
        if (base == null) base = AppPaths.appHome();
        return base;
    }

    private static Path presetsDir() {
        return baseDir().resolve("presets");
    }

    private static Path file() {
        return presetsDir().resolve("course_presets.json");
    }

    public static synchronized List<Preset> loadAll() {
        try {
            Files.createDirectories(presetsDir());
            if (Files.exists(file())) {
                String json = Files.readString(file());
                List<Preset> list = GSON.fromJson(json, new TypeToken<List<Preset>>(){}.getType());
                return (list != null) ? list : new ArrayList<>();
            }
        } catch (IOException ignored) {}
        return new ArrayList<>();
    }

    public static synchronized void saveAll(List<Preset> presets) {
        try {
            Files.createDirectories(presetsDir());
            Files.writeString(file(), GSON.toJson(presets),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("講義プリセット保存に失敗: " + e.getMessage(), e);
        }
    }

    public static synchronized Optional<Preset> findByName(String name) {
        return loadAll().stream().filter(p -> Objects.equals(p.name, name)).findFirst();
    }

    public static synchronized void upsert(Preset preset) {
        List<Preset> all = loadAll();
        all.removeIf(p -> Objects.equals(p.name, preset.name));
        all.add(preset);
        saveAll(all);
    }

    public static synchronized List<String> presetNames() {
        var names = new ArrayList<String>();
        for (Preset p : loadAll()) names.add(p.name);
        names.sort(String::compareTo);
        return names;
    }

    /** 外部 JSON を取り込んで保存にマージ */
    public static synchronized ImportResult importFromFile(Path jsonFile) {
        try {
            String json = Files.readString(jsonFile);
            List<Preset> incoming = GSON.fromJson(json, new TypeToken<List<Preset>>(){}.getType());
            if (incoming == null) incoming = new ArrayList<>();

            List<Preset> current = loadAll();
            ImportResult r = new ImportResult();

            for (Preset in : incoming) {
                Optional<Preset> exOpt = current.stream().filter(p -> Objects.equals(p.name, in.name)).findFirst();
                if (exOpt.isEmpty()) {
                    current.add(in);
                    r.addedPresets++;
                    r.addedCourses += (in.courses == null ? 0 : in.courses.size());
                } else {
                    // 置換
                    Preset ex = exOpt.get();
                    r.updatedPresets++;
                    int old = (ex.courses == null ? 0 : ex.courses.size());
                    int now = (in.courses == null ? 0 : in.courses.size());
                    r.addedCourses += Math.max(0, now - old);
                    ex.courses = (in.courses == null) ? new ArrayList<>() : new ArrayList<>(in.courses);
                }
            }
            saveAll(current);
            return r;
        } catch (IOException e) {
            throw new RuntimeException("インポートに失敗: " + e.getMessage(), e);
        }
    }
}
