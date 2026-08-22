package com.ecommerce.orderservice.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.ecommerce.orderservice.exception.PaymentServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

@Service
public class OrderService {

	private final PaymentGatewayService paymentGatewayService;

	public OrderService(PaymentGatewayService paymentGatewayService) {
		this.paymentGatewayService = paymentGatewayService;
	}

	public String createOrder() {

		

		try {

			String paymentResponse = paymentGatewayService.makePayment();

			return "Order created. " + paymentResponse;

		} catch (CallNotPermittedException e) {

			throw new PaymentServiceUnavailableException(
					"Payment service is currently unavailable. Please try again later.", e);

		} catch (Exception e) {

			throw new PaymentServiceUnavailableException(
					"Payment service is currently unavailable. Please try again later.", e);
		}
	}
}