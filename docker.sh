#!/usr/bin/env bash
set -euo pipefail

ACTION="${1:-up}"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
GRAY='\033[2m'
WHITE='\033[1;37m'
NC='\033[0m'

step()  { echo -e "${CYAN}==>${NC} $1"; }
error() { echo -e "${RED}ERROR:${NC} $1"; }

# Check if Docker is available
if ! command -v docker &>/dev/null; then
    error "Docker is not installed. See https://docs.docker.com/get-docker/"
    exit 1
fi

# Check if Docker daemon is running
if ! docker info &>/dev/null; then
    error "Docker daemon is not running."
    exit 1
fi

case "${ACTION}" in
    up)
        step "Building and starting all services (PostgreSQL + Backend + Frontend)..."
        docker compose up --build -d

        step "Services started. Use 'docker compose logs -f' to see logs."
        echo ""
        echo -e "  ${GREEN}Frontend:${NC} http://localhost:3000"
        echo -e "  ${GREEN}Backend:${NC}  http://localhost:8080"
        echo -e "  ${GREEN}Database:${NC} postgresql://localhost:5432/gtu_ai_assistant"
        ;;

    down)
        step "Stopping and removing all services..."
        docker compose down
        step "All services stopped."
        ;;

    restart)
        step "Restarting all services..."
        docker compose down
        docker compose up --build -d
        step "Services restarted."
        echo ""
        echo -e "  ${GREEN}Frontend:${NC} http://localhost:3000"
        echo -e "  ${GREEN}Backend:${NC}  http://localhost:8080"
        ;;

    logs)
        step "Showing logs (Ctrl+C to exit)..."
        docker compose logs -f
        ;;

    rebuild)
        step "Rebuilding all images without cache..."
        docker compose build --no-cache
        step "Starting services after rebuild..."
        docker compose up -d
        step "Services rebuilt and started."
        ;;

    status)
        step "Service status:"
        docker compose ps
        ;;

    clean)
        step "Stopping services and removing volumes (data will be lost)..."
        docker compose down -v
        step "Cleaned up. All data volumes removed."
        ;;

    *)
        echo ""
        echo -e "${YELLOW}GTU AI Assistant — Docker Management Script${NC}"
        echo ""
        echo -e "${WHITE}Usage:${NC} ./docker.sh [command]"
        echo ""
        echo -e "${WHITE}Commands:${NC}"
        echo -e "  ${GREEN}up${NC}        Build and start all services (default)"
        echo -e "  ${GREEN}down${NC}      Stop and remove all services"
        echo -e "  ${GREEN}restart${NC}   Restart all services"
        echo -e "  ${GREEN}logs${NC}      Show live logs from all services"
        echo -e "  ${GREEN}rebuild${NC}   Rebuild all images from scratch"
        echo -e "  ${GREEN}status${NC}    Show service status"
        echo -e "  ${GREEN}clean${NC}     Stop services and remove all data volumes"
        echo ""
        echo -e "${WHITE}Examples:${NC}"
        echo -e "  ${GRAY}./docker.sh           # Start all services${NC}"
        echo -e "  ${GRAY}./docker.sh logs      # View logs${NC}"
        echo -e "  ${GRAY}./docker.sh down      # Stop everything${NC}"
        ;;
esac
