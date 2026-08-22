package com.ecommerce.paymentservice.controller;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.paymentservice.exception.PaymentFailedException;

@RestController
public class PaymentController {

	private final AtomicInteger attempts = new AtomicInteger();

	@PostMapping("/payments")
	public ResponseEntity<String> makePayment() {

		int attempt = attempts.incrementAndGet();

		System.out.println("Payment attempt: " + attempt + " " + LocalDateTime.now());

		if (attempt % 2 == 0) {
			throw new PaymentFailedException("Temporary payment failure");
		}

		return ResponseEntity.ok("Payment Successful");
	}

	@GetMapping()
	public String getMethodName() {
		return "Hello From Payment Service";
	}

}