#!/bin/bash
set -e

# IoT Real-Time Platform Development Environment Startup Script
# This script starts the complete development environment using Docker Compose

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
COMPOSE_FILE="infra/docker/docker-compose.dev.yml"
PROJECT_NAME="iot-platform"
SERVICES_TO_WAIT=("timescaledb" "redpanda" "keycloak")

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        print_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi
}

# Function to check if docker-compose is available
check_docker_compose() {
    if ! command -v docker-compose > /dev/null 2>&1 && ! docker compose version > /dev/null 2>&1; then
        print_error "Neither 'docker-compose' nor 'docker compose' command is available."
        exit 1
    fi

    # Use docker-compose if available, otherwise use docker compose
    if command -v docker-compose > /dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker-compose"
    else
        DOCKER_COMPOSE_CMD="docker compose"
    fi
}

# Function to wait for service to be healthy
wait_for_service() {
    local service=$1
    local max_attempts=60
    local attempt=1

    print_status "Waiting for $service to be healthy..."

    while [ $attempt -le $max_attempts ]; do
        if $DOCKER_COMPOSE_CMD -f $COMPOSE_FILE -p $PROJECT_NAME ps $service | grep -q "healthy"; then
            print_success "$service is healthy!"
            return 0
        fi

        echo -n "."
        sleep 5
        attempt=$((attempt + 1))
    done

    print_warning "$service did not become healthy within expected time"
    return 1
}

# Function to create necessary directories
create_directories() {
    print_status "Creating necessary directories..."

    directories=(
        "infra/docker/grafana/provisioning/datasources"
        "infra/docker/grafana/provisioning/dashboards"
        "infra/docker/grafana/dashboards"
        "infra/docker/keycloak-config"
        "infra/docker/nginx"
        "infra/docker/mosquitto"
    )

    for dir in "${directories[@]}"; do
        mkdir -p "$dir"
    done
}

# Function to generate configuration files if they don't exist
generate_configs() {
    print_status "Generating configuration files..."

    # Grafana datasource configuration
    if [ ! -f "infra/docker/grafana/provisioning/datasources/datasources.yml" ]; then
        cat > infra/docker/grafana/provisioning/datasources/datasources.yml << EOF
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
  - name: TimescaleDB
    type: postgres
    access: proxy
    url: timescaledb:5432
    database: iot_platform
    user: iot_user
    secureJsonData:
      password: iot_password
EOF
    fi

    # Grafana dashboard configuration
    if [ ! -f "infra/docker/grafana/provisioning/dashboards/dashboards.yml" ]; then
        cat > infra/docker/grafana/provisioning/dashboards/dashboards.yml << EOF
apiVersion: 1
providers:
  - name: 'default'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    options:
      path: /var/lib/grafana/dashboards
EOF
    fi

    # Nginx configuration
    if [ ! -f "infra/docker/nginx.conf" ]; then
        cat > infra/docker/nginx.conf << EOF
events {
    worker_connections 1024;
}

http {
    upstream java_api {
        server java-api:8080;
    }

    upstream rust_ingest {
        server rust-ingest:8090;
    }

    upstream grafana {
        server grafana:3000;
    }

    server {
        listen 80;

        location /api/ {
            proxy_pass http://java_api;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
        }

        location /ingest/ {
            proxy_pass http://rust_ingest/;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
        }

        location /dashboards/ {
            proxy_pass http://grafana/;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
        }

        location /health {
            access_log off;
            return 200 "healthy\n";
            add_header Content-Type text/plain;
        }
    }
}
EOF
    fi

    # Mosquitto configuration
    if [ ! -f "infra/docker/mosquitto/mosquitto.conf" ]; then
        cat > infra/docker/mosquitto/mosquitto.conf << EOF
listener 1883
allow_anonymous true
listener 9001
protocol websockets
EOF
    fi
}

# Function to initialize Kafka topics
init_kafka_topics() {
    print_status "Initializing Kafka topics..."

    topics=("raw.events" "aggregates.minute" "alerts")

    for topic in "${topics[@]}"; do
        $DOCKER_COMPOSE_CMD -f $COMPOSE_FILE -p $PROJECT_NAME exec -T redpanda \
            rpk topic create $topic --brokers localhost:9092 --partitions 3 --replicas 1 || true
    done
}

# Function to display service URLs
show_service_urls() {
    print_success "IoT Platform Development Environment Started Successfully!"
    echo ""
    echo "Available Services:"
    echo "==================="
    echo "🌐 API Documentation:     http://localhost:8081/swagger-ui.html"
    echo "📊 Grafana Dashboards:    http://localhost:3000 (admin/admin123)"
    echo "📈 Prometheus Metrics:    http://localhost:9090"
    echo "🔍 Jaeger Tracing:        http://localhost:16686"
    echo "🔐 Keycloak Admin:        http://localhost:8080 (admin/admin123)"
    echo "🏠 Main Application:      http://localhost:80"
    echo ""
    echo "Direct Service Access:"
    echo "======================"
    echo "🦀 Rust Ingestion:       http://localhost:8090"
    echo "🐹 Go Processor Metrics: http://localhost:9091/metrics"
    echo "☕ Java API:             http://localhost:8081"
    echo "🗄️  TimescaleDB:          localhost:5432 (iot_user/iot_password)"
    echo "📬 Redpanda/Kafka:       localhost:19092"
    echo "📡 MQTT Broker:          localhost:1883"
    echo "🔄 Redis Cache:          localhost:6379"
    echo ""
    echo "To stop the environment, run: ./scripts/dev-down.sh"
    echo "To view logs, run: $DOCKER_COMPOSE_CMD -f $COMPOSE_FILE -p $PROJECT_NAME logs -f [service-name]"
}

# Function to show help
show_help() {
    echo "IoT Platform Development Environment Startup Script"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --build, -b     Force rebuild of all containers"
    echo "  --pull, -p      Pull latest images before starting"
    echo "  --no-wait       Don't wait for services to become healthy"
    echo "  --with-load     Start with load generator for testing"
    echo "  --help, -h      Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                    # Start with default settings"
    echo "  $0 --build            # Rebuild containers and start"
    echo "  $0 --with-load        # Start with load testing enabled"
}

# Parse command line arguments
BUILD_FLAG=""
PULL_FLAG=""
WAIT_FOR_HEALTH=true
WITH_LOAD_GENERATOR=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --build|-b)
            BUILD_FLAG="--build"
            shift
            ;;
        --pull|-p)
            PULL_FLAG="--pull"
            shift
            ;;
        --no-wait)
            WAIT_FOR_HEALTH=false
            shift
            ;;
        --with-load)
            WITH_LOAD_GENERATOR=true
            shift
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# Main execution
main() {
    print_status "Starting IoT Real-Time Platform Development Environment..."

    # Preliminary checks
    check_docker
    check_docker_compose

    # Prepare environment
    create_directories
    generate_configs

    # Pull images if requested
    if [ -n "$PULL_FLAG" ]; then
        print_status "Pulling latest images..."
        $DOCKER_COMPOSE_CMD -f $COMPOSE_FILE -p $PROJECT_NAME pull
    fi

    # Start services
    if [ "$WITH_LOAD_GENERATOR" = true ]; then
        print_status "Starting services with load generator..."
        $DOCKER_COMPOSE_CMD -f $COMPOSE_FILE -p $PROJECT_NAME --profile testing up -d $BUILD_FLAG
    else
        print_status "Starting services..."
        $DOCKER_COMPOSE_CMD -f $COMPOSE_FILE -p $PROJECT_NAME up -d $BUILD_FLAG
    fi

    # Wait for critical services to be healthy
    if [ "$WAIT_FOR_HEALTH" = true ]; then
        for service in "${SERVICES_TO_WAIT[@]}"; do
            wait_for_service $service
        done

        # Initialize Kafka topics after Redpanda is ready
        sleep 10
        init_kafka_topics
    fi

    # Show service information
    show_service_urls
}

# Check if we're in the right directory
if [ ! -f "$COMPOSE_FILE" ]; then
    print_error "Docker Compose file not found at $COMPOSE_FILE"
    print_error "Please run this script from the project root directory"
    exit 1
fi

# Execute main function
main
