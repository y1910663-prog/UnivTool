package app.univtool.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
	
	private static volatile Path baseDirOverride = null;

    public static Connection get() {
        try {
            Path dbPath = resolveDbPath();
            Path parent = dbPath.getParent();
            if (parent != null) Files.createDirectories(parent);         
            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            Connection con = DriverManager.getConnection(url);
            try (Statement st = con.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
            return con;
        } catch (Exception e) {
            throw new RuntimeException("DB接続失敗: " + e.getMessage(), e);
        }
    }


    // 現在有効な DB ファイルパス
    private static Path resolveDbPath() {
        Path defaultDb = AppPaths.appDb();
        if (baseDirOverride == null) {
            return defaultDb;
        }
        // 上書き
        return baseDirOverride.resolve(defaultDb.getFileName().toString());
    }

    public static void initSchema() {
        try (Connection con = get(); Statement st = con.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");

            // 新規テーブル
            st.executeUpdate("""
            		CREATE TABLE IF NOT EXISTS course (
            		  id INTEGER PRIMARY KEY AUTOINCREMENT,
            		  name TEXT NOT NULL,
            		  day TEXT NOT NULL,
            		  period TEXT NOT NULL,
            		  code TEXT,
            		  grade INTEGER,
            		  kind TEXT,
            		  credit REAL,
            		  teacher TEXT,
            		  email TEXT,
            		  webpage TEXT,
            		  syllabus TEXT,
            		  attendance_required INTEGER DEFAULT 0,
            		  exams TEXT,
            		  year INTEGER,
            		  term TEXT,
            		  folder TEXT
            		)
            		""");

            // 既存DBに year 列が無ければ追加
            if (!columnExists(con, "course", "year")) {
                st.executeUpdate("ALTER TABLE course ADD COLUMN year INTEGER");
            }
            if (!columnExists(con, "course", "term")) {
                st.executeUpdate("ALTER TABLE course ADD COLUMN term TEXT");
            }
            if (!columnExists(con, "course", "folder")) {
                st.executeUpdate("ALTER TABLE course ADD COLUMN folder TEXT");
                try { st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS ux_course_folder ON course(folder)"); } catch (SQLException ignore) {}
            }
            
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS saved_file (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  course_id INTEGER,
                  nth INTEGER,
                  kind TEXT,
                  path TEXT UNIQUE,
                  saved_at INTEGER,
                  deadline_date TEXT  -- ISO日付 "yyyy-MM-dd"。未設定はNULL
                )
            """);
            // 既存DBに deadline_date 列が無ければ追加
            if (!columnExists(con, "saved_file", "deadline_date")) {
                st.executeUpdate("ALTER TABLE saved_file ADD COLUMN deadline_date TEXT");
            }
            // 一意インデックス
            try {
                st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS ux_saved_file_path ON saved_file(path)");
            } catch (SQLException ignore) {}

        } catch (SQLException e) {
            throw new RuntimeException("DBスキーマ初期化に失敗: " + e.getMessage(), e);
        }
    }

    // 追加
    public static void flushQuietly() {
        try (Connection con = get(); Statement st = con.createStatement()) {
            st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (Exception ignore) {}
    }



    private static boolean columnExists(Connection con, String table, String col) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (col.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }
    
    public static synchronized void configureBaseDir(Path baseDir) {
        baseDirOverride = baseDir;
    }
    
    
    
    
    
}
