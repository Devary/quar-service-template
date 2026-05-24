package com.sandbox;

import io.quarkus.test.junit.QuarkusTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@Slf4j
class GreetingResourceTest {
    @Test
    void testHelloEndpoint() {
        log.info("Starting testHelloEndpoint");

        given()
          .when().get("/hello")
          .then()
             .statusCode(200)
             .body(is("Hello from Quarkus REST"));

        log.info("Finished testHelloEndpoint successfully");
    }

}