
# API Benchmark Suite

This project benchmarks REST API endpoints against NFRs using Gatling (Maven plugin), with **optional OAuth 2.0 authentication support**.

## 🚀 Key Features

- **High-Performance Load Testing** with Gatling
- **OAuth 2.0 Authentication Support** (Client Credentials & Passw## 🚀 Quick Reference

### Essential C## 📚 Additional Documentationmmands

**Start OAuth Test API Server:**
```bash
cd api
.venv/bin/python app.py
```)
- **Configurable NFR Assertions** for enterprise-grade testing
- **Sample OAuth-enabled API** for end-to-end testing
- **Docker-ready test environment**

## Structure

- `api/`: **OAuth-enabled Flask API** for local testing with JWT tokens
- `gatling-maven/`: Gatling Maven plugin project (main benchmarking suite)  
- `scripts/`: Helper scripts including OAuth demo and testing utilities
- `.venv/`: Python virtual environment (created automatically)

## Prerequisites
- Java 21 LTS (recommended) or Java 11+
- Maven 3.6+
- Python 3.x (for test API)
- curl (for API testing)

## 🎯 Quick Start

### OAuth-Enabled Demo (Recommended)
```bash
# Complete OAuth demonstration with API and performance testing
./scripts/oauth-demo.sh
```

This script will:
1. Start the OAuth-enabled test API
2. Demonstrate OAuth token acquisition
3. Test protected endpoints
4. Run Gatling performance tests with OAuth
5. Show results and cleanup

### Manual Setup
```bash
# Start the OAuth-enabled test API (see detailed instructions below)
cd api && /path/to/venv/bin/python app.py

# In another terminal, run OAuth performance tests
./scripts/run-oauth-test.sh

# Or run comprehensive OAuth scenarios
./scripts/run-oauth-comprehensive.sh
```

**💡 Tip**: For detailed API server setup instructions, see the [Running the Test API Server](#-running-the-test-api-server) section below.

## 🔐 OAuth Configuration

OAuth support is **optional** and can be enabled via configuration:

### 1. Enable OAuth in `gatling-maven/src/test/resources/oauth-config.properties`:
```properties
oauth.enabled=true
oauth.token.endpoint=http://localhost:5050/oauth/token
oauth.client.id=demo-client-id
oauth.client.secret=demo-client-secret
oauth.scope=api:read api:write
```

### 2. Available OAuth Clients (Test API):
- **demo-client-id** / demo-client-secret (scopes: api:read, api:write, admin)
- **test-client** / test-secret (scopes: api:read only)

### 3. Run OAuth-enabled Tests:
```bash
# OAuth simulation
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth

# Comprehensive OAuth scenarios  
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.OAuthComprehensiveSimulation
```

## 🧪 Test API Endpoints

### Public Endpoints (No Auth):
- `GET /api/health` - Health check
- `GET /api/hello` - Basic hello endpoint
- `POST /oauth/token` - OAuth token endpoint

### Protected Endpoints (OAuth Required):
- `GET /api/hello/protected` - Protected hello
- `GET /api/customers` - List customers 
- `POST /api/customers` - Create customer (write scope)
- `GET|PUT|DELETE /api/customers/{id}` - Customer operations
- `GET /api/cev-events` - List CEV events
- `POST /api/cev-events` - Create CEV event (write scope)
- `GET|PUT /api/cev-events/{id}` - CEV event operations
- `GET /api/admin/stats` - Admin statistics (admin scope)

## 🖥️ Running the Test API Server

The OAuth-enabled test API server requires proper setup for full functionality. Follow these steps:

### Step 1: Navigate to API Directory
```bash
cd api
```

### Step 2: Set Up Python Environment
The project uses a virtual environment for dependency management:

```bash
# The Python environment will be configured automatically when using VS Code tools
# Or manually create a virtual environment:
python3 -m venv .venv
source .venv/bin/activate
```

### Step 3: Install Dependencies
```bash
# Install required packages
pip3 install flask pyjwt python-dateutil

# Or install from requirements.txt
pip3 install -r requirements.txt
```

### Step 4: Start the Server
```bash
# Using virtual environment (recommended):
.venv/bin/python app.py

# Or using system Python:
python3 app.py
```

### ✅ Server Started Successfully
When running, you'll see:
```
Starting API Benchmark Suite Test Server...
Available OAuth clients:
  - Client ID: demo-client-id
    Secret: demo-client-secret
    Scopes: api:read, api:write, admin
  - Client ID: test-client
    Secret: test-secret
    Scopes: api:read

Server starting on http://0.0.0.0:5050
* Running on http://127.0.0.1:5050
```

### 🧪 Quick Test Commands
```bash
# Health check (no auth required)
curl http://localhost:5050/api/health

# Get OAuth token
curl -X POST http://localhost:5050/oauth/token \
  -d "grant_type=client_credentials&client_id=demo-client-id&client_secret=demo-client-secret&scope=api:read api:write"

# Test protected endpoint (use token from above)
curl -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  http://localhost:5050/api/hello/protected
```

### 🚨 Troubleshooting
- **"ModuleNotFoundError: No module named 'jwt'"**: Install dependencies with `pip3 install -r requirements.txt`
- **"pip: command not found"**: Use `pip3` instead of `pip`
- **Connection refused**: Ensure server is running and accessible on port 5050
- **Permission denied**: Make sure you have write permissions in the project directory

## Usage
1. **Start your API** (OAuth-enabled or traditional):
   ```bash
   # OAuth-enabled test API (see detailed setup instructions above)
   cd api && .venv/bin/python app.py
   
   # Or use your own API endpoint
   ```
   **📖 For step-by-step API server setup, see [Running the Test API Server](#-running-the-test-api-server) section above.**

2. **Configure OAuth** (optional - skip for non-OAuth APIs):
   ```bash
   # Edit oauth-config.properties with your OAuth server details
   # Or use the pre-configured local test API settings
   ```

3. **Update simulation settings**:
   - Edit simulation files in `gatling-maven/src/test/java/co/tyrell/gatling/simulation/`
   - Set your API's base URL and endpoints
   - Adjust NFR thresholds as needed

4. **Run performance tests**:
   ```bash
   # OAuth-enabled tests (recommended)
   ./scripts/run-oauth-test.sh
   
   # Traditional tests (no OAuth)
   ./scripts/run-standard-test.sh
   
   # Using Java 21 directly
   ./scripts/run-with-java21.sh gatling:test
   
   # Manual Maven execution
   cd gatling-maven && mvn gatling:test
   ```

5. **View results**: Check Gatling HTML reports in `gatling-maven/target/gatling/`

## Default Simulation Parameters

### Standard Tests
- **Concurrent Users:** 500 (ramp up over 60 seconds)
- **Constant Load:** 50 users/sec for 2 minutes
- **Target Endpoint:** `http://localhost:5050/api/hello`

### OAuth Tests  
- **Concurrent Users:** 100 (adjusted for OAuth overhead)
- **Mixed Load:** Health checks, public APIs, and OAuth-protected endpoints
- **Token Caching:** Enabled to minimize OAuth server load
- **Target Endpoints:** Multiple OAuth-protected endpoints

### NFR Thresholds
- **Max response time:** < 1000ms (< 2000ms for OAuth)
- **Mean response time:** < 500ms (< 1000ms for OAuth)  
- **95th percentile:** < 800ms (< 1500ms for OAuth)
- **Success rate:** > 99% (> 95% for OAuth due to expected 401s)
- **Error rate:** < 1% (< 5% for OAuth)
- **Throughput:** > 10 requests/sec (> 2 requests/sec for OAuth)

## 🔧 OAuth Implementation Details

### Supported OAuth Flows
- **Client Credentials Grant:** Service-to-service authentication
- **Password Grant:** User credential-based authentication (configurable)

### Token Management
- **Smart Caching:** Tokens cached and reused across requests
- **Automatic Refresh:** Tokens refreshed before expiration
- **Thread-Safe:** Safe for concurrent load testing
- **Configurable Refresh Buffer:** Default 300 seconds before expiry

### Security Features
- **JWT Token Support:** Full JWT token validation in test API
- **Scope-based Authorization:** Fine-grained permission testing
- **Multiple Client Support:** Test different client configurations
- **Request Timeout Handling:** Configurable OAuth request timeouts
  - Throughput > 10 requests/sec
  - At least 10 requests sent
  - Endpoint-specific response time and success rate

## Supported NFRs
This suite currently supports automated benchmarking of the following non-functional requirements for REST APIs:

- **Response Time**: Max, mean, and 95th percentile response times
- **Throughput**: Requests per second
- **Error Rate**: Percentage of failed requests
- **Success Rate**: Percentage of successful requests
- **Availability**: HTTP 200 response checks
- **Scalability**: Ramp-up and constant load profiles
- **Endpoint-specific checks**: Per-endpoint response time and success rate

## Customization

### OAuth Configuration Files
- **`oauth-config.properties`**: Main OAuth configuration
- **`OAuthConfig.java`**: OAuth configuration class with builder pattern
- **`OAuthTokenManager.java`**: Smart token acquisition and caching
- **`ApiBenchmarkSimulationWithOAuth.java`**: OAuth-enabled simulation
- **`OAuthComprehensiveSimulation.java`**: Advanced OAuth scenarios

### Non-OAuth Configuration  
- Edit `gatling-maven/src/test/java/co/tyrell/gatling/simulation/ApiBenchmarkSimulation.java` to target your API endpoints and NFRs.

### OAuth-Enabled API Customization
The included test API (`api/app.py`) demonstrates:
- OAuth 2.0 Client Credentials flow
- JWT token generation and validation  
- Scope-based authorization
- Protected endpoint patterns
- Multiple client configurations

You can use this as a reference for implementing OAuth in your own APIs.


### Changing NFR Thresholds
To change the performance thresholds (NFRs) for your API tests:

1. Open `gatling-maven/src/test/java/co/tyrell/gatling/simulation/ApiBenchmarkSimulation.java`.
2. Locate the `assertions` array. Each line sets a threshold for a specific NFR, e.g.:
	- `global().responseTime().max().lt(1000)` sets max response time < 1000ms
	- `global().requestsPerSec().gt(10.0)` sets throughput > 10 requests/sec
	- `global().failedRequests().percent().lt(1.0)` sets error rate < 1%
3. Change the numeric values to your desired thresholds.
4. Save the file and re-run the simulation.

You can also adjust the load profile (number of users, ramp-up time, etc.) in the `injectOpen` section of the simulation file.

## � Quick Reference

### Essential Commands

**Start OAuth Test API Server:**
```bash
cd /Users/tyrell/Development/git/api-benchmark-suite/api
/Users/tyrell/Development/git/api-benchmark-suite/.venv/bin/python app.py
```

**Run OAuth Performance Tests:**
```bash
cd gatling-maven
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth
```

**Test API Manually:**
```bash
# Health check
curl http://localhost:5050/api/health

# Get OAuth token
curl -X POST http://localhost:5050/oauth/token \
  -d "grant_type=client_credentials&client_id=demo-client-id&client_secret=demo-client-secret"
```

**OAuth Clients for Testing:**
- `demo-client-id` / `demo-client-secret` (full access)
- `test-client` / `test-secret` (read-only)

## �📚 Additional Documentation

- **[OAUTH_README.md](OAUTH_README.md)**: Comprehensive OAuth implementation guide
- **[OAuth API Testing Guide](scripts/test-oauth-api.sh)**: Manual OAuth API testing
- **[Performance Test Scripts](scripts/)**: Ready-to-use testing scripts

## 🤝 Contributing

This project welcomes contributions! Please see individual component documentation for implementation details.

## Author

**Tyrell Perera**  
🌐 Website: [tyrell.co](https://tyrell.co)

## Open Source & License

This project is released under the MIT License. See the included `LICENSE` file for details.


