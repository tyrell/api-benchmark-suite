#!/bin/bash

# Standard API benchmark test (no OAuth)
# This script runs the original simulation without any authentication

cd "$(dirname "$0")/../gatling-maven"

echo "Running standard API benchmark simulation (no OAuth)..."
echo "Target: http://localhost:5050"
echo

mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulation

echo
echo "Test completed. Check the results in target/gatling/ directory"
echo "To view the HTML report, open the latest index.html file in your browser"