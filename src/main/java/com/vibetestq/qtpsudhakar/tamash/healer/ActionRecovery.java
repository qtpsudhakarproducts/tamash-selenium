package com.vibetestq.qtpsudhakar.tamash.healer;

import com.vibetestq.qtpsudhakar.tamash.healer.providers.ActionTactic;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.ActionTacticResult;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.HealProvider;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.SuggestActionTacticInput;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.TokenUsage;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.util.Map;
import java.util.Set;

/**
 * Port of src/healer/action-recovery.ts — a strictly second-order fallback, only ever called
 * after a locator has already been successfully healed but replaying the original action on it
 * still failed for a non-selector reason. Bounded by design: the AI only ever picks among the
 * fixed tactic menu below.
 */
public final class ActionRecovery {
  private ActionRecovery() {}

  private static final Set<String> CLICK_ACTIONS = Set.of("click", "submit");
  private static final Map<String, String> DISPATCH_EVENT_MAP = Map.of(
      "click", "click", "submit", "submit", "sendKeys", "input", "clear", "input");

  private static final long WAIT_TACTIC_DELAY_MS = 500;

  public record Outcome(RecoveryHealing healing, TokenUsage usage, String stage) {}

  public record RecoveryHealing(String provider, String warning, Object result) {}

  public static Object applyActionTactic(ActionTactic tactic, WebDriver driver, WebElement element, String action,
                                         Object[] args, Replayer plainReplay) throws Throwable {
    JavascriptExecutor js = (JavascriptExecutor) driver;
    switch (tactic) {
      case SCROLL:
        js.executeScript("arguments[0].scrollIntoView({block:'center', inline:'center'});", element);
        return plainReplay.replay(element);
      case WAIT:
        Thread.sleep(WAIT_TACTIC_DELAY_MS);
        return plainReplay.replay(element);
      case FORCE:
        if (CLICK_ACTIONS.contains(action)) {
          js.executeScript("arguments[0].click();", element);
          return null;
        }
        if ("sendKeys".equals(action) || "fill".equals(action) || "type".equals(action)) {
          String value = firstArgString(args);
          js.executeScript("arguments[0].value = arguments[1];"
              + "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));"
              + "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", element, value);
          return null;
        }
        new Actions(driver).moveToElement(element).click().perform();
        return null;
      case DISPATCH: {
        String eventType = DISPATCH_EVENT_MAP.getOrDefault(action, "click");
        js.executeScript(
            "arguments[0].dispatchEvent(new MouseEvent(arguments[1], {bubbles:true, cancelable:true, view:window}));",
            element, eventType);
        return null;
      }
      default:
        throw new IllegalStateException("tactic none is not applicable");
    }
  }

  private static String firstArgString(Object[] args) {
    if (args == null || args.length == 0 || args[0] == null) {
      return "";
    }
    Object v = args[0];
    if (v instanceof CharSequence[] arr) {
      return String.join("", arr);
    }
    if (v instanceof Object[] arr) {
      StringBuilder sb = new StringBuilder();
      for (Object o : arr) {
        sb.append(o);
      }
      return sb.toString();
    }
    return String.valueOf(v);
  }

  public static Outcome tryActionRecovery(HealProvider provider, WebDriver driver, WebElement element, String action,
                                          Object[] args, String replayErrorMessage, double timeoutMs,
                                          Replayer plainReplay) {
    ActionTacticResult result = provider.suggestActionTactic(
        new SuggestActionTacticInput(action, replayErrorMessage, timeoutMs));
    if (result == null) {
      return new Outcome(null, null, "action_recovery_failed");
    }
    if (result.getTactic() == ActionTactic.NONE) {
      return new Outcome(null, result.getUsage(), "action_recovery_declined");
    }
    try {
      Object tacticResult = applyActionTactic(result.getTactic(), driver, element, action, args, plainReplay);
      return new Outcome(
          new RecoveryHealing(provider.getName(),
              "Recovered using " + provider.getName() + " via action recovery (" + result.getTactic().name().toLowerCase() + ").",
              tacticResult),
          result.getUsage(), null);
    } catch (Throwable t) {
      return new Outcome(null, result.getUsage(), "action_recovery_failed");
    }
  }
}
