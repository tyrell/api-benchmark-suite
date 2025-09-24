#!/bin/bash

# Script to run Maven commands with Java 21
# Usage: ./run-with-java21.sh [maven-command]
# Example: ./run-with-java21.sh clean compile
# Example: ./run-with-java21.sh gatling:test

export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home

# Navigate to the gatling-maven directory
cd "$(dirname "$0")/../gatling-maven"

# Run the maven command with the provided arguments
if [ $# -eq 0 ]; then
    echo "Usage: $0 [maven-command]"
    echo "Example: $0 clean compile"
    echo "Example: $0 gatling:test"
    exit 1
fi

echo "Using Java 21: $JAVA_HOME"
$JAVA_HOME/bin/java -version
echo ""
echo "Running: mvn $@"
mvn "$@"
