package io.github.reqstool.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.reqstool.annotations.SVCs;

class HelloIT {

	@Test
	@SVCs("SVC_002")
	void testHelloIntegration() {
		assertEquals("hello", new Hello().hello());
	}

}
