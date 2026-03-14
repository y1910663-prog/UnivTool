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

/**
 * 科目区分プリセットの保存/読込。
 * JSON: ~/.univtool/kind_presets.json
 */
public final class KindPresetStore {

    public static final class Preset {
        public String name;            // プリセット名
        public List<String> kinds;     // 科目区分の集合
        // 将来拡張予定
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
        if (base == null) {
            base = AppPaths.appHome();
        }
        return base;
    }
    
    private static Path presetsDir() {
        return baseDir().resolve("presets");
    }
    
    private static Path file() {
        return presetsDir().resolve("kind_presets.json");
    }
    
    private static Path legacyFile() {
        return AppPaths.appHome().resolve("kind_presets.json");
    }
    
    
 // 置換
    public static synchronized List<Preset> loadAll() {
        try {
            Files.createDirectories(presetsDir());

            Path f = file();
            if (Files.exists(f)) {
                String json = Files.readString(f);
                List<Preset> list = GSON.fromJson(json, new TypeToken<List<Preset>>(){}.getType());
                return list != null ? list : new ArrayList<>();
            }

            // 互換1
            Path old1 = legacyFile();
            if (Files.exists(old1)) {
                String json = Files.readString(old1);
                List<Preset> list = GSON.fromJson(json, new TypeToken<List<Preset>>(){}.getType());
                List<Preset> safe = (list != null ? list : new ArrayList<>());
                saveAll(safe);                            // 新ロケーションに保存（旧は残してOK）
                return safe;
            }

            // 互換2
            Path old2 = baseDir().resolve("kind_presets.json");
            if (Files.exists(old2)) {
                String json = Files.readString(old2);
                List<Preset> list = GSON.fromJson(json, new TypeToken<List<Preset>>(){}.getType());
                List<Preset> safe = (list != null ? list : new ArrayList<>());
                saveAll(safe);
                return safe;
            }
        } catch (IOException ignored) {}
        return new ArrayList<>();
    }



 // 置換
    public static synchronized void saveAll(List<Preset> presets) {
        try {
            Files.createDirectories(presetsDir());
            Files.writeString(
                file(),
                GSON.toJson(presets),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new RuntimeException("プリセット保存に失敗: " + e.getMessage(), e);
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
        List<String> names = new ArrayList<>();
        for (Preset p : loadAll()) names.add(p.name);
        names.sort(String::compareTo);
        return names;
    }
    
    public static final class ImportResult {
        public int addedPresets;   // 新規に追加されたプリセット数
        public int updatedPresets; // 既存名へマージされたプリセット数
        public int addedKinds;     // 既存へマージ時に追加された区分の総数
    }
    
    private static List<String> normalizeKinds(List<String> kinds) {
        if (kinds == null) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String s : kinds) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }
    
    private static List<Preset> parseAny(String json) {
        List<Preset> list = GSON.fromJson(json, new com.google.gson.reflect.TypeToken<List<Preset>>(){}.getType());
        if (list != null && !list.isEmpty()) return list;

        Preset one = GSON.fromJson(json, Preset.class);
        if (one != null && (one.name != null && !one.name.isBlank())) {
            one.kinds = normalizeKinds(one.kinds);
            List<Preset> single = new ArrayList<>();
            single.add(one);
            return single;
        }
        return new ArrayList<>();
    }
    
    public static synchronized ImportResult importFromFile(java.nio.file.Path jsonFile) {
        try {
            if (jsonFile == null || !java.nio.file.Files.exists(jsonFile)) {
                throw new IllegalArgumentException("ファイルが見つかりません: " + jsonFile);
            }
            String json = java.nio.file.Files.readString(jsonFile, java.nio.charset.StandardCharsets.UTF_8);
            List<Preset> incoming = parseAny(json);

            List<Preset> current = loadAll();
            ImportResult r = new ImportResult();

            for (Preset in : incoming) {
                if (in.name == null || in.name.isBlank()) continue;
                in.kinds = normalizeKinds(in.kinds);

                int idx = -1;
                for (int i = 0; i < current.size(); i++) {
                    if (Objects.equals(current.get(i).name, in.name)) { idx = i; break; }
                }
                if (idx < 0) {
                    current.add(in);
                    r.addedPresets++;
                } else {
                    Preset ex = current.get(idx);
                    List<String> exKinds = normalizeKinds(ex.kinds);
                    int before = exKinds.size();
                    for (String k : in.kinds) {
                        if (!exKinds.contains(k)) exKinds.add(k);
                    }
                    ex.kinds = exKinds;
                    current.set(idx, ex);
                    r.updatedPresets++;
                    r.addedKinds += (exKinds.size() - before);
                }
            }
            saveAll(current);
            return r;
        } catch (Exception e) {
            throw new RuntimeException("プリセットの取り込みに失敗: " + e.getMessage(), e);
        }
    }
    
    
}
