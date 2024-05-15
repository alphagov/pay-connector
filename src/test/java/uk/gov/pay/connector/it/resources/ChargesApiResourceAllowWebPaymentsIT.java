package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.it.util.ChargeUtils.createChargePostBody;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;

public class ChargesApiResourceAllowWebPaymentsIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = AppWithPostgresAndSqsExtension.withPersistence();

    
    private static ObjectMapper objectMapper = new ObjectMapper();

    private DatabaseFixtures.TestAccount testAccount;
    private Long accountId;
    private String chargeId;
    private Long credentialsId;
    
    @BeforeEach
    void setupGateway() {
        testAccount = addGatewayAccountAndCredential("worldpay");
        accountId = testAccount.getAccountId();
        chargeId = createCharge(app.getLocalPort(), accountId.toString());

        credentialsId = testAccount.getCredentials().get(0).getId();
    }

    @Test
    void assertApplePayPermission() throws JsonProcessingException {
        assertAppleAndGooglePayAreDisabledByDefault();

        allowWebPaymentsOnGatewayAccount("allow_apple_pay");

        given().port(app.getLocalPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allow_apple_pay", is(true));
    }

    @ParameterizedTest()
    @MethodSource("permissions")
    void assertGooglePayPermission(boolean setAllowGooglePayFlag, boolean setGatewayMerchantId, boolean isGooglePayAllowed) throws JsonProcessingException {
        assertAppleAndGooglePayAreDisabledByDefault();

        if (setAllowGooglePayFlag) {
            allowWebPaymentsOnGatewayAccount("allow_google_pay");
        }
        
        if (setGatewayMerchantId) {
            addGatewayMerchantIdToGatewayAccount();
        }

        given().port(app.getLocalPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allow_google_pay", is(isGooglePayAllowed));
    }

    private void assertAppleAndGooglePayAreDisabledByDefault() {
        given().port(app.getLocalPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allow_apple_pay", is(false))
                .body("gateway_account.allow_google_pay", is(false));
    }

    private void allowWebPaymentsOnGatewayAccount(String path) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", path,
                "value", true));

        given().port(app.getLocalPort()).contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(HttpStatus.SC_OK);
    }
    
    private void addGatewayMerchantIdToGatewayAccount() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(
                List.of(Map.of("op", "replace",
                "path", "credentials/gateway_merchant_id",
                "value", "94b53bf6b12b6c5")));

        given().port(app.getLocalPort()).contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId + "/credentials/" + credentialsId)
                .then()
                .statusCode(HttpStatus.SC_OK);
    }
    
    private String createCharge(int port, String accountId) {
        return given().port(port).contentType(JSON)
                .contentType(JSON)
                .body(createChargePostBody(accountId))
                .post(format("/v1/api/accounts/%s/charges", accountId))
                .then()
                .extract()
                .path("charge_id");
    }

    private DatabaseFixtures.TestAccount addGatewayAccountAndCredential(String paymentProvider) {
        long accountId = nextLong(2, 10000);
        LocalDateTime createdDate = LocalDate.parse("2021-01-01").atStartOfDay();
        LocalDateTime activeStartDate = LocalDate.parse("2021-02-01").atStartOfDay();
        LocalDateTime activeEndDate = LocalDate.parse("2021-03-01").atStartOfDay();

        AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                .withGatewayAccountId(accountId)
                .withPaymentProvider(paymentProvider)
                .withCreatedDate(createdDate.toInstant(ZoneOffset.UTC))
                .withActiveStartDate(activeStartDate.toInstant(ZoneOffset.UTC))
                .withActiveEndDate(activeEndDate.toInstant(ZoneOffset.UTC))
                .withState(GatewayAccountCredentialState.ACTIVE)
                .withCredentials(Map.of(
                                ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                        CREDENTIALS_MERCHANT_CODE, "a-merchant-code",
                                        CREDENTIALS_USERNAME, "a-username",
                                        CREDENTIALS_PASSWORD, "a-password")))
                .build();

        return app.getDatabaseFixtures().aTestAccount().withPaymentProvider(paymentProvider)
                .withIntegrationVersion3ds(2)
                .withAccountId(accountId)
                .withGatewayAccountCredentials(Collections.singletonList(credentialsParams))
                .insert();
    }

    static Stream<Arguments> permissions() {
        return Stream.of(
                Arguments.of(true, false, false ),
                Arguments.of(true, true, true),
                Arguments.of(false, true, false)
        );
    }
}
