import java.util.Random; import java.util.UUID; import java.util.concurrent.ThreadLocalRandom;
public class Hello {
  public static void main(String[] a) throws Exception {
    long t0 = System.nanoTime(); Random r = new Random();
    System.out.println("start pid=" + ProcessHandle.current().pid() + " " + line(t0, r));
    for (int i = 1; ; i++) { Thread.sleep(1000); System.out.println("tick " + i + " " + line(t0, r)); }
  }
  static String line(long t0, Random r) {
    return "nano+" + (System.nanoTime() - t0) / 1_000_000 + "ms wall=" + java.time.Instant.now()
      + " rnd=" + r.nextInt() + " tlr=" + ThreadLocalRandom.current().nextInt() + " uuid=" + UUID.randomUUID();
  }
}
