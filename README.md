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

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 21+ | must be on `PATH` |
| Maven | 3.9+ | must be on `PATH` |
| Node.js | 20+ | must be on `PATH` |
| PostgreSQL | 16 | running locally on port `5432` (native install or Docker) |

The startup script checks all four automatically and tells you exactly what's missing before doing anything else.

### One-click local startup (Windows)

No Docker required for this path - everything runs as native Java/Node processes so you get live console output per service and fast restarts.

```bat
start-all.bat
```

This will, in order:
1. Verify Java, Maven, Node, and PostgreSQL are available.
2. Create `logs/` if it doesn't exist.
3. Run `mvn clean install -DskipTests` - skipped automatically if nothing under `backend/` has changed since the last successful build.
4. Start **Config Server**, wait for `/actuator/health` to report `UP`.
5. Start **Discovery Service (Eureka)**, wait for it to accept connections.
6. Start the 10 business microservices (auth, customer, subscription, farm, inventory, production, order, payment, delivery, notification), each in its own window, waiting for its health check before moving to the next.
7. Start the **API Gateway**, wait for it to become healthy.
8. Run `npm install` (only if `frontend/node_modules` is missing) and `npm run dev`, then open `http://localhost:3000` in your browser automatically.

Each service opens in its own window so you can watch it live, or `Ctrl+C` an individual one without affecting the rest. If any step fails, the script stops there, prints the service name, the reason, and its log file path, and does not start anything further down the dependency chain.

`start-all.bat` is a thin wrapper - all the actual logic lives in [`start-all-services.ps1`](start-all-services.ps1), which you can also run directly (e.g. from a VS Code or Eclipse terminal) or with `-SkipBuild` to skip the Maven build check on a fast restart:

```powershell
.\start-all-services.ps1 -SkipBuild
```

### Stopping everything

```bat
stop-all.bat
```

Reads the list of processes `start-all.bat` started (`logs/.session-pids.json`), stops each one (backend windows and the frontend dev server, including its child Node process), and leaves everything else on your machine untouched - it will never touch a Java process this session didn't start itself.

### Run with Docker Compose (alternative)

```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f <service-name>
```

### Manual / individual service (either platform)

```bash
cd frontend
npm install
npm run dev

cd backend
mvn clean install -DskipTests

# Run one service directly
cd auth-service
mvn spring-boot:run
```

### Logs

Every service - backend and frontend - writes to a single shared folder at the project root, never inside the individual service directories:

```
Farm2Home-Milk/
└── logs/
    ├── config-server.log
    ├── discovery-service.log
    ├── auth-service.log
    ├── customer-service.log
    ├── subscription-service.log
    ├── farm-service.log
    ├── inventory-service.log
    ├── production-service.log
    ├── order-service.log
    ├── payment-service.log
    ├── delivery-service.log
    ├── notification-service.log
    ├── api-gateway.log
    ├── frontend.log
    ├── build.log                  # output of the last `mvn clean install`
    └── archive/                   # rotated/compressed backend logs
```

Backend logs rotate daily or at 50MB, whichever comes first, are gzip-compressed into `logs/archive/`, and are kept for 30 days (configured once in `backend/shared-libs/common-observability` and shared by every service - see that module if you need to change the policy).

### Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `PostgreSQL is not running.` | Start your local PostgreSQL service, or `docker-compose up -d postgres`, then re-run `start-all.bat`. |
| Config Server or Discovery step fails immediately | Check `logs/config-server.log` / `logs/discovery-service.log` - most often a port already in use (`8888` / `8761`) from a previous run that `stop-all.bat` didn't fully clean up. |
| A microservice fails health check but its window shows no obvious error | Give it a bit longer on a cold machine (first-run JIT/class loading is slow) via `-HealthTimeoutSeconds 180`, or check its schema-specific log for a Postgres/Flyway migration issue. |
| `start-all.bat` says a jar wasn't found | The build didn't produce output for that module - check `logs/build.log`. |
| Browser opens to a blank page / connection refused on `:3000` | Check `logs/frontend.log`; usually `npm install` needs to finish or a dependency failed to install. |
| `order-service` / `payment-service` / `delivery-service` / `notification-service` hangs or times out on its health check | These 4 need a running Kafka broker. The script warns at startup if it can't reach `localhost:9092` but doesn't block on it - start Kafka first (`docker-compose up -d zookeeper kafka`), then re-run. |
| Port already in use | Run `stop-all.bat` first - it stops only processes this tooling started, so a truly orphaned process (e.g. after a crashed run) may need `taskkill /PID <pid> /T /F` manually. Also check nothing else on your machine already owns one of this project's ports (`8080` in particular is a common default for other local software) via `Get-NetTCPConnection -LocalPort 8080`. |
| Script blocked by PowerShell execution policy | `start-all.bat`/`stop-all.bat` already invoke PowerShell with `-ExecutionPolicy Bypass` for just that process, so this shouldn't happen via the `.bat` files; if running the `.ps1` directly, use the same flag or `Set-ExecutionPolicy -Scope Process Bypass`. |

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
