public class Probe {
  public static void main(String[] a) throws Exception {
    System.out.println("before checkpoint: " + java.time.Instant.now());
    java.io.File go = new java.io.File("/work/go");
    while (!go.exists()) Thread.sleep(100);
    System.out.println("after restore: " + java.time.Instant.now() + " on " + System.getProperty("os.arch"));
  }
}
