package com.ecommerce.orderservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
@Component
public class Person {
	@Value("${name}")
	String name;
	@Value("${age}")
	String age;
	@Value("${profile}")
	String pro;
	
	
	@PostConstruct
	public void hello()
	{
		System.out.println(toString());
	}


	@Override
	public String toString() {
		return "Person [name=" + name + ", age=" + age + ", pro=" + pro + "]";
	}

}
