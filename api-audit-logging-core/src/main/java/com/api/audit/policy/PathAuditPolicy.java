package com.api.audit.policy;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.spi.AuditRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

/**
 * Policy that audits requests based on configuration-declared path rules.
 *
 * <p>This is the zero-code-change mode of the library. An operator declares rules under {@code
 * audit.logging.policies.path-based.rules} and the policy decides whether a request is audited — no
 * annotation and no application code change is required. The application only needs the library on
 * its classpath, the rules in configuration, and a JVM restart.
 *
 * <p><b>Rule evaluation:</b>
 *
 * <ol>
 *   <li>Only rules whose matcher accepts the request path are considered. A rule additionally
 *       filters by HTTP method when one or more {@code methods} are declared.
 *   <li>Among matching rules, an explicit skip rule ({@code audit: false}) always wins. If several
 *       skip rules match, the most specific one is reported (which is a no-op for the boolean
 *       outcome, but keeps logging consistent).
 *   <li>If no skip rule matches, the most specific positive rule wins.
 * </ol>
 *
 * <p><b>Specificity:</b> rules are ranked by a deterministic score so that operators do not have to
 * rely on declaration order. The score rewards longer literal prefixes, more path segments, fewer
 * wildcards, and an explicit HTTP method constraint. See {@link #specificity} for the exact
 * formula.
 *
 * <p><b>Immutability &amp; thread safety:</b> the compiled rule set is built once in the
 * constructor and never mutated. Ant matching uses {@link AntPathMatcher}, and regular expressions
 * are pre-compiled. The policy is safe for concurrent use on request threads.
 *
 * @author Puneet Swarup
 * @see AuditPolicy
 * @see AuditDecision
 */
@Slf4j
public class PathAuditPolicy implements AuditPolicy {

  private final boolean enabled;
  private final AntPathMatcher antMatcher = new AntPathMatcher();
  private final List<CompiledRule> rules;

  /**
   * Creates the policy and pre-compiles the configured rules.
   *
   * <p>Invalid patterns (for example a malformed regular expression) are logged and skipped rather
   * than failing application startup, so a typo in one rule does not take the whole service down.
   * Operators see the warning in the logs and can correct the rule.
   *
   * @param properties the library configuration properties; never {@code null}
   */
  public PathAuditPolicy(AuditLoggingProperties properties) {
    AuditLoggingProperties.PathBasedPolicy pathBased = properties.getPolicies().getPathBased();
    this.enabled = pathBased.isEnabled();
    this.rules = compile(pathBased.getRules());

    if (enabled && rules.isEmpty()) {
      log.warn(
          "[AuditLog] Path-based auditing is enabled but no valid rules were configured under"
              + " audit.logging.policies.path-based.rules. No path will be audited by this policy.");
    } else if (enabled) {
      log.info("[AuditLog] Path-based auditing active with {} compiled rule(s).", rules.size());
    }
  }

  @Override
  public int getOrder() {
    return AuditPolicyOrder.PATH_BASED;
  }

  @Override
  public Optional<AuditDecision> decide(AuditRequest request, String path) {
    if (!enabled || rules.isEmpty()) {
      return Optional.empty();
    }

    String method = request.getMethod();
    List<CompiledRule> matches = new ArrayList<>();
    for (CompiledRule rule : rules) {
      if (rule.matches(path, method)) {
        matches.add(rule);
      }
    }

    if (matches.isEmpty()) {
      return Optional.empty();
    }

    // Explicit skips win over positive matches.
    Optional<CompiledRule> skip =
        matches.stream().filter(r -> !r.audit).max((a, b) -> Integer.compare(a.score, b.score));
    if (skip.isPresent()) {
      return Optional.of(AuditDecision.skip());
    }

    CompiledRule winner =
        matches.stream().max((a, b) -> Integer.compare(a.score, b.score)).orElseThrow();

    String description =
        StringUtils.hasText(winner.description)
            ? winner.description
            : method.toUpperCase(Locale.ROOT) + " " + path;

    return Optional.of(new AuditDecision(true, description, winner.tags, winner.capture));
  }

  private List<CompiledRule> compile(List<AuditLoggingProperties.PathRule> configured) {
    List<CompiledRule> compiled = new ArrayList<>();
    if (configured == null) {
      return compiled;
    }
    for (AuditLoggingProperties.PathRule rule : configured) {
      if (!StringUtils.hasText(rule.getPattern())) {
        log.warn("[AuditLog] Ignoring path audit rule with blank pattern.");
        continue;
      }
      try {
        compiled.add(new CompiledRule(rule));
      } catch (PatternSyntaxException ex) {
        log.warn(
            "[AuditLog] Ignoring path audit rule with invalid pattern '{}': {}",
            rule.getPattern(),
            ex.getMessage());
      }
    }
    return compiled;
  }

  /**
   * Computes a deterministic specificity score for a rule.
   *
   * <p>Higher scores are more specific. The formula is intentionally simple and documented so that
   * operators can predict precedence:
   *
   * <pre>
   * score = (literal characters in pattern)   // longer literal prefixes are more specific
   *       + (path segments * 10)              // deeper paths are more specific
   *       - (wildcard count * 50)             // broad patterns lose
   *       + (method constrained ? 100 : 0)    // an explicit method is more specific
   * </pre>
   *
   * @param pattern the raw rule pattern
   * @param methods the configured HTTP methods (may be empty)
   * @return the specificity score
   */
  static int specificity(String pattern, List<String> methods) {
    if (pattern == null) {
      return Integer.MIN_VALUE;
    }
    int literalChars = 0;
    int wildcards = 0;
    int segments = 1;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '*' || c == '?' || c == '[') {
        wildcards++;
      } else if (c == '/') {
        segments++;
        literalChars++;
      } else {
        literalChars++;
      }
    }
    int methodBonus = (methods == null || methods.isEmpty()) ? 0 : 100;
    return literalChars + (segments * 10) - (wildcards * 50) + methodBonus;
  }

  /** A configured rule with its matcher and specificity pre-computed. */
  private final class CompiledRule {

    private final String pattern;
    private final PathMatcherType matcherType;
    private final boolean audit;
    private final List<String> methods;
    private final String description;
    private final CaptureMode capture;
    private final Map<String, String> tags;
    private final int score;
    private final Pattern regex;

    private CompiledRule(AuditLoggingProperties.PathRule rule) {
      this.pattern = rule.getPattern();
      this.matcherType = rule.getMatcher();
      this.audit = rule.isAudit();
      this.methods = normalizeMethods(rule.getMethods());
      this.description = rule.getDescription();
      this.capture = rule.getCapture();
      this.tags = rule.getTags() == null ? Map.of() : Map.copyOf(rule.getTags());
      this.score = specificity(pattern, methods);
      this.regex = matcherType == PathMatcherType.REGEX ? Pattern.compile(pattern) : null;
    }

    private boolean matches(String path, String method) {
      if (!matchesMethod(method)) {
        return false;
      }
      if (matcherType == PathMatcherType.REGEX) {
        return regex.matcher(path).find();
      }
      return antMatcher.match(pattern, path);
    }

    private boolean matchesMethod(String method) {
      if (methods.isEmpty()) {
        return true;
      }
      return methods.contains(method == null ? "" : method.toUpperCase(Locale.ROOT));
    }
  }

  private static List<String> normalizeMethods(List<String> methods) {
    if (methods == null || methods.isEmpty()) {
      return List.of();
    }
    List<String> normalized = new ArrayList<>(methods.size());
    for (String m : methods) {
      if (StringUtils.hasText(m)) {
        normalized.add(m.trim().toUpperCase(Locale.ROOT));
      }
    }
    return List.copyOf(normalized);
  }
}
