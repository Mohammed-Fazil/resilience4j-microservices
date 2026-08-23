package com.ecommerce.orderservice.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(PaymentServiceUnavailableException.class)
	public ResponseEntity<ErrorResponse> handlePaymentServiceUnavailable(PaymentServiceUnavailableException exception) {

		ErrorResponse error = new ErrorResponse(LocalDateTime.now(), HttpStatus.SERVICE_UNAVAILABLE.value(),
				"PAYMENT_SERVICE_UNAVAILABLE", exception.getMessage());

		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
	}
	@ExceptionHandler(TooManyRequestsException.class)
	public ResponseEntity<ErrorResponse> handleRateLimiter(TooManyRequestsException exception) {

		ErrorResponse error = new ErrorResponse(LocalDateTime.now(), HttpStatus.SERVICE_UNAVAILABLE.value(),
				"PAYMENT_SERVICE_UNAVAILABLE", exception.getMessage());

		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
	}
}