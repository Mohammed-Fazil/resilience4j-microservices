# Resilience4j Microservices

A Spring Boot microservices project for learning and implementing resilience patterns using Resilience4j.

## 🚀 Current Features

- Eureka Server
- Order Service
- Payment Service
- OpenFeign
- Eureka Service Discovery
- Spring Cloud LoadBalancer
- Spring Boot Actuator
- Retry
- Circuit Breaker
- RateLimiter
- Exponential Backoff
- Randomized Wait / Jitter
- Custom Exceptions
- Global Exception Handling

## 🏗️ Architecture

```text
                 ┌──────────────────┐
                 │   Eureka Server  │
                 │      :8761       │
                 └────────┬─────────┘
                          │
                 Service Discovery
                          │
          ┌───────────────┴───────────────┐
          │                               │
          ▼                               ▼
 ┌─────────────────┐             ┌─────────────────┐
 │  Order Service  │             │ Payment Service │
 │      :8082      │             │      :8081      │
 └────────┬────────┘             └─────────────────┘
          │
          ▼
 ┌─────────────────────────┐
 │       Resilience4j      │
 │                         │
 │ RateLimiter             │
 │ Retry                   │
 │ Jitter                  │
 │ Exponential Backoff     │
 │ Circuit Breaker         │
 └────────────┬────────────┘
              │
              ▼
           OpenFeign
              │
              ▼
       Payment Service
```

Communication flow:

```text
Order Service
      |
   OpenFeign
      |
LoadBalancer
      |
    Eureka
      |
Payment Service
```

## 📦 Services

### Eureka Server

Port: `8761`

Dashboard:

```text
http://localhost:8761
```

### Payment Service

Port: `8081`

Endpoint:

```http
POST /payments
```

The Payment Service intentionally simulates failures for resilience testing.

```java
@PostMapping("/payments")
public ResponseEntity<String> makePayment() {

    int attempt = attempts.incrementAndGet();

    if (attempt % 2 == 0) {
        throw new PaymentFailedException(
                "Temporary payment failure"
        );
    }

    return ResponseEntity.ok("Payment Successful");
}
```

This produces:

```text
Attempt 1 → SUCCESS
Attempt 2 → FAILURE
Attempt 3 → SUCCESS
Attempt 4 → FAILURE
```

### Order Service

Port: `8082`

Endpoint:

```http
POST /orders
```

The Order Service communicates with Payment Service through OpenFeign and applies Resilience4j.

---

# 🔄 OpenFeign

```java
@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/payments")
    String makePayment();
}
```

Eureka resolves `payment-service` to an available instance.

---

# 🛡️ Resilience4j

## 1. RateLimiter

Example:

```properties
resilience4j.ratelimiter.instances.paymentService.limit-for-period=5
resilience4j.ratelimiter.instances.paymentService.limit-refresh-period=10s
resilience4j.ratelimiter.instances.paymentService.timeout-duration=0
```

Behavior:

```text
10 second period

Request 1 → ✅
Request 2 → ✅
Request 3 → ✅
Request 4 → ✅
Request 5 → ✅
Request 6 → ❌
```

### Timeout Duration

With:

```properties
timeout-duration=0
```

a request with no available permit is rejected immediately.

With:

```properties
timeout-duration=5s
```

the request can wait for a permit for up to 5 seconds.

This is not a persistent message queue.

---

## 2. Retry

Example:

```properties
resilience4j.retry.instances.paymentService.max-attempts=3
resilience4j.retry.instances.paymentService.wait-duration=2s
```

Three total attempts are allowed:

```text
Attempt 1 → failure
    ↓ 2 seconds
Attempt 2 → failure
    ↓ 2 seconds
Attempt 3 → failure
```

---

## 3. Jitter

Jitter randomizes retry delays.

```properties
resilience4j.retry.instances.paymentService.enable-randomized-wait=true
resilience4j.retry.instances.paymentService.randomized-wait-factor=0.5
```

Without jitter:

```text
2s → 2s → 2s
```

With jitter:

```text
~2.1s → ~2.4s → ...
```

---

## 4. Exponential Backoff

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

When combined with jitter, the actual delays vary.

---

## 5. Circuit Breaker

Example configuration:

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

States:

```text
CLOSED
  |
  | failure threshold reached
  v
OPEN
  |
  | wait duration
  v
HALF_OPEN
  |
  +---- success ----> CLOSED
  |
  +---- failure ----> OPEN
```

Successfully demonstrated:

```text
CLOSED → OPEN
OPEN → HALF_OPEN
HALF_OPEN → OPEN
HALF_OPEN → CLOSED
```

---

# 🔗 Combined Resilience Flow

The current combined flow is:

```text
                    Order Request
                         |
                         v
                    RateLimiter
                         |
                         v
                       Retry
                         |
                         v
                  Circuit Breaker
                         |
                         v
                     OpenFeign
                         |
                         v
                  Payment Service
```

Responsibilities:

```text
RateLimiter
→ Controls traffic volume.

Retry
→ Retries temporary failures.

Circuit Breaker
→ Stops calls when the downstream service is repeatedly failing.
```

Typical failure flow:

```text
Order Request
     ↓
RateLimiter allows
     ↓
Retry attempt 1 → failure
     ↓
Retry attempt 2 → failure
     ↓
Retry attempt 3 → failure
     ↓
Final application failure
     ↓
Custom exception
     ↓
@RestControllerAdvice
     ↓
HTTP 503
```

After enough failures:

```text
Circuit Breaker
CLOSED → OPEN
```

Future calls are rejected while the circuit is OPEN.

---

# ⚠️ Issues Encountered and Solutions

## Feign 404

Error:

```text
FeignException$NotFound: [404]
```

Cause:

Feign called `POST /` while Payment Service expected `POST /payments`.

Solution:

```java
// Payment Service
@PostMapping("/payments")

// Feign Client
@PostMapping("/payments")
```

## Connection Refused

Error:

```text
java.net.ConnectException: Connection refused
```

Cause:

Payment Service was not reachable.

Solution:

1. Verify Payment Service is running.
2. Verify it is registered with Eureka.
3. Verify `PAYMENT-SERVICE` is UP.
4. Verify the correct port.

## Load Balancer Has No Instance

Error:

```text
Load balancer does not contain an instance
for the service payment-service
```

Cause:

Order Service called Payment Service before Eureka had an available instance.

Solution:

```text
Start Eureka
    ↓
Start Payment Service
    ↓
Wait for PAYMENT-SERVICE UP
    ↓
Start Order Service
```

## Retry Appeared Not to Work With Circuit Breaker

Cause:

A Circuit Breaker fallback converted an exception into a normal return value. Retry then saw a successful result.

Solution:

Do not use the same fallback on both Retry and Circuit Breaker. Let failures propagate through the resilience layers and handle the final failure centrally.

## RateLimiter Timeout

`timeout-duration=0`:

```text
No permit → reject immediately
```

`timeout-duration=5s`:

```text
No permit
   ↓
Wait up to 5 seconds
   ↓
Permit available → continue
No permit → reject
```

---

# 📊 Actuator

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

---

# ⚙️ Environment Configuration

Never commit secrets.

Use:

```text
.env
```

locally and commit:

```text
.env.example
```

Example:

```env
DB_USERNAME=your_username
DB_PASSWORD=your_password
JWT_SECRET=change_me
```

Make sure `.env` is in `.gitignore`.

---

# ▶️ Running the Project

Start services in this order:

```text
1. Eureka Server    :8761
2. Payment Service  :8081
3. Order Service    :8082
```

Wait for both services to appear as `UP` in Eureka.

Test:

```http
POST http://localhost:8082/orders
```

---

# 📚 Recommended Repository Structure

```text
Resilience4j/
│
├── eureka-server/
├── order-service/
├── payment-service/
│
│
├── .gitignore
├── .env.example
└── README.md
```

- `README.md` — project overview and setup
- `LEARNING.md` — concepts learned
- `RESILIENCE-PATTERNS.md` — pattern experiments
- `TROUBLESHOOTING.md` — actual issues and solutions

---

# 🗺️ Roadmap

## Microservices Foundation

- [x] Eureka Server
- [x] Order Service
- [x] Payment Service
- [x] Service registration
- [x] Service discovery
- [x] OpenFeign
- [x] Client-side load balancing

## Observability

- [x] Spring Boot Actuator
- [x] Health
- [x] Info
- [x] Metrics

## Resilience4j

- [x] Circuit Breaker
- [x] CLOSED state
- [x] OPEN state
- [x] HALF_OPEN state
- [x] Retry
- [x] Randomized Wait / Jitter
- [x] Exponential Backoff
- [x] RateLimiter
- [x] RateLimiter timeout behavior
- [x] Custom exceptions
- [x] Global exception handling
- [x] Combined RateLimiter + Retry + Circuit Breaker

## Advanced Resilience

- [ ] Bulkhead
- [ ] TimeLimiter
- [ ] Advanced RateLimiter scenarios
- [ ] Combining all resilience patterns
- [ ] Resilience4j metrics
- [ ] Improved failure simulation
- [ ] Monitoring dashboard

---

# 🎯 Learning Goals

This project focuses on practical understanding of:

- Microservice communication
- Service discovery
- Client-side load balancing
- OpenFeign
- Retry
- Exponential Backoff
- Jitter
- Circuit Breaker
- Rate Limiting
- Bulkhead isolation
- Timeouts
- Exception handling
- Monitoring
- Distributed-system failure scenarios

The goal is to understand how each pattern works, why it is needed, and how multiple resilience patterns interact.

---

