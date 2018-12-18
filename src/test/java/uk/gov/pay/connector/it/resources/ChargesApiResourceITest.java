package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.math.RandomUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.ws.rs.core.HttpHeaders;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargesApiResourceITest extends ChargingITestBase {

    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";
    private static final String JSON_MESSAGE_KEY = "message";
    private static final String JSON_CORPORATE_CARD_SURCHARGE_KEY = "corporate_card_surcharge";
    private static final String JSON_TOTAL_AMOUNT_KEY = "total_amount";
    private static final String PROVIDER_NAME = "sandbox";

    public ChargesApiResourceITest() {
        super(PROVIDER_NAME);
    }

    @Test
    public void makeChargeSubmitCaptureAndCheckSettlementSummary() {
        ZonedDateTime startOfTest = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC);
        String expectedDayOfCapture = DateTimeUtils.toUTCDateString(startOfTest);

        String chargeId = authoriseNewCharge();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        // Trigger the capture process programmatically which normally would be invoked by the scheduler.
        testContext.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        testContext.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);

        getCharge(chargeId)
                .body("settlement_summary.capture_submit_time", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("settlement_summary.capture_submit_time", isWithin(10, SECONDS))
                .body("settlement_summary.captured_date", equalTo(expectedDayOfCapture));
    }

    @Test
    public void shouldReturn404OnGetCharge_whenAccountIdIsNonNumeric() {
        connectorRestApiClient
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
    public void shouldReturn404_whenAccountIdIsNonNumeric() {
        connectorRestApiClient
                .withAccountId("invalidAccountId")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldGetCardDetails_whenStatusIsBeyondAuthorised() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, RETURN_URL, null);
        databaseTestHelper.updateChargeCardDetails(chargeId, AuthCardDetailsFixture.anAuthCardDetails().withCardNo("12345678").build());
        databaseTestHelper.addToken(chargeId, "tokenId");

        connectorRestApiClient
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
    public void shouldReturnCardBrandLabel_whenChargeIsAuthorised() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        CardTypeEntity mastercardCredit = databaseTestHelper.getMastercardCreditCard();

        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, RETURN_URL, null,
                ServicePaymentReference.of("ref"), null, EMAIL);
        databaseTestHelper.updateChargeCardDetails(chargeId, mastercardCredit.getBrand(), "1234", "123456", "Mr. McPayment",
                "03/18", "line1", null, "postcode", "city", null, "country");
        databaseTestHelper.addToken(chargeId, "tokenId");

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("card_details.card_brand", is(mastercardCredit.getLabel()));
    }

    @Test
    public void shouldReturnEmptyCardBrandLabel_whenChargeIsAuthorisedAndBrandUnknown() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, RETURN_URL, null,
                ServicePaymentReference.of("ref"), null, EMAIL);
        databaseTestHelper.updateChargeCardDetails(chargeId, "unknown-brand", "1234", "123456", "Mr. McPayment",
                "03/18", "line1", null, "postcode", "city", null, "country");
        databaseTestHelper.addToken(chargeId, "tokenId");

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("card_details.card_brand", is(""));
    }

    @Test
    public void shouldNotReturnBillingAddress_whenNoAddressDetailsPresentInDB() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, RETURN_URL, null,
                ServicePaymentReference.of("ref"), null, EMAIL);
        databaseTestHelper.updateChargeCardDetails(chargeId, "Visa", "1234", "123456", "Mr. McPayment",
                "03/18", null, null, null, null, null, null);
        databaseTestHelper.addToken(chargeId, "tokenId");

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("$card_details", not(hasKey("billing_address")));
    }

    @Test
    public void shouldReturnCorporateCardSurchargeAndTotalAmount_V1() {
        long chargeId = nextInt();
        String externalChargeId = "charge1";

        createCharge(externalChargeId, chargeId);

        connectorRestApiClient
                .withAccountId(accountId)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0]." + JSON_CHARGE_KEY, is(externalChargeId))
                .body("results[0]." + JSON_CORPORATE_CARD_SURCHARGE_KEY, is(150))
                .body("results[0]." + JSON_TOTAL_AMOUNT_KEY, is(Long.valueOf(AMOUNT).intValue() + 150));
    }

    @Test
    public void shouldReturnCorporateCardSurchargeAndTotalAmountForCharges_V2() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        createCharge(externalChargeId, chargeId);

        connectorRestApiClient
                .withAccountId(accountId)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0]." + JSON_CHARGE_KEY, is(externalChargeId))
                .body("results[0]." + JSON_CORPORATE_CARD_SURCHARGE_KEY, is(150))
                .body("results[0]." + JSON_TOTAL_AMOUNT_KEY, is(Long.valueOf(AMOUNT).intValue() + 150));
    }

    @Test
    public void shouldNotReturnCorporateCardSurchargeAndTotalAmountForRefunds_V2() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        createCharge(externalChargeId, chargeId);
        databaseTestHelper.addRefund(RandomUtils.nextInt(), randomAlphanumeric(10), "refund-2-provider-reference", AMOUNT + 150L, RefundStatus.REFUNDED.getValue(), chargeId, now().minusHours(3));

        connectorRestApiClient
                .withAccountId(accountId)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[1]." + JSON_CHARGE_KEY, is(externalChargeId))
                .body("results[1]", not(hasKey(JSON_CORPORATE_CARD_SURCHARGE_KEY)))
                .body("results[1]", not(hasKey(JSON_TOTAL_AMOUNT_KEY)));
    }

    @Test
    public void shouldGetChargeTransactionsForJSONAcceptHeader() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        final CardTypeEntity mastercardCreditCard = databaseTestHelper.getMastercardCreditCard();
        databaseTestHelper.addAcceptedCardType(Long.valueOf(accountId), mastercardCreditCard.getId());
        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, RETURN_URL, null,
                ServicePaymentReference.of("My reference"), createdDate);
        databaseTestHelper.updateChargeCardDetails(chargeId, "VISA", "1234", "123456", "Mr. McPayment",
                "03/18", "line1", null, "postcode", "city", null, "country");
        databaseTestHelper.addToken(chargeId, "tokenId");
        databaseTestHelper.addEvent(chargeId, chargeStatus.getValue());

        String description = "Test description";

        connectorRestApiClient
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].state.status", is(EXTERNAL_SUBMITTED.getStatus()))
                .body("results[0].amount", is(6234))
                .body("results[0].card_details", notNullValue())
                .body("results[0].gateway_account", nullValue())
                .body("results[0].reference", is("My reference"))
                .body("results[0].return_url", is(RETURN_URL))
                .body("results[0].description", is(description))
                .body("results[0].created_date", is("2016-01-26T13:45:32Z"))
                .body("results[0].payment_provider", is(PROVIDER_NAME));
    }

    @Test
    public void shouldGetChargeLegacyTransactions() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        final CardTypeEntity mastercardCreditCard = databaseTestHelper.getMastercardCreditCard();
        databaseTestHelper.addAcceptedCardType(Long.valueOf(accountId), mastercardCreditCard.getId());
        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, RETURN_URL, null,
                ServicePaymentReference.of("My reference"), createdDate);
        databaseTestHelper.updateChargeCardDetails(chargeId, "visa", null, null, null, null,
                null, null, null, null, null, null);
        databaseTestHelper.addToken(chargeId, "tokenId");
        databaseTestHelper.addEvent(chargeId, chargeStatus.getValue());

        connectorRestApiClient
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].amount", is(6234))
                .body("results[0].card_details", notNullValue())
                .body("results[0].card_details.card_brand", is("Visa"))
                .body("results[0].card_details.cardholder_name", nullValue())
                .body("results[0].card_details.last_digits_card_number", nullValue())
                .body("results[0].card_details.first_digits_card_number", nullValue())
                .body("results[0].card_details.expiry_date", nullValue());
    }

    @Test
    public void cannotGetCharge_WhenInvalidChargeId() {
        String chargeId = "23235124";
        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, is(format("Charge with id [%s] not found.", chargeId)));
    }

    @Test
    public void shouldGetSuccessAndFailedResponseForExpiryChargeTask() {
        //create charge
        String extChargeId = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref"), ZonedDateTime.now().minusMinutes(90));

        // run expiry task
        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        // get the charge back and assert its status is expired
        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

    }

    @Test
    public void shouldGetSuccessResponseForExpiryChargeTaskFor3dsRequiredPayments() {
        String extChargeId = addChargeAndCardDetails(ChargeStatus.AUTHORISATION_3DS_REQUIRED, ServicePaymentReference.of("ref"),
                ZonedDateTime.now().minusMinutes(90));

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

    }

    @Test
    public void shouldGetSuccessForExpiryChargeTask_withStatus_awaitingCaptureRequest() {
        //create charge
        String extChargeId = addChargeAndCardDetails(AWAITING_CAPTURE_REQUEST,
                ServicePaymentReference.of("ref"), ZonedDateTime.now().minusHours(48L));

        // run expiry task
        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        // get the charge back and assert its status is expired
        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

    }

    @Test
    public void shouldGetNoContentForMarkChargeAsCaptureApproved_withStatus_awaitingCaptureRequest() {
        //create charge
        String extChargeId = addChargeAndCardDetails(AWAITING_CAPTURE_REQUEST,
                ServicePaymentReference.of("ref"), ZonedDateTime.now().minusMinutes(90));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .postMarkChargeAsCaptureApproved()
                .statusCode(NO_CONTENT.getStatusCode());

        // get the charge back and assert its status is expired
        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(CAPTURE_APPROVED.toExternal().getStatus()));

    }

    @Test
    public void shouldGetNoContentForMarkChargeAsCaptureApproved_withStatus_captureApproved() {
        //create charge
        String extChargeId = addChargeAndCardDetails(CAPTURE_APPROVED,
                ServicePaymentReference.of("ref"), ZonedDateTime.now().minusMinutes(90));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .postMarkChargeAsCaptureApproved()
                .statusCode(NO_CONTENT.getStatusCode());

        // get the charge back and assert its status is expired
        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(CAPTURE_APPROVED.toExternal().getStatus()));

    }

    @Test
    public void shouldGetNotFoundFor_markChargeAsCaptureApproved_whenNoChargeExists() {
        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId("i-do-not-exist")
                .postMarkChargeAsCaptureApproved()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, is("Charge with id [i-do-not-exist] not found."));
    }

    @Test
    public void shouldGetConflictExceptionFor_markChargeAsCaptureApproved_whenNoChargeExists() {
        //create charge
        String extChargeId = addChargeAndCardDetails(EXPIRED,
                ServicePaymentReference.of("ref"), ZonedDateTime.now().minusMinutes(90));

        final String expectedErrorMessage = format("Operation for charge conflicting, %s, attempt to perform delayed capture on charge not in AWAITING CAPTURE REQUEST state.", extChargeId);
        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .postMarkChargeAsCaptureApproved()
                .statusCode(CONFLICT.getStatusCode())
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, is(expectedErrorMessage));
    }

    private void createCharge(String externalChargeId, long chargeId) {
        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, RETURN_URL, null,
                ServicePaymentReference.of("ref"), null, EMAIL);
        databaseTestHelper.updateChargeCardDetails(chargeId, "unknown-brand", "1234", "123456", "Mr. McPayment",
                "03/18", "line1", null, "postcode", "city", null, "country");
        databaseTestHelper.updateCorporateSurcharge(chargeId, 150L);
        databaseTestHelper.addToken(chargeId, "tokenId");
    }
}
