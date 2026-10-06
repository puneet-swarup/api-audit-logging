package com.api.audit.policy;

/**
 * Supported matching strategies for a path-based audit rule.
 *
 * <p>The default is {@link #ANT}, which matches Spring's well-known Ant-style path patterns such as
 * {@code /api/v1/payments/**}. {@link #REGEX} is offered as an explicit alternative for teams that
 * already express routing rules as regular expressions; it is never the default because Ant
 * patterns are easier to read and less prone to catastrophic backtracking.
 *
 * @author Puneet Swarup
 * @see com.api.audit.config.AuditLoggingProperties.PathRule
 */
public enum PathMatcherType {

  /**
   * Ant-style path pattern, evaluated with Spring's {@code AntPathMatcher}. Examples:
   *
   * <pre>{@code
   * /api/v1/payments/**
   * /api/**&#47;orders/*
   * }</pre>
   */
  ANT,

  /**
   * Java regular expression matched against the request path with {@code Matcher.find()}.
   *
   * <pre>{@code
   * /api/v[0-9]+/payments/.*
   * }</pre>
   */
  REGEX
}
