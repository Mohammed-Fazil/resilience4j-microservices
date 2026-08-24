package com.ecommerce.orderservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Profile("qa")
public class QA {
	
	@Value("${message}")
	String msg;
	
	@PostConstruct
	public void init()
	{
		System.out.println(toString());
	}

	@Override
	public String toString() {
		return "QA [msg=" + msg + "]";
	}

}
