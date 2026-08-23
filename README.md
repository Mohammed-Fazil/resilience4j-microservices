# Resilience4j Microservices

A hands-on Spring Boot microservices project demonstrating fault-tolerance and resilience patterns using **Resilience4j**, **Spring Cloud Eureka**, and **OpenFeign**.

## Architecture

```text
                         ┌──────────────────┐
                         │   Eureka Server  │
                         │      :8761       │
                         └────────┬─────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
             ┌──────▼──────┐             ┌──────▼──────┐
             │ Order       │  OpenFeign   │ Payment     │
             │ Service     ├────────────►│ Service     │
             │ :8082       │             │             │
             └──────┬──────┘             └─────────────┘
                    │
              Resilience4j
                    │
        ┌───────────┼───────────┐
        │           │           │
      Retry    Circuit Breaker  │
        │           │           │
   RateLimiter  ThreadPool Bulkhead
```

## Technologies

- Java 21
- Spring Boot
- Spring Cloud Eureka
- Spring Cloud OpenFeign
- Resilience4j
- Maven
- REST APIs

## Services

### Eureka Server

Service discovery server.

```text
Port: 8761
```

### Order Service

Main service that calls Payment Service.

```text
Port: 8082
```

### Payment Service

Simulated downstream service used to test failures, delays, retries, and resilience patterns.

---

# Resilience4j Patterns Implemented

## 1. Retry

Retry automatically attempts a failed operation again.

Current configuration:

```properties
resilience4j.retry.instances.paymentService.max-attempts=3
resilience4j.retry.instances.paymentService.wait-duration=1s
resilience4j.retry.instances.paymentService.enable-randomized-wait=true
resilience4j.retry.instances.paymentService.randomized-wait-factor=0.5
resilience4j.retry.instances.paymentService.enable-exponential-backoff=true
resilience4j.retry.instances.paymentService.exponential-backoff-multiplier=2
```

The project tested:

- Basic retry
- Jitter / randomized wait
- Exponential backoff
- Exponential backoff + jitter

Example:

```text
Attempt 1 → failure
      ↓
wait with backoff + jitter
      ↓
Attempt 2 → failure
      ↓
wait with larger backoff + jitter
      ↓
Attempt 3 → failure
```

---

# 2. Circuit Breaker

Circuit Breaker prevents repeated calls to an unhealthy downstream service.

Current configuration:

```properties
resilience4j.circuitbreaker.instances.paymentService.register-health-indicator=true
resilience4j.circuitbreaker.instances.paymentService.sliding-window-type=COUNT_BASED
resilience4j.circuitbreaker.instances.paymentService.sliding-window-size=20
resilience4j.circuitbreaker.instances.paymentService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.paymentService.minimum-number-of-calls=15
resilience4j.circuitbreaker.instances.paymentService.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.paymentService.permitted-number-of-calls-in-half-open-state=2
resilience4j.circuitbreaker.instances.paymentService.automatic-transition-from-open-to-half-open-enabled=true
```

### Meaning

The Circuit Breaker uses a count-based sliding window of 20 calls.

It starts evaluating the failure rate after at least 15 calls.

If the failure rate reaches or exceeds 50%, the Circuit Breaker can transition from:

```text
CLOSED → OPEN
```

After 10 seconds:

```text
OPEN → HALF_OPEN
```

Two permitted test calls are allowed in HALF_OPEN.

Successful calls can move the circuit back to:

```text
HALF_OPEN → CLOSED
```

A failure can move it back to:

```text
HALF_OPEN → OPEN
```

Important exception:

```text
CallNotPermittedException
```

This occurs when the Circuit Breaker is OPEN and rejects a call.

---

# 3. RateLimiter

RateLimiter controls how many requests are allowed during a time period.

Current configuration:

```properties
resilience4j.ratelimiter.instances.paymentService.limit-for-period=10
resilience4j.ratelimiter.instances.paymentService.limit-refresh-period=60s
resilience4j.ratelimiter.instances.paymentService.timeout-duration=0
```

Meaning:

```text
10 permits
within 60 seconds
```

Because:

```properties
timeout-duration=0
```

a request that cannot obtain a permit is rejected immediately instead of waiting.

Important exception:

```text
RequestNotPermitted
```

Example:

```text
Request 1  → allowed
Request 2  → allowed
...
Request 10 → allowed
Request 11 → rejected
```

after consuming the available permits within the same refresh period.

---

# 4. Bulkhead

Bulkhead prevents one slow dependency from consuming unlimited resources.

## Semaphore Bulkhead

The project also tested Semaphore Bulkhead using:

```properties
resilience4j.bulkhead.instances.paymentService.max-concurrent-calls=3
resilience4j.bulkhead.instances.paymentService.max-wait-duration=5s
```

This limits concurrent calls.

## ThreadPool Bulkhead

Current configuration:

```properties
resilience4j.thread-pool-bulkhead.instances.paymentService.max-thread-pool-size=3
resilience4j.thread-pool-bulkhead.instances.paymentService.core-thread-pool-size=2
resilience4j.thread-pool-bulkhead.instances.paymentService.queue-capacity=2
resilience4j.thread-pool-bulkhead.instances.paymentService.keep-alive-duration=20s
```

The project tested:

```text
3 worker threads
+
2 queue slots
=
5 accepted tasks
```

Additional work can be rejected with:

```text
BulkheadFullException
```

Dedicated threads observed during testing:

```text
bulkhead-paymentService-1
bulkhead-paymentService-2
bulkhead-paymentService-3
```

This confirmed that the Payment call was executing through the dedicated ThreadPool Bulkhead.

### Keep-alive duration

```properties
keep-alive-duration=20s
```

controls how long an idle extra worker thread can remain available before it can be removed.

It is **not** a request timeout.

---

# Combining All Four Patterns

All four patterns were tested together.

The service uses:

```java
@Bulkhead(
    name = "paymentService",
    type = Bulkhead.Type.THREADPOOL
)
@RateLimiter(name = "paymentService")
@Retry(name = "paymentService")
@CircuitBreaker(name = "paymentService")
public CompletableFuture<String> makePayment() {
    // payment call
}
```

Conceptually:

```text
Order Request
      |
      v
    Retry
      |
      v
Circuit Breaker
      |
      v
 RateLimiter
      |
      v
ThreadPool Bulkhead
      |
      v
Payment Service
```

The exact interaction between the patterns depends on the Resilience4j aspect ordering and the type of failure being tested.

---

# Exception Handling Approach

The project initially used fallback methods.

It was then refactored to use the specific Resilience4j exceptions and translate them into application-level exceptions.

Important exceptions:

```text
Circuit Breaker
    ↓
CallNotPermittedException

RateLimiter
    ↓
RequestNotPermitted

Bulkhead
    ↓
BulkheadFullException
```

These are converted into application-level exceptions such as:

```text
PaymentServiceUnavailableException
```

and handled centrally using:

```java
@RestControllerAdvice
```

This keeps Resilience4j-specific implementation details away from the REST API layer.

---

# CompletableFuture and ThreadPool Bulkhead

ThreadPool Bulkhead uses asynchronous execution.

The protected method returns:

```java
CompletableFuture<String>
```

When the Order Service calls:

```java
paymentGatewayService.makePayment().get();
```

an asynchronous failure can be wrapped inside:

```text
ExecutionException
```

Therefore the actual cause is extracted:

```java
Throwable cause = e.getCause();
```

Then the application checks the actual Resilience4j exception:

```java
if (cause instanceof BulkheadFullException) {
    // Bulkhead rejected the call
}

if (cause instanceof CallNotPermittedException) {
    // Circuit Breaker is OPEN
}

if (cause instanceof RequestNotPermitted) {
    // RateLimiter rejected the request
}
```

The original application exception is preserved when appropriate:

```java
catch (PaymentServiceUnavailableException e) {
    throw e;
}
```

---

# Global Exception Handling

The application uses:

```java
@RestControllerAdvice
```

to convert application exceptions into consistent JSON responses.

Example:

```json
{
    "timestamp": "2026-08-23T15:59:29.108023",
    "status": 503,
    "error": "PAYMENT_SERVICE_UNAVAILABLE",
    "message": "Payment service is busy. Please try again later. Bulkhead Pattern"
}
```

The application can therefore keep resilience-specific logic in the service layer while keeping controllers simple.

---

# Problems Faced and Solutions

## Retry appeared to execute only once

Verified that Retry only retries configured failures/exceptions and added logging around the protected method.

## Retry + Circuit Breaker interaction

Retry and Circuit Breaker were tested separately first and then together to understand failure propagation and Circuit Breaker state changes.

## Fallback message was being replaced

The fallback initially produced a specific Bulkhead message, but `OrderService` caught the exception and created a new generic application exception.

The solution was to preserve the existing application exception:

```java
catch (PaymentServiceUnavailableException e) {
    throw e;
}
```

## ThreadPool Bulkhead initially showed Tomcat threads

Initially logs showed:

```text
http-nio-8082-exec-X
```

After using `CompletableFuture<String>` with ThreadPool Bulkhead, logs showed:

```text
bulkhead-paymentService-1
bulkhead-paymentService-2
bulkhead-paymentService-3
```

## Unsupported aspect-order property

The application previously failed when this unsupported property was added:

```properties
resilience4j.bulkhead.bulkhead-aspect-order=4
```

with:

```text
No setter found for property: bulkhead-aspect-order
```

The unsupported aspect-order properties were removed.

The current configuration leaves the manual RateLimiter/Bulkhead aspect-order properties commented out.

---

# Current application.properties

Important current resilience configuration:

```properties
# ===============================
# Circuit Breaker
# ===============================

resilience4j.circuitbreaker.instances.paymentService.register-health-indicator=true
resilience4j.circuitbreaker.instances.paymentService.sliding-window-type=COUNT_BASED
resilience4j.circuitbreaker.instances.paymentService.sliding-window-size=20
resilience4j.circuitbreaker.instances.paymentService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.paymentService.minimum-number-of-calls=15
resilience4j.circuitbreaker.instances.paymentService.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.paymentService.permitted-number-of-calls-in-half-open-state=2
resilience4j.circuitbreaker.instances.paymentService.automatic-transition-from-open-to-half-open-enabled=true


# ===============================
# Retry
# ===============================

resilience4j.retry.instances.paymentService.max-attempts=3
resilience4j.retry.instances.paymentService.wait-duration=1s
resilience4j.retry.instances.paymentService.enable-randomized-wait=true
resilience4j.retry.instances.paymentService.randomized-wait-factor=0.5
resilience4j.retry.instances.paymentService.enable-exponential-backoff=true
resilience4j.retry.instances.paymentService.exponential-backoff-multiplier=2


# ===============================
# RateLimiter
# ===============================

resilience4j.ratelimiter.instances.paymentService.limit-for-period=10
resilience4j.ratelimiter.instances.paymentService.limit-refresh-period=60s
resilience4j.ratelimiter.instances.paymentService.timeout-duration=0


# ===============================
# ThreadPool Bulkhead
# ===============================

resilience4j.thread-pool-bulkhead.instances.paymentService.max-thread-pool-size=3
resilience4j.thread-pool-bulkhead.instances.paymentService.core-thread-pool-size=2
resilience4j.thread-pool-bulkhead.instances.paymentService.queue-capacity=2
resilience4j.thread-pool-bulkhead.instances.paymentService.keep-alive-duration=20s
```


# Learning Progress

- [x] Eureka Service Discovery
- [x] OpenFeign communication
- [x] Retry
- [x] Retry with jitter
- [x] Exponential backoff
- [x] Exponential backoff + jitter
- [x] Circuit Breaker
- [x] Circuit Breaker states
- [x] RateLimiter
- [x] Semaphore Bulkhead
- [x] ThreadPool Bulkhead
- [x] Keep-alive duration
- [x] Custom application exceptions
- [x] Global exception handling
- [x] CompletableFuture exception handling
- [x] Combining resilience patterns

## Next

- [ ] TimeLimiter
- [ ] Combine TimeLimiter with the other resilience patterns
- [ ] Metrics and monitoring
- [ ] Production-oriented resilience configuration
