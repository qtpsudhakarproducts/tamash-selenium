package com.vibetestq.qtpsudhakar.tamash;

import com.vibetestq.qtpsudhakar.tamash.bindings.Bindings;
import org.openqa.selenium.WebDriver;

/**
 * The one-liner. Wrap your {@link WebDriver} once and every {@code findElement} through it — in
 * Page Objects, {@code @FindBy} fields (plain {@code PageFactory.initElements}), helper/util
 * layers, inside {@code WebDriverWait} — becomes self-healing. Nothing else changes.
 *
 * <pre>{@code
 * WebDriver driver = SelfHealingDriver.wrap(new ChromeDriver());
 * }</pre>
 *
 * <p>With no {@code .env} it heals using the rule-based {@code tamash} provider (no key, no
 * network). Set {@code HEALER_PROVIDER} + a key for AI-backed healing. {@code HEALER_ENABLED=false}
 * turns it off entirely.
 *
 * <p>Wrapping also pins Selenium's implicit wait to 0 (mixing implicit + explicit waits is a
 * Selenium anti-pattern, and a high implicit wait delays healing) — set
 * {@code TAMASH_KEEP_IMPLICIT_WAIT=true} to keep yours.
 */
public final class SelfHealingDriver {
  private SelfHealingDriver() {}

  public static WebDriver wrap(WebDriver driver) {
    return Bindings.bindDriver(driver);
  }
}
