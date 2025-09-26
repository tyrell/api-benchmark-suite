package co.tyrell.gatling.simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

/**
 * HYPOTHETICAL Customer API Performance Test Simulation
 * 
 * ⚠️  WARNING: This tests a FICTIONAL/HYPOTHETICAL API for demonstration purposes only.
 * ⚠️  This does NOT represent any real-world Customer API or actual business systems.
 * ⚠️  All endpoints, data, and business logic are COMPLETELY FICTIONAL.
 * 
 * This simulation demonstrates basic API performance testing with OAuth authentication
 * against a mock Customer API test server.
 * 
 * 🎭 DISCLAIMER: All API endpoints and customer data are FABRICATED for testing.
 */
public class ApiBenchmarkSimulation extends Simulation {

  // Load VU count from system properties
  // Reference: https://docs.gatling.io/guides/passing-parameters/
  private static final int vu = Integer.parseInt(System.getProperty("gatling.users", "500"));
  
  // Load API configuration from system properties
  private static final String baseUrl = System.getProperty("api.base.url", "http://localhost:5050");
  private static final String brand = System.getProperty("api.brand", "AAMI");
  private static final String clientId = System.getProperty("oauth.client.id", "demo-client-id");
  private static final String clientSecret = System.getProperty("oauth.client.secret", "demo-client-secret");
  private static final String oauthScope = System.getProperty("oauth.scope", "customer:read customer:write");
  private static final int searchLimit = Integer.parseInt(System.getProperty("api.customer.search.limit", "3"));
  
  // Load performance thresholds from system properties
  private static final int maxResponseTime = Integer.parseInt(System.getProperty("performance.max.response.time", "1000"));
  private static final int meanResponseTime = Integer.parseInt(System.getProperty("performance.mean.response.time", "500"));
  private static final int percentile95ResponseTime = Integer.parseInt(System.getProperty("performance.percentile.95.response.time", "800"));
  private static final int oauthMaxResponseTime = Integer.parseInt(System.getProperty("performance.oauth.max.response.time", "1500"));
  private static final double successRateThreshold = Double.parseDouble(System.getProperty("performance.success.rate.threshold", "99.0"));
  private static final double minThroughput = Double.parseDouble(System.getProperty("performance.min.throughput", "10.0"));
  private static final long minRequestCount = Long.parseLong(System.getProperty("performance.min.request.count", "10"));
  
  // Load testing pattern configuration
  private static final int rampUpDuration = Integer.parseInt(System.getProperty("load.ramp.up.duration", "60"));
  private static final int steadyStateDuration = Integer.parseInt(System.getProperty("load.steady.state.duration", "120"));
  private static final int usersPerSecond = Integer.parseInt(System.getProperty("load.users.per.second", "50"));

  // Define HTTP configuration
  // Reference: https://docs.gatling.io/reference/script/protocols/http/protocol/
  private static final HttpProtocolBuilder httpProtocol = http.baseUrl(baseUrl)
      .acceptHeader("application/json")
      .userAgentHeader(
          "Gatling-Performance-Test-Agent");

  // Define scenario
  // Reference: https://docs.gatling.io/reference/script/core/scenario/
  private static final ScenarioBuilder scenario = scenario("Customer API Comprehensive Test")
      // Health check first
      .exec(http("Health Check").get("/api/health")
          .check(status().is(200))
          .check(jsonPath("$.status").is("healthy"))
          .check(jsonPath("$.service").is("customer-api-test-server"))
          .check(jsonPath("$.version").is("3.0.0")))
      .pause(1)
      
      // Test OAuth token endpoint (public access)
      .exec(http("OAuth Token Request").post("/oauth/token")
          .header("Content-Type", "application/x-www-form-urlencoded")
          .formParam("grant_type", "client_credentials")
          .formParam("client_id", clientId)
          .formParam("client_secret", clientSecret)
          .formParam("scope", oauthScope)
          .check(status().is(200))
          .check(jsonPath("$.access_token").exists().saveAs("access_token"))
          .check(jsonPath("$.token_type").is("Bearer")))
      .pause(1)
      
      // Test protected customer search endpoint - only if we have a token
      .doIf(session -> session.contains("access_token"))
          .then(exec(http("Search Customers").get("/v3/brands/" + brand + "/customers?limit=" + searchLimit)
              .header("Authorization", "Bearer #{access_token}")
              .check(status().is(200))
              .check(bodyString().transform(body -> body.length() > 0))))
      .pause(1);

  // Define assertions for industry-standard NFRs
  // Reference: https://docs.gatling.io/reference/script/core/assertions/
  private static final Assertion[] assertions = new Assertion[] {
      global().responseTime().max().lt(maxResponseTime), // Max response time configurable
      global().responseTime().mean().lt(meanResponseTime), // Mean response time configurable
      global().responseTime().percentile(95).lt(percentile95ResponseTime), // 95th percentile configurable
      global().successfulRequests().percent().gt(successRateThreshold), // Success rate configurable
      global().failedRequests().percent().lt(100.0 - successRateThreshold), // Error rate derived from success rate
      global().requestsPerSec().gt(minThroughput), // Throughput configurable
      global().allRequests().count().gt(minRequestCount), // Minimum requests configurable
      details("Health Check").responseTime().max().lt(maxResponseTime), // Endpoint-specific response time
      details("Health Check").successfulRequests().percent().gt(successRateThreshold), // Endpoint-specific success rate
      details("OAuth Token Request").responseTime().max().lt(oauthMaxResponseTime), // OAuth can be slower
      details("OAuth Token Request").successfulRequests().percent().gt(successRateThreshold),
      details("Search Customers").responseTime().max().lt(oauthMaxResponseTime), // Customer API with auth
      details("Search Customers").successfulRequests().percent().gt(successRateThreshold)
  };

  // Define injection profile and execute the test
  // Reference: https://docs.gatling.io/reference/script/core/injection/
  {
    setUp(
        scenario.injectOpen(
            rampUsers(vu).during(rampUpDuration), // Configurable ramp up users and duration
            constantUsersPerSec(usersPerSecond).during(steadyStateDuration) // Configurable users/sec and duration
        ))
        .assertions(assertions)
        .protocols(httpProtocol);
        
    // Print configuration info
    System.out.println("======= API Benchmark Configuration =======");
    System.out.println("Simulation: ApiBenchmarkSimulation");
    System.out.println("Base URL: " + baseUrl);
    System.out.println("Brand: " + brand);
    System.out.println("Virtual Users: " + vu);
    System.out.println("OAuth Client ID: " + clientId);
    System.out.println("OAuth Scope: " + oauthScope);
    System.out.println("Search Limit: " + searchLimit);
    System.out.println("Load Profile:");
    System.out.println("  Ramp: " + vu + " users/" + rampUpDuration + "s");
    System.out.println("  Steady: " + usersPerSecond + " users/sec/" + steadyStateDuration + "s");
    System.out.println("NFR Thresholds:");
    System.out.println("  Max Response Time: " + maxResponseTime + "ms (global), " + oauthMaxResponseTime + "ms (OAuth)");
    System.out.println("  Mean Response Time: <" + meanResponseTime + "ms");
    System.out.println("  95th Percentile: <" + percentile95ResponseTime + "ms");
    System.out.println("  Success Rate: >" + successRateThreshold + "%");
    System.out.println("  Throughput: >" + minThroughput + " req/sec");
    System.out.println("===============================================");
  }
}
