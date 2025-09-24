package co.tyrell.gatling.simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

public class ApiBenchmarkSimulation extends Simulation {

  // Load VU count from system properties
  // Reference: https://docs.gatling.io/guides/passing-parameters/
  private static final int vu = 500;

  // Define HTTP configuration
  // Reference: https://docs.gatling.io/reference/script/protocols/http/protocol/
  private static final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:5050")
      .acceptHeader("application/json")
      .userAgentHeader(
          "Gatling-Performance-Test-Agent");

  // Define scenario
  // Reference: https://docs.gatling.io/reference/script/core/scenario/
  private static final ScenarioBuilder scenario = scenario("Flask API Benchmark")
      .exec(http("Hello Endpoint").get("/api/hello")
          .check(status().is(200))
          .check(jsonPath("$.message").exists()));

  // Define assertions for industry-standard NFRs
  // Reference: https://docs.gatling.io/reference/script/core/assertions/
  private static final Assertion[] assertions = new Assertion[] {
      global().responseTime().max().lt(1000), // Max response time < 1000ms
      global().responseTime().mean().lt(500), // Mean response time < 500ms
      global().responseTime().percentile(95).lt(800), // 95th percentile < 800ms
      global().successfulRequests().percent().gt(99.0), // Success rate > 99%
      global().failedRequests().percent().lt(1.0), // Error rate < 1%
      global().requestsPerSec().gt(10.0), // Throughput > 10 req/sec
      global().allRequests().count().gt(10L), // At least 10 requests sent
      details("Hello Endpoint").responseTime().max().lt(1000), // Endpoint-specific response time
      details("Hello Endpoint").successfulRequests().percent().gt(99.0) // Endpoint-specific success rate
  };

  // Define injection profile and execute the test
  // Reference: https://docs.gatling.io/reference/script/core/injection/
  {
    setUp(
        scenario.injectOpen(
            rampUsers(vu).during(60), // Ramp up to 500 users over 60 seconds
            constantUsersPerSec(50).during(120) // Maintain 50 users/sec for 2 minutes
        ))
        .assertions(assertions)
        .protocols(httpProtocol);
  }
}
