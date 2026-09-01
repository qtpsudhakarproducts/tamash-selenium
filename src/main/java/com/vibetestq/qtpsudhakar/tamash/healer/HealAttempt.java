package com.vibetestq.qtpsudhakar.tamash.healer;

import org.json.JSONObject;

/**
 * One entry per meaningful recovery attempt (cache / ref / text / action-recovery), in
 * the order they ran. Unlike {@code failureStage}/{@code suggestedSelector} (which only ever
 * reflect the LAST attempt), this list is never overwritten — see src/healer/index.ts's
 * {@code HealAttempt}.
 */
public final class HealAttempt {
  public String method;            // cache | ref | text | action-recovery
  public String provider;
  public String suggestedSelector;
  public boolean succeeded;
  public String stage;
  public String error;
  public String aiRef;
  public String aiNearbyRef;
  public String aiNearbyText;
  public String aiNearbyRole;
  public Boolean scoped;

  public static HealAttempt of(String method) {
    HealAttempt a = new HealAttempt();
    a.method = method;
    return a;
  }

  public HealAttempt provider(String v) { this.provider = v; return this; }
  public HealAttempt suggested(String v) { this.suggestedSelector = v; return this; }
  public HealAttempt succeeded(boolean v) { this.succeeded = v; return this; }
  public HealAttempt stage(String v) { this.stage = v; return this; }
  public HealAttempt error(String v) { this.error = v; return this; }
  public HealAttempt scoped(Boolean v) { this.scoped = v; return this; }

  public HealAttempt aiRefContext(String ref, String nearbyRef, String nearbyText, String nearbyRole) {
    this.aiRef = ref;
    this.aiNearbyRef = nearbyRef;
    this.aiNearbyText = nearbyText;
    this.aiNearbyRole = nearbyRole;
    return this;
  }

  public JSONObject toJson() {
    JSONObject o = new JSONObject();
    o.put("method", method);
    if (provider != null) o.put("provider", provider);
    if (suggestedSelector != null) o.put("suggestedSelector", suggestedSelector);
    o.put("succeeded", succeeded);
    if (stage != null) o.put("stage", stage);
    if (error != null) o.put("error", error);
    if (aiRef != null) o.put("aiRef", aiRef);
    if (aiNearbyRef != null) o.put("aiNearbyRef", aiNearbyRef);
    if (aiNearbyText != null) o.put("aiNearbyText", aiNearbyText);
    if (aiNearbyRole != null) o.put("aiNearbyRole", aiNearbyRole);
    if (scoped != null) o.put("scoped", scoped);
    return o;
  }
}
