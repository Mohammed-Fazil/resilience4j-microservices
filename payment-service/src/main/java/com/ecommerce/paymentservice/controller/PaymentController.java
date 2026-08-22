package com.ecommerce.paymentservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {
	int c = 0;

	@PostMapping
	public String makePayment() {

		System.out.println("Called Payment Service");

//		throw new RuntimeException("Payment service is currently failing");
		return "Payment Succesfull";
	}

	@GetMapping
	public String getMethodName() {
		return "Hello";
	}
}