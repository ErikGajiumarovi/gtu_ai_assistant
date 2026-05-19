param(
    [string]$Action = "up"
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Error {
    param([string]$Message)
    Write-Host "ERROR: $Message" -ForegroundColor Red
}

# Check if Docker is available
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker is not installed. Please install Docker Desktop from https://www.docker.com/products/docker-desktop/"
    exit 1
}

# Check if Docker is running
try {
    $dockerInfo = docker info 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker daemon is not running. Please start Docker Desktop."
        exit 1
    }
}
catch {
    Write-Error "Docker daemon is not running. Please start Docker Desktop."
    exit 1
}

switch ($Action.ToLower()) {
    "up" {
        Write-Step "Building and starting all services (PostgreSQL + Backend + Frontend)..."
        docker compose up --build -d

        Write-Step "Services started. Use 'docker compose logs -f' to see logs."
        Write-Host ""
        Write-Host "  Frontend: http://localhost:3000" -ForegroundColor Green
        Write-Host "  Backend:  http://localhost:8080" -ForegroundColor Green
        Write-Host "  Database: postgresql://localhost:5432/gtu_ai_assistant" -ForegroundColor Green
    }

    "down" {
        Write-Step "Stopping and removing all services..."
        docker compose down

        Write-Step "All services stopped."
    }

    "restart" {
        Write-Step "Restarting all services..."
        docker compose down
        docker compose up --build -d

        Write-Step "Services restarted."
        Write-Host ""
        Write-Host "  Frontend: http://localhost:3000" -ForegroundColor Green
        Write-Host "  Backend:  http://localhost:8080" -ForegroundColor Green
    }

    "logs" {
        Write-Step "Showing logs (Ctrl+C to exit)..."
        docker compose logs -f
    }

    "rebuild" {
        Write-Step "Rebuilding all images without cache..."
        docker compose build --no-cache

        Write-Step "Starting services after rebuild..."
        docker compose up -d

        Write-Step "Services rebuilt and started."
    }

    "status" {
        Write-Step "Service status:"
        docker compose ps
    }

    "clean" {
        Write-Step "Stopping services and removing volumes (data will be lost)..."
        docker compose down -v

        Write-Step "Cleaned up. All data volumes removed."
    }

    default {
        Write-Host ""
        Write-Host "GTU AI Assistant — Docker Management Script" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Usage: .\docker.ps1 [command]" -ForegroundColor White
        Write-Host ""
        Write-Host "Commands:" -ForegroundColor White
        Write-Host "  up        Build and start all services (default)" -ForegroundColor Green
        Write-Host "  down      Stop and remove all services" -ForegroundColor Green
        Write-Host "  restart   Restart all services" -ForegroundColor Green
        Write-Host "  logs      Show live logs from all services" -ForegroundColor Green
        Write-Host "  rebuild   Rebuild all images from scratch" -ForegroundColor Green
        Write-Host "  status    Show service status" -ForegroundColor Green
        Write-Host "  clean     Stop services and remove all data volumes" -ForegroundColor Green
        Write-Host ""
        Write-Host "Examples:" -ForegroundColor White
        Write-Host "  .\docker.ps1           # Start all services" -ForegroundColor Gray
        Write-Host "  .\docker.ps1 logs      # View logs" -ForegroundColor Gray
        Write-Host "  .\docker.ps1 down      # Stop everything" -ForegroundColor Gray
    }
}
