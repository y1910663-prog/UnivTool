package app.univtool.ui.panel2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import app.univtool.core.AppPaths;

public class FilenamePresetStore {
    public static final String FILE = "filename-presets.json";

    public static class Data {
        public Map<String,String> presets = new LinkedHashMap<>();
        public boolean hasPreset(String name){return presets.containsKey(name);}    
        public Set<String> names(){return presets.keySet();}
        public Optional<String> patternOf(String name){return Optional.ofNullable(presets.get(name));}
        public void upsertPreset(String name, String pattern){ if(name==null||name.isBlank()) return; presets.put(name, pattern==null?"":pattern); }
        public void deletePreset(String name){ presets.remove(name); }
    }

    private static final Gson G = new GsonBuilder().setPrettyPrinting().create();

    public static Data load(){
        try {
            Path p = AppPaths.appHome().resolve(FILE);
            if (Files.exists(p)) {
                String json = Files.readString(p);
                Data d = G.fromJson(json, Data.class);
                if (d!=null && d.presets!=null) return d;
            }
        } catch (Exception ignored) {}
        Data d = new Data();
        d.upsertPreset("デフォルトプリセット", "{course}_{nth}_{kind}{ext}");
        return d;
    }

    public static void save(Data d){
        try {
            Files.createDirectories(AppPaths.appHome());
            Path p = AppPaths.appHome().resolve(FILE);
            Files.writeString(p, G.toJson(d));
        } catch (IOException e) { throw new RuntimeException(e); }
    }
}
