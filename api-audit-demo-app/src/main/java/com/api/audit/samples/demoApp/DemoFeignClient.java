package com.api.audit.samples.demoApp;

import com.api.audit.annotation.AuditLog;
import java.net.URI;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client used by the demo application to show annotated outbound capture.
 *
 * <p>The {@link AuditLog} annotation is placed on the client method because the Feign logger records
 * only the outbound calls developers explicitly mark as auditable.
 *
 * @author Puneet Swarup
 */
@FeignClient(name = "demoFeignClient", url = "https://localhost.invalid")
public interface DemoFeignClient {

  /** Calls a downstream status endpoint through Feign. */
  @AuditLog("DEMO_FEIGN_OUTBOUND")
  @GetMapping("/api/v1/status")
  Map<String, Object> status(URI baseUrl, @RequestParam("client") String client);
}
