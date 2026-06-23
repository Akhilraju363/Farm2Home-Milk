#!/bin/bash
# Stop all Farm2Home Milk services

set -e

echo "Stopping Farm2Home Milk Platform..."
cd "$(dirname "$0")/../.."

docker-compose down

echo "All services stopped."
