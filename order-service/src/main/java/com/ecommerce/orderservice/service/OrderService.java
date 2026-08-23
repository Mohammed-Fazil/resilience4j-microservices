package com.ecommerce.orderservice.service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

import org.springframework.stereotype.Service;

import com.ecommerce.orderservice.exception.PaymentServiceUnavailableException;
import com.ecommerce.orderservice.exception.TooManyRequestsException;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

@Service
public class OrderService {

	private final PaymentGatewayService paymentGatewayService;

	public OrderService(PaymentGatewayService paymentGatewayService) {
		this.paymentGatewayService = paymentGatewayService;
	}

	public String createOrder() {

//		System.out.println("Calling Payment Gateway from Order Service : " + LocalDateTime.now() + " "
//				+ Thread.currentThread().getName());

		try {

			String paymentResponse = paymentGatewayService.makePayment().get();
			
//			System.out.println("Got Responce "+paymentResponse);

			return "Order created. " + paymentResponse;

		}

		/*
		 * ThreadPool Bulkhead / asynchronous exceptions can be wrapped inside
		 * ExecutionException.
		 */
		catch (ExecutionException e) {

			Throwable cause = e.getCause();

			/*
			 * 1. Bulkhead is full
			 */
			if (cause instanceof BulkheadFullException) {

				throw new PaymentServiceUnavailableException(
						"Payment service is busy. Please try again later. " + "Bulkhead Pattern", cause);
			}

			/*
			 * 2. Circuit Breaker is OPEN
			 */
			if (cause instanceof CallNotPermittedException) {

				throw new PaymentServiceUnavailableException(
						"Payment service is currently unavailable. " + "Circuit Breaker is OPEN.", cause);
			}

			/*
			 * 3. RateLimiter rejected the request
			 */
			if (cause instanceof RequestNotPermitted) {

				throw new TooManyRequestsException("Too many payment requests. Please try again later.", cause);
			}

			/*
			 * 4. Our own application exception
			 */
			if (cause instanceof PaymentServiceUnavailableException paymentException) {

				throw paymentException;
			}

			/*
			 * 5. Unknown failure
			 */
			throw new PaymentServiceUnavailableException("Payment service failed unexpectedly.", cause);
		}

		/*
		 * The thread waiting on CompletableFuture was interrupted.
		 */
		catch (InterruptedException e) {

			Thread.currentThread().interrupt();

			throw new PaymentServiceUnavailableException("Payment service request was interrupted.", e);
		}
	}
}