# API Benchmark Suite

A comprehensive REST API performance testing suite built with Gatling, featuring **optional OAuth 2.0 authentication support** for enterprise-grade load testing.

## 🚀 Key Features

- **High-Performance Load Testing** with Gatling Maven plugin
- **OAuth 2.0 Authentication Support** (Client Credentials & Password flows)
- **Configurable NFR Assertions** for enterprise-grade testing
- **Sample OAuth-enabled API** for end-to-end testing
- **Smart Token Management** with caching and automatic refresh
- **Multiple Testing Scenarios** from basic to comprehensive OAuth flows

## 📁 Project Structure

```
api-benchmark-suite/
├── api/                    # OAuth-enabled Flask test API
├── gatling-maven/         # Gatling performance testing suite
├── scripts/               # Ready-to-use testing scripts
└── docs/                  # Documentation (README, OAuth guide)
```

## ⚡ Quick Start

### Option 1: Complete OAuth Demo (Recommended)
```bash
# Run full OAuth demonstration with API and performance testing
./scripts/oauth-demo.sh
```

### Option 2: Manual Steps
```bash
# 1. Start the OAuth-enabled test API
cd api && .venv/bin/python app.py

# 2. Run OAuth performance tests (in another terminal)
./scripts/run-oauth-test.sh
```

## 🔧 Prerequisites

- **Java 21 LTS** (recommended) or Java 11+
- **Maven 3.6+** 
- **Python 3.x** (for test API)
- **curl** (for API testing)

## 🔐 OAuth Configuration

OAuth support is **completely optional**. Enable it by configuring `gatling-maven/src/test/resources/oauth-config.properties`:

```properties
oauth.enabled=true
oauth.token.endpoint=http://localhost:5050/oauth/token
oauth.client.id=demo-client-id
oauth.client.secret=demo-client-secret
oauth.scope=api:read api:write
```

### Available Test Clients
| Client ID | Secret | Scopes | Purpose |
|-----------|--------|---------|---------|
| `demo-client-id` | `demo-client-secret` | `api:read`, `api:write`, `admin` | Full access testing |
| `test-client` | `test-secret` | `api:read` | Read-only testing |

## 🧪 API Endpoints

### Public Endpoints (No Authentication)
- `GET /api/health` - Health check
- `GET /api/hello` - Basic hello endpoint  
- `POST /oauth/token` - OAuth token endpoint

### Protected Endpoints (OAuth Required)
- `GET /api/hello/protected` - Protected hello
- `GET /api/customers` - List customers
- `POST /api/customers` - Create customer (requires `api:write`)
- `GET|PUT|DELETE /api/customers/{id}` - Customer operations
- `GET /api/cev-events` - List CEV events
- `POST /api/cev-events` - Create CEV event (requires `api:write`)
- `GET|PUT /api/cev-events/{id}` - CEV event operations
- `GET /api/admin/stats` - Admin statistics (requires `admin`)

## 🏃‍♂️ Running Tests

### OAuth-Enabled Tests
```bash
# Mixed public/protected endpoint testing
./scripts/run-oauth-test.sh

# Comprehensive OAuth scenarios
./scripts/run-oauth-comprehensive.sh
```

### Traditional Tests (No OAuth)
```bash
# Standard performance testing
./scripts/run-standard-test.sh

# Using specific Java version
./scripts/run-with-java21.sh gatling:test
```

### Direct Maven Execution
```bash
cd gatling-maven

# OAuth simulation
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth

# Standard simulation
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulation
```

## 🖥️ Setting Up the Test API Server

### Step 1: Environment Setup
```bash
cd api

# Create virtual environment (if not exists)
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip3 install -r requirements.txt
```

### Step 2: Start the Server
```bash
# Using virtual environment (recommended)
.venv/bin/python app.py

# Or using system Python
python3 app.py
```

### Step 3: Verify Server is Running
```bash
# Health check
curl http://localhost:5050/api/health

# Get OAuth token
curl -X POST http://localhost:5050/oauth/token \
  -d "grant_type=client_credentials&client_id=demo-client-id&client_secret=demo-client-secret&scope=api:read api:write"

# Test protected endpoint (replace YOUR_TOKEN_HERE with actual token)
curl -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  http://localhost:5050/api/hello/protected
```

### 🚨 Troubleshooting
| Issue | Solution |
|-------|----------|
| `ModuleNotFoundError: No module named 'jwt'` | Run `pip3 install -r requirements.txt` |
| `pip: command not found` | Use `pip3` instead of `pip` |
| Connection refused | Ensure server is running on port 5050 |
| Permission denied | Check write permissions in project directory |

## ⚙️ Performance Parameters & NFRs

### Load Testing Profiles

| Test Type | Users | Ramp-up | Duration | Target |
|-----------|-------|---------|----------|--------|
| **Standard** | 500 concurrent | 60s | 2 min constant load | `http://localhost:5050/api/hello` |
| **OAuth** | 100 concurrent | 30s | Mixed scenarios | Multiple protected endpoints |

### NFR Thresholds

| Metric | Standard Tests | OAuth Tests | 
|--------|----------------|-------------|
| **Max Response Time** | < 1000ms | < 2000ms |
| **Mean Response Time** | < 500ms | < 1000ms |
| **95th Percentile** | < 800ms | < 1500ms |
| **Success Rate** | > 99% | > 95% |
| **Error Rate** | < 1% | < 5% |
| **Throughput** | > 10 req/sec | > 2 req/sec |

## 🔧 Customization

### Modifying NFR Thresholds
1. Edit `gatling-maven/src/test/java/co/tyrell/gatling/simulation/ApiBenchmarkSimulation.java`
2. Locate the `assertions` section:
   ```java
   .assertions(
       global().responseTime().max().lt(1000),           // Max response time
       global().requestsPerSec().gt(10.0),               // Throughput  
       global().failedRequests().percent().lt(1.0)       // Error rate
   )
   ```
3. Adjust values and re-run tests

### OAuth Configuration Files
- **`oauth-config.properties`** - Main OAuth settings
- **`OAuthConfig.java`** - Configuration class with builder pattern  
- **`OAuthTokenManager.java`** - Token caching and refresh logic
- **OAuth Simulations** - `ApiBenchmarkSimulationWithOAuth.java`, `OAuthComprehensiveSimulation.java`

## 🔐 OAuth Implementation Details

### Supported Flows
- **Client Credentials Grant** - Service-to-service authentication
- **Password Grant** - User credential-based authentication  

### Features
- ✅ **Smart Token Caching** - Tokens cached and reused across requests
- ✅ **Automatic Refresh** - Tokens refreshed before expiration  
- ✅ **Thread-Safe** - Safe for concurrent load testing
- ✅ **JWT Support** - Full JWT token validation
- ✅ **Scope-based Authorization** - Fine-grained permission testing
- ✅ **Multiple Client Support** - Test different client configurations
- ✅ **Configurable Timeouts** - OAuth request timeout handling

## 📊 Results & Reports

After running tests, view detailed HTML reports:
```bash
# Reports are generated in:
gatling-maven/target/gatling/

# Open the latest report:
open gatling-maven/target/gatling/*/index.html
```

## 🚀 Quick Reference

### Essential Commands
```bash
# Complete OAuth demo
./scripts/oauth-demo.sh

# Start API server
cd api && .venv/bin/python app.py

# Run OAuth tests
./scripts/run-oauth-test.sh

# Run standard tests
./scripts/run-standard-test.sh

# Manual token testing
curl -X POST http://localhost:5050/oauth/token \
  -d "grant_type=client_credentials&client_id=demo-client-id&client_secret=demo-client-secret"
```

### Test Clients
| Client ID | Secret | Access Level |
|-----------|--------|--------------|
| `demo-client-id` | `demo-client-secret` | Full access (read/write/admin) |
| `test-client` | `test-secret` | Read-only access |

## 📚 Additional Documentation

- **[OAUTH_README.md](OAUTH_README.md)** - Comprehensive OAuth implementation guide
- **[OAuth API Testing Guide](scripts/test-oauth-api.sh)** - Manual OAuth API testing  
- **[Performance Test Scripts](scripts/)** - Ready-to-use testing scripts

## 🤝 Contributing

This project welcomes contributions! Please see individual component documentation for implementation details.

## 👨‍💻 Author

**Tyrell Perera**  
🌐 Website: [tyrell.co](https://tyrell.co)

## 📄 License

This project is released under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## ✨ What's Included

This API Benchmark Suite provides everything you need for professional REST API performance testing:

### 🔥 Core Features
- **Production-ready OAuth 2.0** implementation with JWT tokens
- **Enterprise-grade load testing** with Gatling
- **Smart token management** with caching and refresh
- **Comprehensive test scenarios** from basic to advanced
- **Professional reporting** with detailed HTML reports

### 🎯 Perfect For
- **API Performance Testing** - Validate your API against NFRs
- **OAuth Implementation** - Reference implementation for OAuth 2.0
- **Load Testing** - High-performance testing with Gatling
- **CI/CD Integration** - Automated performance testing in pipelines
- **Enterprise Development** - Professional-grade testing tools

Get started in minutes with the OAuth demo, or customize for your specific API testing needs!