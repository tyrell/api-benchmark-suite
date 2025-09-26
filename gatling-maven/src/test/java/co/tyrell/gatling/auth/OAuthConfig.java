package co.tyrell.gatling.auth;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * OAuth configuration class to hold OAuth-related settings.
 * This allows for easy configuration of OAuth parameters without hardcoding.
 */
public class OAuthConfig {
    
    // OAuth settings
    private final String tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final String grantType;
    
    // Optional settings for Authorization Code flow
    private final String authorizationEndpoint;
    private final String redirectUri;
    private final String username;
    private final String password;
    
    // Token management settings
    private final boolean enabled;
    private final long tokenRefreshBuffer; // Seconds before expiry to refresh token
    
    public OAuthConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.tokenEndpoint = builder.tokenEndpoint;
        this.clientId = builder.clientId;
        this.clientSecret = builder.clientSecret;
        this.scope = builder.scope;
        this.grantType = builder.grantType;
        this.authorizationEndpoint = builder.authorizationEndpoint;
        this.redirectUri = builder.redirectUri;
        this.username = builder.username;
        this.password = builder.password;
        this.tokenRefreshBuffer = builder.tokenRefreshBuffer;
    }
    
    // Getters
    public boolean isEnabled() { return enabled; }
    public String getTokenEndpoint() { return tokenEndpoint; }
    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getScope() { return scope; }
    public String getGrantType() { return grantType; }
    public String getAuthorizationEndpoint() { return authorizationEndpoint; }
    public String getRedirectUri() { return redirectUri; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public long getTokenRefreshBuffer() { return tokenRefreshBuffer; }
    
    // Builder pattern for easy configuration
    public static class Builder {
        private boolean enabled = false;
        private String tokenEndpoint;
        private String clientId;
        private String clientSecret;
        private String scope = "";
        private String grantType = "client_credentials";
        private String authorizationEndpoint;
        private String redirectUri;
        private String username;
        private String password;
        private long tokenRefreshBuffer = 300; // 5 minutes default
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder tokenEndpoint(String tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
            return this;
        }
        
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }
        
        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }
        
        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }
        
        public Builder grantType(String grantType) {
            this.grantType = grantType;
            return this;
        }
        
        public Builder authorizationEndpoint(String authorizationEndpoint) {
            this.authorizationEndpoint = authorizationEndpoint;
            return this;
        }
        
        public Builder redirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
            return this;
        }
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        public Builder tokenRefreshBuffer(long tokenRefreshBuffer) {
            this.tokenRefreshBuffer = tokenRefreshBuffer;
            return this;
        }
        
        public OAuthConfig build() {
            return new OAuthConfig(this);
        }
    }
    
    // Load properties file from classpath
    private static Properties loadPropertiesFile() {
        Properties properties = new Properties();
        try (InputStream input = OAuthConfig.class.getClassLoader().getResourceAsStream("gatling-simulation.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            // Ignore - we'll fall back to system properties and defaults
        }
        return properties;
    }

    // Get property value with fallback chain: system property -> properties file -> default
    private static String getProperty(Properties props, String key, String defaultValue) {
        // First check system properties (highest priority)
        String systemValue = System.getProperty(key);
        if (systemValue != null) {
            return systemValue;
        }
        
        // Then check properties file
        String fileValue = props.getProperty(key);
        if (fileValue != null && !fileValue.trim().isEmpty()) {
            return fileValue;
        }
        
        // Finally use default
        return defaultValue;
    }
    
    private static boolean getBooleanProperty(Properties props, String key, boolean defaultValue) {
        String value = getProperty(props, key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }
    
    private static long getLongProperty(Properties props, String key, long defaultValue) {
        String value = getProperty(props, key, String.valueOf(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    // Factory method to create from system properties and properties file
    public static OAuthConfig fromSystemProperties() {
        Properties props = loadPropertiesFile();
        
        return new Builder()
            .enabled(getBooleanProperty(props, "oauth.enabled", false))
            .tokenEndpoint(getProperty(props, "oauth.token.endpoint", null))
            .clientId(getProperty(props, "oauth.client.id", null))
            .clientSecret(getProperty(props, "oauth.client.secret", null))
            .scope(getProperty(props, "oauth.scope", ""))
            .grantType(getProperty(props, "oauth.grant.type", "client_credentials"))
            .authorizationEndpoint(getProperty(props, "oauth.authorization.endpoint", null))
            .redirectUri(getProperty(props, "oauth.redirect.uri", null))
            .username(getProperty(props, "oauth.username", null))
            .password(getProperty(props, "oauth.password", null))
            .tokenRefreshBuffer(getLongProperty(props, "oauth.token.refresh.buffer", 300))
            .build();
    }
}