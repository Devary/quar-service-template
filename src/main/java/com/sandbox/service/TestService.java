package com.sandbox.service;

import com.sandbox.TestGrpc;
import com.sandbox.TestReply;
import com.sandbox.TestRequest;
import io.smallrye.mutiny.Uni;

public class TestService implements TestGrpc {

    @Override
    public Uni<TestReply> test(TestRequest request) {
        String name = request.getName();
        return Uni.createFrom().item("Hello " + name)
                .map(res -> TestReply.newBuilder().setMessage(res).build());
    }
}
