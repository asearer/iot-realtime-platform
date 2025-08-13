#!/bin/bash
set -e

# IoT Real-Time Platform Development Environment Shutdown Script
# This script stops and cleans up the development environment

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
COMPOSE_FILE="infra/docker/docker-compose.dev.yml"
PROJECT_NAME="iot-platform"

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

# Function to stop services
stop_services() {
    print_status "Stopping IoT Platform services..."
    $DOCKER_COMPOSE_CMD -f $COMPOSE_FILE -p $PROJECT_NAME stop
}

# Function to remove containers
remove_containers() {
    print_status "Removing containers..."
    $DOCKER_COMPOSE_CMD -f $COMPOSE_FILE -p $PROJECT_NAME rm -f
}

# Function to remove volumes
remove_volumes() {
    print_status "Removing volumes..."

    volumes=$(
        $DOCKER_COMPOSE_CMD -f $COMPOSE_FILE -p $PROJECT_NAME config --volumes 2>/dev/null || \
        docker volume ls --format "table {{.Name}}" | grep "^${PROJECT_NAME}_" | cut -d'_' -f2- 2>/dev/null || \
        echo ""
    )

    if [ -n "$volumes" ]; then
        for volume in $volumes; do
            volume_name="${PROJECT_NAME}_${volume}"
            if docker volume ls | grep -q "$volume_name"; then
                print_status "Removing volume: $volume_name"
                docker volume rm "$volume_name" 2>/dev/null || print_warning "Could not remove volume $volume_name"
            fi
        done
    else
        print_status "No volumes found to remove"
    fi
}

# Function to remove network
remove_network() {
    network_name="${PROJECT_NAME}_iot-network"
    if docker network ls | grep -q "$network_name"; then
        print_status "Removing network: $network_name"
        docker network rm "$network_name" 2>/dev/null || print_warning "Could not remove network $network_name"
    fi
}

# Function to clean up orphaned containers
cleanup_orphans() {
    print_status "Cleaning up orphaned containers..."
    $DOCKER_COMPOSE_CMD -f $COMPOSE_FILE -p $PROJECT_NAME down --remove-orphans
}

# Function to prune unused Docker resources
prune_docker_resources() {
    print_status "Pruning unused Docker resources..."

    # Remove dangling images
    docker image prune -f >/dev/null 2>&1 || true

    # Remove unused networks
    docker network prune -f >/dev/null 2>&1 || true

    # Remove build cache (if supported)
    docker builder prune -f >/dev/null 2>&1 || true
}

# Function to show running containers that might be related
show_remaining_containers() {
    remaining=$(docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}" | grep -E "(iot-|${PROJECT_NAME})" || echo "")

    if [ -n "$remaining" ]; then
        print_warning "Some containers are still running:"
        echo "$remaining"
        echo ""
        echo "To forcefully stop them, run:"
        echo "docker stop \$(docker ps -q --filter \"name=iot-\")"
    fi
}

# Function to show disk usage
show_disk_usage() {
    print_status "Docker disk usage after cleanup:"
    docker system df 2>/dev/null || print_warning "Could not retrieve Docker disk usage"
}

# Function to show help
show_help() {
    echo "IoT Platform Development Environment Shutdown Script"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --clean, -c     Remove all containers, volumes, and networks"
    echo "  --volumes       Remove data volumes (destructive!)"
    echo "  --prune         Prune unused Docker resources after shutdown"
    echo "  --force, -f     Force removal without confirmation prompts"
    echo "  --help, -h      Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                    # Stop services only"
    echo "  $0 --clean            # Stop and remove containers and networks"
    echo "  $0 --clean --volumes  # Stop and remove everything including data"
    echo "  $0 --prune            # Stop and clean up unused Docker resources"
}

# Parse command line arguments
CLEAN_MODE=false
REMOVE_VOLUMES=false
PRUNE_RESOURCES=false
FORCE_MODE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --clean|-c)
            CLEAN_MODE=true
            shift
            ;;
        --volumes)
            REMOVE_VOLUMES=true
            shift
            ;;
        --prune)
            PRUNE_RESOURCES=true
            shift
            ;;
        --force|-f)
            FORCE_MODE=true
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

# Confirmation for destructive operations
confirm_destructive_action() {
    if [ "$FORCE_MODE" = false ]; then
        if [ "$REMOVE_VOLUMES" = true ]; then
            print_warning "This will permanently delete all data volumes!"
            read -p "Are you sure you want to continue? (y/N): " -r
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                print_status "Operation cancelled"
                exit 0
            fi
        elif [ "$CLEAN_MODE" = true ]; then
            print_warning "This will remove all containers and networks (but preserve data volumes)"
            read -p "Continue? (y/N): " -r
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                print_status "Operation cancelled"
                exit 0
            fi
        fi
    fi
}

# Main execution
main() {
    print_status "Shutting down IoT Real-Time Platform Development Environment..."

    # Check prerequisites
    check_docker_compose

    # Check if we're in the right directory
    if [ ! -f "$COMPOSE_FILE" ]; then
        print_error "Docker Compose file not found at $COMPOSE_FILE"
        print_error "Please run this script from the project root directory"
        exit 1
    fi

    # Confirm destructive actions
    confirm_destructive_action

    # Always stop services first
    stop_services

    if [ "$CLEAN_MODE" = true ]; then
        # Clean up containers and orphans
        cleanup_orphans
        remove_containers
        remove_network

        if [ "$REMOVE_VOLUMES" = true ]; then
            remove_volumes
        fi
    fi

    if [ "$PRUNE_RESOURCES" = true ]; then
        prune_docker_resources
    fi

    # Show status
    show_remaining_containers
    show_disk_usage

    print_success "IoT Platform shutdown completed!"

    if [ "$CLEAN_MODE" = false ]; then
        echo ""
        echo "Services have been stopped but containers and data are preserved."
        echo "To start again, run: ./scripts/dev-up.sh"
        echo "For complete cleanup, run: ./scripts/dev-down.sh --clean --volumes"
    else
        echo ""
        echo "Environment has been cleaned up."
        if [ "$REMOVE_VOLUMES" = false ]; then
            echo "Data volumes have been preserved."
        else
            echo "All data has been removed."
        fi
        echo "To start fresh, run: ./scripts/dev-up.sh"
    fi
}

# Execute main function
main
