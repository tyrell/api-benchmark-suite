package co.tyrell.gatling.simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import co.tyrell.gatling.auth.OAuthConfig;
import co.tyrell.gatling.auth.OAuthTokenManager;

/**
 * Enhanced API Benchmark Simulation with comprehensive OAuth support and NFR testing.
 * 
 * This simulation provides enterprise-grade performance testing of OAuth-enabled APIs,
 * matching the same rigorous NFR standards as ApiBenchmarkSimulation.java while adding
 * comprehensive OAuth authentication flows.
 * 
 * Features:
 * - Industry-standard NFR assertions (same as ApiBenchmarkSimulation)
 * - Comprehensive OAuth 2.0 Client Credentials flow testing
 * - Mixed workload scenarios (health check, public, protected APIs)
 * - Endpoint-specific performance validation
 * - Configurable load patterns matching production requirements
 * - Detailed performance metrics and assertions
 * 
 * NFR Standards:
 * - Max response time: <1000ms (global), <1500ms (OAuth endpoints)
 * - Mean response time: <500ms
 * - 95th percentile: <800ms  
 * - Success rate: >99% (global), >95% (OAuth endpoints)
 * - Throughput: >10 req/sec
 * 
 * Usage:
 * 1. Start test API: cd api && python app.py
 * 2. Run with OAuth: mvn gatling:test -Doauth.enabled=true -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth
 * 3. Configuration loaded from oauth-config.properties and system properties
 * 
 * System Properties:
 * - gatling.users: Number of virtual users (default: 500)
 * - api.base.url: Base API URL (default: http://localhost:5050)
 * - oauth.enabled: Enable OAuth testing (default: false)
 */
public class ApiBenchmarkSimulationWithOAuth extends Simulation {

  // Load configuration with proper defaults
  private static final int vu = Integer.parseInt(System.getProperty("gatling.users", "500")); // Match standard simulation
  private static final String baseUrl = System.getProperty("api.base.url", "http://localhost:5050");
  
  // OAuth configuration
  private static final OAuthConfig oauthConfig = OAuthConfig.fromSystemProperties();

  // Define HTTP configuration
  private static final HttpProtocolBuilder httpProtocol = http.baseUrl(baseUrl)
      .acceptHeader("application/json")
      .contentTypeHeader("application/json")
      .userAgentHeader("Gatling-OAuth-Performance-Test");

  // Health check scenario (no OAuth required) - comprehensive monitoring
  private static final ScenarioBuilder healthCheckScenario = scenario("Health Check")
      .exec(http("Health Check")
          .get("/api/health")
          .check(status().is(200))
          .check(jsonPath("$.status").is("healthy"))
          .check(jsonPath("$.service").exists())
          .check(responseTimeInMillis().lt(500))) // Health checks should be very fast
      .pause(1);

  // Public endpoint scenario (no OAuth required) - comprehensive testing
  private static final ScenarioBuilder publicApiScenario = scenario("Public API")
      .exec(http("Public Hello")
          .get("/api/hello")
          .check(status().is(200))
          .check(jsonPath("$.message").is("Hello, world!"))
          .check(responseTimeInMillis().lt(1000))) // Individual response time check
      .pause(1);

  // Protected endpoints scenario (OAuth required)
  private static final ScenarioBuilder protectedApiScenario = scenario("Protected API with OAuth")
      .exec(oauthConfig.isEnabled() ? 
          // OAuth flow: acquire token first
          OAuthTokenManager.acquireClientCredentialsToken(oauthConfig)
          :
          // No OAuth: skip token acquisition  
          exec(session -> session)
      )
      // Test protected hello endpoint
      .exec(oauthConfig.isEnabled() ?
          OAuthTokenManager.createAuthorizedRequest("Protected Hello", "GET", "/api/hello/protected")
              .check(status().is(200))
              .check(jsonPath("$.message").exists())
          :
          http("Protected Hello (no auth)")
              .get("/api/hello/protected")
              .check(status().is(401)) // Should fail without auth
      )
      .pause(1)
      
      // Test customer API
      .exec(oauthConfig.isEnabled() ?
          OAuthTokenManager.createAuthorizedRequest("Get Customers", "GET", "/api/customers")
              .check(status().is(200))
              .check(jsonPath("$.customers").exists())
          :
          http("Get Customers (no auth)")
              .get("/api/customers")  
              .check(status().is(401)) // Should fail without auth
      )
      .pause(1)
      
      // Create customer (requires write scope)
      .exec(oauthConfig.isEnabled() ?
          OAuthTokenManager.createAuthorizedRequest("Create Customer", "POST", "/api/customers")
              .body(StringBody("{\"name\": \"Test Customer ${__Random(1,1000)}\", \"email\": \"test${__Random(1,1000)}@example.com\"}"))
              .check(status().is(201))
              .check(jsonPath("$.id").saveAs("customerId"))
              .check(jsonPath("$.name").exists())
          :
          http("Create Customer (no auth)")
              .post("/api/customers")
              .body(StringBody("{\"name\": \"Test Customer\", \"email\": \"test@example.com\"}"))
              .check(status().is(401)) // Should fail without auth
      )
      .pause(1)
      
      // Update customer if one was created
      .exec(oauthConfig.isEnabled() ?
          doIf(session -> session.contains("customerId")).then(
              OAuthTokenManager.createAuthorizedRequest("Update Customer", "PUT", "/api/customers/#{customerId}")
                  .body(StringBody("{\"name\": \"Updated Customer ${__Random(1,1000)}\", \"email\": \"updated${__Random(1,1000)}@example.com\"}"))
                  .check(status().is(200))
          )
          :
          exec(session -> session)
      )
      .pause(1)
      
      // Create CEV event
      .exec(oauthConfig.isEnabled() ?
          OAuthTokenManager.createAuthorizedRequest("Create CEV Event", "POST", "/api/cev-events")
              .body(StringBody("{\"type\": \"user.login\", \"data\": {\"user_id\": \"${__Random(1,1000)}\", \"timestamp\": \"${__time()}\"}}"))
              .check(status().is(201))
              .check(jsonPath("$.id").saveAs("eventId"))
          :
          http("Create CEV Event (no auth)")
              .post("/api/cev-events")
              .body(StringBody("{\"type\": \"user.login\", \"data\": {\"user_id\": \"123\"}}"))
              .check(status().is(401)) // Should fail without auth
      )
      .pause(1)
      
      // Get events
      .exec(oauthConfig.isEnabled() ?
          OAuthTokenManager.createAuthorizedRequest("Get CEV Events", "GET", "/api/cev-events")
              .check(status().is(200))
              .check(jsonPath("$.events").exists())
          :
          http("Get CEV Events (no auth)")
              .get("/api/cev-events")
              .check(status().is(401)) // Should fail without auth
      );

  // Define industry-standard NFR assertions matching ApiBenchmarkSimulation
  private static final Assertion[] assertions = new Assertion[] {
      // Global performance assertions
      global().responseTime().max().lt(1000), // Max response time < 1000ms (same as standard)
      global().responseTime().mean().lt(500), // Mean response time < 500ms (same as standard)
      global().responseTime().percentile(95).lt(800), // 95th percentile < 800ms (same as standard)
      global().successfulRequests().percent().gt(99.0), // Success rate > 99% (same as standard)
      global().failedRequests().percent().lt(1.0), // Error rate < 1% (same as standard)
      global().requestsPerSec().gt(10.0), // Throughput > 10 req/sec (same as standard)
      global().allRequests().count().gt(10L), // At least 10 requests sent (same as standard)
      
      // Endpoint-specific assertions for OAuth scenarios
      details("Health Check").responseTime().max().lt(1000),
      details("Health Check").successfulRequests().percent().gt(99.0),
      details("Public Hello").responseTime().max().lt(1000), 
      details("Public Hello").successfulRequests().percent().gt(99.0),
      details("Protected Hello").responseTime().max().lt(1500), // Slightly higher for OAuth overhead
      details("Protected Hello").successfulRequests().percent().gt(95.0), // Account for OAuth failures
      details("Get Customers").responseTime().max().lt(1500), // OAuth overhead
      details("Get Customers").successfulRequests().percent().gt(95.0),
      details("Create Customer").responseTime().max().lt(1500), // OAuth + DB operation
      details("Create Customer").successfulRequests().percent().gt(95.0),
      details("Create CEV Event").responseTime().max().lt(1500), // OAuth + DB operation
      details("Create CEV Event").successfulRequests().percent().gt(95.0),
      details("Get CEV Events").responseTime().max().lt(1500), // OAuth overhead
      details("Get CEV Events").successfulRequests().percent().gt(95.0)
  };

  // Setup simulation with comprehensive load testing patterns
  {
    setUp(
        // Health check scenario - lightweight monitoring
        healthCheckScenario.injectOpen(constantUsersPerSec(5).during(180)),
        
        // Public API scenario - baseline performance 
        publicApiScenario.injectOpen(constantUsersPerSec(10).during(180)),
        
        // Protected API scenario - comprehensive OAuth load testing
        protectedApiScenario.injectOpen(
            rampUsers(vu).during(60), // Ramp up to target users over 60 seconds (same as standard)
            constantUsersPerSec(50).during(120) // Maintain 50 users/sec for 2 minutes (same as standard)
        )
    )
    .assertions(assertions)
    .protocols(httpProtocol);
    
    // Print comprehensive configuration info
    System.out.println("======= OAuth API Benchmark Configuration =======");
    System.out.println("Simulation: ApiBenchmarkSimulationWithOAuth");
    System.out.println("Base URL: " + baseUrl);
    System.out.println("Virtual Users: " + vu);
    System.out.println("OAuth Enabled: " + oauthConfig.isEnabled());
    
    if (oauthConfig.isEnabled()) {
        System.out.println("OAuth Configuration:");
        System.out.println("  Token Endpoint: " + oauthConfig.getTokenEndpoint());
        System.out.println("  Client ID: " + oauthConfig.getClientId());
        System.out.println("  Grant Type: " + oauthConfig.getGrantType());
        System.out.println("  Scope: " + oauthConfig.getScope());
    }
    
    System.out.println("Load Profile:");
    System.out.println("  Health Check: 5 users/sec for 180s");
    System.out.println("  Public API: 10 users/sec for 180s");
    System.out.println("  Protected API: Ramp " + vu + " users/60s + 50 users/sec/120s");
    
    System.out.println("NFR Targets:");
    System.out.println("  Max Response Time: 1000ms (global), 1500ms (OAuth)");
    System.out.println("  Mean Response Time: <500ms");
    System.out.println("  95th Percentile: <800ms");
    System.out.println("  Success Rate: >99% (global), >95% (OAuth)");
    System.out.println("  Throughput: >10 req/sec");
    System.out.println("================================================");
  }
}