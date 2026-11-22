package za.co.neroland.neropower.command;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The gallery's few delayed steps (setup a few ticks after placement, then the formed check), run from the
 * server tick NeroPower already hooks. Memory only, at most a handful of entries, nothing about any player.
 * Cleared when the server stops (a task for a level that is gone is dropped).
 */
public final class GalleryTasks {

    private record Task(ServerLevel level, long due, Runnable body) {
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private GalleryTasks() {
    }

    /** Run {@code body} {@code delayTicks} server ticks from now. */
    public static synchronized void schedule(ServerLevel level, int delayTicks, Runnable body) {
        TASKS.add(new Task(level, (long) level.getServer().getTickCount() + Math.max(1, delayTicks), body));
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        List<Task> due = new ArrayList<>();
        synchronized (GalleryTasks.class) {
            if (TASKS.isEmpty()) {
                return;
            }
            long now = server.getTickCount();
            for (Iterator<Task> it = TASKS.iterator(); it.hasNext();) {
                Task task = it.next();
                if (task.level().getServer() != server) {
                    it.remove(); // a stopped server's task
                } else if (task.due() <= now) {
                    it.remove();
                    due.add(task);
                }
            }
        }
        for (Task task : due) {
            task.body().run();
        }
    }

    /** Number of pending tasks (tests). */
    static synchronized int pending() {
        return TASKS.size();
    }
}
