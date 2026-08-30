package io.github.qtpsudhakarproducts.tamash.pagefactory;

import io.github.qtpsudhakarproducts.tamash.bindings.Bindings;
import io.github.qtpsudhakarproducts.tamash.bindings.SourceLocations;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsElement;
import org.openqa.selenium.interactions.Locatable;
import org.openqa.selenium.support.FindAll;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.FindBys;
import org.openqa.selenium.support.pagefactory.Annotations;
import org.openqa.selenium.support.pagefactory.DefaultElementLocatorFactory;
import org.openqa.selenium.support.pagefactory.DefaultFieldDecorator;
import org.openqa.selenium.support.pagefactory.ElementLocator;
import org.openqa.selenium.support.pagefactory.ElementLocatorFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * A drop-in {@code FieldDecorator} for {@code PageFactory} that puts a self-healing proxy behind
 * each {@code @FindBy} / {@code @FindBys} / {@code @FindAll} {@code WebElement} field. Use it via
 * {@link TamashPageFactory#initElements(WebDriver, Object)}.
 *
 * <p>{@code @FindBy List<WebElement>} fields and any un-annotated fields fall through to Selenium's
 * default handling (a healed list has no well-defined "expected count").
 */
public final class TamashFieldDecorator extends DefaultFieldDecorator {

  private final WebDriver driver;

  public TamashFieldDecorator(WebDriver driver) {
    // Locate against the RAW driver — this decorator's own proxy does all the healing, so the
    // inner findElement must not also go through the @UseTamashSelenium wrapper (double-heal), and
    // the healer's internal DOM-identity checks need un-proxied elements.
    this(Bindings.unwrap(driver), new DefaultElementLocatorFactory(Bindings.unwrap(driver)));
  }

  public TamashFieldDecorator(WebDriver driver, ElementLocatorFactory factory) {
    super(factory);
    this.driver = Bindings.unwrap(driver);
  }

  @Override
  public Object decorate(ClassLoader loader, Field field) {
    boolean isElement = WebElement.class.isAssignableFrom(field.getType());
    if (!isElement || !hasLocatorAnnotation(field)) {
      return super.decorate(loader, field);
    }
    ElementLocator locator = factory.createLocator(field);
    if (locator == null) {
      return super.decorate(loader, field);
    }
    By by = safeBuildBy(field);
    if (by == null) {
      return super.decorate(loader, field);
    }
    String description = describe(field);
    String sourceLocation = SourceLocations.locateFindByField(field.getDeclaringClass(), field.getName());
    String enclosingClass = field.getDeclaringClass().getSimpleName();

    return Proxy.newProxyInstance(loader,
        new Class<?>[]{WebElement.class, WrapsElement.class, Locatable.class},
        new LazyHealingElementHandler(driver, locator, by, description, sourceLocation,
            field.getName(), enclosingClass));
  }

  private static boolean hasLocatorAnnotation(Field field) {
    return field.isAnnotationPresent(FindBy.class)
        || field.isAnnotationPresent(FindBys.class)
        || field.isAnnotationPresent(FindAll.class);
  }

  private static By safeBuildBy(Field field) {
    try {
      return new Annotations(field).buildBy();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String describe(Field field) {
    SourceLocations.Decoded d = SourceLocations.decodeVariableName(field.getName());
    if (d == null) {
      return field.getName();
    }
    return d.typeHint() != null ? d.name() + " (" + d.typeHint() + ")" : d.name();
  }
}
