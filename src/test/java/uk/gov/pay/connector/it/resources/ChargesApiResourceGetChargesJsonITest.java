package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.ServicePaymentReference;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.ws.rs.core.HttpHeaders;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static com.jayway.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;

public class ChargesApiResourceGetChargesJsonITest extends ChargingITestBase {
    private static final String PROVIDER_NAME = "sandbox";

    public ChargesApiResourceGetChargesJsonITest() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldReturn404OnGetTransactionsWhenAccountIdIsNonNumeric() {
        connectorRestApiClient
                .withAccountId("invalidAccountId")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldGetChargeTransactionsForJSONAcceptHeader() {
        long chargeId = nextInt();
        String externalChargeId = "charge3";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        UUID card = UUID.randomUUID();
        app.getDatabaseTestHelper().addCardType(card, "label", "CREDIT", "brand", false);
        app.getDatabaseTestHelper().addAcceptedCardType(Long.valueOf(accountId), card);
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, RETURN_URL, null,
                ServicePaymentReference.of("My reference"), createdDate, SupportedLanguage.WELSH, true);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, "VISA", "1234", "123456", "Mr. McPayment", "03/18", "line1", null, "postcode", "city", null, "country");
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());

        String description = "Test description";

        connectorRestApiClient
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].state.status", is(EXTERNAL_SUBMITTED.getStatus()))
                .body("results[0].amount", is(6234))
                .body("results[0].card_details", notNullValue())
                .body("results[0].gateway_account", nullValue())
                .body("results[0].reference", is("My reference"))
                .body("results[0].description", is(description))
                .body("results[0].created_date", is("2016-01-26T13:45:32Z"))
                .body("results[0].language", is(SupportedLanguage.WELSH.toString()))
                .body("results[0].delayed_capture", is(true))
                .body("results[0].corporate_surcharge", is(nullValue()))
                .body("results[0].total_amount", is(nullValue()));
    }

    @Test
    public void shouldGetChargeTransactionsForJSONAcceptHeader_withCorporateSurcharge() {
        long chargeId = nextInt();
        String externalChargeId = "charge3";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        UUID card = UUID.randomUUID();
        app.getDatabaseTestHelper().addCardType(card, "label", "CREDIT", "brand", false);
        app.getDatabaseTestHelper().addAcceptedCardType(Long.valueOf(accountId), card);
        final long corporateSurcharge = 250L;
        app.getDatabaseTestHelper().addChargeWithCorporateSurcharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, RETURN_URL, null,
                ServicePaymentReference.of("My reference"), createdDate, SupportedLanguage.WELSH, true, corporateSurcharge);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, "VISA", "1234", "123456", "Mr. McPayment", "03/18", "line1", null, "postcode", "city", null, "country");
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());

        String description = "Test description";

        connectorRestApiClient
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].state.status", is(EXTERNAL_SUBMITTED.getStatus()))
                .body("results[0].amount", is(6234))
                .body("results[0].card_details", notNullValue())
                .body("results[0].gateway_account", nullValue())
                .body("results[0].reference", is("My reference"))
                .body("results[0].description", is(description))
                .body("results[0].created_date", is("2016-01-26T13:45:32Z"))
                .body("results[0].language", is(SupportedLanguage.WELSH.toString()))
                .body("results[0].delayed_capture", is(true))
                .body("results[0].corporate_surcharge", is(250))
                .body("results[0].total_amount", is(6484));
    }

    @Test
    public void shouldGetChargeLegacyTransactions() {

        long chargeId = nextInt();
        String externalChargeId = "charge3";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        UUID card = UUID.randomUUID();
        app.getDatabaseTestHelper().addCardType(card, "label", "CREDIT", "brand", false);
        app.getDatabaseTestHelper().addAcceptedCardType(Long.valueOf(accountId), card);
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, RETURN_URL, null,
                ServicePaymentReference.of("My reference"), createdDate);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, "visa", null, null, null, null,
                null, null, null, null, null, null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());

        connectorRestApiClient
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
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
}
