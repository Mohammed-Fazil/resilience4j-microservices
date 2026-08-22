package com.ecommerce.orderservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

	@Bean
	public CircuitBreaker paymentCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {

		CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");
	
		circuitBreaker.getEventPublisher().onStateTransition(
				event -> System.out.println("Circuit Breaker state changed: " + event.getStateTransition()));

		return circuitBreaker;
	}
}