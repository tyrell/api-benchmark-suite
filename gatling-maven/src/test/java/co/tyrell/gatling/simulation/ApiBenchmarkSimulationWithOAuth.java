package co.tyrell.gatling.simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import co.tyrell.gatling.auth.OAuthConfig;
import co.tyrell.gatling.auth.OAuthTokenManager;

/**
 * Enhanced API Benchmark Simulation with OAuth support.
 * 
 * This simulation tests the OAuth-enabled test API with various endpoints.
 * It supports both public and protected endpoints, automatically handling
 * OAuth authentication when enabled.
 * 
 * Usage:
 * 1. Start test API: cd api && python app.py
 * 2. Run with OAuth: mvn gatling:test -Doauth.enabled=true
 * 3. Configuration is loaded from oauth-config.properties
 */
public class ApiBenchmarkSimulationWithOAuth extends Simulation {

  // Load configuration
  private static final int vu = Integer.parseInt(System.getProperty("gatling.users", "100"));
  private static final String baseUrl = System.getProperty("api.base.url", "http://localhost:5050");
  
  // OAuth configuration
  private static final OAuthConfig oauthConfig = OAuthConfig.fromSystemProperties();

  // Define HTTP configuration
  private static final HttpProtocolBuilder httpProtocol = http.baseUrl(baseUrl)
      .acceptHeader("application/json")
      .contentTypeHeader("application/json")
      .userAgentHeader("Gatling-OAuth-Performance-Test");

  // Health check scenario (no OAuth required)
  private static final ScenarioBuilder healthCheckScenario = scenario("Health Check")
      .exec(http("Health Check")
          .get("/api/health")
          .check(status().is(200))
          .check(jsonPath("$.status").is("healthy")))
      .pause(1);

  // Public endpoint scenario (no OAuth required)  
  private static final ScenarioBuilder publicApiScenario = scenario("Public API")
      .exec(http("Public Hello")
          .get("/api/hello")
          .check(status().is(200))
          .check(jsonPath("$.message").is("Hello, world!")))
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

  // Define assertions for industry-standard NFRs
  private static final Assertion[] assertions = new Assertion[] {
      global().responseTime().max().lt(5000), // Increased for OAuth overhead
      global().responseTime().mean().lt(2000), // Increased for OAuth overhead  
      global().responseTime().percentile(95).lt(3000), // Increased for OAuth overhead
      global().successfulRequests().percent().gt(95.0), // Slightly reduced due to expected 401s
      global().failedRequests().percent().lt(5.0),
      global().requestsPerSec().gt(2.0), // Reduced due to OAuth overhead
      global().allRequests().count().gt(10L)
  };

  // Setup simulation
  {
    setUp(
        // Mix of scenarios
        healthCheckScenario.injectOpen(constantUsersPerSec(2).during(60)),
        publicApiScenario.injectOpen(constantUsersPerSec(5).during(60)), 
        protectedApiScenario.injectOpen(
            rampUsers(vu).during(30), // Ramp up over 30 seconds
            constantUsersPerSec(10).during(90) // Reduced rate for OAuth overhead
        )
    )
    .assertions(assertions)
    .protocols(httpProtocol);
    
    // Print configuration info
    System.out.println("=== OAuth API Benchmark Configuration ===");
    System.out.println("Base URL: " + baseUrl);
    System.out.println("OAuth Enabled: " + oauthConfig.isEnabled());
    if (oauthConfig.isEnabled()) {
        System.out.println("Token Endpoint: " + oauthConfig.getTokenEndpoint());
        System.out.println("Client ID: " + oauthConfig.getClientId());
        System.out.println("Grant Type: " + oauthConfig.getGrantType());
        System.out.println("Scope: " + oauthConfig.getScope());
    }
    System.out.println("Virtual Users: " + vu);
    System.out.println("=========================================");
  }
}