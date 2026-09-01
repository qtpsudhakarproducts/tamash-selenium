package com.vibetestq.qtpsudhakar.tamash.healer.providers;

/**
 * Flattened Java equivalent of the TS {@code AiSuggestion} discriminated union
 * (src/healer/providers/types.ts) — a single class with a {@code strategy} tag and every possible
 * field nullable, populated per strategy via the static factories below.
 *
 * <p>TS splits {@code SelectorSuggestion} (persistable) from {@code AiSuggestion} (adds the
 * transient {@code ref}) purely for a compile-time guarantee that a {@code ref} never reaches
 * {@code heals.jsonl}. Java has no clean union type, so that guarantee moves to runtime:
 * {@link #isPersistable()} is false for {@code ref}/{@code none}, and the heal-log write path
 * checks it (mirrors TS {@code excludeRefStrategy}).
 *
 * <p>Selenium strategies: {@code none, ref, css, xpath, id, name, text, near, adjacent, scoped,
 * containing, normalized}. (A {@code role} the model reports is folded into {@code css}; {@code
 * near}/{@code adjacent}/{@code scoped} still carry a {@code role} field used to build XPath.)
 */
public final class AiSuggestion {
  private final String strategy;

  // direct Selenium locators
  private final String css;
  private final String xpath;
  private final String id;
  private final String nameAttr;
  private final String text;

  // role / structural identity (near / adjacent / scoped / containing)
  private final String role;
  private final String name;          // accessible name of the target (scoped)
  private final String anchorText;
  private final Integer parentLevels;      // 1 or 2, filled in at heal time by resolveNearLocator
  private final Integer anchorClimbLevels; // >= 0, computed by findAdjacentBranchPath
  private final String containerRole;
  private final String containerName;

  // ref strategy (transient — never persisted)
  private final String ref;
  private final String nearbyRef;
  private final String nearbyText;
  private final String nearbyRole;

  // normalized (a derived selector that fit no structured shape — a raw css/xpath string)
  private final String code;

  private AiSuggestion(Builder b) {
    this.strategy = b.strategy;
    this.css = b.css;
    this.xpath = b.xpath;
    this.id = b.id;
    this.nameAttr = b.nameAttr;
    this.text = b.text;
    this.role = b.role;
    this.name = b.name;
    this.anchorText = b.anchorText;
    this.parentLevels = b.parentLevels;
    this.anchorClimbLevels = b.anchorClimbLevels;
    this.containerRole = b.containerRole;
    this.containerName = b.containerName;
    this.ref = b.ref;
    this.nearbyRef = b.nearbyRef;
    this.nearbyText = b.nearbyText;
    this.nearbyRole = b.nearbyRole;
    this.code = b.code;
  }

  // ---- factories -----------------------------------------------------------

  public static AiSuggestion none() {
    return new Builder("none").build();
  }

  public static AiSuggestion ref(String ref, String nearbyRef, String nearbyText, String nearbyRole) {
    return new Builder("ref").ref(ref).nearbyRef(nearbyRef).nearbyText(nearbyText).nearbyRole(nearbyRole).build();
  }

  public static AiSuggestion css(String css) {
    return new Builder("css").css(css).build();
  }

  public static AiSuggestion xpath(String xpath) {
    return new Builder("xpath").xpath(xpath).build();
  }

  public static AiSuggestion id(String id) {
    return new Builder("id").id(id).build();
  }

  public static AiSuggestion nameAttr(String nameAttr) {
    return new Builder("name").nameAttr(nameAttr).build();
  }

  public static AiSuggestion text(String text) {
    return new Builder("text").text(text).build();
  }

  public static AiSuggestion near(String anchorText, String role, Integer parentLevels) {
    return new Builder("near").anchorText(anchorText).role(role).parentLevels(parentLevels).build();
  }

  public static AiSuggestion adjacent(String anchorText, String role, Integer anchorClimbLevels) {
    return new Builder("adjacent").anchorText(anchorText).role(role).anchorClimbLevels(anchorClimbLevels).build();
  }

  public static AiSuggestion scoped(String containerRole, String containerName, String role, String name) {
    return new Builder("scoped").containerRole(containerRole).containerName(containerName).role(role).name(name).build();
  }

  public static AiSuggestion containing(String role, String anchorText) {
    return new Builder("containing").role(role).anchorText(anchorText).build();
  }

  public static AiSuggestion normalized(String code) {
    return new Builder("normalized").code(code).build();
  }

  // ---- accessors ----------------------------------------------------------

  public String getStrategy() { return strategy; }
  public String getCss() { return css; }
  public String getXpath() { return xpath; }
  public String getId() { return id; }
  public String getNameAttr() { return nameAttr; }
  public String getText() { return text; }
  public String getRole() { return role; }
  public String getName() { return name; }
  public String getAnchorText() { return anchorText; }
  public Integer getParentLevels() { return parentLevels; }
  public Integer getAnchorClimbLevels() { return anchorClimbLevels; }
  public String getContainerRole() { return containerRole; }
  public String getContainerName() { return containerName; }
  public String getRef() { return ref; }
  public String getNearbyRef() { return nearbyRef; }
  public String getNearbyText() { return nearbyText; }
  public String getNearbyRole() { return nearbyRole; }
  public String getCode() { return code; }

  public boolean isNone() { return "none".equals(strategy); }

  /** True unless this is a {@code ref} (page-instance-only) or {@code none} — mirrors TS
   *  {@code excludeRefStrategy}: only a persistable suggestion may reach {@code heals.jsonl}. */
  public boolean isPersistable() {
    return !"ref".equals(strategy) && !"none".equals(strategy);
  }

  /** Returns a copy with {@code parentLevels} set (resolveNearLocator fills this in at heal time). */
  public AiSuggestion withParentLevels(Integer levels) {
    return toBuilder().parentLevels(levels).build();
  }

  // ---- JSON (heal-log persistence — only persistable strategies) -----------

  public org.json.JSONObject toJson() {
    org.json.JSONObject o = new org.json.JSONObject();
    o.put("strategy", strategy);
    switch (strategy) {
      case "css" -> o.put("css", css);
      case "xpath" -> o.put("xpath", xpath);
      case "id" -> o.put("id", id);
      case "name" -> o.put("name", nameAttr);
      case "text" -> o.put("text", text);
      case "near" -> { o.put("anchorText", anchorText); o.put("role", role); putIf(o, "parentLevels", parentLevels); }
      case "adjacent" -> { o.put("anchorText", anchorText); o.put("role", role); putIf(o, "anchorClimbLevels", anchorClimbLevels); }
      case "scoped" -> { o.put("containerRole", containerRole); putIf(o, "containerName", containerName); o.put("role", role); putIf(o, "name", name); }
      case "containing" -> { o.put("role", role); o.put("anchorText", anchorText); }
      case "normalized" -> o.put("code", code);
      default -> { }
    }
    return o;
  }

  private static void putIf(org.json.JSONObject o, String key, Object v) {
    if (v != null) {
      o.put(key, v);
    }
  }

  public static AiSuggestion fromJson(org.json.JSONObject o) {
    if (o == null || !o.has("strategy")) {
      return null;
    }
    String strat = o.optString("strategy", null);
    if (strat == null) {
      return null;
    }
    return switch (strat) {
      case "css" -> AiSuggestion.css(o.optString("css", null));
      case "xpath" -> AiSuggestion.xpath(o.optString("xpath", null));
      case "id" -> AiSuggestion.id(o.optString("id", null));
      case "name" -> AiSuggestion.nameAttr(o.optString("name", null));
      case "text" -> AiSuggestion.text(o.optString("text", null));
      case "near" -> AiSuggestion.near(o.optString("anchorText", null), o.optString("role", null),
          o.has("parentLevels") ? o.optInt("parentLevels") : null);
      case "adjacent" -> AiSuggestion.adjacent(o.optString("anchorText", null), o.optString("role", null),
          o.has("anchorClimbLevels") ? o.optInt("anchorClimbLevels") : null);
      case "scoped" -> AiSuggestion.scoped(o.optString("containerRole", null),
          o.has("containerName") ? o.optString("containerName") : null,
          o.optString("role", null), o.has("name") ? o.optString("name") : null);
      case "containing" -> AiSuggestion.containing(o.optString("role", null), o.optString("anchorText", null));
      case "normalized" -> AiSuggestion.normalized(o.optString("code", null));
      default -> null;
    };
  }

  private Builder toBuilder() {
    Builder b = new Builder(strategy);
    b.css = css; b.xpath = xpath; b.id = id; b.nameAttr = nameAttr; b.text = text;
    b.role = role; b.name = name; b.anchorText = anchorText;
    b.parentLevels = parentLevels; b.anchorClimbLevels = anchorClimbLevels;
    b.containerRole = containerRole; b.containerName = containerName;
    b.ref = ref; b.nearbyRef = nearbyRef; b.nearbyText = nearbyText; b.nearbyRole = nearbyRole;
    b.code = code;
    return b;
  }

  private static final class Builder {
    final String strategy;
    String css, xpath, id, nameAttr, text;
    String role, name, anchorText, containerRole, containerName, code;
    String ref, nearbyRef, nearbyText, nearbyRole;
    Integer parentLevels, anchorClimbLevels;

    Builder(String strategy) { this.strategy = strategy; }

    Builder css(String v) { this.css = v; return this; }
    Builder xpath(String v) { this.xpath = v; return this; }
    Builder id(String v) { this.id = v; return this; }
    Builder nameAttr(String v) { this.nameAttr = v; return this; }
    Builder text(String v) { this.text = v; return this; }
    Builder role(String v) { this.role = v; return this; }
    Builder name(String v) { this.name = v; return this; }
    Builder anchorText(String v) { this.anchorText = v; return this; }
    Builder parentLevels(Integer v) { this.parentLevels = v; return this; }
    Builder anchorClimbLevels(Integer v) { this.anchorClimbLevels = v; return this; }
    Builder containerRole(String v) { this.containerRole = v; return this; }
    Builder containerName(String v) { this.containerName = v; return this; }
    Builder ref(String v) { this.ref = v; return this; }
    Builder nearbyRef(String v) { this.nearbyRef = v; return this; }
    Builder nearbyText(String v) { this.nearbyText = v; return this; }
    Builder nearbyRole(String v) { this.nearbyRole = v; return this; }
    Builder code(String v) { this.code = v; return this; }

    AiSuggestion build() { return new AiSuggestion(this); }
  }
}
