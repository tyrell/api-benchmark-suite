#!/bin/bash

# Comprehensive OAuth Simulation Runner
# This script demonstrates various OAuth testing scenarios

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
GATLING_DIR="$PROJECT_DIR/gatling-maven"

echo "🚀 Running Comprehensive OAuth Simulation..."
echo "Project Directory: $PROJECT_DIR"
echo "Gatling Directory: $GATLING_DIR"

# Change to Gatling directory
cd "$GATLING_DIR"

# Check if oauth-config.properties exists
if [[ ! -f "src/test/resources/oauth-config.properties" ]]; then
    echo "❌ OAuth configuration file not found!"
    echo "Please configure src/test/resources/oauth-config.properties"
    exit 1
fi

echo "📋 OAuth Configuration:"
echo "----------------------"
grep -E "^oauth\." src/test/resources/oauth-config.properties || echo "No OAuth configuration found"
echo "----------------------"

# Run the comprehensive OAuth simulation
echo "🎯 Executing OAuthComprehensiveSimulation..."
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.OAuthComprehensiveSimulation

# Check if execution was successful
if [[ $? -eq 0 ]]; then
    echo "✅ OAuth simulation completed successfully!"
    echo "📊 Results available in: target/gatling/"
    
    # Find the latest results directory
    LATEST_RESULT=$(find target/gatling -name "*oauthcomprehensivesimulation*" -type d | sort | tail -1)
    if [[ -n "$LATEST_RESULT" ]]; then
        echo "🌐 Open results: file://$GATLING_DIR/$LATEST_RESULT/index.html"
    fi
else
    echo "❌ OAuth simulation failed!"
    exit 1
fi