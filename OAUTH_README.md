# OAuth Support for Gatling API Benchmark Suite

This document describes the OAuth authentication support added to the Gatling API Benchmark Suite.

## Overview

The OAuth support has been implemented as an optional authentication mechanism that can be easily enabled/disabled through configuration. It supports multiple OAuth flows and provides comprehensive token management.

## Components

### 1. OAuth Configuration (`OAuthConfig.java`)
- **Purpose**: Manages OAuth configuration parameters
- **Features**: 
  - Builder pattern for easy configuration
  - Support for multiple OAuth grant types
  - Configurable token caching and refresh settings
  - Environment-based configuration loading

### 2. OAuth Token Manager (`OAuthTokenManager.java`)
- **Purpose**: Handles token acquisition, caching, and refresh
- **Features**:
  - Automatic token caching to avoid unnecessary requests
  - Token refresh before expiration
  - Thread-safe token management
  - Support for Client Credentials and Authorization Code flows

### 3. Enhanced Simulations
- **`ApiBenchmarkSimulationWithOAuth.java`**: Basic simulation with optional OAuth
- **`OAuthComprehensiveSimulation.java`**: Advanced simulation demonstrating multiple OAuth scenarios

## Configuration

### OAuth Properties File (`oauth-config.properties`)

```properties
# Enable/disable OAuth authentication
oauth.enabled=true

# OAuth Server Configuration
oauth.tokenUrl=https://your-auth-server.com/oauth/token
oauth.clientId=your-client-id
oauth.clientSecret=your-client-secret

# OAuth Flow Configuration
oauth.grantType=client_credentials
oauth.scope=api:read api:write

# Token Management
oauth.tokenCache.enabled=true
oauth.tokenCache.refreshThresholdSeconds=300

# Request Configuration
oauth.request.timeout=5000
```

## Usage

### Running OAuth-Enabled Tests

1. **Basic OAuth Test**:
   ```bash
   ./scripts/run-oauth-test.sh
   ```

2. **Comprehensive OAuth Test**:
   ```bash
   ./scripts/run-oauth-comprehensive.sh
   ```

3. **Maven Direct Execution**:
   ```bash
   mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth
   ```

### Configuration Options

#### Grant Types Supported
- `client_credentials`: Service-to-service authentication
- `authorization_code`: User-based authentication (requires additional setup)

#### Token Caching
- **Enabled by default**: Reduces OAuth server load during load testing
- **Refresh threshold**: Configurable time before token expiration to refresh
- **Thread-safe**: Safe for concurrent use in load testing scenarios

## Implementation Details

### Token Acquisition Flow
1. Check if cached token exists and is valid
2. If no valid token, request new token from OAuth server
3. Cache token with expiration information
4. Return token for use in API requests

### Error Handling
- Network timeouts and connection errors
- Invalid client credentials
- Token refresh failures
- Malformed OAuth server responses

### Performance Considerations
- Token caching minimizes OAuth server requests
- Configurable timeouts prevent hanging requests
- Efficient token validation and refresh logic

## Example API Integration

The OAuth implementation can be used with any REST API that requires OAuth authentication:

```java
// Basic usage in a Gatling scenario
ScenarioBuilder scenario = scenario("OAuth API Test")
    .exec(session -> {
        if (oauthConfig.isEnabled()) {
            String token = tokenManager.getValidToken();
            return session.set("authToken", token);
        }
        return session;
    })
    .exec(http("API Request")
        .get("/api/protected-endpoint")
        .header("Authorization", "Bearer #{authToken}")
    );
```

## Troubleshooting

### Common Issues

1. **OAuth Configuration Not Found**
   - Ensure `oauth-config.properties` exists in `src/test/resources/`
   - Verify all required properties are set

2. **Token Request Failures**
   - Check OAuth server URL and credentials
   - Verify network connectivity
   - Review OAuth server logs for error details

3. **Performance Issues**
   - Ensure token caching is enabled
   - Adjust refresh threshold if tokens are refreshed too frequently
   - Monitor OAuth server performance under load

### Debug Mode
Enable debug logging by adding to `logback-test.xml`:
```xml
<logger name="co.tyrell.gatling.auth" level="DEBUG"/>
```

## Future Enhancements

Potential improvements for the OAuth implementation:
- Support for additional OAuth flows (PKCE, device flow)
- JWT token validation and claims extraction
- OAuth scope-based authorization testing
- Token introspection support
- Multi-tenant OAuth configuration