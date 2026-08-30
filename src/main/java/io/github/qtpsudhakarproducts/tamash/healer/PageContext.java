package io.github.qtpsudhakarproducts.tamash.healer;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * Java stand-in for the TS {@code PageContext} — the "thing a replacement locator is resolved
 * against". For Selenium that's a {@link SearchContext} (normally the {@link WebDriver} itself,
 * occasionally a container {@link WebElement} for a scoped find) plus the owning driver, which is
 * what the snapshot / screenshot / JS-identity calls need.
 *
 * <p>iframe healing threads a {@code frameChain} through: the healer switches into it before
 * capturing / finding and back to {@code defaultContent()} after — Selenium finds never descend
 * into a frame on their own.
 */
public final class PageContext {
  private final WebDriver driver;
  private final SearchContext searchContext;
  private final List<By> frameChain;

  private PageContext(WebDriver driver, SearchContext searchContext, List<By> frameChain) {
    this.driver = driver;
    this.searchContext = searchContext;
    this.frameChain = frameChain == null ? List.of() : frameChain;
  }

  public static PageContext of(WebDriver driver) {
    return new PageContext(driver, driver, List.of());
  }

  public static PageContext of(WebDriver driver, SearchContext searchContext) {
    return new PageContext(driver, searchContext != null ? searchContext : driver, List.of());
  }

  public static PageContext of(WebDriver driver, List<By> frameChain) {
    return new PageContext(driver, driver, frameChain);
  }

  public WebDriver driver() {
    return driver;
  }

  public SearchContext searchContext() {
    return searchContext;
  }

  public List<By> frameChain() {
    return frameChain;
  }

  public JavascriptExecutor js() {
    return (JavascriptExecutor) driver;
  }

  /** Switch into this context's frame chain (no-op at top level). */
  public void enterFrame() {
    if (frameChain.isEmpty()) {
      return;
    }
    driver.switchTo().defaultContent();
    for (By frame : frameChain) {
      driver.switchTo().frame(driver.findElement(frame));
    }
  }

  public void exitFrame() {
    if (!frameChain.isEmpty()) {
      try {
        driver.switchTo().defaultContent();
      } catch (Exception ignored) {
        // best-effort
      }
    }
  }

  public WebElement find(By by) {
    return searchContext.findElement(by);
  }

  public List<WebElement> findAll(By by) {
    return searchContext.findElements(by);
  }

  public int count(By by) {
    try {
      return searchContext.findElements(by).size();
    } catch (Exception e) {
      return 0;
    }
  }

  /** First match or null (never throws — a candidate ladder tries many shapes). */
  public WebElement findOrNull(By by) {
    try {
      List<WebElement> all = searchContext.findElements(by);
      return all.isEmpty() ? null : all.get(0);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
