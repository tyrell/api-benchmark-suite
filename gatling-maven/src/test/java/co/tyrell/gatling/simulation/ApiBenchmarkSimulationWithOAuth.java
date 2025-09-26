package co.tyrell.gatling.simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import co.tyrell.gatling.auth.OAuthConfig;
import co.tyrell.gatling.auth.OAuthTokenManager;

/**
 * HYPOTHETICAL Enhanced API Benchmark Simulation with OAuth support and NFR testing.
 * 
 * ⚠️  WARNING: This tests a FICTIONAL/HYPOTHETICAL API for demonstration purposes only.
 * ⚠️  This does NOT represent any real-world Customer API or actual business systems.
 * ⚠️  All endpoints, data, and business logic are COMPLETELY FICTIONAL.
 * 
 * This simulation provides enterprise-grade performance testing patterns against a mock
 * Customer API test server, demonstrating OAuth authentication flows and NFR validation.
 * 
 * 🎭 DISCLAIMER: All API endpoints and customer data are FABRICATED for testing.
 * 
 * Features (All Hypothetical):
 * - Industry-standard NFR assertions for fictional API
 * - Mock OAuth 2.0 Client Credentials flow testing
 * - Simulated workload scenarios (health check, public, protected APIs)
 * - Endpoint-specific performance validation against test server
 * - Configurable load patterns for demonstration purposes
 * - Detailed performance metrics and assertions
 * 
 * NFR Standards (For Test Server):
 * - Max response time: <1000ms (global), <1500ms (OAuth endpoints)
 * - Mean response time: <500ms
 * - 95th percentile: <800ms  
 * - Success rate: >99% (global), >95% (OAuth endpoints)
 * - Throughput: >10 req/sec
 * 
 * Usage:
 * 1. Start fictional test API: cd api && python app.py
 * 2. Run with OAuth: mvn gatling:test -Doauth.enabled=true -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth
 * 3. Configuration loaded from gatling-simulation.properties and system properties
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
  private static final String brand = System.getProperty("api.brand", "AAMI");
  private static final int searchLimit = Integer.parseInt(System.getProperty("api.customer.search.limit", "5"));
  private static final String sampleEmailDomain = System.getProperty("api.customer.sample.email.domain", "example.com");
  private static final String phoneCountryCode = System.getProperty("api.customer.sample.phone.country.code", "+61");
  private static final String phonePrefix = System.getProperty("api.customer.sample.phone.prefix", "04");
  
  // Load performance thresholds from system properties
  private static final int maxResponseTime = Integer.parseInt(System.getProperty("performance.max.response.time", "1000"));
  private static final int meanResponseTime = Integer.parseInt(System.getProperty("performance.mean.response.time", "500"));
  private static final int percentile95ResponseTime = Integer.parseInt(System.getProperty("performance.percentile.95.response.time", "800"));
  private static final int oauthMaxResponseTime = Integer.parseInt(System.getProperty("performance.oauth.max.response.time", "1500"));
  private static final double successRateThreshold = Double.parseDouble(System.getProperty("performance.success.rate.threshold", "99.0"));
  private static final double oauthSuccessRateThreshold = Double.parseDouble(System.getProperty("performance.oauth.success.rate.threshold", "95.0"));
  private static final double minThroughput = Double.parseDouble(System.getProperty("performance.min.throughput", "10.0"));
  private static final long minRequestCount = Long.parseLong(System.getProperty("performance.min.request.count", "10"));
  
  // Load testing pattern configuration
  private static final int rampUpDuration = Integer.parseInt(System.getProperty("load.ramp.up.duration", "60"));
  private static final int steadyStateDuration = Integer.parseInt(System.getProperty("load.steady.state.duration", "120"));
  private static final int usersPerSecond = Integer.parseInt(System.getProperty("load.users.per.second", "50"));
  private static final int healthCheckUsersPerSecond = Integer.parseInt(System.getProperty("load.health.check.users.per.second", "5"));
  private static final int publicApiUsersPerSecond = Integer.parseInt(System.getProperty("load.public.api.users.per.second", "10"));
  private static final int totalDuration = Integer.parseInt(System.getProperty("load.total.duration", "180"));
  
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
      .exec(http("Health Check")
          .get("/api/health")
          .check(status().is(200))
          .check(jsonPath("$.status").is("healthy"))
          .check(jsonPath("$.service").is("customer-api-test-server"))
          .check(jsonPath("$.version").is("3.0.0"))
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
      // Test customer search endpoint
      .exec(oauthConfig.isEnabled() ?
          OAuthTokenManager.createAuthorizedRequest("Search Customers", "GET", "/v3/brands/" + brand + "/customers?limit=" + searchLimit)
              .check(status().is(200))
              .check(bodyString().saveAs("searchResponse"))
          :
          http("Search Customers (no auth)")
              .get("/v3/brands/" + brand + "/customers")
              .check(status().is(401)) // Should fail without auth
      )
      .pause(1)
      
      // Test create customer endpoint
      .exec(oauthConfig.isEnabled() ?
          OAuthTokenManager.createAuthorizedRequest("Create Customer", "POST", "/v3/brands/" + brand + "/customers")
              .header("Content-Type", "application/vnd.api+json")
              .body(StringBody("{"
                + "\"data\": {"
                + "  \"type\": \"Individual\","
                + "  \"attributes\": {"
                + "    \"partyDetails\": {"
                + "      \"individual\": {"
                + "        \"firstName\": \"Test${__Random(1,1000)}\","
                + "        \"lastName\": \"Customer${__Random(1,1000)}\","
                + "        \"gender\": \"MALE\","
                + "        \"deceased\": false,"
                + "        \"dateOfBirth\": \"1990-01-01\""
                + "      },"
                + "      \"emailContact\": [{"
                + "        \"emailAddress\": \"test${__Random(1,1000)}@" + sampleEmailDomain + "\""
                + "      }],"
                + "      \"phoneContact\": [{"
                + "        \"phoneType\": \"MOBILE_PHONE\","
                + "        \"countryCode\": \"" + phoneCountryCode + "\","
                + "        \"phoneNumber\": \"" + phonePrefix + "${__Random(10000000,99999999)}\","
                + "        \"contactPriority\": \"1\""
                + "      }]"
                + "    }"
                + "  }"
                + "}"
                + "}"))
              .check(status().is(201))
              .check(jsonPath("$.data.id").saveAs("customerId"))
              .check(jsonPath("$.data.type").exists())
          :
          http("Create Customer (no auth)")
              .post("/v3/brands/" + brand + "/customers")
              .header("Content-Type", "application/vnd.api+json")
              .body(StringBody("{\"data\":{\"type\":\"Individual\"}}"))
              .check(status().is(401)) // Should fail without auth
      )
      .pause(1)
      
      // Get specific customer if one was created
      .exec(oauthConfig.isEnabled() ?
          doIf(session -> session.contains("customerId")).then(
              OAuthTokenManager.createAuthorizedRequest("Get Customer", "GET", "/v3/brands/" + brand + "/customers/#{customerId}")
                  .check(status().is(200))
                  .check(jsonPath("$.data.id").exists())
                  .check(jsonPath("$.data.type").exists())
          )
          :
          exec(session -> session)
      )
      .pause(1)
      
      // Update customer if one was created
      .exec(oauthConfig.isEnabled() ?
          doIf(session -> session.contains("customerId")).then(
              OAuthTokenManager.createAuthorizedRequest("Update Customer", "PATCH", "/v3/brands/" + brand + "/customers/#{customerId}")
                  .header("Content-Type", "application/vnd.api+json")
                  .body(StringBody("""
                    {
                      "data": {
                        "attributes": {
                          "partyDetails": {
                            "individual": {
                              "firstName": "Updated${__Random(1,1000)}"
                            }
                          }
                        }
                      }
                    }
                  """))
                  .check(status().is(200))
          )
          :
          exec(session -> session)
      )
      .pause(1)
      
      // Test vulnerabilities endpoint
      .exec(oauthConfig.isEnabled() ?
          doIf(session -> session.contains("customerId")).then(
              OAuthTokenManager.createAuthorizedRequest("Get Vulnerabilities", "GET", "/v3/brands/" + brand + "/customers/#{customerId}/vulnerabilities")
                  .check(status().in(200, 404)) // 404 is OK if no vulnerabilities exist
          )
          :
          http("Get Vulnerabilities (no auth)")
              .get("/v3/brands/" + brand + "/customers/123/vulnerabilities")
              .check(status().is(401)) // Should fail without auth
      )
      .pause(1)
      
      // Test lifecycle endpoint
      .exec(oauthConfig.isEnabled() ?
          OAuthTokenManager.createAuthorizedRequest("Lifecycle Operation", "POST", "/v3/brands/" + brand + "/customers/lifecycle")
              .header("Content-Type", "application/vnd.api+json")
              .body(StringBody("""
                {
                  "data": {
                    "operation": "activate",
                    "attributes": {
                      "partyDetails": {
                        "individual": {
                          "firstName": "Lifecycle",
                          "lastName": "Test"
                        }
                      }
                    }
                  }
                }
              """))
              .check(status().is(200))
          :
          http("Lifecycle Operation (no auth)")
              .post("/v3/brands/" + brand + "/customers/lifecycle")
              .header("Content-Type", "application/vnd.api+json")
              .body(StringBody("{\"data\":{\"operation\":\"test\"}}"))
              .check(status().is(401)) // Should fail without auth
      );

  // Define industry-standard NFR assertions matching ApiBenchmarkSimulation
  private static final Assertion[] assertions = new Assertion[] {
      // Global performance assertions
      global().responseTime().max().lt(maxResponseTime), // Max response time configurable
      global().responseTime().mean().lt(meanResponseTime), // Mean response time configurable  
      global().responseTime().percentile(95).lt(percentile95ResponseTime), // 95th percentile configurable
      global().successfulRequests().percent().gt(successRateThreshold), // Success rate configurable
      global().failedRequests().percent().lt(100.0 - successRateThreshold), // Error rate derived from success rate
      global().requestsPerSec().gt(minThroughput), // Throughput configurable
      global().allRequests().count().gt(minRequestCount), // At least minimum requests configurable
      
      // Endpoint-specific assertions for Customer API v3.0.0 scenarios
      details("Health Check").responseTime().max().lt(maxResponseTime),
      details("Health Check").successfulRequests().percent().gt(successRateThreshold),
      details("Search Customers").responseTime().max().lt(oauthMaxResponseTime), // Customer search with OAuth overhead
      details("Search Customers").successfulRequests().percent().gt(oauthSuccessRateThreshold),
      details("Create Customer").responseTime().max().lt(oauthMaxResponseTime), // OAuth + create operation
      details("Create Customer").successfulRequests().percent().gt(oauthSuccessRateThreshold),
      details("Get Customer").responseTime().max().lt(oauthMaxResponseTime), // OAuth overhead
      details("Get Customer").successfulRequests().percent().gt(oauthSuccessRateThreshold),
      details("Update Customer").responseTime().max().lt(oauthMaxResponseTime), // OAuth + update operation
      details("Update Customer").successfulRequests().percent().gt(oauthSuccessRateThreshold),
      details("Get Vulnerabilities").responseTime().max().lt(oauthMaxResponseTime), // OAuth overhead
      details("Get Vulnerabilities").successfulRequests().percent().gt(90.0), // Lower due to 404s being OK
      details("Lifecycle Operation").responseTime().max().lt(oauthMaxResponseTime), // OAuth + lifecycle operation
      details("Lifecycle Operation").successfulRequests().percent().gt(oauthSuccessRateThreshold)
  };

  // Setup simulation with comprehensive load testing patterns
  {
    setUp(
        // Health check scenario - lightweight monitoring
        healthCheckScenario.injectOpen(constantUsersPerSec(healthCheckUsersPerSecond).during(totalDuration)),
        
        // Public API scenario - baseline performance 
        publicApiScenario.injectOpen(constantUsersPerSec(publicApiUsersPerSecond).during(totalDuration)),
        
        // Protected API scenario - comprehensive OAuth load testing
        protectedApiScenario.injectOpen(
            rampUsers(vu).during(rampUpDuration), // Configurable ramp up users and duration
            constantUsersPerSec(usersPerSecond).during(steadyStateDuration) // Configurable users/sec and duration
        )
    )
    .assertions(assertions)
    .protocols(httpProtocol);
    
    // Print comprehensive configuration info
    System.out.println("======= OAuth API Benchmark Configuration =======");
    System.out.println("Simulation: ApiBenchmarkSimulationWithOAuth");
    System.out.println("Base URL: " + baseUrl);
    System.out.println("Brand: " + brand);
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
    System.out.println("  Health Check: " + healthCheckUsersPerSecond + " users/sec for " + totalDuration + "s");
    System.out.println("  Public API: " + publicApiUsersPerSecond + " users/sec for " + totalDuration + "s");
    System.out.println("  Customer API v3.0.0: Ramp " + vu + " users/" + rampUpDuration + "s + " + usersPerSecond + " users/sec/" + steadyStateDuration + "s");
    
    System.out.println("Customer API Endpoints (Brand: " + brand + "):");
    System.out.println("  Search: GET /v3/brands/" + brand + "/customers (limit=" + searchLimit + ")");
    System.out.println("  Create: POST /v3/brands/" + brand + "/customers");
    System.out.println("  Get: GET /v3/brands/" + brand + "/customers/{id}");
    System.out.println("  Update: PATCH /v3/brands/" + brand + "/customers/{id}");
    System.out.println("  Vulnerabilities: GET /v3/brands/" + brand + "/customers/{id}/vulnerabilities");
    System.out.println("  Lifecycle: POST /v3/brands/" + brand + "/customers/lifecycle");
    
    System.out.println("NFR Targets:");
    System.out.println("  Max Response Time: " + maxResponseTime + "ms (global), " + oauthMaxResponseTime + "ms (OAuth)");
    System.out.println("  Mean Response Time: <" + meanResponseTime + "ms");
    System.out.println("  95th Percentile: <" + percentile95ResponseTime + "ms");
    System.out.println("  Success Rate: >" + successRateThreshold + "% (global), >" + oauthSuccessRateThreshold + "% (OAuth)");
    System.out.println("  Throughput: >" + minThroughput + " req/sec");
    
    System.out.println("Sample Data Configuration:");
    System.out.println("  Email Domain: " + sampleEmailDomain);
    System.out.println("  Phone Country Code: " + phoneCountryCode);
    System.out.println("  Phone Prefix: " + phonePrefix);
    System.out.println("================================================");
  }
}