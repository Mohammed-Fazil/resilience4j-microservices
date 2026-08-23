package com.ecommerce.orderservice.service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.ecommerce.orderservice.client.PaymentClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class PaymentGatewayService {

	private final PaymentClient paymentClient;

	private final AtomicInteger counter = new AtomicInteger(0);

	public PaymentGatewayService(PaymentClient paymentClient) {
		this.paymentClient = paymentClient;
	}

	@RateLimiter(name = "paymentService", fallbackMethod = "rateLimitFallback")
	@Retry(name = "paymentService")
	@CircuitBreaker(name = "paymentService")
	public String makePayment() {
		System.out.println(
				"Calling Payment form Order Service : " + counter.getAndIncrement() + " " + LocalDateTime.now());

		return paymentClient.makePayment();
	}

	public String rateLimitFallback(RequestNotPermitted exception) {

		System.out.println("Rate limit exceeded");

		return "Too many requests. Please try again later.";
	}

}