#!/bin/bash

# OAuth API Demo and Test Script
# This script demonstrates the complete OAuth workflow

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "🚀 OAuth API Benchmark Suite Demo"
echo "=================================="
echo

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
echo "📋 Checking prerequisites..."
if ! command_exists python3; then
    echo "❌ Python 3 is required but not installed"
    exit 1
fi

if ! command_exists mvn; then
    echo "❌ Maven is required but not installed"
    exit 1
fi

if ! command_exists curl; then
    echo "❌ curl is required but not installed"
    exit 1
fi

echo "✅ All prerequisites found"
echo

# Install Python dependencies
echo "📦 Installing Python API dependencies..."
cd "$PROJECT_DIR/api"
pip3 install -r requirements.txt
echo

# Start the API server in background
echo "🌐 Starting OAuth-enabled test API server..."
echo "   Server will run on http://localhost:5050"
echo "   Press Ctrl+C to stop the demo"
echo

# Start API in background with output redirect
python3 app.py > api.log 2>&1 &
API_PID=$!

# Function to cleanup on exit
cleanup() {
    echo
    echo "🛑 Stopping API server..."
    kill $API_PID 2>/dev/null || true
    wait $API_PID 2>/dev/null || true
    echo "✅ Cleanup completed"
}

# Set up signal handlers
trap cleanup EXIT INT TERM

# Wait for API to start
echo "⏳ Waiting for API to start..."
sleep 3

# Test if API is running
if ! curl -s http://localhost:5050/api/health > /dev/null; then
    echo "❌ API failed to start. Check api.log for details:"
    cat api.log
    exit 1
fi

echo "✅ API server is running!"
echo

# Show API endpoints and OAuth clients
echo "📋 OAuth API Information:"
echo "========================"
echo "Base URL: http://localhost:5050"
echo
echo "OAuth Clients:"
echo "  1. demo-client-id / demo-client-secret (scopes: api:read, api:write, admin)"
echo "  2. test-client / test-secret (scopes: api:read)"
echo
echo "Endpoints:"
echo "  Public:"
echo "    GET  /api/health                           - Health check"
echo "    GET  /v3/brands/{brand}/customers          - Customer search (public)"
echo "    POST /oauth/token                          - OAuth token endpoint"
echo "  Protected (OAuth required):"
echo "    GET  /v3/brands/{brand}/customers          - List customers with OAuth"
echo "    POST /v3/brands/{brand}/customers          - Create customer (write scope)"
echo "    GET  /v3/brands/{brand}/customers/{id}     - Get customer details"
echo "    PUT  /api/customers/{id}          - Update customer (write scope)"
echo "    DELETE /api/customers/{id}        - Delete customer (write scope)"
echo "    GET  /api/cev-events              - List CEV events"
echo "    POST /api/cev-events              - Create CEV event (write scope)"
echo "    GET  /api/cev-events/{id}         - Get CEV event"
echo "    PUT  /api/cev-events/{id}         - Update CEV event (write scope)"
echo "    GET  /api/admin/stats             - Admin stats (admin scope)"
echo "    GET  /api/token/info              - Token information"
echo

# Run API functionality test
echo "🧪 Testing OAuth API functionality..."
echo "===================================="
cd "$SCRIPT_DIR"
./test-oauth-api.sh

echo
echo "⚡ Running Gatling Performance Test..."
echo "====================================="

# Change to Gatling directory
cd "$PROJECT_DIR/gatling-maven"

# Show current OAuth configuration
echo "OAuth Configuration:"
grep -E "^oauth\." src/test/resources/gatling-simulation.properties || echo "No OAuth configuration found"
echo

# Run the OAuth-enabled Gatling test
mvn clean gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth

# Check if execution was successful
if [[ $? -eq 0 ]]; then
    echo
    echo "✅ Performance test completed successfully!"
    
    # Find the latest results directory
    LATEST_RESULT=$(find target/gatling -name "*apibenchmarksimulationwithoauth*" -type d | sort | tail -1)
    if [[ -n "$LATEST_RESULT" ]]; then
        echo "📊 Results available at: file://$PROJECT_DIR/gatling-maven/$LATEST_RESULT/index.html"
    fi
else
    echo "❌ Performance test failed!"
fi

echo
echo "🎉 Demo completed!"
echo
echo "Next steps:"
echo "  1. Review the performance test results in your browser"
echo "  2. Modify gatling-simulation.properties to test different configurations"
echo "  3. Run additional scenarios with: ./scripts/run-oauth-comprehensive.sh"
echo "  4. Customize the API endpoints in the simulation files"
echo

# Keep the API running until user wants to exit
echo "📡 API server is still running for your testing..."
echo "   You can test endpoints manually with curl or Postman"
echo "   API logs are in: $PROJECT_DIR/api/api.log"
echo
read -p "Press Enter to stop the API server and exit..."