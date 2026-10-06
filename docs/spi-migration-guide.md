# SPI Migration Guide: Framework-Neutral Search Store

## Who this affects

Only applications or libraries that implement `com.api.audit.spi.AuditLogSearchStore` themselves. If
you use the built-in JPA, JDBC, or memory stores, nothing changes — the internal endpoint and all
config behave the same.

## What changed and why

Previously the search SPI carried a Spring Data `Pageable` parameter and returned a Spring Data
`Page`. That forced every custom store — even one talking to Elasticsearch or a remote API — to put
Spring Data on its classpath, and it coupled the storage contract to a web/pagination concern.

The SPI is now framework-neutral:

    // before
    Page<AuditLogRecord> search(
        String correlationId, LocalDateTime start, LocalDateTime end, String type, String url,
        String serviceName, String method, Integer httpStatus, String clientIp, String principalName,
        String errorType, String tagKey, String tagValue, Pageable pageable);

    // after
    AuditLogPage<AuditLogRecord> search(AuditLogQuery query);

`AuditLogQuery` holds all filters plus `page`, `size`, `sortBy`, and `sortAscending`. `AuditLogPage`
holds the content plus `page`, `size`, and `totalElements`.

## How to migrate a custom store

Before:

    @Override
    public Page<AuditLogRecord> search(
        String correlationId, LocalDateTime start, /* ... many params ... */ Pageable pageable) {
      // translate params into your backend query
      List<AuditLogRecord> content = backend.query(/* ... */);
      return new PageImpl<>(content, pageable, total);
    }

After:

    @Override
    public AuditLogPage<AuditLogRecord> search(AuditLogQuery query) {
      List<AuditLogRecord> content = backend.query(
          query.getCorrelationId(), query.getType(), query.getTagKey(), query.getTagValue(),
          query.getStart(), query.getEnd() /* ... */);
      return new AuditLogPage<>(content, query.effectivePage(), query.effectiveSize(), total);
    }

Key points:

- Read filters from the `query` object instead of individual parameters.
- Use `query.effectivePage()` / `query.effectiveSize()` / `query.offset()` for pagination; they are
  clamped to safe values.
- Use `query.getSortBy()` and `query.isSortAscending()` for ordering.
- Return an `AuditLogPage`; there is no Spring Data type involved.

## Compatibility notes

- The HTTP contract of `/internal/audit-logs` is unchanged. Query parameters such as `page`, `size`,
  `sort`, `tagKey`, and `tagValue` still work; the controller converts them into an `AuditLogQuery`.
- `AuditLogPage` is serialized by Jackson as a plain object with `content`, `page`, `size`,
  `totalElements`, `totalPages`, and so on. If a client depended on the exact Spring Data `Page`
  JSON shape, adjust it to the fields above.
- Custom stores that previously relied on Spring Data `Page` helpers (`map`, `getTotalPages`) can use
  the equivalents on `AuditLogPage`.

## If you cannot migrate immediately

The old signature was removed in this release. To stay on the previous contract, pin the library to
`2.2.0`. To adopt the new one, update your `AuditLogSearchStore` implementation as shown above.
