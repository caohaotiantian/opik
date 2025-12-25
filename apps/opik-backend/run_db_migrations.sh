#!/bin/bash
set -e

echo "=============================================="
echo "Running database migrations..."
echo "=============================================="
echo "Current directory is: $(pwd)"
echo "OPIK_VERSION=${OPIK_VERSION:-not set}"

# Set default values for environment variables if not provided
export STATE_DB_DATABASE_NAME="${STATE_DB_DATABASE_NAME:-opik}"
export ANALYTICS_DB_DATABASE_NAME="${ANALYTICS_DB_DATABASE_NAME:-opik}"

echo "STATE_DB_DATABASE_NAME=${STATE_DB_DATABASE_NAME}"
echo "ANALYTICS_DB_DATABASE_NAME=${ANALYTICS_DB_DATABASE_NAME}"

# JVM options for Liquibase property substitution
JAVA_MIGRATION_OPTS="${JAVA_OPTS:--Dliquibase.propertySubstitutionEnabled=true}"

# Determine the JAR file path
if [ -n "$OPIK_VERSION" ]; then
  JAR_FILE="opik-backend-${OPIK_VERSION}.jar"
else
  # Try to find the JAR file in common locations
  if [ -f "opik-backend-1.0-SNAPSHOT.jar" ]; then
    JAR_FILE="opik-backend-1.0-SNAPSHOT.jar"
  elif [ -f "target/opik-backend-1.0-SNAPSHOT.jar" ]; then
    JAR_FILE="target/opik-backend-1.0-SNAPSHOT.jar"
  else
    echo "ERROR: Could not find opik-backend JAR file. Please set OPIK_VERSION or ensure JAR exists."
    exit 1
  fi
fi

echo "Using JAR file: ${JAR_FILE}"

# Verify JAR file exists
if [ ! -f "$JAR_FILE" ]; then
  echo "ERROR: JAR file not found: ${JAR_FILE}"
  exit 1
fi

echo ""
echo "----------------------------------------------"
echo "Step 1/2: Running MySQL (state) database migration..."
echo "----------------------------------------------"
java ${JAVA_MIGRATION_OPTS} -jar "${JAR_FILE}" db migrate config.yml

echo ""
echo "----------------------------------------------"
echo "Step 2/2: Running ClickHouse (analytics) database migration..."
echo "----------------------------------------------"
java ${JAVA_MIGRATION_OPTS} -jar "${JAR_FILE}" dbAnalytics migrate config.yml

echo ""
echo "=============================================="
echo "Database migrations completed successfully!"
echo "=============================================="
