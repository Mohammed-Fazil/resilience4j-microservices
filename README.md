# Resilience4j Microservices

A hands-on Spring Boot microservices project demonstrating fault-tolerance and resilience patterns using **Resilience4j**, **Spring Cloud Eureka**, and **OpenFeign**.

## Architecture

```text
                         ┌─────────────────────┐
                         │    Eureka Server    │
                         │       :8761         │
                         └──────────┬──────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    │                               │
             ┌──────▼──────┐                 ┌──────▼──────┐
             │    Order    │    OpenFeign    │   Payment   │
             │   Service   ├────────────────►│   Service   │
             │    :8082    │                 │             │
             └──────┬──────┘                 └─────────────┘
                    │
                    │
             ┌──────▼────────────────────────────────────┐
             │               Resilience4j                │
             │                                           │
             │   ┌─────────┐      ┌──────────────────┐   │
             │   │  Retry  │─────►│ Circuit Breaker  │   │
             │   └─────────┘      └────────┬─────────┘   │
             │                             │             │
             │   ┌────────────┐            ▼             │
             │   │RateLimiter │────────► Bulkhead        │
             │   └────────────┘         (Thread Pool)    │
             │                              │            │
             │                              ▼            │
             │                        ┌────────────┐     │
             │                        │ TimeLimiter│     │
             │                        └─────┬──────┘     │
             └──────────────────────────────┼────────────┘
                                            │
                                            ▼
                                     Payment Service
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


# ===============================
# TimeLimiter
# ===============================

resilience4j.timelimiter.instances.paymentService.timeout-duration=3s
resilience4j.timelimiter.instances.paymentService.cancel-running-future=true
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


# 5. TimeLimiter

TimeLimiter limits how long an asynchronous operation is allowed to run.

Current configuration:

```properties
resilience4j.timelimiter.instances.paymentService.timeout-duration=3s
resilience4j.timelimiter.instances.paymentService.cancel-running-future=true
```

Meaning:

```text
Payment operation
      |
      | maximum 3 seconds
      v
TimeLimiter
      |
      +---- completed → return response
      |
      +---- timeout → TimeoutException
```

The Payment Service was deliberately slowed down during testing to verify timeout behavior.

The protected method returns:

```java
CompletableFuture<String>
```

The timeout is converted into an application-specific `PaymentTimeoutException` and handled through the global exception handler.

The API returns:

```text
HTTP 504 Gateway Timeout
```

Example:

```json
{
    "status": 504,
    "error": "PAYMENT_SERVICE_TIMEOUT",
    "message": "Payment service did not respond within the expected time."
}
```

### TimeLimiter + Retry

The combination was tested successfully.

With a 3-second TimeLimiter and 3 retry attempts:

```text
Attempt 1 → timeout → Retry
Attempt 2 → timeout → Retry
Attempt 3 → timeout → final failure
```

The final timeout is translated into `PaymentTimeoutException`.

### Important behavior

`cancel-running-future=true` does not guarantee that a blocking downstream HTTP operation is physically interrupted. The TimeLimiter stops waiting for the result when the timeout is reached.

# Combining All Five Patterns

All five resilience patterns were tested together.

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

TimeLimiter
    ↓
TimeoutException
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

if (cause instanceof TimeoutException) {
    // TimeLimiter timeout
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


# ===============================
# TimeLimiter
# ===============================

resilience4j.timelimiter.instances.paymentService.timeout-duration=3s
resilience4j.timelimiter.instances.paymentService.cancel-running-future=true
```



# Resilience4j Configuration Explained

The following configuration is the final configuration used in the project.

## Circuit Breaker Properties

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

### `register-health-indicator=true`

Registers the Circuit Breaker with Spring Boot Actuator health.

This allows the Circuit Breaker state to be reflected in the application's health information.

### `sliding-window-type=COUNT_BASED`

The Circuit Breaker evaluates a fixed number of calls rather than a time duration.

### `sliding-window-size=20`

The Circuit Breaker keeps the results of the latest 20 calls in its count-based window.

### `failure-rate-threshold=50`

The Circuit Breaker can open when the recorded failure rate reaches 50% or more, after the minimum number of calls requirement is satisfied.

### `minimum-number-of-calls=15`

The Circuit Breaker does not calculate the failure rate for opening the circuit until at least 15 calls have been recorded.

Example:

```text
15 calls
8 failures
7 successes

Failure rate = 53.33%

53.33% >= 50%
        ↓
CLOSED → OPEN
```

### `wait-duration-in-open-state=10s`

When the Circuit Breaker enters OPEN, it stays OPEN for at least 10 seconds before allowing the transition toward HALF_OPEN.

### `permitted-number-of-calls-in-half-open-state=2`

When the Circuit Breaker is HALF_OPEN, at most 2 test calls are permitted.

The results of those calls determine whether the circuit can close again or must reopen.

### `automatic-transition-from-open-to-half-open-enabled=true`

Automatically moves the Circuit Breaker from OPEN to HALF_OPEN after the configured wait duration.

Without automatic transition, an external call/trigger may be required depending on configuration and implementation.

---

## Retry Properties

```properties
resilience4j.retry.instances.paymentService.max-attempts=3
resilience4j.retry.instances.paymentService.wait-duration=1s
resilience4j.retry.instances.paymentService.enable-randomized-wait=true
resilience4j.retry.instances.paymentService.randomized-wait-factor=0.5
resilience4j.retry.instances.paymentService.enable-exponential-backoff=true
resilience4j.retry.instances.paymentService.exponential-backoff-multiplier=2
```

### `max-attempts=3`

The maximum number of attempts, including the initial call, is 3.

```text
Attempt 1
Attempt 2
Attempt 3
```

It does not mean 3 retries after the initial call.

### `wait-duration=1s`

The base waiting duration between retry attempts is 1 second.

### `enable-randomized-wait=true`

Enables randomized waiting, commonly called jitter.

Instead of every retry waiting for exactly the same duration, the actual wait is randomized around the configured duration.

This helps avoid many clients retrying at exactly the same time.

### `randomized-wait-factor=0.5`

Controls the amount of randomization around the base wait duration.

With a base wait of 1 second and a factor of 0.5, the retry wait is randomized within the range supported by the configured Resilience4j behavior.

### `enable-exponential-backoff=true`

Enables exponential growth of the retry wait duration.

The delay becomes larger for subsequent attempts.

### `exponential-backoff-multiplier=2`

Each retry delay grows by a factor of 2 before the randomized component is applied according to the configured retry behavior.

Conceptually:

```text
Base delay
   ↓
Attempt 1 → wait around 1s
Attempt 2 → wait around 2s
Attempt 3 → wait around 4s
```

The actual observed delay can vary because jitter is enabled.

---

## RateLimiter Properties

```properties
resilience4j.ratelimiter.instances.paymentService.limit-for-period=10
resilience4j.ratelimiter.instances.paymentService.limit-refresh-period=60s
resilience4j.ratelimiter.instances.paymentService.timeout-duration=0
```

### `limit-for-period=10`

Allows 10 permission/permit acquisitions during one refresh period.

### `limit-refresh-period=60s`

The RateLimiter refreshes its available permits every 60 seconds.

Conceptually:

```text
10 permits
   ↓
60 second refresh period
   ↓
permits become available again
```

### `timeout-duration=0`

A request does not wait for a permit.

If no permit is immediately available, the request is rejected.

The important exception is:

```text
RequestNotPermitted
```

---

## Semaphore Bulkhead Properties

The Semaphore Bulkhead configuration was also tested separately:

```properties
resilience4j.bulkhead.instances.paymentService.max-concurrent-calls=3
resilience4j.bulkhead.instances.paymentService.max-wait-duration=5s
```

### `max-concurrent-calls=3`

Allows at most 3 calls to execute concurrently through the Semaphore Bulkhead.

### `max-wait-duration=5s`

A caller can wait up to 5 seconds for permission to enter the Bulkhead.

This configuration is commented out in the final ThreadPool Bulkhead setup because the project uses the ThreadPool Bulkhead for the combined asynchronous flow.

---

## ThreadPool Bulkhead Properties

```properties
resilience4j.thread-pool-bulkhead.instances.paymentService.max-thread-pool-size=3
resilience4j.thread-pool-bulkhead.instances.paymentService.core-thread-pool-size=2
resilience4j.thread-pool-bulkhead.instances.paymentService.queue-capacity=2
resilience4j.thread-pool-bulkhead.instances.paymentService.keep-alive-duration=20s
```

### `max-thread-pool-size=3`

The maximum number of worker threads that the ThreadPool Bulkhead can create is 3.

### `core-thread-pool-size=2`

The core number of worker threads is 2.

### `queue-capacity=2`

Up to 2 additional tasks can wait in the queue when the workers are busy.

The test therefore demonstrated:

```text
3 running
+
2 queued
=
5 accepted tasks
```

Additional work can be rejected with:

```text
BulkheadFullException
```

### `keep-alive-duration=20s`

Controls how long an idle extra worker thread can remain available before it can be removed.

It is important to remember:

```text
keep-alive-duration ≠ request timeout
```

The request timeout is handled by TimeLimiter.

---

## TimeLimiter Properties

```properties
resilience4j.timelimiter.instances.paymentService.timeout-duration=3s
resilience4j.timelimiter.instances.paymentService.cancel-running-future=true
```

### `timeout-duration=3s`

The TimeLimiter allows the asynchronous operation to complete for up to 3 seconds.

If the operation does not complete within that duration, the TimeLimiter completes the operation exceptionally with a timeout.

### `cancel-running-future=true`

Requests cancellation of the running Future when the timeout occurs.

For a blocking downstream HTTP operation, this does not guarantee that the remote server or the underlying blocking operation is physically stopped.

The important behavior is that the caller stops waiting after the TimeLimiter timeout.

---

## Retry Logging

```properties
logging.level.io.github.resilience4j.retry=DEBUG
```

Enables DEBUG-level logging for Resilience4j Retry.

This is useful while learning and troubleshooting retry behavior.

---

## Aspect Order Properties

The following properties were intentionally left commented out:

```properties
# resilience4j.retry.retry-aspect-order=1
# resilience4j.circuitbreaker.circuit-breaker-aspect-order=2
# resilience4j.ratelimiter.rate-limiter-aspect-order=3
# resilience4j.bulkhead.bulkhead-aspect-order=4
```

The project previously failed to start when the unsupported property:

```properties
resilience4j.bulkhead.bulkhead-aspect-order=4
```

was added.

The error was:

```text
No setter found for property: bulkhead-aspect-order
```

Therefore unsupported manual aspect-order properties were removed instead of forcing them into the configuration.

---

## Final Combined Configuration

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

# ===============================
# TimeLimiter
# ===============================

resilience4j.timelimiter.instances.paymentService.timeout-duration=3s
resilience4j.timelimiter.instances.paymentService.cancel-running-future=true

# ===============================
# Retry logs
# ===============================

logging.level.io.github.resilience4j.retry=DEBUG
```

---

# Completed Resilience4j Implementation

The project has implemented and tested all five resilience patterns:

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
- [x] TimeLimiter
- [x] Custom application exceptions
- [x] Global exception handling
- [x] CompletableFuture exception handling
- [x] Retry + TimeLimiter
- [x] Circuit Breaker + TimeLimiter
- [x] RateLimiter + TimeLimiter
- [x] ThreadPool Bulkhead + TimeLimiter
- [x] Combined Retry + Circuit Breaker + RateLimiter + ThreadPool Bulkhead + TimeLimiter

## Complete Resilience Flow

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
                         TimeLimiter
                              |
                              v
                       Payment Service
```

Each pattern has a specific responsibility:

| Pattern | Responsibility |
|---|---|
| Retry | Retries temporary failures |
| Circuit Breaker | Stops calls to an unhealthy service |
| RateLimiter | Controls request rate |
| ThreadPool Bulkhead | Limits concurrent work and isolates resources |
| TimeLimiter | Limits how long an asynchronous operation can take |

All five patterns were tested individually and then tested together with failing and slow Payment Service scenarios.
