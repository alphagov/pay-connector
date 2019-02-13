package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.After;
import org.junit.Before;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
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

public class GatewayAccountResourceTestBase {

    public static final String ACCOUNTS_API_URL = "/v1/api/accounts/";
    public static final String ACCOUNTS_FRONTEND_URL = "/v1/frontend/accounts/";

    @DropwizardTestContext
    protected TestContext testContext;
    protected DatabaseTestHelper databaseTestHelper;
    protected DatabaseFixtures databaseFixtures;

    @Before
    public void setUp() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
    }

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }

    protected RequestSpecification givenSetup() {
        return given().port(testContext.getPort())
                .contentType(JSON);
    }

    public static String extractGatewayAccountId(ValidatableResponse validatableResponse) {
        return validatableResponse.extract().path("gateway_account_id");
    }
    
    public static ValidatableResponse createAGatewayAccountFor(int port, String testProvider) {
        return createAGatewayAccountFor(port, testProvider, null, null);
    }

    public static ValidatableResponse createAGatewayAccountFor(int port, String testProvider, String description, String analyticsId) {
        return createAGatewayAccountFor(port, testProvider, description, analyticsId, null);
    }

    public static ValidatableResponse createAGatewayAccountFor(int port, String testProvider, String description, String analyticsId, String requires_3ds) {
        Map<String, String> payload = Maps.newHashMap();
        payload.put("payment_provider", testProvider);
        if (description != null) {
            payload.put("description", description);
        }
        if (analyticsId != null) {
            payload.put("analytics_id", analyticsId);
        }
        if (requires_3ds != null) {
            payload.put("requires_3ds", requires_3ds);
        }
        return given().port(port)
                .contentType(JSON)
                .body(toJson(payload))
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON);
    }

    public static void assertGettingAccountReturnsProviderName(int port, ValidatableResponse response, String providerName, GatewayAccountEntity.Type providerUrlType) {
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
    
    public static void assertCorrectCreateResponse(ValidatableResponse response, GatewayAccountEntity.Type type, String description, String analyticsId, String name) {
        String accountId = response.extract().path("gateway_account_id");
        String urlSlug = "api/accounts/" + accountId;

        response.header("Location", containsString(urlSlug))
                .body("gateway_account_id", containsString(accountId))
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

        public Map<String, String> getCredentials() {
            HashMap<String, String> credentials = new HashMap<>();

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

        Map<String, Object> buildCredentialsPayload() {
            return ImmutableMap.of("credentials", getCredentials());
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
}
