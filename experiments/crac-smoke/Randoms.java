// WHICH generators repeat after a restore, and which do not — the question `Hello.java` answered
// only for the thread that happened to draw.
//
// Five draws, taken after the restore, differing in what holds the state and when it was made:
//   old-random   a java.util.Random constructed BEFORE the checkpoint
//   old-tlr      ThreadLocalRandom on a thread that existed BEFORE the checkpoint
//   new-tlr      ThreadLocalRandom on a thread created AFTER the restore
//   new-random   a java.util.Random constructed AFTER the restore
//   securerandom SecureRandom(), which the JDK documents as reseeded on restore
//
// Restore one checkpoint twice and compare the two lines: whatever matches is a generator every
// replica of that snapshot shares.
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class Randoms {
    static final Random OLD = new Random();
    public static void main(String[] args) throws Exception {
        var toOldThread = new ArrayBlockingQueue<String>(1);
        var fromOldThread = new ArrayBlockingQueue<String>(1);
        // A thread that lives across the checkpoint, the way a dispatcher worker does.
        Thread old = new Thread(() -> {
            try {
                while (true) {
                    toOldThread.take();
                    fromOldThread.put(String.valueOf(ThreadLocalRandom.current().nextInt()));
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "old-thread");
        old.setDaemon(true);
        old.start();
        // Draw once on each before the checkpoint, so the state is not pristine.
        OLD.nextInt();
        toOldThread.put("go"); fromOldThread.take();
        System.out.println("before checkpoint: ready");
        // The checkpoint arrives here (jcmd), and everything below runs after the restore. The wait
        // is on a FILE and not a sleep: a sleep resumes with its remaining time on every restore,
        // which would make each restore wait out the rest of it. The harness creates the file after
        // the checkpoint is taken, so a restore walks straight through.
        java.io.File go = new java.io.File(args.length > 0 ? args[0] : "/work/go");
        while (!go.exists()) Thread.sleep(100);
        var newThreadValue = new ArrayBlockingQueue<String>(1);
        Thread fresh = new Thread(() -> {
            try { newThreadValue.put(String.valueOf(ThreadLocalRandom.current().nextInt())); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "new-thread");
        fresh.start();
        toOldThread.put("go");
        System.out.println("old-random=" + OLD.nextInt()
            + " old-tlr=" + fromOldThread.take()
            + " new-tlr=" + newThreadValue.take()
            + " new-random=" + new Random().nextInt()
            + " securerandom=" + new SecureRandom().nextInt());
    }
}
