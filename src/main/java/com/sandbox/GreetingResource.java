package com.sandbox;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/hello")
@Slf4j
@Tag(name = "Greeting", description = "Operations about greetings")
public class GreetingResource {

    @ConfigProperty(name = "fakher")
    String testValue;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Returns a greeting message", description = "Provides a friendly greeting for the caller.")
    public String hello() {
        log.info("GET /hello called");
        return "Hello from Quarkus REST";
    }

    @GET
    @Path("/test")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Returns the configured test value", description = "Lets you verify the Vault-backed 'test' property is being consumed by the service.")
    public String testValue() {
        log.info("GET /hello/test called; Vault-backed value present={}", testValue != null && !testValue.isBlank());
        return testValue;
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Returns a greeting message with a name", description = "Provides a friendly greeting for the caller with his name.")
    public String helloAgain(@RequestBody String name) {
        log.info("POST /hello called with name={}", name);
        return "Hello " + name + " from Quarkus REST";
    }
}
