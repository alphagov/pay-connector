package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.apache.commons.lang3.Strings.CS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.SERVICE_ID;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomInt;

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

    private static Stream<ChargeStatus> invalidChargeStatusesForChargeCreation() {
        return Stream.of(
                ChargeStatus.AUTHORISATION_REJECTED,
                ChargeStatus.AUTHORISATION_ERROR,
                ChargeStatus.CAPTURE_READY,
                ChargeStatus.CAPTURED,
                ChargeStatus.CAPTURE_SUBMITTED,
                ChargeStatus.CAPTURE_ERROR,
                ChargeStatus.EXPIRED,
                ChargeStatus.EXPIRE_CANCEL_FAILED,
                ChargeStatus.SYSTEM_CANCEL_ERROR,
                ChargeStatus.SYSTEM_CANCELLED,
                ChargeStatus.USER_CANCEL_READY,
                ChargeStatus.USER_CANCELLED,
                ChargeStatus.USER_CANCEL_ERROR
        );
    }

    @Nested
    class ByGatewayAccountId {
        @Test
        @DisplayName("Should return 204 when successful")
        void success_shouldReturn204() {
            var chargeId = createNewInPastChargeWithStatus(CREATED);
            app.givenSetup()

                    .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", testBaseExtension.getAccountId(), chargeId))
                    .then().statusCode(204);

            await()
                    .pollDelay(Duration.ofSeconds(1))
                    .atMost(Duration.ofSeconds(5)).until(() -> {
                        var chargeStatus = app.givenSetup()
                                .get(String.format("/v1/api/accounts/%s/charges/%s", testBaseExtension.getAccountId(), chargeId))
                                .then()
                                .extract().path("state.status");
                        assertThat(chargeStatus, is("cancelled"));
                        return true;
                    });
        }

        @Test
        @DisplayName("Should return 202 if cancel operation is already in progress")
        void success_shouldReturn202() {
            var cancelSubmittedChargeId = createNewInPastChargeWithStatus(SYSTEM_CANCEL_SUBMITTED);
            app.givenSetup()
                    .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", testBaseExtension.getAccountId(), cancelSubmittedChargeId))
                    .then().statusCode(202);
        }

        @ParameterizedTest()
        @MethodSource("uk.gov.pay.connector.it.resources.ChargeCancelResourceIT#invalidChargeStatusesForChargeCreation")
        @DisplayName("Should return 400 if charge is not in correct state")
        void badChargeState_shouldReturn400(ChargeStatus chargeStatus) {
            var badStateChargeId = createNewInPastChargeWithStatus(chargeStatus);
            app.givenSetup()
                    .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", testBaseExtension.getAccountId(), badStateChargeId))
                    .then().statusCode(400)
                    .body("message", contains(String.format("Charge not in correct state to be processed, %s", badStateChargeId)));
        }

        @Test
        @DisplayName("Should return 404 if charge is not found")
        void chargeNotFound_shouldReturn404() {
            app.givenSetup()
                    .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", testBaseExtension.getAccountId(), "not-a-real-charge-id"))
                    .then().statusCode(404)
                    .body("message", contains(String.format("Charge with id [%s] not found.", "not-a-real-charge-id")));
        }

        @Test
        @DisplayName("Should return 404 if account is not found")
        void accountNotFound_shouldReturn404() {
            var chargeId = createNewInPastChargeWithStatus(CREATED);
            app.givenSetup()
                    .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", MAX_VALUE, chargeId))
                    .then().statusCode(404)
                    .body("message", contains(String.format("Charge with id [%s] not found.", chargeId)));
        }

        @Test
        @DisplayName("Should return 404 if account is non numeric")
        void nonNumericAccount_shouldReturn404() {
            var chargeId = createNewInPastChargeWithStatus(CREATED);
            app.givenSetup()
                    .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", "invalid-account-id", chargeId))
                    .then().statusCode(404)
                    .body("message", is("HTTP 404 Not Found"));
        }

        @Test
        @DisplayName("Should preserve charge card details when charge is cancelled")
        void shouldPreserveCardDetailsIfCancelled() {
            String externalChargeId = createNewInPastChargeWithStatus(AUTHORISATION_SUCCESS);
            Long chargeId = Long.valueOf(CS.removeStart(externalChargeId, "charge"));

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
        @DisplayName("Should add locking status to charge events if charge is cancelled after auth success")
        void chargeEventsShouldHaveLockingStatus_IfCancelledAfterAuth() {
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
        @DisplayName("Should add locking status to charge events if charge is cancelled after auth failed")
        void chargeEventsShouldHaveLockingStatus_IfCancelFailedAfterAuth() {
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
        @DisplayName("Should not add locking status to charge events if charge is cancelled before auth")
        void chargeEventsShouldNotHaveLockingStatus_IfCancelledBeforeAuth(ChargeStatus status) {
            String chargeId = createNewInPastChargeWithStatus(status);
            testBaseExtension.cancelChargeAndCheckApiStatus(chargeId, ChargeStatus.SYSTEM_CANCELLED, 204);

            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
            assertThat(events.size(), is(2));
            assertThat(events, hasItems(status.getValue(), ChargeStatus.SYSTEM_CANCELLED.getValue()));
        }

        private String createNewInPastChargeWithStatus(ChargeStatus status) {
            long chargeId = secureRandomInt();
            return testBaseExtension.addCharge(anAddChargeParameters().withChargeStatus(status)
                    .withCreatedDate(Instant.now().minus(1, HOURS))
                    .withChargeId(chargeId)
                    .withExternalChargeId("charge" + chargeId)
                    .build());
        }
    }

    @Nested
    class ByServiceIdAndAccountType {
        @Test
        @DisplayName("Should return 204 when successful")
        void success_shouldReturn204() {
            String chargeExternalId = testBaseExtension.createNewChargeWithServiceId(SERVICE_ID);
            app.givenSetup()
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then().statusCode(204);

            await()
                    .pollDelay(Duration.ofSeconds(1))
                    .atMost(Duration.ofSeconds(5)).until(() -> {
                        var chargeStatus = app.givenSetup()
                                .get(String.format("/v1/api/accounts/%s/charges/%s", testBaseExtension.getAccountId(), chargeExternalId))
                                .then()
                                .extract().path("state.status");
                        assertThat(chargeStatus, is("cancelled"));
                        return true;
                    });
        }

        @Test
        @DisplayName("Should return 202 if cancel operation is already in progress")
        void success_shouldReturn202() {
            String cancelSubmittedChargeId = testBaseExtension.createNewChargeWithServiceIdAndStatus(SERVICE_ID, SYSTEM_CANCEL_SUBMITTED);
            app.givenSetup()
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GatewayAccountType.TEST, cancelSubmittedChargeId))
                    .then().statusCode(202);
        }

        @ParameterizedTest()
        @MethodSource("uk.gov.pay.connector.it.resources.ChargeCancelResourceIT#invalidChargeStatusesForChargeCreation")
        @DisplayName("Should return 400 if charge is not in correct state")
        void badChargeState_shouldReturn400(ChargeStatus chargeStatus) {
            String badStateChargeId = testBaseExtension.createNewChargeWithServiceIdAndStatus(SERVICE_ID, chargeStatus);
            app.givenSetup()
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GatewayAccountType.TEST, badStateChargeId))
                    .then().statusCode(400)
                    .body("message", contains(String.format("Charge not in correct state to be processed, %s", badStateChargeId)));
        }

        @Test
        @DisplayName("Should return 404 if charge is not found")
        void chargeNotFound_shouldReturn404() {
            app.givenSetup()
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GatewayAccountType.TEST, "not-a-real-charge-id"))
                    .then().statusCode(404)
                    .body("message", contains(String.format("Charge with id [%s] not found.", "not-a-real-charge-id")));
        }

        @Test
        @DisplayName("Should return 404 if account is not found")
        void accountNotFound_shouldReturn404() {
            var chargeId = testBaseExtension.createNewCharge();

            app.givenSetup()
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", "not-real-service-id", GatewayAccountType.TEST, chargeId))
                    .then().statusCode(404)
                    .body("message", contains(String.format("Charge with id [%s] not found.", chargeId)));
        }


        @Test
        @DisplayName("Should preserve charge card details when charge is cancelled")
        void shouldPreserveCardDetailsIfCancelled() {
            String chargeExternalId = testBaseExtension.authoriseNewChargeWithServiceId(SERVICE_ID);
            long chargeInternalId = app.getDatabaseTestHelper().getChargeIdByExternalId(chargeExternalId);
            app.getDatabaseTestHelper().addEvent(chargeInternalId, AUTHORISATION_SUCCESS.toString());

            app.getWorldpayMockClient().mockCancelSuccess();

            Map<String, Object> cardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeInternalId);
            assertThat(cardDetails.isEmpty(), is(false));

            app.givenSetup()
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
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
        @DisplayName("Should add locking status to charge events if charge is cancelled after auth success")
        void chargeEventsShouldHaveLockingStatus_IfCancelledAfterAuth() {
            String chargeExternalId = testBaseExtension.authoriseNewChargeWithServiceId(SERVICE_ID);
            long chargeInternalId = app.getDatabaseTestHelper().getChargeIdByExternalId(chargeExternalId);
            app.getDatabaseTestHelper().addEvent(chargeInternalId, AUTHORISATION_SUCCESS.toString());

            app.getWorldpayMockClient().mockCancelSuccess();

            app.givenSetup()
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
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
        @DisplayName("Should add locking status to charge events if charge is cancelled after auth failed")
        void chargeEventsShouldHaveLockingStatus_IfCancelFailedAfterAuth() {
            String chargeExternalId = testBaseExtension.authoriseNewChargeWithServiceId(SERVICE_ID);
            long chargeInternalId = app.getDatabaseTestHelper().getChargeIdByExternalId(chargeExternalId);
            app.getDatabaseTestHelper().addEvent(chargeInternalId, AUTHORISATION_SUCCESS.toString());

            app.getWorldpayMockClient().mockCancelError();

            app.givenSetup()
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(204);

            checkCancelStatusViaApi(chargeExternalId, SYSTEM_CANCEL_ERROR);

            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeExternalId);
            assertThat(events.size(), is(3));
            assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                    SYSTEM_CANCEL_READY.getValue(),
                    SYSTEM_CANCEL_ERROR.getValue()));
        }

        @Test
        @DisplayName("Should not add locking status to charge events if charge is cancelled in created state")
        void chargeEventsShouldNotHaveLockingStatus_IfCancelledFromCreatedState() {
            String chargeExternalId = testBaseExtension.createNewChargeWithServiceId(SERVICE_ID);
            long chargeInternalId = app.getDatabaseTestHelper().getChargeIdByExternalId(chargeExternalId);
            app.getDatabaseTestHelper().addEvent(chargeInternalId, CREATED.toString());

            app.givenSetup()
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(204);

            checkCancelStatusViaApi(chargeExternalId, SYSTEM_CANCELLED);

            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeExternalId);
            assertThat(events.size(), is(2));
            assertThat(events, hasItems(ChargeStatus.CREATED.getValue(), ChargeStatus.SYSTEM_CANCELLED.getValue()));
        }

        @Test
        @DisplayName("Should not add locking status to charge events if charge is cancelled in entering card details state")
        void chargeEventsShouldNotHaveLockingStatus_IfCancelledFromEnteringCardDetailsState() {
            String chargeExternalId = testBaseExtension.createNewChargeWithServiceId(SERVICE_ID);
            long chargeInternalId = app.getDatabaseTestHelper().getChargeIdByExternalId(chargeExternalId);
            app.getDatabaseTestHelper().addEvent(chargeInternalId, ENTERING_CARD_DETAILS.toString());

            app.givenSetup()
                    .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(204);

            checkCancelStatusViaApi(chargeExternalId, SYSTEM_CANCELLED);

            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeExternalId);
            assertThat(events.size(), is(2));
            assertThat(events, hasItems(ENTERING_CARD_DETAILS.getValue(), ChargeStatus.SYSTEM_CANCELLED.getValue()));
        }

        void checkCancelStatusViaApi(String chargeId, ChargeStatus targetState) {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", SERVICE_ID, GatewayAccountType.TEST, chargeId))
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
