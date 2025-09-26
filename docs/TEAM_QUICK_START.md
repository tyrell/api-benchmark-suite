# API Performance Testing - Quick Start for Dev Team

## What This Does
This tool automatically tests your **business-critical API endpoints** for performance and reliability - the kind of testing that catches issues before they hit production. Since your APIs are OAuth-protected, the tool handles authentication automatically so you can focus on testing what matters:
- ✅ Customer API response times under load
- ✅ CRUD operations success rates with multiple concurrent users  
- ✅ Search and retrieval endpoint performance
- ✅ How your business APIs behave with realistic traffic patterns

## Why We Need This
Before deploying to production, we need to know if our critical business endpoints can handle the load:
- Will customer creation/updates work reliably with 50+ concurrent users?
- Are search and data retrieval APIs fast enough under real traffic?
- Do our core business operations maintain acceptable response times?
- Will API performance meet our SLA commitments?

## Quick Setup (5 minutes)

### 1. Clone and Configure
```bash
git clone https://github.com/tyrell/api-benchmark-suite.git
cd api-benchmark-suite/gatling-maven
```

### 2. Configure for Your API Environment
Edit `src/test/resources/gatling-simulation.properties`:
```properties
# Point to your perf environment
api.base.url=https://your-perf-api.company.com

# OAuth credentials (needed to access your protected APIs)
oauth.enabled=true
oauth.client.id=your-client-id
oauth.client.secret=your-client-secret
oauth.token.endpoint=/oauth/token
oauth.scope=api:read api:write

# Your API brand/tenant identifier
api.brand=YOUR_BRAND
```

### 3. Test Your Business APIs
```bash
# Test your business APIs with 25 concurrent users
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth \
  -Dgatling.users=25 -Dload.steady.state.duration=120

# Quick API smoke test (5 users, 1 minute)
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth \
  -Dgatling.users=5 -Dload.steady.state.duration=60
```

## Understanding the Results

After the test runs, you'll get a report like this:

```
✅ GOOD API PERFORMANCE:
> Create Customer: percentage of successful events is greater than 95.0 : true (actual : 100.0%)
> Get Customer: percentage of successful events is greater than 95.0 : true (actual : 100.0%)
> Search Customers: 95th percentile of response time is less than 500.0 : true (actual : 245ms)
> Update Customer: percentage of successful events is greater than 95.0 : true (actual : 99.8%)

❌ API ISSUES TO FIX:
> Get Vulnerabilities: max response time is less than 1500.0 : false (actual : 3200ms)
> Lifecycle Operation: percentage of successful events is greater than 95.0 : false (actual : 85.2%)
```

### What These Business API Metrics Mean:
- **Customer APIs Success > 95%**: Core business operations work reliably under load ✅
- **Search Response Time < 500ms**: Users get fast search results ⏱️
- **CRUD Operations Success > 95%**: Create/Read/Update/Delete operations don't fail ✅  
- **95th Percentile < 800ms**: Most business API calls complete quickly ✅

## Common Business API Testing Scenarios

### Scenario 1: "Can our APIs handle expected customer traffic?"
```bash
# Test with your expected peak concurrent users
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth \
  -Dgatling.users=50 -Dload.steady.state.duration=300
```

### Scenario 2: "Heavy business load stress test"
```bash
# High-volume API load test
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth \
  -Dgatling.users=100 -Dload.ramp.up.duration=30 -Dload.steady.state.duration=180
```

### Scenario 3: "Quick API smoke test after deployment"
```bash
# Fast business API validation (2 minutes)
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth \
  -Dgatling.users=5 -Dload.steady.state.duration=30
```

## Key Configuration Options

You can adjust these without changing code:

```properties
# Load Testing
load.users=25                          # Number of concurrent users
load.ramp.up.duration=10              # Seconds to ramp up to full load
load.steady.state.duration=60         # Seconds to maintain load

# Performance Thresholds (adjust based on your requirements)
performance.max.response.time=1500    # Maximum acceptable response time (ms)
performance.success.rate.threshold=95.0  # Minimum success rate (%)
performance.mean.response.time=500     # Target average response time (ms)
```

## When to Run Tests

🔄 **Regular API Testing:**
- Before major business feature releases
- After API endpoint changes
- After infrastructure or database changes  
- Weekly during active development

⚠️ **Critical API Testing:**
- Before production deployments
- After performance optimization changes
- When adding new business endpoints
- After database or backend system updates

## Getting Help

1. **View the full README.md** for detailed configuration options
2. **Check the HTML reports** generated in `target/gatling/` after each test
3. **Ask questions** - this is meant to be easy to use!

## Sample Business API Test Results

When your business API tests complete successfully, you'll see:
```
✅ All Business API Performance Thresholds Met!
- 99.2% overall API success rate (target: >95%)
- Create Customer: 100% success rate
- Get Customer: 100% success rate  
- Search Customers: 287ms average (target: <500ms)
- Update Customer: 156ms 95th percentile (target: <800ms)
- 45.3 API requests/second throughput
```

---

**Need different business API test scenarios or have questions about your endpoint performance results?** Just ask! This tool is designed to thoroughly test your critical business APIs and provide clear insights into how they perform under realistic load conditions.