package com.ecommerce.orderservice.service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.ecommerce.orderservice.client.PaymentClient;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class PaymentGatewayService {

	private final PaymentClient paymentClient;

	private final AtomicInteger counter = new AtomicInteger(0);

	public PaymentGatewayService(PaymentClient paymentClient) {
		this.paymentClient = paymentClient;
	}

	@Bulkhead(name = "paymentService", type = Bulkhead.Type.THREADPOOL)
	@RateLimiter(name = "paymentService")
	@Retry(name = "paymentService")
	@CircuitBreaker(name = "paymentService")
	public CompletableFuture<String> makePayment() {
		System.out.println("Calling Payment form Payment Gateway : " + counter.getAndIncrement() + " "
				+ LocalDateTime.now() + "  " + Thread.currentThread().getName());

		CompletableFuture<String> completedFuture = CompletableFuture.completedFuture(paymentClient.makePayment());

//		System.out.println("CAllent payment");
		return completedFuture;
	}

}