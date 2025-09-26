package co.tyrell.gatling.auth;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Base64;

/**
 * OAuth Token Manager for Gatling simulations.
 * Provides token acquisition, caching, and automatic refresh capabilities.
 */
public class OAuthTokenManager {
    
    private static final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();
    
    /**
     * Creates a Gatling chain to acquire an OAuth token using Client Credentials flow.
     * The token is cached and reused across virtual users.
     * 
     * @param config OAuth configuration
     * @return ChainBuilder that acquires and stores the token
     */
    public static ChainBuilder acquireClientCredentialsToken(OAuthConfig config) {
        if (!config.isEnabled()) {
            return exec(session -> session); // No-op if OAuth is disabled
        }
        
        String cacheKey = generateCacheKey(config);
        
        return exec(session -> {
            // Check if we have a valid cached token
            TokenInfo cachedToken = tokenCache.get(cacheKey);
            if (cachedToken != null && !isTokenExpired(cachedToken, config.getTokenRefreshBuffer())) {
                return session
                    .set("access_token", cachedToken.accessToken)
                    .set("token_type", cachedToken.tokenType);
            }
            
            // Token is expired or doesn't exist, need to acquire new one
            return session.set("oauth_cache_key", cacheKey);
        })
        .doIf(session -> !session.contains("access_token"))
            .then(
                exec(
                    http("OAuth Token Request")
                        .post(config.getTokenEndpoint())
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .formParam("grant_type", config.getGrantType())
                        .formParam("client_id", config.getClientId())
                        .formParam("client_secret", config.getClientSecret())
                        .formParam("scope", config.getScope())
                        .check(status().is(200))
                        .check(jsonPath("$.access_token").saveAs("access_token"))
                        .check(jsonPath("$.expires_in").optional().saveAs("expires_in"))
                        .check(jsonPath("$.token_type").optional().saveAs("token_type"))
                )
                .exec(session -> {
                    // Cache the token
                    String accessToken = session.getString("access_token");
                    String expiresInStr = session.getString("expires_in");
                    String tokenType = session.getString("token_type");
                    String sessionCacheKey = session.getString("oauth_cache_key");
                    
                    long expiresIn = 3600; // Default to 1 hour if not provided
                    if (expiresInStr != null) {
                        try {
                            expiresIn = Long.parseLong(expiresInStr);
                        } catch (NumberFormatException e) {
                            // Use default
                        }
                    }
                    
                    TokenInfo tokenInfo = new TokenInfo(
                        accessToken,
                        tokenType != null ? tokenType : "Bearer",
                        Instant.now().plusSeconds(expiresIn)
                    );
                    
                    tokenCache.put(sessionCacheKey, tokenInfo);
                    
                    return session;
                })
            );
    }
    
    /**
     * Creates a Gatling chain to acquire an OAuth token using Resource Owner Password Credentials flow.
     * 
     * @param config OAuth configuration
     * @return ChainBuilder that acquires and stores the token
     */
    public static ChainBuilder acquirePasswordCredentialsToken(OAuthConfig config) {
        if (!config.isEnabled()) {
            return exec(session -> session); // No-op if OAuth is disabled
        }
        
        String cacheKey = generateCacheKey(config);
        
        return exec(session -> {
            // Check if we have a valid cached token
            TokenInfo cachedToken = tokenCache.get(cacheKey);
            if (cachedToken != null && !isTokenExpired(cachedToken, config.getTokenRefreshBuffer())) {
                return session
                    .set("access_token", cachedToken.accessToken)
                    .set("token_type", cachedToken.tokenType);
            }
            
            return session.set("oauth_cache_key", cacheKey);
        })
        .doIf(session -> !session.contains("access_token"))
            .then(
                exec(
                    http("OAuth Password Token Request")
                        .post(config.getTokenEndpoint())
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Authorization", "Basic " + createBasicAuthHeader(config.getClientId(), config.getClientSecret()))
                        .formParam("grant_type", "password")
                        .formParam("username", config.getUsername())
                        .formParam("password", config.getPassword())
                        .formParam("scope", config.getScope())
                        .check(status().is(200))
                        .check(jsonPath("$.access_token").saveAs("access_token"))
                        .check(jsonPath("$.expires_in").optional().saveAs("expires_in"))
                        .check(jsonPath("$.token_type").optional().saveAs("token_type"))
                )
                .exec(session -> {
                    // Cache the token
                    String accessToken = session.getString("access_token");
                    String expiresInStr = session.getString("expires_in");
                    String tokenType = session.getString("token_type");
                    String sessionCacheKey = session.getString("oauth_cache_key");
                    
                    long expiresIn = 3600; // Default to 1 hour if not provided
                    if (expiresInStr != null) {
                        try {
                            expiresIn = Long.parseLong(expiresInStr);
                        } catch (NumberFormatException e) {
                            // Use default
                        }
                    }
                    
                    TokenInfo tokenInfo = new TokenInfo(
                        accessToken,
                        tokenType != null ? tokenType : "Bearer",
                        Instant.now().plusSeconds(expiresIn)
                    );
                    
                    tokenCache.put(sessionCacheKey, tokenInfo);
                    
                    return session;
                })
            );
    }
    
    /**
     * Creates an HTTP request builder with OAuth authorization header.
     * 
     * @param requestName Name of the request for Gatling reporting
     * @param method HTTP method (GET, POST, etc.)
     * @param url Request URL
     * @return HttpRequestActionBuilder with OAuth header
     */
    public static HttpRequestActionBuilder createAuthorizedRequest(String requestName, String method, String url) {
        HttpRequestActionBuilder request;
        
        switch (method.toUpperCase()) {
            case "GET":
                request = http(requestName).get(url);
                break;
            case "POST":
                request = http(requestName).post(url);
                break;
            case "PUT":
                request = http(requestName).put(url);
                break;
            case "DELETE":
                request = http(requestName).delete(url);
                break;
            case "PATCH":
                request = http(requestName).patch(url);
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        return request.header("Authorization", "#{token_type} #{access_token}");
    }
    
    /**
     * Clears the token cache. Useful for testing or cleanup.
     */
    public static void clearTokenCache() {
        tokenCache.clear();
    }
    
    private static String generateCacheKey(OAuthConfig config) {
        return String.format("%s:%s:%s", 
            config.getTokenEndpoint(), 
            config.getClientId(), 
            config.getScope());
    }
    
    private static boolean isTokenExpired(TokenInfo token, long bufferSeconds) {
        return Instant.now().plusSeconds(bufferSeconds).isAfter(token.expiresAt);
    }
    
    private static String createBasicAuthHeader(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        return Base64.getEncoder().encodeToString(credentials.getBytes());
    }
    
    /**
     * Internal class to hold token information
     */
    private static class TokenInfo {
        final String accessToken;
        final String tokenType;
        final Instant expiresAt;
        
        TokenInfo(String accessToken, String tokenType, Instant expiresAt) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.expiresAt = expiresAt;
        }
    }
}