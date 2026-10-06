# Path-Based Auditing Guide (Zero Code Changes)

This guide explains how to audit API traffic without touching application code - no @AuditLog annotations, no controller edits, no custom beans. You add the library, declare path rules in configuration, and restart the JVM.

## Contents

- Why this exists
- Quick start
- How the decision engine works
- Rule reference
- Pattern matching: Ant vs regex
- Precedence and specificity
- Capture modes
- Tags
- Explicit skip rules
- Method-scoped rules
- Mixing annotations and path rules
- Custom policies (SPI)
- Troubleshooting

## Why this exists

Before path-based auditing, capturing a request required annotating the controller or method with @AuditLog. That is fine when you own the code, but it is not truly pluggable: you cannot enable auditing for a third-party endpoint, a legacy controller you do not want to modify, or a large surface area, without editing and redeploying application code.

Path-based auditing moves the decision from code into configuration. The capture machinery (request and response wrapping, masking, async persistence) is unchanged - only the decision source changes.

## Quick start

Step 1. Add the library dependency.

    implementation "io.github.puneet-swarup:api-audit-logging-starter:2.2.0"

Step 2. Enable path-based auditing and declare rules.

    audit:
      logging:
        enabled: true
        policies:
          path-based:
            enabled: true
            rules:
              - pattern: /api/v1/payments/**
                description: Payment APIs
                tags:
                  module: payments
                  tier: critical

Step 3. Restart the JVM.

Requests to /api/v1/payments/** are now audited. The controller behind that path needs no annotation and no code change.

## How the decision engine works

The library evaluates every registered policy and reduces the results into exactly one decision per request. Two policies ship out of the box:

| Policy | Order | What it does |
|---|---:|---|
| PathAuditPolicy | 100 | Matches the request path against configured rules |
| AnnotationAuditPolicy | 200 | Opts in requests whose handler carries @AuditLog |

Because the annotation policy runs later, when both a path rule and an annotation match, the annotation's description and tags are preferred. An explicit skip (audit: false) always wins, regardless of order. Custom policies can be placed before or after the built-ins.

The engine returns a single decision, so a request is published at most once - there is no double logging even when several rules match.

## Rule reference

Each entry under audit.logging.policies.path-based.rules supports:

| Property | Default | Description |
|---|---|---|
| pattern | - (required) | The path pattern to match |
| matcher | ANT | ANT or REGEX |
| audit | true | false creates an explicit skip rule |
| methods | empty (all) | HTTP methods the rule applies to, e.g. [POST, PUT] |
| description | derived | Human label; falls back to "METHOD path" |
| capture | FULL | FULL, METADATA_ONLY, or BODY_ONLY |
| tags | empty | Arbitrary key/value dimensions attached to the record |

## Pattern matching: Ant vs regex

By default a rule uses Ant-style patterns, evaluated with Spring's AntPathMatcher:

    - pattern: /api/v1/payments/**

When you already express routing as a regular expression, set matcher: REGEX:

    - pattern: /api/v[0-9]+/orders/.*
      matcher: REGEX

Ant is the recommended default because it is easier to read and avoids catastrophic backtracking. A malformed regex is logged and skipped rather than failing application startup.

## Precedence and specificity

When several rules match, the library picks the most specific one, so you never have to rely on declaration order. Specificity is computed from:

- longer literal prefixes score higher;
- deeper paths (more segments) score higher;
- more wildcards score lower;
- a rule with an explicit methods constraint scores higher.

An explicit skip rule always beats any positive rule, no matter how specific the positive rule is.

## Capture modes

capture controls how much of the exchange is stored:

| Mode | Headers | Bodies | Use when |
|---|---|---|---|
| FULL (default) | yes | yes | You need complete request/response payloads |
| METADATA_ONLY | yes | no | High-volume or privacy-sensitive endpoints |
| BODY_ONLY | no | yes | Payload matters but headers may hold external secrets |

    - pattern: /api/v1/orders/**
      capture: METADATA_ONLY

## Tags

Tags are arbitrary key/value pairs attached to the audit record. They are useful for downstream filtering, reporting, and SIEM routing.

    - pattern: /api/v1/payments/**
      tags:
        module: payments
        tier: critical
        team: money-movement

Tags flow into the tags field of the audit record and are persisted by the JPA and JDBC stores as a compact JSON object.

## Explicit skip rules

Use audit: false to guarantee a path is never captured, even when a broader rule or an annotation would otherwise include it:

    - pattern: /actuator/**
      audit: false

## Method-scoped rules

Constrain a rule to specific HTTP methods. Matching is case-insensitive:

    - pattern: /api/v1/orders/**
      methods: [POST, PUT, PATCH]
      description: Order mutations

## Mixing annotations and path rules

Both mechanisms can be active at once and are complementary:

- A path rule can audit unannotated endpoints.
- An annotation on a controller still works exactly as before.
- When both match, the annotation supplies the description and tags.
- An explicit skip always wins.

To run in pure configuration mode, disable the annotation policy:

    audit:
      logging:
        policies:
          annotation:
            enabled: false
          path-based:
            enabled: true
            rules:
              - pattern: /api/**

## Custom policies (SPI)

For logic that configuration cannot express - a header, a tenant, a feature flag - implement the AuditPolicy interface and register it as a Spring bean. It is picked up automatically and ordered by getOrder().

    @Component
    public class TenantAuditPolicy implements AuditPolicy {

      @Override
      public int getOrder() {
        return AuditPolicyOrder.CUSTOM_FIRST;
      }

      @Override
      public Optional<AuditDecision> decide(HttpServletRequest request, String path) {
        if ("acme".equals(request.getHeader("X-Tenant"))) {
          return Optional.of(
              new AuditDecision(true, "Tenant Acme", Map.of("tenant", "acme"), CaptureMode.FULL));
        }
        return Optional.empty();
      }
    }

Returning Optional.empty() means no opinion. Returning a decision with isAudit() == false is an explicit skip that wins over everything.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Nothing is captured | policies.path-based.enabled is false | Set it to true |
| Nothing is captured | pattern does not match the actual URI | Check for a trailing slash and Ant syntax |
| A path is captured when it should not be | A broader rule matches | Add an explicit audit: false rule |
| Description is GET /path | No description set on the rule and no annotation | Set description on the rule |
| Malformed regex rule ignored | Invalid regex | Check startup logs for the warning |

For the full list of audit.logging.* properties, see the main README configuration reference.
