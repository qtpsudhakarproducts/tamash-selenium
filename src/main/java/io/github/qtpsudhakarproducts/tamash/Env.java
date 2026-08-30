package io.github.qtpsudhakarproducts.tamash;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Config lookup: real OS environment variable → JVM system property ({@code -Dkey=value}) → a
 * value from a {@code .env} file in the working directory.
 *
 * <p>Java can't mutate its own process environment at runtime the way Node's {@code process.env}
 * or Python's {@code os.environ} can, so {@code .env} values live in a separate Dotenv lookup
 * rather than actually populating {@link System#getenv()}. The system-property fallback lets
 * {@code mvn test -DHEALER_ENABLED=false} work (used by {@code apply-heals}'s verification script).
 */
public final class Env {
  private Env() {}

  private static final Dotenv DOTENV = Dotenv.configure().ignoreIfMissing().load();

  public static String get(String key) {
    String fromEnv = System.getenv(key);
    if (fromEnv != null) {
      return fromEnv;
    }
    String fromProp = System.getProperty(key);
    if (fromProp != null) {
      return fromProp;
    }
    return DOTENV.get(key);
  }
}
