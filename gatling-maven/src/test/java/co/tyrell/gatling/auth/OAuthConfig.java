package co.tyrell.gatling.auth;

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
    
    // Factory method to create from system properties
    public static OAuthConfig fromSystemProperties() {
        return new Builder()
            .enabled(Boolean.parseBoolean(System.getProperty("oauth.enabled", "false")))
            .tokenEndpoint(System.getProperty("oauth.token.endpoint"))
            .clientId(System.getProperty("oauth.client.id"))
            .clientSecret(System.getProperty("oauth.client.secret"))
            .scope(System.getProperty("oauth.scope", ""))
            .grantType(System.getProperty("oauth.grant.type", "client_credentials"))
            .authorizationEndpoint(System.getProperty("oauth.authorization.endpoint"))
            .redirectUri(System.getProperty("oauth.redirect.uri"))
            .username(System.getProperty("oauth.username"))
            .password(System.getProperty("oauth.password"))
            .tokenRefreshBuffer(Long.parseLong(System.getProperty("oauth.token.refresh.buffer", "300")))
            .build();
    }
}