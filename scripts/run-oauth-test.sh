#!/bin/bash

# OAuth-enabled Gatling Test Runner
# This script provides easy ways to run Gatling tests with OAuth authentication

set -e

# Default values
SIMULATION_CLASS="co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth"
OAUTH_ENABLED="false"
BASE_URL="http://localhost:5050"
ENDPOINT="/v3/brands/AAMI/customers"
USERS="500"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Run Gatling performance tests with optional OAuth support"
    echo ""
    echo "Options:"
    echo "  -h, --help                 Show this help message"
    echo "  -s, --simulation CLASS     Simulation class to run (default: ApiBenchmarkSimulationWithOAuth)"
    echo "  -u, --url URL             Base URL of the API (default: http://localhost:5050)"
    echo "  -e, --endpoint PATH       API endpoint to test (default: /v3/brands/AAMI/customers)"
    echo "  --users COUNT             Number of virtual users (default: 500)"
    echo ""
    echo "OAuth Options:"
    echo "  --oauth-enabled           Enable OAuth authentication"
    echo "  --token-endpoint URL      OAuth token endpoint URL"
    echo "  --client-id ID            OAuth client ID"
    echo "  --client-secret SECRET    OAuth client secret"
    echo "  --grant-type TYPE         OAuth grant type (client_credentials|password, default: client_credentials)"
    echo "  --scope SCOPE             OAuth scope (optional)"
    echo "  --username USER           Username for password grant type"
    echo "  --password PASS           Password for password grant type"
    echo ""
    echo "Examples:"
    echo "  # Run without OAuth"
    echo "  $0"
    echo ""
    echo "  # Run with Client Credentials OAuth"
    echo "  $0 --oauth-enabled --token-endpoint https://auth.example.com/token --client-id my-client --client-secret my-secret"
    echo ""
    echo "  # Run comprehensive OAuth simulation"
    echo "  $0 -s co.tyrell.gatling.simulation.OAuthComprehensiveSimulation --oauth-enabled --token-endpoint https://auth.example.com/token --client-id my-client --client-secret my-secret"
    echo ""
    echo "  # Run with password grant type"
    echo "  $0 --oauth-enabled --token-endpoint https://auth.example.com/token --client-id my-client --client-secret my-secret --grant-type password --username testuser --password testpass"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        -s|--simulation)
            SIMULATION_CLASS="$2"
            shift 2
            ;;
        -u|--url)
            BASE_URL="$2"
            shift 2
            ;;
        -e|--endpoint)
            ENDPOINT="$2"
            shift 2
            ;;
        --users)
            USERS="$2"
            shift 2
            ;;
        --oauth-enabled)
            OAUTH_ENABLED="true"
            shift
            ;;
        --token-endpoint)
            TOKEN_ENDPOINT="$2"
            shift 2
            ;;
        --client-id)
            CLIENT_ID="$2"
            shift 2
            ;;
        --client-secret)
            CLIENT_SECRET="$2"
            shift 2
            ;;
        --grant-type)
            GRANT_TYPE="$2"
            shift 2
            ;;
        --scope)
            SCOPE="$2"
            shift 2
            ;;
        --username)
            USERNAME="$2"
            shift 2
            ;;
        --password)
            PASSWORD="$2"
            shift 2
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

# Validation
if [[ "$OAUTH_ENABLED" == "true" ]]; then
    if [[ -z "$TOKEN_ENDPOINT" || -z "$CLIENT_ID" || -z "$CLIENT_SECRET" ]]; then
        echo -e "${RED}Error: When OAuth is enabled, token-endpoint, client-id, and client-secret are required${NC}"
        exit 1
    fi
    
    if [[ "$GRANT_TYPE" == "password" && (-z "$USERNAME" || -z "$PASSWORD") ]]; then
        echo -e "${RED}Error: When using password grant type, username and password are required${NC}"
        exit 1
    fi
fi

# Build Maven command
MAVEN_ARGS=()
MAVEN_ARGS+=("-Dgatling.simulationClass=$SIMULATION_CLASS")
MAVEN_ARGS+=("-Dapi.base.url=$BASE_URL")
MAVEN_ARGS+=("-Dapi.endpoint=$ENDPOINT")
MAVEN_ARGS+=("-Dgatling.users=$USERS")
MAVEN_ARGS+=("-Doauth.enabled=$OAUTH_ENABLED")

if [[ "$OAUTH_ENABLED" == "true" ]]; then
    MAVEN_ARGS+=("-Doauth.token.endpoint=$TOKEN_ENDPOINT")
    MAVEN_ARGS+=("-Doauth.client.id=$CLIENT_ID")
    MAVEN_ARGS+=("-Doauth.client.secret=$CLIENT_SECRET")
    
    if [[ -n "$GRANT_TYPE" ]]; then
        MAVEN_ARGS+=("-Doauth.grant.type=$GRANT_TYPE")
    fi
    
    if [[ -n "$SCOPE" ]]; then
        MAVEN_ARGS+=("-Doauth.scope=$SCOPE")
    fi
    
    if [[ -n "$USERNAME" ]]; then
        MAVEN_ARGS+=("-Doauth.username=$USERNAME")
    fi
    
    if [[ -n "$PASSWORD" ]]; then
        MAVEN_ARGS+=("-Doauth.password=$PASSWORD")
    fi
fi

# Change to gatling-maven directory
cd "$(dirname "$0")/../gatling-maven"

echo -e "${GREEN}Starting Gatling performance test...${NC}"
echo -e "${YELLOW}Simulation: $SIMULATION_CLASS${NC}"
echo -e "${YELLOW}Base URL: $BASE_URL${NC}"
echo -e "${YELLOW}Endpoint: $ENDPOINT${NC}"
echo -e "${YELLOW}Users: $USERS${NC}"
echo -e "${YELLOW}OAuth Enabled: $OAUTH_ENABLED${NC}"

if [[ "$OAUTH_ENABLED" == "true" ]]; then
    echo -e "${YELLOW}Token Endpoint: $TOKEN_ENDPOINT${NC}"
    echo -e "${YELLOW}Grant Type: ${GRANT_TYPE:-client_credentials}${NC}"
    if [[ -n "$SCOPE" ]]; then
        echo -e "${YELLOW}Scope: $SCOPE${NC}"
    fi
fi

echo ""

# Run Maven command
mvn gatling:test "${MAVEN_ARGS[@]}"

echo ""
echo -e "${GREEN}Test completed! Check the results in target/gatling/ directory${NC}"