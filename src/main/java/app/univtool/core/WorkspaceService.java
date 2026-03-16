package app.univtool.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WorkspaceService {

    public record WorkspacePaths(Path workspace, Path archive) {}

    public static WorkspacePaths ensureWorkspace(String workspaceDir) {
        Path ws = Paths.get(workspaceDir);
        Path archive = ws.resolve("archive");
        try {
            Files.createDirectories(ws);
            Files.createDirectories(archive);
        } catch (IOException e) {
            throw new RuntimeException("ワークスペース作成に失敗: " + e.getMessage(), e);
        }
        return new WorkspacePaths(ws, archive);
    }

    public static WorkspacePaths ensureDefault() {
        var s = SettingsStore.load();
        if (s.workspaceDir == null || s.workspaceDir.isBlank()) {
            s.workspaceDir = AppPaths.defaultWorkspace().toAbsolutePath().toString();
            SettingsStore.save(s);
        }
        return ensureWorkspace(s.workspaceDir);
    }
}
