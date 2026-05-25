package com.sandbox.controller;

import com.sandbox.TestGrpc;
import com.sandbox.TestReply;
import com.sandbox.TestRequest;
import com.sandbox.service.TestService;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@GrpcService
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TestGrpcController implements TestGrpc {

    TestService testService;

    @Override
    public Uni<TestReply> test(TestRequest request) {
        log.info("gRPC Test called with name={}", request.getName());
        return testService.test(request);
    }
}
