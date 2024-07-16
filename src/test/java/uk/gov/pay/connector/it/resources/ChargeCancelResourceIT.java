package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.AddChargeParams;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.AddGatewayAccountParams;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

public class ChargeCancelResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app.getLocalPort(), app.getDatabaseTestHelper());
    
    public static Stream<Arguments> cancellableChargeStatesPriorToAuthorisation() {
        return Stream.of(
                Arguments.of(ChargeStatus.CREATED),
                Arguments.of(ChargeStatus.ENTERING_CARD_DETAILS)
        );
    }

    @Nested
    class ByGatewayAccountId {
        @Test
        public void shouldPreserveCardDetailsIfCancelled() {
            String externalChargeId = createNewInPastChargeWithStatus(AUTHORISATION_SUCCESS);
            Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge"));
    
            app.getWorldpayMockClient().mockCancelSuccess();
    
            Map<String, Object> cardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
            assertThat(cardDetails.isEmpty(), is(false));
    
            testBaseExtension.cancelChargeAndCheckApiStatus(externalChargeId, SYSTEM_CANCELLED, 204);
    
            cardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
            assertThat(cardDetails, is(notNullValue()));
            assertThat(cardDetails.get("card_brand"), is(notNullValue()));
            assertThat(cardDetails.get("last_digits_card_number"), is(notNullValue()));
            assertThat(cardDetails.get("first_digits_card_number"), is(notNullValue()));
            assertThat(cardDetails.get("expiry_date"), is(notNullValue()));
            assertThat(cardDetails.get("cardholder_name"), is(notNullValue()));
            assertThat(cardDetails.get("address_line1"), is(notNullValue()));
            assertThat(cardDetails.get("address_line2"), is(notNullValue()));
            assertThat(cardDetails.get("address_postcode"), is(notNullValue()));
            assertThat(cardDetails.get("address_country"), is(notNullValue()));
        }
    
        @Test
        public void shouldRespondWith204WithLockingStatus_IfCancelledAfterAuth() {
            String chargeId = createNewInPastChargeWithStatus(AUTHORISATION_SUCCESS);
            app.getWorldpayMockClient().mockCancelSuccess();
    
            testBaseExtension.cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCELLED, 204);
    
            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
            assertThat(events.size(), is(3));
            assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                    SYSTEM_CANCEL_READY.getValue(),
                    SYSTEM_CANCELLED.getValue()));
        }
    
        @Test
        public void shouldRespondWith204WithLockingStatus_IfCancelFailedAfterAuth() {
            String chargeId = createNewInPastChargeWithStatus(AUTHORISATION_SUCCESS);
            app.getWorldpayMockClient().mockCancelError();
    
            testBaseExtension.cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCEL_ERROR, 204);
    
            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
            assertThat(events.size(), is(3));
            assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                    SYSTEM_CANCEL_READY.getValue(),
                    SYSTEM_CANCEL_ERROR.getValue()));
        }
    
        @ParameterizedTest()
        @MethodSource("uk.gov.pay.connector.it.resources.ChargeCancelResourceIT#cancellableChargeStatesPriorToAuthorisation")
        public void shouldRespond204WithNoLockingEvent_IfCancelledBeforeAuth(ChargeStatus status) {
            String chargeId = createNewInPastChargeWithStatus(status);
            testBaseExtension.cancelChargeAndCheckApiStatus(chargeId, ChargeStatus.SYSTEM_CANCELLED, 204);
    
            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
            assertThat(events.size(), is(2));
            assertThat(events, hasItems(status.getValue(), ChargeStatus.SYSTEM_CANCELLED.getValue()));
        }
        
        private String createNewInPastChargeWithStatus(ChargeStatus status) {
            long chargeId = RandomUtils.nextInt();
            return testBaseExtension.addCharge(anAddChargeParameters().withChargeStatus(status)
                    .withCreatedDate(Instant.now().minus(1, HOURS))
                    .withChargeId(chargeId)
                    .withExternalChargeId("charge" + chargeId)
                    .build());
        }
    }
    
    @Nested
    class ByServiceIdAndAccountType {
        private String gatewayAccountId;
        private long gatewayAccountCredentialsId;
        private static String VALID_SERVICE_ID = "valid-service-id";
        @BeforeEach
        void setUpAccount() {
            gatewayAccountId = String.valueOf(RandomUtils.nextInt());
            gatewayAccountCredentialsId = RandomUtils.nextInt();
            AddGatewayAccountCredentialsParams credentials = anAddGatewayAccountCredentialsParams()
                    .withId(gatewayAccountCredentialsId)
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.ACTIVE)
                    .withGatewayAccountId(Long.parseLong(gatewayAccountId))
                    .withCredentials(Map.of("one_off_customer_initiated", Map.of("merchant_code", "acc123")))
                    .build();
            AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                    .withGatewayAccountCredentials(List.of(credentials))
                    .withAccountId(gatewayAccountId)
                    .withServiceId(VALID_SERVICE_ID)
                    .build();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);
        }

        @Test
        public void shouldPreserveCardDetailsIfCancelled() {
            String chargeExternalId = "test-charge-id";
            long chargeInternalId = RandomUtils.nextInt();
            
            AddChargeParams addChargeParams = anAddChargeParams()
                    .withChargeId(chargeInternalId)
                    .withGatewayAccountId(gatewayAccountId)
                    .withExternalChargeId(chargeExternalId)
                    .withStatus(AUTHORISATION_SUCCESS)
                    .withServiceId(VALID_SERVICE_ID)
                    .withPaymentProvider("worldpay")
                    .withGatewayCredentialId(gatewayAccountCredentialsId)
                    .build();
            app.getDatabaseTestHelper().addCharge(addChargeParams);
            app.getDatabaseTestHelper().updateChargeCardDetails(chargeInternalId, AuthCardDetailsFixture.anAuthCardDetails().withCardNo("12345678").build());
            app.getDatabaseTestHelper().addEvent(chargeInternalId, AUTHORISATION_SUCCESS.toString());
            
            app.getWorldpayMockClient().mockCancelSuccess();

            Map<String, Object> cardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeInternalId);
            assertThat(cardDetails.isEmpty(), is(false));

            app.givenSetup()
                    .body("")
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(204);

            checkCancelStatusViaApi(chargeExternalId, SYSTEM_CANCELLED);
            
            cardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeInternalId);
            assertThat(cardDetails, is(notNullValue()));
            assertThat(cardDetails.get("card_brand"), is(notNullValue()));
            assertThat(cardDetails.get("last_digits_card_number"), is(notNullValue()));
            assertThat(cardDetails.get("first_digits_card_number"), is(notNullValue()));
            assertThat(cardDetails.get("expiry_date"), is(notNullValue()));
            assertThat(cardDetails.get("cardholder_name"), is(notNullValue()));
            assertThat(cardDetails.get("address_line1"), is(notNullValue()));
            assertThat(cardDetails.get("address_line2"), is(notNullValue()));
            assertThat(cardDetails.get("address_postcode"), is(notNullValue()));
            assertThat(cardDetails.get("address_country"), is(notNullValue()));
        }
        
        
        @Test
        public void shouldRespondWith204WithLockingStatus_IfCancelledAfterAuth_byServiceId() {
            String chargeExternalId = "test-charge-id";
            long chargeInternalId = RandomUtils.nextInt();
    
            AddChargeParams addChargeParams = anAddChargeParams()
                    .withChargeId(chargeInternalId)
                    .withGatewayAccountId(gatewayAccountId)
                    .withExternalChargeId(chargeExternalId)
                    .withStatus(AUTHORISATION_SUCCESS)
                    .withServiceId(VALID_SERVICE_ID)
                    .withPaymentProvider("worldpay")
                    .withGatewayCredentialId(gatewayAccountCredentialsId)
                    .build();
            app.getDatabaseTestHelper().addCharge(addChargeParams);
            app.getDatabaseTestHelper().addEvent(chargeInternalId, AUTHORISATION_SUCCESS.toString());        
            
            app.getWorldpayMockClient().mockCancelSuccess();
    
            app.givenSetup()
                    .body("")
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(204);

            checkCancelStatusViaApi(chargeExternalId, SYSTEM_CANCELLED);
            
            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeExternalId);
            assertThat(events.size(), is(3));
            assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                    SYSTEM_CANCEL_READY.getValue(),
                    SYSTEM_CANCELLED.getValue()));
        }

        @Test
        public void shouldRespondWith204WithLockingStatus_IfCancelFailedAfterAuth() {
            String chargeExternalId = "test-charge-id";
            long chargeInternalId = RandomUtils.nextInt();

            AddChargeParams addChargeParams = anAddChargeParams()
                    .withChargeId(chargeInternalId)
                    .withGatewayAccountId(gatewayAccountId)
                    .withExternalChargeId(chargeExternalId)
                    .withStatus(AUTHORISATION_SUCCESS)
                    .withServiceId(VALID_SERVICE_ID)
                    .withPaymentProvider("worldpay")
                    .withGatewayCredentialId(gatewayAccountCredentialsId)
                    .build();
            app.getDatabaseTestHelper().addCharge(addChargeParams);
            app.getDatabaseTestHelper().addEvent(chargeInternalId, AUTHORISATION_SUCCESS.toString());
            
            app.getWorldpayMockClient().mockCancelError();

            app.givenSetup()
                    .body("")
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(204);

            checkCancelStatusViaApi(chargeExternalId, SYSTEM_CANCEL_ERROR);
            
            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeExternalId);
            assertThat(events.size(), is(3));
            assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                    SYSTEM_CANCEL_READY.getValue(),
                    SYSTEM_CANCEL_ERROR.getValue()));
        }

        @ParameterizedTest()
        @MethodSource("uk.gov.pay.connector.it.resources.ChargeCancelResourceIT#cancellableChargeStatesPriorToAuthorisation")
        public void shouldRespond204WithNoLockingEvent_IfCancelledBeforeAuth(ChargeStatus status) {
            String chargeExternalId = "test-charge-id";
            long chargeInternalId = RandomUtils.nextInt();

            AddChargeParams addChargeParams = anAddChargeParams()
                    .withChargeId(chargeInternalId)
                    .withGatewayAccountId(gatewayAccountId)
                    .withExternalChargeId(chargeExternalId)
                    .withStatus(status)
                    .withServiceId(VALID_SERVICE_ID)
                    .withPaymentProvider("worldpay")
                    .withGatewayCredentialId(gatewayAccountCredentialsId)
                    .build();
            app.getDatabaseTestHelper().addCharge(addChargeParams);
            app.getDatabaseTestHelper().addEvent(chargeInternalId, status.toString());

            app.givenSetup()
                    .body("")
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(204);

            checkCancelStatusViaApi(chargeExternalId, SYSTEM_CANCELLED);
            
            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeExternalId);
            assertThat(events.size(), is(2));
            assertThat(events, hasItems(status.getValue(), ChargeStatus.SYSTEM_CANCELLED.getValue()));
        }
        
        void checkCancelStatusViaApi(String chargeId, ChargeStatus targetState) {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeId))
                    .then()
                    .body("state.status", is("cancelled"))
                    .body("state.finished", is(true))
                    .body("state.message", is("Payment was cancelled by the service"))
                    .body("state.code", is("P0040"));

            app.givenSetup()
                    .get(format("/v1/frontend/charges/%s", chargeId))
                    .then()
                    .body("status", is(targetState.getValue()))
                    .body("state.status", is("cancelled"))
                    .body("state.finished", is(true))
                    .body("state.message", is("Payment was cancelled by the service"))
                    .body("state.code", is("P0040"));
        }
    }
}
