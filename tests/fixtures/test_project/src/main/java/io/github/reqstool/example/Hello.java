package io.github.reqstool.example;

import io.github.reqstool.annotations.Requirements;

public class Hello {

    @Requirements("REQ_001")
    public String hello() {
        return "hello";
    }
}
