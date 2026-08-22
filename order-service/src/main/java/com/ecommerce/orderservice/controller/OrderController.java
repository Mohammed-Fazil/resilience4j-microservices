package com.ecommerce.orderservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.orderservice.service.OrderService;

;

@RestController
@RequestMapping("/orders")
public class OrderController {

	private final OrderService orderService;

	int c = 0;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public String createOrder() {

//		System.out.println("\n Order Srevice " + ++c + "\n");

		return orderService.createOrder();
	}

	@GetMapping
	public String hello() {
		return "Hello";
	}

}