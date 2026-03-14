package app.univtool.core;

import java.util.concurrent.CopyOnWriteArrayList;

public final class TimetableSyncBus {

    public interface Listener {
        default void onTimetableChanged(String timetableId) {}
        default void onRangeChanged(String timetableId) {}
        default void onTimetableDeleted(String timetableId) {}
        default void onTimetableOpened(String timetableId) {}
    }

    private static final CopyOnWriteArrayList<Listener> LS = new CopyOnWriteArrayList<>();

    public static void add(Listener l)   { if (l!=null) LS.add(l); }
    public static void remove(Listener l){ LS.remove(l); }

    public static void fireTimetableChanged(String id) { LS.forEach(l -> l.onTimetableChanged(id)); }
    public static void fireRangeChanged(String id)     { LS.forEach(l -> l.onRangeChanged(id)); }
    public static void fireTimetableDeleted(String id) { LS.forEach(l -> l.onTimetableDeleted(id)); }
    public static void fireTimetableOpened(String id)  { LS.forEach(l -> l.onTimetableOpened(id)); }

    private TimetableSyncBus() {}
}
