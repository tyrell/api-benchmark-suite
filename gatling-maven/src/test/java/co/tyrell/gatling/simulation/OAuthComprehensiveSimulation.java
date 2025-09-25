package co.tyrell.gatling.simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import co.tyrell.gatling.auth.OAuthConfig;
import co.tyrell.gatling.auth.OAuthTokenManager;

/**
 * Comprehensive OAuth API Testing Simulation
 * 
 * This simulation demonstrates various OAuth scenarios:
 * 1. Client Credentials flow
 * 2. Resource Owner Password Credentials flow
 * 3. Multiple API endpoints with different access patterns
 * 4. Error handling and token refresh
 * 
 * Example usage:
 * mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.OAuthComprehensiveSimulation \
 *   -Doauth.enabled=true \
 *   -Doauth.token.endpoint=https://auth.example.com/token \
 *   -Doauth.client.id=your-client-id \
 *   -Doauth.client.secret=your-secret \
 *   -Doauth.grant.type=client_credentials \
 *   -Doauth.scope="read write"
 */
public class OAuthComprehensiveSimulation extends Simulation {

    // Configuration
    private static final String baseUrl = System.getProperty("api.base.url", "http://localhost:5050");
    private static final OAuthConfig oauthConfig = OAuthConfig.fromSystemProperties();
    
    // HTTP Protocol
    private static final HttpProtocolBuilder httpProtocol = http.baseUrl(baseUrl)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling-OAuth-Test-Agent");

    // Scenario 1: Read-only operations with Client Credentials
    private static final ScenarioBuilder readOnlyScenario = scenario("Read-Only Operations")
        .exec(
            // Configure OAuth for read operations
            session -> session.set("oauth_scope", "read")
        )
        .exec(OAuthTokenManager.acquireClientCredentialsToken(oauthConfig))
        .repeat(5).on(
            exec(
                OAuthTokenManager.createAuthorizedRequest("Get Resource", "GET", "/api/resources")
                    .check(status().in(200, 404)) // Allow 404 for non-existent resources
            )
            .pause(1, 3)
        );

    // Scenario 2: Full CRUD operations with enhanced scope
    private static final ScenarioBuilder crudScenario = scenario("CRUD Operations")
        .exec(OAuthTokenManager.acquireClientCredentialsToken(oauthConfig))
        .exec(
            // Create resource
            OAuthTokenManager.createAuthorizedRequest("Create Resource", "POST", "/api/resources")
                .body(StringBody("{\"name\":\"test-resource-#{userId}\",\"value\":\"#{randomString(10)}\"}"))
                .check(status().in(201, 200))
                .check(jsonPath("$.id").optional().saveAs("resourceId"))
        )
        .pause(1, 2)
        .doIf(session -> session.contains("resourceId")).then(
            exec(
                // Read created resource
                OAuthTokenManager.createAuthorizedRequest("Get Created Resource", "GET", "/api/resources/#{resourceId}")
                    .check(status().is(200))
            )
            .pause(1, 2)
            .exec(
                // Update resource
                OAuthTokenManager.createAuthorizedRequest("Update Resource", "PUT", "/api/resources/#{resourceId}")
                    .body(StringBody("{\"name\":\"updated-resource-#{userId}\",\"value\":\"#{randomString(10)}\"}"))
                    .check(status().in(200, 204))
            )
            .pause(1, 2)
            .exec(
                // Delete resource
                OAuthTokenManager.createAuthorizedRequest("Delete Resource", "DELETE", "/api/resources/#{resourceId}")
                    .check(status().in(200, 204, 404))
            )
        );

    // Scenario 3: Password flow (if configured)
    private static final ScenarioBuilder passwordFlowScenario = scenario("Password Flow Operations")
        .doIf(session -> "password".equals(oauthConfig.getGrantType())).then(
            exec(OAuthTokenManager.acquirePasswordCredentialsToken(oauthConfig))
            .exec(
                OAuthTokenManager.createAuthorizedRequest("User Profile", "GET", "/api/user/profile")
                    .check(status().in(200, 404))
            )
            .pause(2, 5)
            .exec(
                OAuthTokenManager.createAuthorizedRequest("Update Profile", "PUT", "/api/user/profile")
                    .body(StringBody("{\"email\":\"user-#{userId}@example.com\",\"name\":\"User #{userId}\"}"))
                    .check(status().in(200, 204, 404))
            )
        );

    // Scenario 4: Error handling and edge cases
    private static final ScenarioBuilder errorHandlingScenario = scenario("Error Handling")
        .exec(OAuthTokenManager.acquireClientCredentialsToken(oauthConfig))
        .exec(
            // Test unauthorized endpoint (should work with token)
            OAuthTokenManager.createAuthorizedRequest("Protected Endpoint", "GET", "/api/protected")
                .check(status().not(401)) // Should not get unauthorized with valid token
        )
        .pause(1, 2)
        .exec(
            // Test with potentially invalid resource
            OAuthTokenManager.createAuthorizedRequest("Non-existent Resource", "GET", "/api/nonexistent")
                .check(status().in(404, 200)) // Either not found or OK (depending on API design)
        );

    // Token refresh scenario (simulates long-running test)
    private static final ScenarioBuilder tokenRefreshScenario = scenario("Token Refresh Test")
        .exec(OAuthTokenManager.acquireClientCredentialsToken(oauthConfig))
        .repeat(10).on(
            exec(
                OAuthTokenManager.createAuthorizedRequest("Long Running Request", "GET", "/api/status")
                    .check(status().is(200))
            )
            .pause(30) // Wait 30 seconds between requests to test token expiry
            // Token manager will automatically refresh if needed
        );

    // Load profile configuration
    private static final PopulationBuilder[] populations;

    static {
        if (oauthConfig.isEnabled()) {
            populations = new PopulationBuilder[]{
                readOnlyScenario.injectOpen(
                    rampUsers(50).during(30),
                    constantUsersPerSec(5).during(60)
                ),
                crudScenario.injectOpen(
                    rampUsers(30).during(45),
                    constantUsersPerSec(3).during(90)
                ),
                passwordFlowScenario.injectOpen(
                    rampUsers(20).during(60)
                ),
                errorHandlingScenario.injectOpen(
                    atOnceUsers(10)
                ),
                tokenRefreshScenario.injectOpen(
                    atOnceUsers(2)
                )
            };
        } else {
            // Fallback scenario without OAuth
            ScenarioBuilder fallbackScenario = scenario("No OAuth Fallback")
                .exec(
                    http("Health Check")
                        .get("/api/health")
                        .check(status().is(200))
                );
            
            populations = new PopulationBuilder[]{
                fallbackScenario.injectOpen(rampUsers(10).during(30))
            };
        }
    }

    // Assertions adjusted for OAuth overhead
    private static final Assertion[] assertions = new Assertion[] {
        global().responseTime().max().lt(5000), // Higher threshold for OAuth
        global().responseTime().mean().lt(2000),
        global().responseTime().percentile(95).lt(3000),
        global().successfulRequests().percent().gt(95.0), // Slightly lower due to test scenarios
        global().failedRequests().percent().lt(5.0)
    };

    {
        setUp(populations)
            .protocols(httpProtocol)
            .assertions(assertions);
    }
}