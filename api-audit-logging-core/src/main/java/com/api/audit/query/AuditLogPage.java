package com.api.audit.query;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Framework-neutral page of results returned by {@link com.api.audit.spi.AuditLogSearchStore}.
 *
 * <p>This replaces Spring Data's {@code Page} in the storage SPI so a custom store — or any code
 * that consumes the SPI — does not depend on Spring Data. It carries exactly what a caller needs:
 * the content, the page metadata, and the total count for building pagination UI.
 *
 * <p>Instances are immutable and safe to share.
 *
 * @param <T> the element type (typically {@link com.api.audit.model.AuditLogRecord})
 * @author Puneet Swarup
 * @see AuditLogQuery
 */
public final class AuditLogPage<T> {

  private final List<T> content;
  private final int page;
  private final int size;
  private final long totalElements;

  /**
   * Creates a page.
   *
   * @param content the page content; {@code null} is treated as empty
   * @param page the zero-based page index
   * @param size the page size
   * @param totalElements the total number of elements across all pages
   */
  public AuditLogPage(List<T> content, int page, int size, long totalElements) {
    this.content = content == null ? Collections.emptyList() : List.copyOf(content);
    this.page = Math.max(page, 0);
    this.size = size < 1 ? 20 : size;
    this.totalElements = Math.max(totalElements, 0);
  }

  /**
   * Creates an empty page for the given query shape.
   *
   * @param page the zero-based page index
   * @param size the page size
   * @param <T> the element type
   * @return an empty page
   */
  public static <T> AuditLogPage<T> empty(int page, int size) {
    return new AuditLogPage<>(Collections.emptyList(), page, size, 0);
  }

  /**
   * @return the page content; never {@code null}
   */
  public List<T> getContent() {
    return content;
  }

  /**
   * @return the zero-based page index
   */
  public int getPage() {
    return page;
  }

  /**
   * @return the page size
   */
  public int getSize() {
    return size;
  }

  /**
   * @return the total number of elements across all pages
   */
  public long getTotalElements() {
    return totalElements;
  }

  /**
   * @return the total number of pages given {@link #getSize()} and {@link #getTotalElements()}
   */
  public int getTotalPages() {
    if (size == 0) {
      return 0;
    }
    return (int) Math.ceil((double) totalElements / (double) size);
  }

  /**
   * @return the number of elements in this page
   */
  public int getNumberOfElements() {
    return content.size();
  }

  /**
   * @return whether this is the first page
   */
  public boolean isFirst() {
    return page == 0;
  }

  /**
   * @return whether this is the last page
   */
  public boolean isLast() {
    return page >= getTotalPages() - 1;
  }

  /**
   * Maps the content to another type, preserving the page metadata.
   *
   * @param mapper the mapping function
   * @param <R> the target element type
   * @return a new page with mapped content
   */
  public <R> AuditLogPage<R> map(Function<? super T, ? extends R> mapper) {
    List<R> mapped = new java.util.ArrayList<>(content.size());
    for (T element : content) {
      mapped.add(mapper.apply(element));
    }
    return new AuditLogPage<>(mapped, page, size, totalElements);
  }
}
