package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountResourceITHelpers {
     
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private final int appLocalPort;

    public GatewayAccountResourceITHelpers(int appLocalPort) {
        this.appLocalPort = appLocalPort;
    }

    public static final String ACCOUNTS_API_URL = "/v1/api/accounts/";
    public static final String ACCOUNTS_API_SERVICE_ID_URL = "/v1/api/service/{serviceId}/account/{accountType}";
    public static final String ACCOUNTS_FRONTEND_URL = "/v1/frontend/accounts/";
    public static final String ACCOUNT_FRONTEND_EXTERNAL_ID_URL = "/v1/frontend/accounts/external-id/";
    
    protected String createGatewayAccount(Map<String, String> createGatewayAccountPayload) {
        return app.givenSetup()
                .body(toJson(createGatewayAccountPayload))
                .post(ACCOUNTS_API_URL)
                .then()
                .extract().path("gateway_account_id");
    }
    
    void updateGatewayAccount(String gatewayAccountId, String path, Object value) {
        given()
                .port(appLocalPort)
                .contentType(JSON)
                .body(Map.of("path", path,
                        "op", "replace",
                        "value", value))
                .patch(ACCOUNTS_API_URL + gatewayAccountId)
                .then()
                .statusCode(200);
    }

    public static void assertCorrectCreateResponse(ValidatableResponse response, GatewayAccountType type, String description, String analyticsId, String name) {
        String accountId = response.extract().path("gateway_account_id");
        String urlSlug = "api/accounts/" + accountId;

        response.header("Location", containsString(urlSlug))
                .body("gateway_account_id", containsString(accountId))
                .body("external_id", is(notNullValue()))
                .body("type", is(type.toString()))
                .body("description", is(description))
                .body("service_name", is(name))
                .body("analytics_id", is(analyticsId))
                .body("corporate_credit_card_surcharge_amount", is(nullValue()))
                .body("corporate_debit_card_surcharge_amount", is(nullValue()))
                .body("links[0].href", containsString(urlSlug))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"));
    }
    
    public static class CreateGatewayAccountPayloadBuilder {
        private Map<String, String> payload = new HashMap<String, String> (
                Map.of(
                "payment_provider", "sandbox",
                "service_id", "a-valid-service-id")
        );
        
        public static CreateGatewayAccountPayloadBuilder aCreateGatewayAccountPayloadBuilder() {
            return new CreateGatewayAccountPayloadBuilder();
        }
        
        public Map<String, String> build() {
            return Map.copyOf(payload);
        }

        public CreateGatewayAccountPayloadBuilder withServiceId(String serviceId) {
            payload.put("service_id", serviceId);
            return this;
        }

        public CreateGatewayAccountPayloadBuilder withProvider(String provider) {
            payload.put("payment_provider", provider);
            return this;
        }

        public CreateGatewayAccountPayloadBuilder withDescription(String description) {
            payload.put("description", description);
            return this;
        }

        public CreateGatewayAccountPayloadBuilder withAnalyticsId(String analyticsId) {
            payload.put("analytics_id", analyticsId);
            return this;
        }

        public CreateGatewayAccountPayloadBuilder withRequires3ds(boolean requires3ds) {
            payload.put("requires_3ds", String.valueOf(requires3ds));
            return this;
        }

        public CreateGatewayAccountPayloadBuilder withAllowApplePay(boolean allowApplePay) {
            payload.put("allow_apple_pay", String.valueOf(allowApplePay));
            return this;
        }

        public CreateGatewayAccountPayloadBuilder withAllowGooglePay(boolean allowGooglePay) {
            payload.put("allow_google_pay", String.valueOf(allowGooglePay));
            return this;
        }

        public CreateGatewayAccountPayloadBuilder withType(GatewayAccountType type) {
            payload.put("type", type.name());
            return this;
        }
    }
    
}
