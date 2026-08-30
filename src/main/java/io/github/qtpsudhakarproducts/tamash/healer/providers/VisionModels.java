package io.github.qtpsudhakarproducts.tamash.healer.providers;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Port of src/healer/providers/vision-models.ts — a best-effort name heuristic, not an
 * authoritative capability registry. A false positive just means the vision fallback is attempted
 * and the provider call degrades; a false negative just means it's never attempted.
 */
public final class VisionModels {
  private VisionModels() {}

  private record Patterns(List<Pattern> include, List<Pattern> exclude) {}

  private static Pattern ci(String regex) {
    return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
  }

  private static final Patterns OPENAI = new Patterns(
      List.of(ci("gpt-4o"), ci("gpt-4\\.1"), ci("gpt-4\\.5"), ci("gpt-5"), ci("\\bo1\\b"), ci("\\bo3\\b"), ci("\\bo4\\b"), ci("vision")),
      List.of(ci("gpt-3\\.5"), ci("embedding"), ci("whisper"), ci("\\btts\\b"), ci("moderation")));

  private static final Patterns ANTHROPIC = new Patterns(
      List.of(ci("claude-(3|4|5)"), ci("claude-(opus|sonnet|haiku)-(3|4|5)"), ci("^(haiku|sonnet|opus)(-latest)?$")),
      List.of(ci("claude-2"), ci("claude-instant")));

  private static final Patterns GEMINI = new Patterns(
      List.of(ci("gemini")),
      List.of(ci("embedding"), ci("\\baqa\\b")));

  private static final Patterns OLLAMA = new Patterns(
      List.of(ci("llava"), ci("bakllava"), ci("moondream"), ci("llama3\\.2-vision"), ci("qwen2(\\.5)?-vl"),
          ci("minicpm-v"), ci("pixtral"), ci("granite3\\.2-vision"), ci("gemma-?[3-9]"), ci("vision")),
      List.of());

  public static boolean isVisionCapableModel(String vendor, String model) {
    if (model == null) return false;
    Patterns p = switch (vendor) {
      case "openai" -> OPENAI;
      case "anthropic" -> ANTHROPIC;
      case "gemini" -> GEMINI;
      case "ollama" -> OLLAMA;
      default -> null;
    };
    if (p == null) return false;
    for (Pattern ex : p.exclude()) {
      if (ex.matcher(model).find()) return false;
    }
    for (Pattern in : p.include()) {
      if (in.matcher(model).find()) return true;
    }
    return false;
  }
}
