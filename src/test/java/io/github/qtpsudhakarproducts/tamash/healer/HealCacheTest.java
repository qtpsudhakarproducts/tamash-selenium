package io.github.qtpsudhakarproducts.tamash.healer;

import io.github.qtpsudhakarproducts.tamash.healer.providers.AiSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.*;

class HealCacheTest {

  @BeforeEach
  void clear() {
    HealCache.clear();
  }

  @Test
  void positive_isKeyedByLocatorAndPage() {
    By broken = By.cssSelector("#old");
    assertNull(HealCache.positive(broken, "/login"));

    HealCache.recordPositive(broken, "/login", By.id("new"), "By.id(\"new\")", AiSuggestion.id("new"));

    HealCache.Hit hit = HealCache.positive(broken, "/login");
    assertNotNull(hit);
    assertEquals("By.id: new", hit.healedBy().toString());
    assertEquals("By.id(\"new\")", hit.describedAs());

    // different page -> miss (same broken selector can mean different things)
    assertNull(HealCache.positive(broken, "/home"));
    // different locator -> miss
    assertNull(HealCache.positive(By.id("other"), "/login"));
  }

  @Test
  void negative_expiresAndClearsOnDomChange() throws Exception {
    By broken = By.xpath("//button[@id='gone']");
    assertFalse(HealCache.recentlyDeclined(broken, "domA"));

    HealCache.recordDeclined(broken, "domA");
    assertTrue(HealCache.recentlyDeclined(broken, "domA"));
    // a different DOM state is a fresh chance
    assertFalse(HealCache.recentlyDeclined(broken, "domB"));
  }

  @Test
  void clear_wipesBoth() {
    HealCache.recordPositive(By.id("a"), "/p", By.id("b"), "By.id(\"b\")", AiSuggestion.id("b"));
    HealCache.recordDeclined(By.id("c"), "dom");
    HealCache.recordFailing(By.id("d"));
    HealCache.clear();
    assertNull(HealCache.positive(By.id("a"), "/p"));
    assertFalse(HealCache.recentlyDeclined(By.id("c"), "dom"));
    assertEquals(0, HealCache.failCount(By.id("d")));
  }

  @Test
  void failCount_countsPerRunAndPeeksWithoutIncrementing() {
    By broken = By.name("first_name");
    assertEquals(0, HealCache.failCount(broken));
    assertEquals(1, HealCache.recordFailing(broken));
    assertEquals(2, HealCache.recordFailing(broken));
    assertEquals(2, HealCache.failCount(broken));   // peek — no increment
    assertEquals(2, HealCache.failCount(broken));
  }

  @Test
  void everHealed_tracksWhetherALocatorHealedThisRun_anyPage() {
    By broken = By.name("first_name");
    assertFalse(HealCache.everHealed(broken));
    HealCache.recordPositive(broken, "/pim/addEmployee", By.name("firstName"), "By.name(\"firstName\")",
        AiSuggestion.nameAttr("firstName"));
    assertTrue(HealCache.everHealed(broken));                       // page-independent
    assertNull(HealCache.positive(broken, "/some/other/page"));     // but the positive hit is page-keyed
  }
}
