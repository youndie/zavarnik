// WHICH generators repeat after a restore, and which do not — the question `Hello.java` answered
// only for the thread that happened to draw.
//
// Every draw below is taken AFTER the restore. They differ in what holds the state and when it was
// made, which is the whole subject: a snapshot carries the state, so a generator seeded before the
// checkpoint hands every replica of that snapshot the same numbers.
//
//   old-random     java.util.Random constructed BEFORE the checkpoint
//   old-tlr        ThreadLocalRandom on a thread that existed BEFORE the checkpoint
//   new-tlr        ThreadLocalRandom on a thread created AFTER the restore
//   new-random     java.util.Random constructed AFTER the restore
//   old-splittable SplittableRandom constructed BEFORE the checkpoint
//   new-splittable SplittableRandom constructed AFTER the restore
//   math-random    Math.random(), whose generator is a JDK-wide java.util.Random
//   default-rng    RandomGenerator.getDefault(), the JDK 17+ entry point
//   uuid           UUID.randomUUID(), which is SecureRandom underneath
//   securerandom   SecureRandom(), documented as reseeded on restore
//   seeded-secure  SecureRandom(byte[]), documented as NOT reseeded
//
// Restore one checkpoint twice and compare the two lines: whatever matches is a generator every
// replica of that snapshot shares. `generator-guard.sh` does exactly that and fails on a match.
import java.security.SecureRandom;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public class Randoms {
    static final Random OLD = new Random();
    static final SplittableRandom OLD_SPLIT = new SplittableRandom();
    static final SecureRandom SEEDED = new SecureRandom(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});

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
        // Draw once on each before the checkpoint, so no state is pristine.
        OLD.nextInt();
        OLD_SPLIT.nextInt();
        SEEDED.nextInt();
        Math.random();
        toOldThread.put("go");
        fromOldThread.take();
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
            + " old-splittable=" + OLD_SPLIT.nextInt()
            + " new-splittable=" + new SplittableRandom().nextInt()
            + " math-random=" + (long) (Math.random() * Long.MAX_VALUE)
            + " default-rng=" + RandomGenerator.getDefault().nextInt()
            + " uuid=" + UUID.randomUUID()
            + " securerandom=" + new SecureRandom().nextInt()
            + " seeded-secure=" + SEEDED.nextInt());
    }
}
