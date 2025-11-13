.PHONY: help build up down restart logs clean rebuild stop start

# Default target
help:
	@echo "GMS Docker Compose Makefile"
	@echo ""
	@echo "Available commands:"
	@echo "  make build      - Build all Docker images"
	@echo "  make up         - Start all services (database, backend, frontend)"
	@echo "  make up-dev     - Start services using development.env (Neon DB)"
	@echo "  make down       - Stop and remove all containers"
	@echo "  make restart    - Restart all services"
	@echo "  make logs       - Show logs from all services"
	@echo "  make logs-db    - Show database logs"
	@echo "  make logs-backend - Show backend logs"
	@echo "  make logs-frontend - Show frontend logs"
	@echo "  make clean      - Stop containers and remove volumes (WARNING: deletes database data)"
	@echo "  make rebuild    - Rebuild and restart all services"
	@echo "  make stop       - Stop all services"
	@echo "  make start      - Start all services"
	@echo "  make ps         - Show running containers"
	@echo "  make shell-backend - Open shell in backend container"
	@echo "  make shell-frontend - Open shell in frontend container"
	@echo "  make shell-db   - Open PostgreSQL shell"

# Build all images
build:
	docker-compose build

# Start all services
up:
	docker-compose up -d
	@echo ""
	@echo "Services started:"
	@echo "  - Database:    http://localhost:5432"
	@echo "  - Backend API: http://localhost:8080"
	@echo "  - Frontend:    http://localhost:3000"
	@echo ""
	@echo "Use 'make logs' to view logs"

# Start services with development.env (Neon database)
up-dev:
	ENV_FILE=development.env docker-compose up -d backend frontend
	@echo ""
	@echo "Services started with development.env (Neon DB):"
	@echo "  - Backend API: http://localhost:8080"
	@echo "  - Frontend:    http://localhost:3000"
	@echo ""
	@echo "Note: Using Neon PostgreSQL database (no local postgres container)"
	@echo "Use 'make logs' to view logs"

# Stop all services
down:
	docker-compose down

# Restart all services
restart:
	docker-compose restart

# Show logs from all services
logs:
	docker-compose logs -f

# Show database logs
logs-db:
	docker-compose logs -f postgres

# Show backend logs
logs-backend:
	docker-compose logs -f backend

# Show frontend logs
logs-frontend:
	docker-compose logs -f frontend

# Clean everything including volumes (WARNING: deletes database data)
clean:
	docker-compose down -v
	@echo "All containers and volumes removed"

# Rebuild and restart
rebuild:
	docker-compose down
	docker-compose build --no-cache
	docker-compose up -d
	@echo ""
	@echo "Services rebuilt and started:"
	@echo "  - Database:    http://localhost:5432"
	@echo "  - Backend API: http://localhost:8080"
	@echo "  - Frontend:    http://localhost:3000"

# Stop services
stop:
	docker-compose stop

# Start services
start:
	docker-compose start

# Show running containers
ps:
	docker-compose ps

# Open shell in backend container
shell-backend:
	docker-compose exec backend sh

# Open shell in frontend container
shell-frontend:
	docker-compose exec frontend sh

# Open PostgreSQL shell
shell-db:
	docker-compose exec postgres psql -U gms_user -d gms

# Test database connectivity from backend container
test-db-connection:
	@echo "Testing database connection from backend container..."
	# docker-compose run backend sh -c 'echo "DB_HOST=$$DB_HOST, DB_NAME=$$DB_NAME, DB_USERNAME=$$DB_USERNAME"'
	docker-compose run backend sh -c 'wget -O- --timeout=5 "http://localhost:8080/actuator/health" 2>/dev/null || echo "Backend not responding"'

