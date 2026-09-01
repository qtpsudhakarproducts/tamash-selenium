package com.vibetestq.qtpsudhakar.tamash;

import com.vibetestq.qtpsudhakar.tamash.healer.HealCache;
import com.vibetestq.qtpsudhakar.tamash.healer.Healer;
import com.vibetestq.qtpsudhakar.tamash.healer.SelfHealingReport;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.ProviderFactory;
import com.vibetestq.qtpsudhakar.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real end-to-end against a live AI provider. A no-op unless a real provider is selected — under
 * the default {@code mvn test} ({@code HEALER_PROVIDER} unset / {@code tamash}) every test here
 * is skipped. CI's {@code ai-providers} job sets {@code HEALER_PROVIDER=openai|anthropic|gemini}
 * with a key; run locally with e.g. {@code mvn test -Dtest=AiHealingE2ETest -DHEALER_PROVIDER=openai}.
 *
 * <p>The AI (unlike the rule-based {@code tamash} provider) tolerates a paraphrased description
 * and a snapshot with no exact label, so the broken locators here don't need on-page-text-exact
 * variable names.
 */
@UseTamashSelenium
class AiHealingE2ETest {

  private static final String PROVIDER = firstNonBlank(
      System.getProperty("HEALER_PROVIDER"), System.getenv("HEALER_PROVIDER"));

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Create account</h1><form>"
      + "<label for='fn'>First name</label><input id='fn' name='firstName' type='text'/>"
      + "<label for='ln'>Last name</label><input id='ln' name='lastName' type='text'/>"
      + "<button id='submit' type='submit'>Create account</button>"
      + "</form></body></html>";

  @BeforeAll
  static void requireRealProvider() throws IOException {
    assumeTrue(PROVIDER != null && !PROVIDER.isBlank() && !"tamash".equalsIgnoreCase(PROVIDER),
        "AiHealingE2ETest needs a real AI provider (HEALER_PROVIDER=openai|anthropic|gemini|...)");
    // The persistent heal log doubles as a cross-run cache — a prior run's confirmed selectors
    // would be served without an AI call and defeat the "a real call happened" assertion.
    Files.deleteIfExists(Path.of(".tamash-selenium", "heals.jsonl"));
    ProviderFactory.resetCache();
  }

  @BeforeEach
  void freshCache() {
    HealCache.clear();
  }

  @Test
  void healsThreeBrokenLocatorsViaTheAiProvider(WebDriver driver) {
    driver.get(PAGE);

    By firstNameField = By.name("first_name");                        // real: name=firstName
    By lastNameField = By.cssSelector("input#last-name");             // real: #ln / name=lastName
    By createButton = By.xpath("//button[normalize-space()='Sign up']"); // real text: Create account

    driver.findElement(firstNameField).sendKeys("Ada");
    driver.findElement(lastNameField).sendKeys("Lovelace");
    driver.findElement(createButton).click();

    assertEquals("Ada", driver.findElement(By.name("firstName")).getDomProperty("value"));
    assertEquals("Lovelace", driver.findElement(By.name("lastName")).getDomProperty("value"));

    var heals = Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> r.testSelector != null && r.testSelector.contains("AiHealingE2ETest"))
        .toList();
    assertEquals(3, heals.size(), "all three broken locators should heal");

    for (SelfHealingReport r : heals) {
      String fam = providerFamily(r.getProvider());
      assertTrue(PROVIDER.toLowerCase().equals(fam) || "cache".equals(fam),
          () -> "expected " + PROVIDER + " (or a cache hit) but was " + r.getProvider());
      assertNotNull(r.getSuggestedSelector(), "a durable selector");
      assertTrue(r.getSuggestedSelector().startsWith("By."), r.getSuggestedSelector());
    }
    // at least one heal went straight to the real provider (not served from this run's cache) —
    // proof a live AI call actually happened
    assertTrue(heals.stream().anyMatch(r -> PROVIDER.toLowerCase().equals(providerFamily(r.getProvider()))),
        "at least one heal must have come directly from " + PROVIDER);
  }

  /** "openai:gpt-4o-mini" / "cache" -> "openai"; leaves plain names alone. */
  private static String providerFamily(String name) {
    if (name == null) return null;
    int colon = name.indexOf(':');
    return (colon == -1 ? name : name.substring(0, colon)).toLowerCase();
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b != null && !b.isBlank() ? b : null;
  }
}
