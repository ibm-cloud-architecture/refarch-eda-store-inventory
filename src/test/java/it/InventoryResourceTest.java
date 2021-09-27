package it;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class InventoryResourceTest {

    @Test
    public void testInventoryStoreEndpoint() {
        given()
          .when().get("/api/v1/stores/store_1")
          .then()
             .statusCode(200)
             .body("storeName", is("store_1")).extract().response();
    }

}