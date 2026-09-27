# FinLedger Docker Setup Guide

## Overview

This guide explains how to run the entire FinLedger microservices system using Docker Compose. All services, databases, and message queues are orchestrated in isolated containers.

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- 6+ GB free RAM
- 20+ GB free disk space

## Quick Start

### Build and Start All Services

```bash
# From project root directory
docker-compose up --build

# Or in background
docker-compose up --build -d
```

**Wait 2-3 minutes** for all services to start and register with Eureka.

### Verify Services Are Running

```bash
# Check container status
docker-compose ps

# Expected output: all services "healthy"
```

### Stop All Services

```bash
docker-compose down
```

### Stop and Remove All Data

```bash
docker-compose down -v  # -v removes volumes (persistent data)
```

---

## Accessing Services

### API Gateway (Entry Point)
```
http://localhost:8080
```

**Test health check:**
```bash
curl http://localhost:8080/actuator/health
```

### Swagger API Documentation
```
http://localhost:8080/swagger-ui.html
```

### Ledger Service (Direct Access)
```
http://localhost:8082
```

**Health check:**
```bash
curl http://localhost:8082/actuator/health/readiness
```

### Auth Service (Direct Access)
```
http://localhost:8081
```

### Notification Service
```
http://localhost:8083
```

### Eureka Service Registry
```
http://localhost:8761
```

Shows all registered microservices. Healthy services show "UP" status.

### Kafka UI (Kafdrop)
```
http://localhost:9000
```

Browse topics, view messages, inspect Dead Letter Topics.

### PostgreSQL (Direct Connection)
```
Host: localhost:5432
User: ledger
Password: ledger123
Databases:
  - auth_db (auth service)
  - ledger (ledger service)
```

### Redis
```
Host: localhost:6379
Port: 6379
```

Use with: `redis-cli -h localhost`

---

## Service Architecture

### Startup Order
1. **Infrastructure** (parallel):
   - PostgreSQL (health check 10s interval)
   - Redis
   - Zookeeper
   - Kafka (waits for Zookeeper)
   - Kafdrop (waits for Kafka)

2. **Microservices** (parallel, with health checks):
   - Eureka Service Registry (no dependencies)
   - Auth Service (waits for: PostgreSQL, Eureka)
   - Ledger Service (waits for: PostgreSQL, Redis, Kafka, Eureka)
   - Notification Service (waits for: Kafka, Eureka)
   - Gateway Service (waits for: Eureka, Auth Service, Ledger Service, Redis)

### Network
All services communicate via `ledger_network` bridge network:
- Internal: `service-name:port` (e.g., `postgres:5432`)
- External: `localhost:port` (e.g., `localhost:5432`)

---

## Resource Allocation

Each service has CPU and memory limits:

| Service | CPU Limit | Memory Limit | Reserved |
|---------|-----------|--------------|----------|
| PostgreSQL | 1.0 | 1GB | 0.5 / 512MB |
| Redis | 0.5 | 512MB | 0.25 / 256MB |
| Kafka | 1.0 | 1.5GB | 0.5 / 768MB |
| Ledger Service | 2.0 | 2GB | 1.0 / 1GB |
| Gateway Service | 2.0 | 2GB | 1.0 / 1GB |
| Auth Service | 1.0 | 1GB | 0.5 / 512MB |
| Others | 0.5-1.0 | 512MB-1GB | varies |

**Total: ~12-15GB memory required**

---

## Example API Calls

### 1. Create an Account

```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Checking Account",
    "type": "ASSET",
    "currency": "INR"
  }'
```

### 2. Post a Transfer

First, create two accounts and note their IDs. Then:

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "uuid-1",
    "destinationAccountId": "uuid-2",
    "amount": 500.50,
    "currency": "INR",
    "description": "Payment",
    "idempotencyKey": "req-12345"
  }'
```

### 3. Get Account Balance

```bash
curl http://localhost:8080/api/accounts/{accountId}/balance
```

### 4. Reconciliation Report

```bash
curl "http://localhost:8080/api/reconciliation/report?from=2026-06-01&to=2026-06-30"
```

### 5. Find Balance Discrepancies

```bash
curl "http://localhost:8080/api/reconciliation/discrepancies?from=2026-06-01&to=2026-06-30"
```

---

## Troubleshooting

### Service Health Checks Fail

```bash
# View logs for a specific service
docker-compose logs ledger-service
docker-compose logs gateway-service

# Follow logs in real-time
docker-compose logs -f ledger-service
```

### Database Connection Errors

```bash
# Check PostgreSQL is running
docker-compose ps postgres

# Connect to database
docker-compose exec postgres psql -U ledger -d ledger

# List databases
\l

# Exit
\q
```

### Kafka Connection Issues

```bash
# Check Kafka health
docker-compose exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# View topics
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Check consumer groups
docker-compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
```

### Redis Connection Issues

```bash
# Test Redis
docker-compose exec redis redis-cli ping

# Check cache contents
docker-compose exec redis redis-cli KEYS "balance:*"
```

### Eureka Services Not Registering

1. Check service logs: `docker-compose logs service-name`
2. Verify EUREKA_CLIENT_SERVICEURL_DEFAULTZONE is set
3. Wait 30-60 seconds (initial registration takes time)
4. Visit http://localhost:8761 and refresh

### Port Already in Use

If a port is already bound:

```bash
# Find process using port (example: 8080)
lsof -i :8080  # On macOS/Linux
netstat -ano | findstr :8080  # On Windows

# Kill the process or change port in docker-compose.yml
# Then rebuild: docker-compose up --build
```

---

## Production Considerations

### Security

1. **Change default passwords:**
   - PostgreSQL: `ledger123` → strong password
   - JWT_SECRET: Base64-encoded 32+ char secret
   - Redis: Add requirepass authentication

2. **Network isolation:**
   - Don't expose Postgres/Redis/Kafka ports in production
   - Use firewall rules
   - Place gateway-service behind reverse proxy (Nginx)

3. **Secrets management:**
   - Use Docker secrets or environment variable files (`.env`)
   - Never commit credentials to git
   - Rotate JWT secrets periodically

### Monitoring & Logging

1. **Health checks:**
   - Monitor `/actuator/health` endpoints
   - Set up alerting on service failure

2. **Centralized logging:**
   - Ship JSON logs to ELK/Splunk/Datadog
   - Parse correlation IDs for tracing
   - Retain logs 30+ days

3. **Metrics:**
   - Export Prometheus metrics: `/actuator/metrics`
   - Set up Grafana dashboards
   - Monitor: request latency, error rates, cache hit ratio

### Scaling

For production scale:

1. **Database:**
   - Use managed PostgreSQL (RDS, CloudSQL)
   - Enable replication and failover
   - Increase connection pool size

2. **Kafka:**
   - Deploy multi-broker cluster (3+ brokers)
   - Enable replication factor 3
   - Increase partition count for parallelism

3. **Microservices:**
   - Run multiple replicas of each service
   - Use Kubernetes for orchestration
   - Configure horizontal pod autoscaling

---

## Maintenance

### Backup PostgreSQL Data

```bash
docker-compose exec postgres pg_dump -U ledger ledger > ledger_backup.sql
docker-compose exec postgres pg_dump -U ledger auth_db > auth_backup.sql
```

### Restore PostgreSQL Data

```bash
docker-compose exec -T postgres psql -U ledger ledger < ledger_backup.sql
docker-compose exec -T postgres psql -U ledger auth_db < auth_backup.sql
```

### View Volumes

```bash
docker volume ls  # List all volumes
docker volume inspect finledger_postgres_data  # Inspect specific volume
```

### Clean Up Unused Resources

```bash
docker system prune -a --volumes  # Remove all unused containers, images, volumes
docker container prune  # Remove stopped containers
docker image prune  # Remove unused images
```

---

## Development Workflow

### Make Changes to Code

```bash
# Edit source files
# Rebuild affected service(s)
docker-compose up --build ledger-service
```

### View Real-Time Logs

```bash
# Follow all logs
docker-compose logs -f

# Follow specific service
docker-compose logs -f ledger-service --tail=100
```

### Access Service Shell

```bash
# SSH into a container
docker-compose exec ledger-service /bin/sh

# Run one-off command
docker-compose exec ledger-service ls -la
```

### Rebuild All Services

```bash
docker-compose down -v  # Remove everything
docker-compose up --build  # Clean rebuild
```

---

## Next Steps

1. **Explore Swagger UI:** http://localhost:8080/swagger-ui.html
2. **Try example API calls** (see section above)
3. **View logs** to understand system behavior
4. **Check Eureka dashboard** to see service registration
5. **Browse Kafdrop** to see Kafka topics
6. **Monitor health checks** on `/actuator/health` endpoints

---

## Documentation

For detailed information, see:

- **FINLEDGER_COMPLETE_ROADMAP.md** — Feature breakdown and architecture
- **Swagger UI** — Interactive API documentation at port 8080
- **Docker logs** — Service-specific logs for troubleshooting
- **Eureka dashboard** — Service registration and health status

---

## Support

For issues or questions:
1. Check **Troubleshooting** section above
2. View service logs: `docker-compose logs service-name`
3. Check health endpoints: `/actuator/health`
4. Visit **Kafdrop** for Kafka debugging
5. Check **PostgreSQL** database directly

---

**Created:** 2026-06-21  
**FinLedger Version:** 1.0.0  
**Last Updated:** Feature 9.4 (Docker Compose Final Configuration)
