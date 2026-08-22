package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.PaymentClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

	private final PaymentClient paymentClient;

	public OrderService(PaymentClient paymentClient) {
		this.paymentClient = paymentClient;
	}

	@CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallBack")
	public String createOrder() {

		String paymentResponse = paymentClient.makePayment();

		return "Order created. " + paymentResponse;
	}

	public String paymentFallBack(Throwable throwable) {

//		System.out.println("Payment Service is Currently Unavailable. Please try again.");
		return "Payment Service is Currently Unavailable. Please try again.";
	}
}