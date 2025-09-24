
# API Benchmark Suite

This project benchmarks REST API endpoints against NFRs using Gatling (Maven plugin).

## Structure

- `api/`: Sample Flask API for local testing
- `gatling-maven/`: Gatling Maven plugin project (main benchmarking suite)
- `scripts/`: Helper scripts (test-api.sh for API health)

## Prerequisites
- Java 21 LTS (recommended) or Java 11+
- Maven 3.6+

## Quick Start

### Using the Recommended Java 21 Setup
```bash
# Use the included script for Java 21 (recommended)
./scripts/run-with-java21.sh clean compile
./scripts/run-with-java21.sh gatling:test
```

## Usage
1. Start your REST API (local or remote). For the included Flask sample, use `api/run-api.sh`. For any other API, ensure it is running and accessible.
2. Update the simulation file:
	- Edit `gatling-maven/src/test/java/co/tyrell/gatling/simulation/ApiBenchmarkSimulation.java` to set your API's base URL and endpoints.
	- Adjust NFR thresholds as needed.
3. Run Gatling benchmarks:
	```sh
	# Using Java 21 (recommended)
	./scripts/run-with-java21.sh gatling:test
	
	# Or manually with your system Java
	cd gatling-maven
	mvn gatling:test
	```
4. View Gatling HTML reports in `gatling-maven/target/gatling/`.

## Default Simulation Parameters
- **Concurrent Users:** 500 (ramp up over 60 seconds)
- **Constant Load:** 50 users/sec for 2 minutes
- **Target Endpoint:** `http://localhost:5050/api/hello` (customize in simulation file)
- **NFR Thresholds:**
  - Max response time < 1000ms
  - Mean response time < 500ms
  - 95th percentile response time < 800ms
  - Success rate > 99%
  - Error rate < 1%
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
- Edit `gatling-maven/src/test/java/co/tyrell/gatling/simulation/ApiBenchmarkSimulation.java` to target your API endpoints and NFRs.


### Changing NFR Thresholds
To change the performance thresholds (NFRs) for your API tests:

1. Open `gatling-maven/src/test/java/example/ApiBenchmarkSimulation.java`.
2. Locate the `assertions` array. Each line sets a threshold for a specific NFR, e.g.:
	- `global().responseTime().max().lt(1000)` sets max response time < 1000ms
	- `global().requestsPerSec().gt(10.0)` sets throughput > 10 requests/sec
	- `global().failedRequests().percent().lt(1.0)` sets error rate < 1%
3. Change the numeric values to your desired thresholds.
4. Save the file and re-run the simulation.

You can also adjust the load profile (number of users, ramp-up time, etc.) in the `injectOpen` section of the simulation file.

## Author

**Tyrell Perera**  
🌐 Website: [tyrell.co](https://tyrell.co)

## Open Source & License

This project is released under the MIT License. See the included `LICENSE` file for details.


