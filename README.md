# Farm2Home Milk

Enterprise Dairy Farm Management & Milk Delivery Platform

---

## Overview

Farm2Home Milk connects dairy farms directly to customers via a subscription-based daily milk delivery system. The platform covers customer management, subscriptions, deliveries, farm operations (cow health, milk production), inventory, payments, and analytics — all through a microservices architecture.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 19 + TypeScript + Material UI + Redux Toolkit |
| Backend | Java 21 + Spring Boot 3.2 + Spring Cloud |
| Database | PostgreSQL (per-service schema) + Flyway migrations |
| Auth | JWT + OTP (Spring Security) |
| API Docs | OpenAPI 3 / Swagger UI |
| Messaging | Apache Kafka |
| Containerization | Docker + Docker Compose |
| CI/CD | GitLab CI/CD |
| Cloud | AWS (EC2, RDS, S3, ELB, CloudWatch) |
| Monitoring | Prometheus + Grafana + Spring Actuator |

---

## Project Structure

```
Farm2Home-Milk/
├── backend/
│   ├── api-gateway/           # Spring Cloud Gateway
│   ├── auth-service/          # JWT + OTP authentication
│   ├── customer-service/      # Customer profiles & addresses
│   ├── subscription-service/  # Milk subscription plans
│   ├── order-service/         # Daily order generation
│   ├── payment-service/       # UPI / Razorpay / Wallet
│   ├── delivery-service/      # Route assignment & tracking
│   ├── farm-service/          # Cow registration & health
│   ├── production-service/    # Milk collection & quality
│   ├── inventory-service/     # Feed, medicine, equipment
│   └── notification-service/  # SMS, email, push
├── frontend/                  # React + TypeScript SPA
├── infrastructure/
│   ├── docker/                # Shared Docker configs
│   ├── k8s/                   # Kubernetes manifests
│   ├── terraform/             # AWS infrastructure as code
│   └── scripts/               # Utility scripts
└── docs/                      # PRD and architecture docs
```

---

## Microservices

| Service | Port | Description |
|---|---|---|
| api-gateway | 8080 | Single entry point, routing, auth filter |
| auth-service | 8081 | Registration, login, OTP, JWT |
| customer-service | 8082 | Customer profiles, addresses |
| subscription-service | 8083 | Subscription plans & scheduling |
| order-service | 8084 | Order creation & lifecycle |
| payment-service | 8085 | UPI, Razorpay, wallet |
| delivery-service | 8086 | Routes, assignments, tracking |
| farm-service | 8087 | Cow management, vaccinations |
| production-service | 8088 | Milk collection & quality |
| inventory-service | 8089 | Feed, medicine, equipment |
| notification-service | 8090 | SMS, email, push notifications |

---

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21
- Node 20+
- Maven 3.9+

### Run with Docker Compose

```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f <service-name>
```

### Frontend Development

```bash
cd frontend
npm install
npm run dev
```

### Backend Development

```bash
cd backend
mvn clean install -DskipTests

# Run individual service
cd auth-service
mvn spring-boot:run
```

---

## User Roles

| Role | Permissions |
|---|---|
| Super Admin | Full system access |
| Farm Manager | Cows, production, inventory |
| Delivery Manager | Routes, assignments |
| Delivery Partner | View & update own deliveries |
| Customer | Subscribe, order, pay |

---

## CI/CD Pipeline (GitLab)

1. Build → Test → SonarQube Scan → Docker Build → Deploy QA → Deploy UAT → Deploy Production

---

## Environments

| Environment | Description |
|---|---|
| Development | Local Docker Compose |
| QA | AWS EC2 — auto-deploy on merge to `develop` |
| UAT | AWS EC2 — manual trigger |
| Production | AWS EC2 — manual trigger after UAT sign-off |

---

## Documentation

- [Product Requirements Document](docs/FARM2HOME_MILK_PRD.md)
- API docs available at `http://localhost:8080/swagger-ui.html` (when running locally)

---

## License

Proprietary — Farm2Home Milk © 2024
