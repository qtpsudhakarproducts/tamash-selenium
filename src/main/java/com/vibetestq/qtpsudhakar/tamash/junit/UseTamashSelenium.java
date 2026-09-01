package com.vibetestq.qtpsudhakar.tamash.junit;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this on a test class to get a self-healing {@link org.openqa.selenium.WebDriver} injected
 * into test methods.
 *
 * <pre>{@code
 * @UseTamashSelenium
 * class LoginTest {
 *   @Test
 *   void logsIn(WebDriver driver) {
 *     driver.get("https://the-internet.herokuapp.com/login");
 *     driver.findElement(By.id("username")).sendKeys("tomsmith"); // healed automatically if this breaks
 *   }
 * }
 * }</pre>
 *
 * <p>The extension runs its own driver lifecycle (via {@link com.vibetestq.qtpsudhakar.tamash.SeleniumLifecycle})
 * — a fresh driver per test method by default, or one per class with {@code TAMASH_REUSE_DRIVER=true}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(TamashSeleniumExtension.class)
public @interface UseTamashSelenium {
}
