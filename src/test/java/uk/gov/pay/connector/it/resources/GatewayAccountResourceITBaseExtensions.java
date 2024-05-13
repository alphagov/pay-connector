package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountResourceITBaseExtensions {
    private final String paymentProvider;
    private final int appLocalPort;

    public GatewayAccountResourceITBaseExtensions(String paymentProvider, int appLocalPort) {
        this.paymentProvider = paymentProvider;
        this.appLocalPort = appLocalPort;
    }

    public static final String ACCOUNTS_API_URL = "/v1/api/accounts/";
    public static final String ACCOUNTS_API_SERVICE_ID_URL = "/v1/api/service/{serviceId}/{accountType}/account";
    public static final String ACCOUNTS_FRONTEND_URL = "/v1/frontend/accounts/";
    public static final String ACCOUNT_FRONTEND_EXTERNAL_ID_URL = "/v1/frontend/accounts/external-id/";

    protected String createAGatewayAccountFor(String provider) {
        return extractGatewayAccountId(createAGatewayAccountFor(appLocalPort, provider));
    }

    protected String createAGatewayAccountFor(String provider, String desc, String analyticsId) {
        return extractGatewayAccountId(createAGatewayAccountFor(appLocalPort, provider, desc, analyticsId));
    }

    protected String createAGatewayAccountFor(String provider, String description, String analyticsId, String requires3ds, String type) {
        return extractGatewayAccountId(createAGatewayAccountFor(appLocalPort, provider, description, analyticsId, requires3ds, type, null));
    }

    public static String extractGatewayAccountId(ValidatableResponse validatableResponse) {
        return validatableResponse.extract().path("gateway_account_id");
    }

    public static ValidatableResponse createAGatewayAccountFor(int port, String testProvider) {
        return createAGatewayAccountFor(port, testProvider, null, null);
    }

    public static ValidatableResponse createAGatewayAccountFor(int port, String testProvider, String description, String analyticsId) {
        return createAGatewayAccountFor(port, testProvider, description, analyticsId, null, "test", null);
    }

    public static RequestSpecification createAGatewayAccountRequestSpecificationFor(int port, String testProvider, String description, String analyticsId, String serviceId) {
        return getRequestSpecification(port, testProvider, description, analyticsId, null, "test", serviceId);
    }

    protected String createAGatewayAccountWithServiceId(String serviceId) {
        return extractGatewayAccountId(createAGatewayAccountFor(appLocalPort, "sandbox", "description", "analytics-id", "", "test", serviceId));
    }

    protected String createAGatewayAccountWithServiceId(String serviceId, String testProvider) {
        return extractGatewayAccountId(createAGatewayAccountFor(appLocalPort, testProvider, "description", "analytics-id", "", "test", serviceId));
    }

    public static ValidatableResponse createAGatewayAccountFor(int port, String testProvider, String description, String analyticsId, String requires3ds, String type, String serviceId) {
        RequestSpecification requestSpecification = getRequestSpecification(port, testProvider, description, analyticsId, requires3ds, type, serviceId);
        return requestSpecification
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON);
    }

    private static RequestSpecification getRequestSpecification(int port, String testProvider, String description, String analyticsId, String requires3ds, String type, String serviceId) {
        Map<String, String> payload = Maps.newHashMap();
        payload.put("payment_provider", testProvider);
        if (description != null) {
            payload.put("description", description);
        }
        if (analyticsId != null) {
            payload.put("analytics_id", analyticsId);
        }
        if (requires3ds != null) {
            payload.put("requires_3ds", requires3ds);
        }
        if (type != null) {
            payload.put("type", type);
        }
        if (serviceId != null) {
            payload.put("service_id", serviceId);
        }
        RequestSpecification requestSpecification = given().port(port)
                .contentType(JSON)
                .body(toJson(payload));
        return requestSpecification;
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

    public static void assertGettingAccountReturnsProviderName(int port, ValidatableResponse response, String providerName, GatewayAccountType providerUrlType) {
        given().port(port)
                .contentType(JSON)
                .get(response.extract().header("Location").replace("https", "http")) //Scheme on links back are forced to be https
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("payment_provider", is(providerName))
                .body("gateway_account_id", is(notNullValue()))
                .body("type", is(providerUrlType.toString()));
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

    public static class GatewayAccountPayload {
        String userName;
        String password;
        String merchantId;
        String serviceName;

        static GatewayAccountPayload createDefault() {
            return new GatewayAccountPayload()
                    .withUsername("a-username")
                    .withPassword("a-password")
                    .withMerchantId("a-merchant-id")
                    .withServiceName("a-service-name");
        }

        public GatewayAccountPayload withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public GatewayAccountPayload withUsername(String userName) {
            this.userName = userName;
            return this;
        }

        public GatewayAccountPayload withPassword(String password) {
            this.password = password;
            return this;
        }

        public GatewayAccountPayload withMerchantId(String merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public Map<String, Object> getCredentials() {
            HashMap<String, Object> credentials = new HashMap<>();

            if (this.userName != null) {
                credentials.put("username", userName);
            }

            if (this.password != null) {
                credentials.put("password", password);
            }

            if (this.merchantId != null) {
                credentials.put("merchant_id", merchantId);
            }

            return credentials;
        }

        Map buildServiceNamePayload() {
            return ImmutableMap.of("service_name", serviceName);
        }

        String getServiceName() {
            return serviceName;
        }

        String getPassword() {
            return password;
        }

        String getUserName() {
            return userName;
        }

        String getMerchantId() {
            return merchantId;
        }

    }

    public String getPaymentProvider() {
        return paymentProvider;
    }
}
