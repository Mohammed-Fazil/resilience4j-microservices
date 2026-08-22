# Resilience4j Microservices

A Spring Boot microservices project built to understand and implement resilience patterns using Resilience4j in a realistic distributed system.

The project demonstrates how microservices communicate using Eureka Service Discovery and OpenFeign, and how Resilience4j protects service-to-service communication from failures.

## 🚀 Project Overview

The project currently consists of:

- Eureka Server
- Order Service
- Payment Service
- OpenFeign for service-to-service communication
- Eureka Service Discovery
- Spring Cloud LoadBalancer
- Spring Boot Actuator
- Resilience4j Retry
- Resilience4j Circuit Breaker
- Exponential Backoff
- Randomized Wait / Jitter
- Global Exception Handling
- Custom application exceptions

The project is being developed incrementally to understand each resilience pattern and the problems it solves.

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
           ┌─────────────────────────┐
           │      Resilience4j       │
           │                         │
           │ Retry                   │
           │ Exponential Backoff     │
           │ Jitter                  │
           │ Circuit Breaker         │
           └────────────┬────────────┘
                        │
                        ▼
                 Payment Service
```

### Service communication

```text
Order Service
      |
      | OpenFeign
      v
Spring Cloud LoadBalancer
      |
      v
Eureka Service Discovery
      |
      v
Payment Service
```

## 📦 Services

### Eureka Server

Port:

```text
8761
```

Dashboard:

```text
http://localhost:8761
```

Responsibilities:

- Service registration
- Service discovery
- Maintaining service instances

### Payment Service

Port:

```text
8081
```

Endpoint:

```http
POST /payments
```

The Payment Service contains controlled failure simulation for learning Retry and Circuit Breaker behavior.

Example:

```java
@PostMapping("/payments")
public ResponseEntity<String> makePayment() {

    int attempt = attempts.incrementAndGet();

    System.out.println(
            "Payment attempt: "
            + attempt
            + " "
            + LocalDateTime.now()
    );

    if (attempt % 2 == 0) {
        throw new PaymentFailedException(
                "Temporary payment failure"
        );
    }

    return ResponseEntity.ok("Payment Successful");
}
```

This allows controlled failures:

```text
Attempt 1 → SUCCESS
Attempt 2 → FAILURE
Attempt 3 → SUCCESS
Attempt 4 → FAILURE
...
```

The Payment Service uses a custom exception and `@RestControllerAdvice` to return structured error responses.

### Order Service

Port:

```text
8082
```

Endpoint:

```http
POST /orders
```

The Order Service calls Payment Service through OpenFeign.

The payment communication is protected by Resilience4j.

## 🔄 OpenFeign Communication

```java
@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/payments")
    String makePayment();
}
```

The service name `payment-service` is resolved through Eureka.

```text
Order Service
      |
      | Feign
      v
Load Balancer
      |
      v
Eureka
      |
      v
Payment Service
```

The Order Service does not hard-code the Payment Service host.

# 🛡️ Resilience4j

## Retry

Retry allows a failed operation to be attempted again.

Current configuration:

```properties
resilience4j.retry.instances.paymentService.max-attempts=3
resilience4j.retry.instances.paymentService.wait-duration=2s
```

`max-attempts=3` means:

```text
Initial attempt
      +
Retry 1
      +
Retry 2
      =
3 total attempts
```

## Randomized Wait / Jitter

Jitter prevents multiple clients from retrying at exactly the same time.

```properties
resilience4j.retry.instances.paymentService.enable-randomized-wait=true
resilience4j.retry.instances.paymentService.randomized-wait-factor=0.5
```

Without jitter:

```text
Attempt 1
   ↓
2 seconds
   ↓
Attempt 2
   ↓
2 seconds
   ↓
Attempt 3
```

With jitter:

```text
Attempt 1
   ↓
randomized delay
   ↓
Attempt 2
   ↓
randomized delay
   ↓
Attempt 3
```

Observed during testing:

```text
Attempt 1 → Attempt 2 ≈ 2.12 seconds
Attempt 2 → Attempt 3 ≈ 2.37 seconds
```

The exact delay is randomized.

## Exponential Backoff

Exponential backoff increases the retry delay after each failure.

```properties
resilience4j.retry.instances.paymentService.enable-exponential-backoff=true
resilience4j.retry.instances.paymentService.exponential-backoff-multiplier=2
```

Conceptually:

```text
Attempt 1
   ↓
~2 seconds
   ↓
Attempt 2
   ↓
~4 seconds
   ↓
Attempt 3
```

When combined with jitter, the actual delays are randomized.

Observed during testing:

```text
Attempt 1 → Attempt 2 ≈ 3.03 seconds
Attempt 2 → Attempt 3 ≈ 4.81 seconds
```

This demonstrates Exponential Backoff + Jitter.

## 🔌 Circuit Breaker

Current configuration:

```properties
resilience4j.circuitbreaker.instances.paymentService.register-health-indicator=true
resilience4j.circuitbreaker.instances.paymentService.sliding-window-type=COUNT_BASED
resilience4j.circuitbreaker.instances.paymentService.sliding-window-size=5
resilience4j.circuitbreaker.instances.paymentService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.paymentService.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.paymentService.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.paymentService.permitted-number-of-calls-in-half-open-state=2
resilience4j.circuitbreaker.instances.paymentService.automatic-transition-from-open-to-half-open-enabled=true
```

### Circuit Breaker states

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
             wait 10 seconds
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

## 🔗 Retry + Circuit Breaker Design

The current design separates resilience from application error handling.

```text
Order Controller
       |
       v
Order Service
       |
       v
Payment Gateway Service
       |
       +---- Retry
       |
       +---- Circuit Breaker
       |
       v
OpenFeign
       |
       v
Payment Service
       |
       | failure
       v
Exception propagates
       |
       v
Custom application exception
       |
       v
@RestControllerAdvice
       |
       v
HTTP 503 JSON response
```

We intentionally avoid putting the same fallback method on both Retry and Circuit Breaker.

The goal is:

```text
Attempt 1 → failure
Attempt 2 → failure
Attempt 3 → failure
        ↓
one final application-level failure
        ↓
custom exception
        ↓
global exception handler
        ↓
HTTP 503
```

## ⚠️ Issues Encountered and Solutions

### 1. Feign 404

**Error**

```text
FeignException$NotFound: [404]
```

**Cause:** The Feign client initially called `POST /` while Payment Service expected `POST /payments`.

**Solution:** Make both paths match:

```java
// Payment Service
@PostMapping("/payments")

// Feign Client
@PostMapping("/payments")
```

### 2. Connection Refused

**Error**

```text
java.net.ConnectException: Connection refused
```

**Cause:** Payment Service was not reachable.

**Solution:**

1. Verify Payment Service is running.
2. Verify it is registered with Eureka.
3. Verify Eureka contains `PAYMENT-SERVICE`.
4. Verify the correct port.

### 3. Load Balancer Does Not Contain an Instance

**Error**

```text
Load balancer does not contain an instance
for the service payment-service
```

**Cause:** Order Service attempted to call Payment Service before Eureka had registered an instance.

**Solution:** Start services in this order:

```text
Eureka
   ↓
Payment Service
   ↓
Order Service
```

Wait until `PAYMENT-SERVICE UP` appears in Eureka.

### 4. Circuit Breaker Did Not Retry

**Problem:** Retry appeared to execute only once.

**Cause:** A Circuit Breaker fallback converted the exception into a normal return value, so Retry saw a successful result.

**Solution:** Do not use the same fallback on both Retry and Circuit Breaker. Let exceptions propagate through the resilience layer and handle the final application failure centrally.

### 5. Fallback Executed Multiple Times

**Problem:** The fallback was executed for individual retry failures.

**Solution:** Move Resilience4j annotations to a separate `PaymentGatewayService` and remove `fallbackMethod` from Retry and Circuit Breaker. Convert the final failure into a custom application exception and handle it using `@RestControllerAdvice`.

## 📊 Spring Boot Actuator

Health:

```text
http://localhost:8082/actuator/health
```

Info:

```text
http://localhost:8082/actuator/info
```

Metrics:

```text
http://localhost:8082/actuator/metrics
```

Configuration:

```properties
management.endpoints.web.exposure.include=health,info,metrics
management.info.env.enabled=true
```

## ⚙️ Environment Configuration

Sensitive configuration should not be committed.

Use:

```text
.env
```

for local secrets and commit:

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

## ▶️ Running the Project

Start services in this order.

### 1. Eureka Server

```text
localhost:8761
```

### 2. Payment Service

```text
localhost:8081
```

Verify:

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

## 🧪 Testing

Create an order:

```http
POST http://localhost:8082/orders
```

When Payment Service succeeds:

```text
Order created. Payment Successful
```

When Payment Service fails repeatedly, observe Retry, Jitter, Exponential Backoff and Circuit Breaker behavior in the logs and Actuator metrics.

## 📚 Project Documentation

Detailed notes can be maintained separately:

```text
docs/
├── LEARNING.md
├── RESILIENCE-PATTERNS.md
└── TROUBLESHOOTING.md
```

- `LEARNING.md` — concepts and learning notes
- `RESILIENCE-PATTERNS.md` — Resilience4j experiments and configurations
- `TROUBLESHOOTING.md` — issues encountered and solutions

## 🗺️ Roadmap

### Phase 1 — Microservices Foundation

- [x] Eureka Server
- [x] Order Service
- [x] Payment Service
- [x] Service registration
- [x] Service discovery
- [x] OpenFeign communication
- [x] Client-side load balancing

### Phase 2 — Observability

- [x] Spring Boot Actuator
- [x] Health endpoint
- [x] Info endpoint
- [x] Metrics endpoint

### Phase 3 — Resilience4j

- [x] Circuit Breaker
- [x] CLOSED state
- [x] OPEN state
- [x] HALF_OPEN state
- [x] Retry
- [x] Randomized wait / Jitter
- [x] Exponential Backoff
- [x] Custom exception handling
- [x] Global exception handling

### Phase 4 — Advanced Resilience

- [ ] Rate Limiter
- [ ] Bulkhead
- [ ] Time Limiter
- [ ] Combining all resilience patterns
- [ ] Resilience4j metrics
- [ ] Improved failure simulation
- [ ] Monitoring dashboard

## 🎯 Learning Goals

This project is intended to develop a practical understanding of:

- Microservice communication
- Service discovery
- Client-side load balancing
- OpenFeign
- Failure handling
- Circuit Breaker
- Retry
- Exponential Backoff
- Jitter
- Rate Limiting
- Bulkhead isolation
- Timeouts
- Fallback mechanisms
- Global exception handling
- Monitoring
- Distributed-system failure scenarios

The focus is on understanding the behavior and trade-offs of each resilience pattern rather than simply adding annotations.

## 📄 License

This project is intended for learning and demonstration purposes.
