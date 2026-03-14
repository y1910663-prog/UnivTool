package app.univtool.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TodoStore {
    public static class TodoItem {
        public String id;                // UUID
        public String createdIso;        // yyyy-MM-dd
        public Integer courseId;         // null可
        public Integer nth;              // null可
        public String title;
        public String detail;            // メモ
        public String dueIso;            // 提出期限
        public boolean done = false;     // 完了
        public List<String> filePaths = new ArrayList<>(); // 関連ファイル
        public String linkedReminderId; // 対応するReminder.id
    }
    public static class Data { public List<TodoItem> list = new ArrayList<>(); }

    private static final Gson G = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "todos.json";
    private static TodoStore INSTANCE;
    private Data data;

    private TodoStore(Data d) { this.data = (d == null ? new Data() : d); }
    public static synchronized TodoStore get() {
        if (INSTANCE == null) INSTANCE = new TodoStore(load());
        return INSTANCE;
    }

    public synchronized TodoItem add(TodoItem t) {
        if (t.id == null) t.id = java.util.UUID.randomUUID().toString();
        if (t.createdIso == null) t.createdIso = java.time.LocalDate.now().toString();
        data.list.add(t); save(); return t;
    }
    public synchronized void update(TodoItem t) {
        for (int i=0;i<data.list.size();i++) if (Objects.equals(data.list.get(i).id,t.id)) { data.list.set(i,t); break; }
        save();
    }
    public synchronized void remove(String id) { data.list.removeIf(x->Objects.equals(x.id,id)); save(); }
    public synchronized void removeAllDone() { data.list.removeIf(x->x.done); save(); }

    public synchronized List<TodoItem> listAll() { return new ArrayList<>(data.list); }
    public synchronized Optional<TodoItem> findByCourseNth(Integer cid, Integer nth, String title) {
        return data.list.stream().filter(t -> Objects.equals(t.courseId,cid) && Objects.equals(t.nth,nth) && Objects.equals(t.title,title)).findFirst();
    }

    private static Data load() {
        try { Path p = AppPaths.appHome().resolve(FILE);
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
    public synchronized void save() {
        save(this.data);
    }
    public synchronized Optional<TodoItem> find(String id) {
        return data.list.stream().filter(t -> Objects.equals(t.id, id)).findFirst();
    }

}
