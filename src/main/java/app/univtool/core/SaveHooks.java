package app.univtool.core;

import java.util.ArrayList;
import java.util.List;

/** PortableMode の引き継ぎで利用。 
 * 開発途中*/
public final class SaveHooks {
    private static final List<Runnable> hooks = new ArrayList<>();

    public static void register(Runnable r) {
        if (r != null) hooks.add(r);
    }

    public static void runAllQuietly() {
        for (Runnable r : hooks) {
            try { r.run(); } catch (Throwable ignored) {}
        }
        try { Database.flushQuietly(); } catch (Throwable ignored) {}
    }

    private SaveHooks() {}
}
