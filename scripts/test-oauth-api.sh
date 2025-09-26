#!/bin/bash

# HYPOTHETICAL OAuth API Testing Script
# ⚠️  WARNING: This tests a FICTIONAL API for demonstration purposes only.
# This script demonstrates how to interact with a mock OAuth-enabled test API

API_BASE="http://localhost:5050"
CLIENT_ID="demo-client-id"  # FAKE credentials for testing
CLIENT_SECRET="demo-client-secret"  # FAKE credentials for testing

echo "🧪 Testing HYPOTHETICAL OAuth API Functionality"
echo "==============================================="
echo "⚠️  NOTE: All API endpoints and data are FICTIONAL"
echo

# Check if API is running
echo "1. Health Check..."
curl -s "$API_BASE/api/health" | python3 -m json.tool
echo

# Test public endpoint (Customer API search)
echo "2. Testing public endpoint..."
curl -s "$API_BASE/v3/brands/AAMI/customers?limit=3" | python3 -m json.tool
echo

# Get OAuth token
echo "3. Requesting OAuth token..."
TOKEN_RESPONSE=$(curl -s -X POST "$API_BASE/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&scope=api:read api:write")

echo "$TOKEN_RESPONSE" | python3 -m json.tool
echo

# Extract token
ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['access_token'])" 2>/dev/null)

if [ -z "$ACCESS_TOKEN" ]; then
    echo "❌ Failed to get access token. Make sure the API server is running."
    exit 1
fi

echo "✅ Got access token: ${ACCESS_TOKEN:0:50}..."
echo

# Test protected endpoint (Customer API with OAuth)
echo "4. Testing protected Customer API endpoint..."
curl -s "$API_BASE/v3/brands/AAMI/customers?limit=1" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | python3 -m json.tool
echo

# Test token info
echo "5. Token information..."
curl -s "$API_BASE/api/token/info" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | python3 -m json.tool
echo

# Create a customer
echo "6. Creating a customer..."
CUSTOMER_RESPONSE=$(curl -s -X POST "$API_BASE/api/customers" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test Customer", "email": "test@example.com"}')

echo "$CUSTOMER_RESPONSE" | python3 -m json.tool
echo

# Extract customer ID
CUSTOMER_ID=$(echo "$CUSTOMER_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])" 2>/dev/null)

# Get customers
echo "7. Retrieving customers..."
curl -s "$API_BASE/api/customers" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | python3 -m json.tool
echo

# Update the customer
if [ ! -z "$CUSTOMER_ID" ]; then
    echo "8. Updating customer $CUSTOMER_ID..."
    curl -s -X PUT "$API_BASE/api/customers/$CUSTOMER_ID" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{"name": "Updated Customer", "email": "updated@example.com"}' | python3 -m json.tool
    echo
fi

# Create a CEV event
echo "9. Creating a CEV event..."
curl -s -X POST "$API_BASE/api/cev-events" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type": "user.login", "data": {"user_id": "12345", "ip": "192.168.1.1"}}' | python3 -m json.tool
echo

# Get events
echo "10. Retrieving CEV events..."
curl -s "$API_BASE/api/cev-events" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | python3 -m json.tool
echo

echo "✅ OAuth API testing completed!"
echo
echo "To run performance tests against this OAuth-enabled API:"
echo "  1. Start the API: cd api && python app.py"
echo "  2. Update gatling-simulation.properties with:"
echo "     oauth.enabled=true"
echo "     oauth.tokenUrl=http://localhost:5050/oauth/token"
echo "     oauth.clientId=demo-client-id"
echo "     oauth.clientSecret=demo-client-secret"
echo "  3. Run: ./scripts/run-oauth-test.sh"