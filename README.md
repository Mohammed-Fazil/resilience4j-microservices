# Resilience4j Microservices

A Spring Boot microservices project built to understand and implement resilience patterns using Resilience4j in a realistic distributed system.

The project demonstrates how microservices communicate using Eureka Service Discovery and OpenFeign, and how Resilience4j protects service-to-service communication from failures.

---

## 🚀 Project Overview

The initial architecture consists of:

- Eureka Server
- Order Service
- Payment Service
- OpenFeign for service-to-service communication
- Spring Boot Actuator for monitoring
- Resilience4j for fault tolerance

The project will gradually introduce multiple resilience patterns:

- Circuit Breaker
- Retry
- Rate Limiter
- Bulkhead
- Time Limiter
- Fallback mechanisms

The goal is not only to implement these patterns, but also to understand when and why each pattern should be used.

---

## 🏗️ Architecture

```text
                         ┌──────────────────┐
                         │   Eureka Server  │
                         │      :8761       │
                         └─────────┬────────┘
                                   │
                          Service Discovery
                                   │
                    ┌──────────────┴──────────────┐
                    │                             │
                    ▼                             ▼
           ┌─────────────────┐           ┌─────────────────┐
           │  Order Service  │           │ Payment Service │
           │      :8082      │           │      :8081      │
           └────────┬────────┘           └─────────────────┘
                    │
                    │ OpenFeign
                    ▼
           ┌─────────────────┐
           │   Resilience4j  │
           │                 │
           │ Circuit Breaker │
           │ Retry           │
           │ Bulkhead        │
           │ RateLimiter     │
           └────────┬────────┘
                    │
                    ▼
           ┌─────────────────┐
           │ Payment Service │
           └─────────────────┘
```

---

## 🔄 Service Communication

Order Service communicates with Payment Service using OpenFeign.

```java
@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/payments")
    String makePayment();
}
```

The `name` refers to the service registered with Eureka.

The communication flow is:

```text
Order Service
      |
      | Feign
      ▼
Load Balancer
      |
      ▼
Eureka
      |
      ▼
Payment Service
```

The Order Service does not directly hard-code the Payment Service host.

---

## 🧰 Technologies

| Technology | Purpose |
|---|---|
| Java 21 | Programming language |
| Spring Boot 4.1.1 | Application framework |
| Spring Cloud 2025.1.2 | Microservices infrastructure |
| Eureka | Service discovery |
| OpenFeign | Service-to-service HTTP communication |
| Resilience4j 2.4.0 | Fault tolerance |
| Spring Boot Actuator | Monitoring and metrics |
| Maven | Build and dependency management |
| Git | Version control |

---

# 📦 Services

## Eureka Server

Port:

```text
8761
```

Responsibilities:

- Service registration
- Service discovery
- Maintaining service instances

Dashboard:

```text
http://localhost:8761
```

---

## Payment Service

Port:

```text
8081
```

Endpoint:

```http
POST /payments
```

Example response:

```text
Payment Successful
```

---

## Order Service

Port:

```text
8082
```

Endpoint:

```http
POST /orders
```

The Order Service calls Payment Service through OpenFeign.

---

# 🛡️ Resilience4j

## Circuit Breaker

The Order Service protects the Payment Service call using:

```java
@CircuitBreaker(
    name = "paymentService",
    fallbackMethod = "paymentFallBack"
)
```

The Circuit Breaker has three main states:

```text
             ┌──────────┐
             │  CLOSED  │
             └────┬─────┘
                  │
             failures
                  │
                  ▼
             ┌──────────┐
             │   OPEN   │
             └────┬─────┘
                  │
             wait duration
                  │
                  ▼
            ┌───────────┐
            │ HALF_OPEN │
            └─────┬─────┘
                  │
             ┌────┴────┐
             │         │
          success    failure
             │         │
             ▼         ▼
          CLOSED      OPEN
```

### Current configuration

```properties
resilience4j.circuitbreaker.instances.paymentService.sliding-window-type=COUNT_BASED
resilience4j.circuitbreaker.instances.paymentService.sliding-window-size=5
resilience4j.circuitbreaker.instances.paymentService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.paymentService.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.paymentService.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.paymentService.permitted-number-of-calls-in-half-open-state=2
resilience4j.circuitbreaker.instances.paymentService.automatic-transition-from-open-to-half-open-enabled=true
```

### Circuit Breaker behavior

If Payment Service continuously fails:

```text
5 failed calls
      ↓
Failure rate >= 50%
      ↓
CLOSED → OPEN
      ↓
Stop calling Payment Service
      ↓
Execute fallback
      ↓
Wait 10 seconds
      ↓
OPEN → HALF_OPEN
      ↓
Test Payment Service
      ↓
 ┌────┴────┐
 ▼         ▼
Success   Failure
 ▼         ▼
CLOSED    OPEN
```

---

# 📊 Actuator

Actuator is used to monitor the microservices.

### Health

```text
http://localhost:8082/actuator/health
```

### Info

```text
http://localhost:8082/actuator/info
```

### Metrics

```text
http://localhost:8082/actuator/metrics
```

Current configuration:

```properties
management.endpoints.web.exposure.include=health,info,metrics
management.info.env.enabled=true
```

---

# ⚙️ Configuration

Environment-specific or sensitive configuration should not be committed.

Use:

```text
.env
```

for local secrets and:

```text
.env.example
```

as a template.

Example:

```env
DB_USERNAME=your_username
DB_PASSWORD=your_password
JWT_SECRET=change_me
```

Never commit the real `.env` file.

---

# ▶️ Running the Project

Start the services in the following order:

### 1. Eureka Server

```text
localhost:8761
```

Wait until Eureka is available.

### 2. Payment Service

```text
localhost:8081
```

Verify that it appears in Eureka as:

```text
PAYMENT-SERVICE    UP
```

### 3. Order Service

```text
localhost:8082
```

Verify:

```text
ORDER-SERVICE      UP
PAYMENT-SERVICE    UP
```

---

# 🧪 Testing

Create an order:

```http
POST http://localhost:8082/orders
```

Expected response when Payment Service is healthy:

```text
Order created. Payment Successful
```

### Testing Circuit Breaker

1. Keep Payment Service running.
2. Make `/payments` fail with HTTP 500.
3. Call `/orders` repeatedly.
4. Observe the Circuit Breaker transition.
5. Restore Payment Service.
6. Observe recovery from HALF_OPEN to CLOSED.

---

# 📚 Learning Documentation

Detailed learning notes are maintained separately:

- `docs/ARCHITECTURE.md` — Service architecture and communication
- `docs/LEARNING.md` — Overall concepts and learning notes
- `docs/RESILIENCE_PATTERNS.md` — Resilience4j concepts and experiments
- `docs/TROUBLESHOOTING.md` — Issues encountered and their solutions

These documents contain the concepts, experiments, issues, and solutions encountered while building the project.

---

# 🗺️ Roadmap

## Phase 1 — Microservices Foundation

- [x] Eureka Server
- [x] Order Service
- [x] Payment Service
- [x] Service registration
- [x] Service discovery
- [x] OpenFeign communication

## Phase 2 — Observability

- [x] Spring Boot Actuator
- [x] Health endpoint
- [x] Info endpoint
- [x] Metrics endpoint

## Phase 3 — Resilience4j

- [x] Circuit Breaker
- [x] Fallback
- [x] CLOSED state
- [x] OPEN state
- [x] HALF_OPEN state
- [ ] Retry
- [ ] Rate Limiter
- [ ] Bulkhead
- [ ] Time Limiter

## Phase 4 — Advanced Resilience

- [ ] Combining Circuit Breaker + Retry
- [ ] Bulkhead with concurrent requests
- [ ] Rate limiting
- [ ] Timeout handling
- [ ] Resilience4j metrics
- [ ] Failure simulation
- [ ] Monitoring dashboard

---

# 🎯 Learning Goals

This project is intended to develop a practical understanding of:

- Microservice communication
- Service discovery
- Client-side load balancing
- OpenFeign
- Failure handling
- Circuit Breaker
- Retry
- Rate Limiting
- Bulkhead isolation
- Timeouts
- Fallback mechanisms
- Monitoring
- Distributed-system failure scenarios

The focus is on understanding the behavior of each resilience pattern rather than simply adding annotations to the application.

---

# 📄 License

This project is intended for learning and demonstration purposes.
