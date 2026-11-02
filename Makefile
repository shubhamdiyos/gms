.PHONY: help build up down restart logs logs-db logs-backend logs-frontend clean rebuild stop \
        start ps shell-backend shell-frontend shell-db

# Default target
help:
	@echo "GMS Docker Compose Makefile"
	@echo ""
	@echo "Available commands:"
	@echo "  make build      - Build all Docker images"
	@echo "  make up         - Start all services (database, backend, frontend)"
	@echo "  make down       - Stop and remove all containers"
	@echo "  make restart    - Restart all services"
	@echo "  make logs       - Show logs from all services"
	@echo "  make logs-db    - Show database logs"
	@echo "  make logs-backend - Show backend logs"
	@echo "  make logs-frontend - Show frontend logs"
	@echo "  make clean      - Stop containers and remove volumes (DELETES DATABASE DATA; needs CONFIRM=yes)"
	@echo "  make rebuild    - Rebuild and restart all services"
	@echo "  make stop       - Stop all services"
	@echo "  make start      - Start all services"
	@echo "  make ps         - Show running containers"
	@echo "  make shell-backend - Open shell in backend container"
	@echo "  make shell-frontend - Open shell in frontend container"
	@echo "  make shell-db   - Open PostgreSQL shell"

# Build all images
build:
	docker compose build

# Start all services
up:
	docker compose up -d
	@echo ""
	@echo "Services started:"
	@echo "  - Database:    127.0.0.1:5433 (psql over loopback, not a URL)"
	@echo "  - Backend API: http://localhost:8080"
	@echo "  - Frontend:    http://localhost:3000"
	@echo ""
	@echo "Use 'make logs' to view logs"

# Stop all services
down:
	docker compose down

# Restart all services
restart:
	docker compose restart

# Show logs from all services
logs:
	docker compose logs -f

# Show database logs
logs-db:
	docker compose logs -f postgres

# Show backend logs
logs-backend:
	docker compose logs -f backend

# Show frontend logs
logs-frontend:
	docker compose logs -f frontend

# Clean everything including volumes. `down -v` removes the postgres_data volume and there is no
# backup to restore from, so this is a database-destroying command; it now asks instead of
# deleting first and printing the warning after.
clean:
	@if [ "$${CONFIRM}" != "yes" ]; then \
	  echo "REFUSED: make clean runs 'docker compose down -v', which deletes the postgres_data volume."; \
	  echo "         There is no backup to restore from. Run 'make clean CONFIRM=yes' to mean it."; \
	  exit 1; \
	fi
	docker compose down -v
	@echo "All containers and volumes removed"

# Rebuild and restart
rebuild:
	docker compose down
	docker compose build --no-cache
	docker compose up -d
	@echo ""
	@echo "Services rebuilt and started:"
	@echo "  - Database:    127.0.0.1:5433 (psql over loopback, not a URL)"
	@echo "  - Backend API: http://localhost:8080"
	@echo "  - Frontend:    http://localhost:3000"

# Stop services
stop:
	docker compose stop

# Start services
start:
	docker compose start

# Show running containers
ps:
	docker compose ps

# Open shell in backend container
shell-backend:
	docker compose exec backend sh

# Open shell in frontend container
shell-frontend:
	docker compose exec frontend sh

# Open PostgreSQL shell
shell-db:
	docker compose exec postgres psql -U gms_user -d gms

