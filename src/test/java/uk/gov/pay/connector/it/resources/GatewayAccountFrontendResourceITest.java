package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.jayway.restassured.response.Response;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.jayway.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class GatewayAccountFrontendResourceITest extends GatewayAccountResourceTestBase {

    static public class GatewayAccountPayload {
        String userName;
        String password;
        String merchantId;
        String serviceName;

        static public GatewayAccountPayload createDefault() {
            return new GatewayAccountPayload()
                    .withUsername("a-username")
                    .withPassword("a-password")
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
            HashMap<String, String> credentials = new HashMap();

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

        public Map<String, Object> buildCredentialsPayload() {
            return ImmutableMap.of("credentials", getCredentials());
        }

        public Map buildServiceNamePayload() {
            return ImmutableMap.of("service_name", serviceName);
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getPassword() {
            return password;
        }

        public String getUserName() {
            return userName;
        }

        public String getMerchantId() {
            return merchantId;
        }

    }

    private Gson gson = new Gson();

    @Test
    public void shouldGetGatewayAccountForExistingAccount() {
        String accountId = createAGatewayAccountFor("worldpay");
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        app.getDatabaseTestHelper().updateCredentialsFor(accountId, gson.toJson(gatewayAccountPayload.getCredentials()));
        app.getDatabaseTestHelper().updateServiceNameFor(accountId, gatewayAccountPayload.getServiceName());

        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_URL + accountId)
                .then()
                .statusCode(200)
                .body("payment_provider", is("worldpay"))
                .body("gateway_account_id", is(Integer.parseInt(accountId)))
                .body("credentials.username", is(gatewayAccountPayload.getUserName()))
                .body("credentials.password", is(nullValue()))
                .body("credentials.merchant_id", is(gatewayAccountPayload.getMerchantId()))
                .body("service_name", is(gatewayAccountPayload.getServiceName()));
    }

    @Test
    public void shouldReturn404IfGatewayAccountDoesNotExist() {
        String nonExistingGatewayAccount = "12345";
        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_URL + nonExistingGatewayAccount)
                .then()
                .statusCode(404)
                .body("message", is("Account with id '12345' not found"));

    }

    @Test
    public void shouldReturn404IfGatewayAccountIsNotNumeric() {
        String nonNumericGatewayAccount = "ABC";
        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_URL + nonNumericGatewayAccount)
                .then()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    // updateGatewayAccountCredentials

    @Test
    public void shouldUpdateGatewayAccountCredentialsForAWorldpayAccountSuccessfully() {
        String accountId = createAGatewayAccountFor("worldpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault()
                .withMerchantId("a-merchant-id");

        updateGatewayAccountCredentialsWith(accountId, gatewayAccountPayload.buildCredentialsPayload())
                .then()
                .statusCode(200);

        Map<String, String> currentCredentials = app.getDatabaseTestHelper().getAccountCredentials(Long.valueOf(accountId));
        assertThat(currentCredentials, is(gatewayAccountPayload.getCredentials()));
    }

    @Test
    public void shouldUpdateGatewayAccountCredentialsForASmartpayAccountSuccessfully() {
        String accountId = createAGatewayAccountFor("smartpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault();

        updateGatewayAccountCredentialsWith(accountId, gatewayAccountPayload.buildCredentialsPayload())
                .then()
                .statusCode(200);

        Map<String, String> currentCredentials = app.getDatabaseTestHelper().getAccountCredentials(Long.valueOf(accountId));
        assertThat(currentCredentials, is(gatewayAccountPayload.getCredentials()));
    }

    @Test
    public void shouldUpdateGatewayAccountCredentialsWithCharactersInUserNamesAndPassword() throws Exception {
        String accountId = createAGatewayAccountFor("smartpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault()
                .withUsername("someone@some{[]where&^%>?\\/")
                .withPassword("56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w");

        updateGatewayAccountCredentialsWith(accountId, gatewayAccountPayload.buildCredentialsPayload())
                .then()
                .statusCode(200);

        Map<String, String> currentCredentials = app.getDatabaseTestHelper().getAccountCredentials(Long.valueOf(accountId));
        assertThat(currentCredentials, is(gatewayAccountPayload.getCredentials()));
    }

    @Test
    public void shouldNotUpdateGatewayAccountCredentialsIfMissingCredentials() {
        String accountId = createAGatewayAccountFor("worldpay");

        updateGatewayAccountCredentialsWith(accountId, new HashMap<>())
                .then()
                .statusCode(400)
                .body("message", is("Field(s) missing: [credentials]"));
    }

    @Test
    public void shouldNotUpdateGatewayAccountCredentialsIfAccountWith2RequiredCredentialsMisses1Credential() {
        String accountId = createAGatewayAccountFor("smartpay");

        Map<String, Object> credentials = new GatewayAccountPayload()
                .withUsername("a-username")
                .withServiceName("a-service-name")
                .buildCredentialsPayload();

        updateGatewayAccountCredentialsWith(accountId, credentials)
                .then()
                .statusCode(400)
                .body("message", is("Field(s) missing: [password]"));
    }

    @Test
    public void shouldNotUpdateGatewayAccountCredentialsIfAccountWith3RequiredCredentialsMisses1Credential() {
        String accountId = createAGatewayAccountFor("worldpay");

        Map<String, Object> credentials = GatewayAccountPayload.createDefault().buildCredentialsPayload();

        updateGatewayAccountCredentialsWith(accountId, credentials)
                .then()
                .statusCode(400)
                .body("message", is("Field(s) missing: [merchant_id]"));
    }

    @Test
    public void shouldNotUpdateGatewayAccountCredentialsIfAccountWith3RequiredCredentialsMisses2Credentials() {
        String accountId = createAGatewayAccountFor("worldpay");

        Map<String, Object> credentials = new GatewayAccountPayload()
                .withUsername("a-username")
                .withServiceName("a-service-name")
                .buildCredentialsPayload();

        updateGatewayAccountCredentialsWith(accountId, credentials)
                .then()
                .statusCode(400)
                .body("message", is("Field(s) missing: [password, merchant_id]"));
    }

    @Test
    public void shouldNotUpdateGatewayAccountCredentialsIfAccountIdIsNotNumeric() {
        Map<String, Object> credentials = GatewayAccountPayload.createDefault().buildCredentialsPayload();
        updateGatewayAccountCredentialsWith("NO_NUMERIC_ACCOUNT_ID", credentials)
                .then()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldNotUpdateGatewayAccountCredentialsIfAccountIdDoesNotExist() {
        String nonExistingAccountId = "111111111";
        createAGatewayAccountFor("smartpay");

        Map<String, Object> credentials = GatewayAccountPayload.createDefault().buildCredentialsPayload();
        updateGatewayAccountCredentialsWith(nonExistingAccountId, credentials)
                .then()
                .statusCode(404)
                .body("message", is("The gateway account id '111111111' does not exist"));
    }

    // updateGatewayAccountServiceName

    @Test
    public void shouldUpdateGatewayAccountServiceNameSuccessfully() {
        String accountId = createAGatewayAccountFor("smartpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault();

        givenSetup().accept(JSON)
                .body(gatewayAccountPayload.buildServiceNamePayload())
                .put(ACCOUNTS_FRONTEND_URL + accountId + "/servicename")
                .then()
                .statusCode(200);

        String currentServiceName = app.getDatabaseTestHelper().getAccountServiceName(Long.valueOf(accountId));
        assertThat(currentServiceName, is(gatewayAccountPayload.getServiceName()));
    }

    @Test
    public void shouldNotUpdateGatewayAccountServiceNameIfMissingServiceName() {
        String accountId = createAGatewayAccountFor("worldpay");

        updateGatewayAccountServiceNameWith(accountId, new HashMap<>())
                .then()
                .statusCode(400)
                .body("message", is("Field(s) missing: [service_name]"));
    }

    @Test
    public void shouldFailUpdatingIfInvalidServiceNameLength() {
        String accountId = createAGatewayAccountFor("worldpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault()
                .withServiceName("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

        updateGatewayAccountServiceNameWith(accountId, gatewayAccountPayload.buildServiceNamePayload())
                .then()
                .statusCode(400)
                .body("message", is("Field(s) are too big: [service_name]"));
    }

    @Test
    public void shouldNotUpdateGatewayAccountServiceNameIfAccountIdIsNotNumeric() {
        Map<String, String> serviceNamePayload = GatewayAccountPayload.createDefault().buildServiceNamePayload();
        updateGatewayAccountServiceNameWith("NO_NUMERIC_ACCOUNT_ID", serviceNamePayload)
                .then()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldNotUpdateGatewayAccountServiceNameIfAccountIdDoesNotExist() {
        String nonExistingAccountId = "111111111";
        createAGatewayAccountFor("smartpay");

        Map<String, String> serviceNamePayload = GatewayAccountPayload.createDefault().buildServiceNamePayload();
        updateGatewayAccountServiceNameWith(nonExistingAccountId, serviceNamePayload)
                .then()
                .statusCode(404)
                .body("message", is("The gateway account id '111111111' does not exist"));
    }

    private Response updateGatewayAccountCredentialsWith(String accountId, Map<String, Object> credentials) {
        return givenSetup().accept(JSON)
                .body(credentials)
                .put(ACCOUNTS_FRONTEND_URL + accountId + "/credentials");
    }

    private Response updateGatewayAccountServiceNameWith(String accountId, Map<String, String> serviceName) {
        return givenSetup().accept(JSON)
                .body(serviceName)
                .put(ACCOUNTS_FRONTEND_URL + accountId + "/servicename");
    }
}
