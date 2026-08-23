package com.ecommerce.orderservice.exception;

public class PaymentServiceUnavailableException extends RuntimeException {

	public PaymentServiceUnavailableException(String message) {
		super(message);
		System.out.println(message);
	}

	public PaymentServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}