package com.api.audit.samples.demoApp;

import java.net.URI;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

/**
 * Small HTTP interface client used by the demo application.
 *
 * <p>The proxy is intentionally plain. Audit logging is applied by the RestClient builder that backs
 * this interface, which is the same integration style a host application would use.
 *
 * @author Puneet Swarup
 */
public interface DemoHttpInterfaceClient {

  /** Calls a downstream status endpoint through Spring's HTTP interface support. */
  @GetExchange
  Map<String, Object> status(URI uri, @RequestParam("client") String client);
}
