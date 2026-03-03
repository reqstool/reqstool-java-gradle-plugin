package io.github.reqstool.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.reqstool.annotations.SVCs;

class HelloTest {

    @Test
    @SVCs("SVC_001")
    void testHello() {
        assertEquals("hello", new Hello().hello());
    }
}
