package io.github.qtpsudhakarproducts.tamash;

/**
 * Java has no {@code test.info()}; this thread-local, set by the JUnit extension in
 * {@code beforeEach} and cleared in {@code afterEach}, is how the healer records WHICH test
 * exercised a healed location (so {@code apply-heals} can re-verify exactly those tests). The
 * value {@code apply-heals} hands to {@code mvn test} is {@code testClass#testMethod}.
 */
public final class CurrentTest {
  private CurrentTest() {}

  public record Info(String testClass, String testMethod, String displayName) {}

  private static final ThreadLocal<Info> CURRENT = new ThreadLocal<>();

  public static void set(Info info) {
    CURRENT.set(info);
  }

  public static void clear() {
    CURRENT.remove();
  }

  public static Info get() {
    return CURRENT.get();
  }
}
