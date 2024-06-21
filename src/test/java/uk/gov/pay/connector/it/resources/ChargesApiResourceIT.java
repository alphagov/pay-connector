package uk.gov.pay.connector.it.resources;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Nested;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.ws.rs.core.HttpHeaders;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.cardtype.model.domain.CardType.DEBIT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.RETURN_URL;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.service.payments.commons.model.ApiResponseDateTimeFormatter.ISO_LOCAL_DATE_IN_UTC;

public class ChargesApiResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";
    private static final String JSON_MESSAGE_KEY = "message";
    private static final String CAPTURE_BY_CHARGE_ID_URL = "v1/api/charges/%s/capture";

    private final DatabaseTestHelper databaseTestHelper = app.getDatabaseTestHelper();
    private final String accountId = testBaseExtension.getAccountId();
    
    @Test
    void makeChargeSubmitCaptureAndCheckSettlementSummary() throws QueueException {
        Instant startOfTest = Instant.now();
        String expectedDayOfCapture = ISO_LOCAL_DATE_IN_UTC.format(startOfTest);

        String chargeId = testBaseExtension.authoriseNewCharge();

        app.givenSetup()
                .post(testBaseExtension.captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        // Trigger the capture process programmatically which normally would be invoked by the scheduler.
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).handleCaptureMessages();

        testBaseExtension.getCharge(chargeId)
                .body("settlement_summary.capture_submit_time", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("settlement_summary.capture_submit_time", isWithin(20, SECONDS))
                .body("settlement_summary.captured_date", equalTo(expectedDayOfCapture));
    }

    @Test
    void shouldReturn404OnGetCharge_whenAccountIdIsNonNumeric() {
        testBaseExtension.getConnectorRestApiClient()
                .withAccountId("wrongAccount")
                .withChargeId("123")
                .withHeader(HttpHeaders.ACCEPT, JSON.getAcceptHeader())
                .getCharge()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    void shouldReturn404WhenGettingNonExistentChargeId() {
        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId("does-not-exist")
                .getCharge()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void shouldGetChargeStatusAsInProgressIfInternalStatusIsAuthorised() {

        long chargeId = nextInt();
        String externalChargeId = "charge1";

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(AUTHORISATION_SUCCESS)
                .withEmail("email@fake.test")
                .build());

        databaseTestHelper.addToken(chargeId, "tokenId");

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(externalChargeId))
                .body(JSON_STATE_KEY, is(EXTERNAL_SUBMITTED.getStatus()));
    }

    @Test
    void shouldGetCardDetails_whenStatusIsBeyondAuthorised() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withCardType(DEBIT)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());

        databaseTestHelper.updateChargeCardDetails(chargeId, AuthCardDetailsFixture.anAuthCardDetails().withCardNo("12345678").build());
        databaseTestHelper.addToken(chargeId, "tokenId");

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("card_details", is(notNullValue()))
                .body("card_details.last_digits_card_number", is("5678"))
                .body("card_details.first_digits_card_number", is("123456"));
    }

    @Test
    void shouldGetMetadataWhenSet() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();
        ExternalMetadata externalMetadata = new ExternalMetadata(
                Map.of("key1", true, "key2", 123, "key3", "string1"));

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(100)
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withExternalMetadata(externalMetadata)
                .build());

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("metadata.key1", is(true))
                .body("metadata.key2", is(123))
                .body("metadata.key3", is("string1"));
    }

    @Test
    void shouldNotReturnMetadataWhenNull() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(100)
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .build());

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("metadata", is(Matchers.nullValue()));
    }

    @Test
    void shouldReturnCardBrandLabel_whenChargeIsAuthorised() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        CardTypeEntity mastercardCredit = databaseTestHelper.getMastercardCreditCard();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, mastercardCredit.getBrand(), "1234", "123456", "Mr. McPayment",
                CardExpiryDate.valueOf("03/18"), null, "line1", null, "postcode", "city", null, "country");
        databaseTestHelper.addToken(chargeId, "tokenId");

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("card_details.card_brand", is(mastercardCredit.getLabel()));
    }

    @Test
    void shouldReturnAuthorisationSummary_whenChargeIsAuthorisedWith3ds() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());
        databaseTestHelper.updateCharge3dsFlexChallengeDetails(chargeId, "acsUrl", "transactionId", "payload", "2.1.0");
        databaseTestHelper.addToken(chargeId, "tokenId");

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("authorisation_summary.three_d_secure.required", is(true))
                .body("authorisation_summary.three_d_secure.version", is("2.1.0"));
    }

    @Test
    void shouldReturnEmptyCardBrandLabel_whenChargeIsAuthorisedAndBrandUnknown() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, "unknown-brand", "1234", "123456", "Mr. McPayment",
                CardExpiryDate.valueOf("03/18"), null, "line1", null, "postcode", "city", null, "country");
        databaseTestHelper.addToken(chargeId, "tokenId");

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("card_details.card_brand", is(""));
    }

    @Test
    void shouldNotReturnBillingAddress_whenNoAddressDetailsPresentInDB() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, "Visa", "1234", "123456", "Mr. McPayment",
                CardExpiryDate.valueOf("03/18"), null, null, null, null, null, null, null);
        databaseTestHelper.addToken(chargeId, "tokenId");

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("$card_details", not(hasKey("billing_address")));
    }

    @Test
    void shouldReturnFeeIfItExists() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();
        long feeCollected = 100L;


        createCharge(externalChargeId, chargeId);
        databaseTestHelper.addFee(RandomIdGenerator.newId(), chargeId, 100L, feeCollected, ZonedDateTime.now(), "irrelevant_id", FeeType.TRANSACTION);

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("fee", is(100));
    }

    @Test
    void shouldReturnNetAmountIfFeeExists() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();
        long feeCollected = 100L;

        long defaultAmount = 6234L;
        long defaultCorporateSurchargeAmount = 150L;

        createCharge(externalChargeId, chargeId);
        databaseTestHelper.addFee(RandomIdGenerator.newId(), chargeId, 100L, feeCollected, ZonedDateTime.now(), "irrelevant_id", FeeType.TRANSACTION);

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("fee", is(100))
                .body("net_amount", is(Long.valueOf(defaultAmount + defaultCorporateSurchargeAmount - feeCollected).intValue()));
    }

    @Test
    void cannotGetCharge_WhenInvalidChargeId() {
        String chargeId = "23235124";
        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, contains(format("Charge with id [%s] not found.", chargeId)))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void shouldGetSuccessAndFailedResponseForExpiryChargeTask() {
        //create charge
        String extChargeId = testBaseExtension.addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref"), Instant.now().minus(90, MINUTES));

        // run expiry task
        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        // get the charge back and assert its status is expired
        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

    }

    @Test
    void shouldGetSuccessResponseForExpiryChargeTaskFor3dsRequiredPayments() {
        String extChargeId = testBaseExtension.addChargeAndCardDetails(ChargeStatus.AUTHORISATION_3DS_REQUIRED, ServicePaymentReference.of("ref"),
                Instant.now().minus(90, MINUTES));

        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

    }

    @Test
    void shouldGetSuccessForExpiryChargeTask_withStatus_awaitingCaptureRequest() {
        //create charge
        String extChargeId = testBaseExtension.addChargeAndCardDetails(AWAITING_CAPTURE_REQUEST,
                ServicePaymentReference.of("ref"), Instant.now().minus(120, HOURS));

        // run expiry task
        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        // get the charge back and assert its status is expired
        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

    }

    @Test
    void shouldReturnNullCardType_whenCardTypeIsNull() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withCardType(null)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, "Visa", "1234", "123456", "Mr. McPayment",
                CardExpiryDate.valueOf("03/18"), null, null, null, null, null, null, null);
        databaseTestHelper.addToken(chargeId, "tokenId");

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("card_details.card_type", is(nullValue()));
    }

    @Test
    void shouldReturnChargeWhenAuthorisationModeIsMotoApi() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAuthorisationMode(AuthorisationMode.MOTO_API)
                .build());

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("authorisation_mode", is("moto_api"));
    }

    @Test
    void shouldReturnCanRetryTrueWhenChargeCanBeRetried() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withStatus(AUTHORISATION_REJECTED)
                .withCanRetry(true)
                .build());

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("state.can_retry", is(true));
    }

    @Test
    void shouldReturnCanRetryFalseWhenChargeHasAuthorisationModeAgreementAndCanBeRetried() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withStatus(AUTHORISATION_REJECTED)
                .withCanRetry(true)
                .build());

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("state.can_retry", is(true));
    }

    @Test
    void shouldReturnCanRetryFalseWhenChargeHasAuthorisationModeAgreementAndCannotBeRetried() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withStatus(AUTHORISATION_REJECTED)
                .withCanRetry(false)
                .build());

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("state.can_retry", is(false));
    }

    @Test
    void shouldNotReturnCanRetryWhenChargeHasAuthorisationModeAgreementAndUnspecifiedWhetherItCanBeRetried() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withStatus(AUTHORISATION_REJECTED)
                .withCanRetry(null)
                .build());

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("state.can_retry", is(nullValue()));
    }

    @Test
    void shouldNotReturnCanRetryWhenChargeHasAuthorisationModeWeb() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAuthorisationMode(AuthorisationMode.WEB)
                .withStatus(AUTHORISATION_REJECTED)
                .withCanRetry(true)
                .build());

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("state.can_retry", is(nullValue()));
    }


    @Nested
    class DelayedCaptureApproveByAccountId {
        @Test
        void shouldGetNoContentForMarkChargeAsCaptureApproved_withStatus_awaitingCaptureRequest() {
            //create charge
            String extChargeId = testBaseExtension.addChargeAndCardDetails(AWAITING_CAPTURE_REQUEST,
                    ServicePaymentReference.of("ref"), Instant.now().minus(90, MINUTES));

            testBaseExtension.getConnectorRestApiClient()
                    .withAccountId(accountId)
                    .withChargeId(extChargeId)
                    .postMarkChargeAsCaptureApprovedByChargeIdAndAccountId()
                    .statusCode(NO_CONTENT.getStatusCode());

            // get the charge back and assert its status is expired
            testBaseExtension.getConnectorRestApiClient()
                    .withAccountId(accountId)
                    .withChargeId(extChargeId)
                    .getCharge()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_CHARGE_KEY, is(extChargeId))
                    .body(JSON_STATE_KEY, is(CAPTURE_APPROVED.toExternal().getStatus()));

        }

        @Test
        void shouldGetNoContentForMarkChargeAsCaptureApproved_withStatus_captureApproved() {
            //create charge
            String extChargeId = testBaseExtension.addChargeAndCardDetails(CAPTURE_APPROVED,
                    ServicePaymentReference.of("ref"), Instant.now().minus(90, MINUTES));

            testBaseExtension.getConnectorRestApiClient()
                    .withAccountId(accountId)
                    .withChargeId(extChargeId)
                    .postMarkChargeAsCaptureApprovedByChargeIdAndAccountId()
                    .statusCode(NO_CONTENT.getStatusCode());

            // get the charge back and assert its status is expired
            testBaseExtension.getConnectorRestApiClient()
                    .withAccountId(accountId)
                    .withChargeId(extChargeId)
                    .getCharge()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_CHARGE_KEY, is(extChargeId))
                    .body(JSON_STATE_KEY, is(CAPTURE_APPROVED.toExternal().getStatus()));

        }

        @Test
        void shouldGetNotFoundFor_markChargeAsCaptureApproved_whenNoChargeExists() {
            testBaseExtension.getConnectorRestApiClient()
                    .withAccountId(accountId)
                    .withChargeId("i-do-not-exist")
                    .postMarkChargeAsCaptureApprovedByChargeIdAndAccountId()
                    .statusCode(NOT_FOUND.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_MESSAGE_KEY, contains("Charge with id [i-do-not-exist] not found."))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        void shouldGetConflictExceptionFor_markChargeAsCaptureApproved_whenNoChargeExists() {
            //create charge
            String extChargeId = testBaseExtension.addChargeAndCardDetails(EXPIRED,
                    ServicePaymentReference.of("ref"), Instant.now().minus(90, MINUTES));

            final String expectedErrorMessage = format("Operation for charge conflicting, %s, attempt to perform delayed capture on charge not in AWAITING CAPTURE REQUEST state.", extChargeId);
            testBaseExtension.getConnectorRestApiClient()
                    .withAccountId(accountId)
                    .withChargeId(extChargeId)
                    .postMarkChargeAsCaptureApprovedByChargeIdAndAccountId()
                    .statusCode(CONFLICT.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_MESSAGE_KEY, contains(expectedErrorMessage))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }
    }

    @Nested
    @Disabled
    class DelayedCaptureApproveByServiceIdAndAccountType {
        @Test
        void shouldGetNoContentForMarkChargeAsCaptureApproved_withStatus_awaitingCaptureRequest() {
            String extChargeId = testBaseExtension.addChargeAndCardDetails(AWAITING_CAPTURE_REQUEST,
                    ServicePaymentReference.of("ref"), Instant.now().minus(90, MINUTES));

            app.givenSetup()
                    .post(format(CAPTURE_BY_CHARGE_ID_URL, extChargeId))
                    .then()
                    .statusCode(NO_CONTENT.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", accountId, extChargeId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_CHARGE_KEY, is(extChargeId))
                    .body(JSON_STATE_KEY, is(CAPTURE_APPROVED.toExternal().getStatus()));
        }

        @Test
        void shouldGetNoContentForMarkChargeAsCaptureApproved_withStatus_captureApproved() {
            String extChargeId = testBaseExtension.addChargeAndCardDetails(CAPTURE_APPROVED,
                    ServicePaymentReference.of("ref"), Instant.now().minus(90, MINUTES));

            app.givenSetup()
                    .post(format(CAPTURE_BY_CHARGE_ID_URL, extChargeId))
                    .then()
                    .statusCode(NO_CONTENT.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", accountId, extChargeId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_CHARGE_KEY, is(extChargeId))
                    .body(JSON_STATE_KEY, is(CAPTURE_APPROVED.toExternal().getStatus()));
        }

        @Test
        void shouldGetNotFoundFor_markChargeAsCaptureApproved_whenNoChargeExists() {
            app.givenSetup()
                    .post(format(CAPTURE_BY_CHARGE_ID_URL, "non-existent-chargeId"))
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_MESSAGE_KEY, contains("Charge with id [non-existent-chargeId] not found."))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        void shouldGetConflictExceptionFor_markChargeAsCaptureApproved_whenChargeExpired() {
            String extChargeId = testBaseExtension.addChargeAndCardDetails(EXPIRED,
                    ServicePaymentReference.of("ref"), Instant.now().minus(90, MINUTES));

            final String expectedErrorMessage = format("Operation for charge conflicting, %s, attempt to perform delayed capture on charge not in AWAITING CAPTURE REQUEST state.", extChargeId);
            app.givenSetup()
                    .post(format(CAPTURE_BY_CHARGE_ID_URL, extChargeId))
                    .then()
                    .statusCode(CONFLICT.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_MESSAGE_KEY, contains(expectedErrorMessage))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }
    }

    @Test
    void shouldReturnDebitCardType_whenCardTypeIsDebit() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withCardType(DEBIT)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, "Visa", "1234", "123456", "Mr. McPayment",
                CardExpiryDate.valueOf("03/18"), DEBIT.toString(), null, null, null, null, null, null);
        databaseTestHelper.addToken(chargeId, "tokenId");

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("card_details.card_type", is("debit"));
    }
    
    private void createCharge(String externalChargeId, long chargeId) {
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(CAPTURED)
                .withReturnUrl(RETURN_URL)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, "unknown-brand", "1234", "123456", "Mr. McPayment",
                CardExpiryDate.valueOf("03/18"), null, "line1", null, "postcode", "city", null, "country");
        databaseTestHelper.updateCorporateSurcharge(chargeId, 150L);
        databaseTestHelper.addToken(chargeId, "tokenId");
    }

}
