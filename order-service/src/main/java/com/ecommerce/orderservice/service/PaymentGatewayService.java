package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.PaymentClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

	private final PaymentClient paymentClient;

	public PaymentGatewayService(PaymentClient paymentClient) {
		this.paymentClient = paymentClient;
	}

	@Retry(name = "paymentService")
	@CircuitBreaker(name = "paymentService")
	public String makePayment() {
		System.out.println("Calling Payment form Order Service : " + LocalDateTime.now());

		return paymentClient.makePayment();
	}
}