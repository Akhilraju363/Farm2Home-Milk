#!/bin/bash
# Start all Farm2Home Milk services with Docker Compose

set -e

echo "Starting Farm2Home Milk Platform..."
cd "$(dirname "$0")/../.."

docker-compose up -d

echo ""
echo "Services started:"
echo "  Frontend:       http://localhost:3000"
echo "  API Gateway:    http://localhost:8081"
echo "  Swagger UI:     http://localhost:8081/swagger-ui.html"
echo "  Auth Service:   http://localhost:8091"
echo ""
echo "Run 'docker-compose logs -f' to follow logs"
