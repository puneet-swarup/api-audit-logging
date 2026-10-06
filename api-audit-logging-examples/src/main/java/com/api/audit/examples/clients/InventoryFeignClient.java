package com.api.audit.examples.clients;

import com.api.audit.annotation.AuditLog;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Example: an audited Feign client.
 *
 * <p>Adding the Feign integration module and annotating the interface method with {@code @AuditLog}
 * is enough to capture outbound calls. The library installs a Feign logger, propagates the
 * correlation ID, and can optionally wrap the error decoder to capture non-2xx response bodies.
 *
 * <p><b>Try it</b>
 *
 * <pre>
 * GET /examples/clients/feign/42
 * </pre>
 *
 * <p>The outbound Feign call appears as an {@code OUTGOING} (or {@code OUTGOING_ERROR}) audit record
 * carrying the same correlation ID as the inbound request.
 *
 * @author Puneet Swarup
 */
@FeignClient(
    name = "inventoryClient",
    url = "${examples.downstream.base-url:https://localhost.invalid}")
public interface InventoryFeignClient {

  /**
   * Fetches an item from the downstream inventory service.
   *
   * @param sku the stock-keeping unit
   * @return the raw response body
   */
  @GetMapping("/inventory/{sku}")
  @AuditLog("Outbound: inventory lookup")
  String findItem(@PathVariable("sku") String sku);
}
