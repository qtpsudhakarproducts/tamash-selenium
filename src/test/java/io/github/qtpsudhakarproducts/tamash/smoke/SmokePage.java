package io.github.qtpsudhakarproducts.tamash.smoke;

/** The tiny page both smoke suites (TestNG + Cucumber) drive. */
final class SmokePage {
  private SmokePage() {}

  static final String URL = "data:text/html,"
      + "<html><body>"
      + "<h1>Smoke</h1>"
      + "<label for='username'>Username</label>"
      + "<input id='username' name='username' type='text'/>"
      + "<button id='go'>Go</button>"
      + "</body></html>";
}
