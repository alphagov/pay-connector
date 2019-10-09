package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.ws.rs.core.HttpHeaders;
import java.time.ZonedDateTime;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.time.ZonedDateTime.now;
import static java.time.temporal.ChronoUnit.HOURS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

/**
 * This is effectively a copy of TransactionsApiResourceIT pointing to V2 resource
 * TransactionsApiResourceIT can be removed once the featureFlag logic is removed from V1 resource
 */
@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargesApiV2ResourceIT extends ChargingITestBase {

    private static final String PROVIDER_NAME = "sandbox";

    private String lastDigitsCardNumber = "1234";
    private String firstDigitsCardNumber = "123456";
    private String cardHolderName = "Mr. McPayment";
    private String expiryDate;

    public ChargesApiV2ResourceIT() {
        super(PROVIDER_NAME);
    }

    /**
     * Contract (Selfservice POV) (Transactions List & CSV)
     * ----------------------------------------------------
     * transaction_type
     * state.status
     * state.finished
     * state.code
     * state.message
     * amount
     * created_date
     * email
     * reference
     * description
     * card_details.card_brand
     * card_details.cardholder_name
     * card_details.expiry_date
     * card_details.last_digits_card_number
     * gateway_transaction_id
     * charge_id
     * <p>
     * Pagination
     */
    @Test
    public void shouldGetExpectedTransactionsByChargeReferenceAndStatusesForBothChargesAndRefunds() {

        String returnUrl = "http://service.url/success-page/";
        String email = randomAlphabetic(242) + "@example.com";
        long chargeId2 = nextLong();

        String transactionIdCharge1 = "transaction-id-ref-3-que";
        String transactionIdCharge2 = "transaction-id-ref-3";
        String externalChargeId1 = addChargeAndCardDetails(nextLong(), EXPIRED, ServicePaymentReference.of("ref-3"), transactionIdCharge1, now(),
                "", returnUrl, email);
        addChargeAndCardDetails(nextLong(), CAPTURED, ServicePaymentReference.of("ref-7"), "transaction-id-ref-7", now(),
                "master-card", returnUrl, email);
        String externalChargeId2 = addChargeAndCardDetails(chargeId2, CAPTURED, ServicePaymentReference.of("ref-3"), transactionIdCharge2, now().minusDays(2),
                "visa", returnUrl, email);

        databaseTestHelper.addRefund(randomAlphanumeric(10), "refund-1-provider-reference", 1L, REFUND_SUBMITTED, chargeId2, randomAlphanumeric(10), now().minusHours(2));
        databaseTestHelper.addRefund(randomAlphanumeric(10), "refund-2-provider-reference", 2L, REFUNDED, chargeId2, randomAlphanumeric(10), now().minusHours(3));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-3")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withQueryParam("payment_states", "success,timedout")
                .withQueryParam("refund_states", "submitted")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("total", is(3))
                .body("count", is(2))
                .body("page", is(1))
                .body("_links.next_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-3&page=2&display_size=2&payment_states=timedout%2Csuccess&refund_states=submitted")))
                .body("_links.prev_page", emptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-3&page=1&display_size=2&payment_states=timedout%2Csuccess&refund_states=submitted")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-3&page=2&display_size=2&payment_states=timedout%2Csuccess&refund_states=submitted")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref-3&page=1&display_size=2&payment_states=timedout%2Csuccess&refund_states=submitted")))

                .body("results[0].transaction_type", is("charge"))
                .body("results[0].gateway_transaction_id", is(transactionIdCharge1))
                .body("results[0].charge_id", is(externalChargeId1))
                .body("results[0].amount", is(6234))
                .body("results[0].description", is("Test description"))
                .body("results[0].reference", is("ref-3"))
                .body("results[0].state.finished", is(true))
                .body("results[0].state.status", is("timedout"))
                .body("results[0].state.code", is("P0020"))
                .body("results[0].state.message", is("Payment expired"))
                .body("results[0].card_details.card_brand", is(nullValue()))
                .body("results[0].card_details.cardholder_name", is(cardHolderName))
                .body("results[0].card_details.expiry_date", is(expiryDate))
                .body("results[0].card_details.last_digits_card_number", is(lastDigitsCardNumber))
                .body("results[0].card_details.first_digits_card_number", is(firstDigitsCardNumber))
                .body("results[0].created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("results[0].created_date", isWithin(3, HOURS)) // The refund CREATED is the most recent

                .body("results[1].transaction_type", is("refund"))
                .body("results[1].gateway_transaction_id", is(transactionIdCharge2))
                .body("results[1].charge_id", is(externalChargeId2))
                .body("results[1].amount", is(1))
                .body("results[1].description", is("Test description"))
                .body("results[1].reference", is("ref-3"))
                .body("results[1].email", is(email))
                .body("results[1].state.finished", is(false))
                .body("results[1].state.status", is("submitted"))
                .body("results[1].state.code", is(emptyOrNullString()))
                .body("results[1].state.message", is(emptyOrNullString()))
                .body("results[1].card_details.card_brand", is("Visa"))
                .body("results[1].card_details.cardholder_name", is(cardHolderName))
                .body("results[1].card_details.expiry_date", is(expiryDate))
                .body("results[1].card_details.last_digits_card_number", is(lastDigitsCardNumber))
                .body("results[1].created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("results[1].created_date", isWithin(3, HOURS)); // The refund CREATED is the most recent
    }

    @Test
    public void shouldGetExpectedCharge_whenOnlySpecifiedPaymentStates() {

        String returnUrl = "http://service.url/success-page/";
        String email = randomAlphabetic(242) + "@example.com";
        long chargeId2 = nextLong();

        String transactionIdCharge2 = "transaction-id-ref-3";
        String transactionIdCharge1 = "transaction-id-ref-3-que";
        ServicePaymentReference referenceCharge1 = ServicePaymentReference.of("ref-3");
        ServicePaymentReference referenceCharge2 = ServicePaymentReference.of("ref-3");
        String externalChargeId1 = addChargeAndCardDetails(nextLong(), CREATED, referenceCharge1, transactionIdCharge1, now(), "", returnUrl, email);
        addChargeAndCardDetails(nextLong(), CAPTURED, ServicePaymentReference.of("ref-7"), "transaction-id-ref-7", now(), "master-card",
                returnUrl, email);
        String externalChargeId2 = addChargeAndCardDetails(chargeId2, CAPTURED, referenceCharge2, transactionIdCharge2, now().minusDays(2), "visa",
                returnUrl, email);

        databaseTestHelper.addRefund(randomAlphanumeric(10), "refund-1-provider-reference", 1L, REFUND_SUBMITTED, chargeId2, randomAlphanumeric(10), now().minusHours(2));
        databaseTestHelper.addRefund(randomAlphanumeric(10), "refund-2-provider-reference", 2L, REFUNDED, chargeId2, randomAlphanumeric(10), now().minusHours(3));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-3")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withQueryParam("payment_states", "success,created")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("total", is(2))
                .body("count", is(2))
                .body("page", is(1))
                .body("results[0].transaction_type", is("charge"))
                .body("results[0].gateway_transaction_id", is(transactionIdCharge1))
                .body("results[0].charge_id", is(externalChargeId1))
                .body("results[0].reference", is(referenceCharge1.toString()))
                .body("results[1].transaction_type", is("charge"))
                .body("results[1].gateway_transaction_id", is(transactionIdCharge2))
                .body("results[1].charge_id", is(externalChargeId2))
                .body("results[1].reference", is(referenceCharge2.toString()));
    }

    @Test
    public void shouldFilterTransactionsByCardHolderName() {
        String cardHolderName = "Mr. PayMcPayment";
        addChargeAndCardDetails(nextLong(), CREATED, ServicePaymentReference.of("ref-1"), "ref", now(), "", "http://service.url/success-page/", "aaa@bbb.test", cardHolderName, "1234", "CREDIT");
        addChargeAndCardDetails(nextLong(), AUTHORISATION_SUCCESS, ServicePaymentReference.of("ref-1"), "ref", now(), "", "http://service.url/success-page/", "aaa@bbb.test", cardHolderName, "1234", "DEBIT");
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("cardholder_name", "PayMc")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].card_details.cardholder_name", is(cardHolderName))
                .body("results[0].card_details.card_type", is("debit"))
                .body("results[0].card_details.last_digits_card_number", is("1234"))
                .body("results[1].card_details.cardholder_name", is(cardHolderName))
                .body("results[1].card_details.card_type", is("credit"))
                .body("results[1].card_details.last_digits_card_number", is("1234"));
    }

    @Test
    public void searchChargesByFullLastFourDigits() {
        String lastFourDigits = "3943";
        addChargeAndCardDetails(nextLong(), CREATED, ServicePaymentReference.of("ref-1"), "ref", now(), "", "http://service.url/success-page/", "aaa@bbb.test", cardHolderName, lastFourDigits, "CREDIT");
        addChargeAndCardDetails(nextLong(), AUTHORISATION_SUCCESS, ServicePaymentReference.of("ref-1"), "ref", now(), "", "http://service.url/success-page/", "aaa@bbb.test", cardHolderName, lastFourDigits, "CREDIT");

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("last_digits_card_number", "3943")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].card_details.cardholder_name", is("Mr. McPayment"))
                .body("results[0].card_details.card_type", is("credit"))
                .body("results[0].card_details.last_digits_card_number", is("3943"));
    }

    @Test
    public void shouldNotMatchChargesByPartialLastFourDigits() {
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("last_digits_card_number", "12")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0));
    }

    @Test
    public void shouldGetExpectedCharge_whenOnlySpecifiedRefundStates() {

        String returnUrl = "http://service.url/success-page/";
        String email = randomAlphabetic(242) + "@example.com";
        long chargeId2 = nextLong();

        String transactionIdCharge2 = "transaction-id-ref-3";
        addChargeAndCardDetails(nextLong(), CREATED, ServicePaymentReference.of("ref-3-que"), "transaction-id-ref-3-que", now(), "",
                returnUrl, email);
        addChargeAndCardDetails(nextLong(), CAPTURED, ServicePaymentReference.of("ref-7"), "transaction-id-ref-7", now(), "master-card",
                returnUrl, email);
        String externalChargeId2 = addChargeAndCardDetails(chargeId2, CAPTURED, ServicePaymentReference.of("ref-3"), transactionIdCharge2, now().minusDays(2),
                "visa", returnUrl, email);

        databaseTestHelper.addRefund(randomAlphanumeric(10), "refund-1-provider-reference", 1L, REFUND_SUBMITTED, chargeId2, randomAlphanumeric(10), now().minusHours(2));
        databaseTestHelper.addRefund(randomAlphanumeric(10), "refund-2-provider-reference", 2L, REFUNDED, chargeId2, randomAlphanumeric(10), now().minusHours(3));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-3")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withQueryParam("refund_states", "success")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("total", is(1))
                .body("count", is(1))
                .body("page", is(1))
                .body("results[0].transaction_type", is("refund"))
                .body("results[0].gateway_transaction_id", is(transactionIdCharge2))
                .body("results[0].charge_id", is(externalChargeId2));
    }

    @Test
    public void shouldReturnFeeandNetChargeInSearchResultsV2IfFeeExists() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();
        long feeCollected = 100;

        createCharge(externalChargeId, chargeId);
        databaseTestHelper.addFee(RandomIdGenerator.newId(), chargeId, 100L, feeCollected, ZonedDateTime.now(), "irrelevant_id");

        connectorRestApiClient
                .withAccountId(accountId)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].fee", is(100))
                .body("results[0].net_amount", is(6284)); //6234 + 150 - 100
    }

    @Test
    public void shouldNotReturnFeeandNetAmountForRefund() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();
        long feeCollected = 100;

        createCharge(externalChargeId, chargeId);
        databaseTestHelper.addFee(RandomIdGenerator.newId(), chargeId, 100L, feeCollected, ZonedDateTime.now(), "irrelevant_id");
        databaseTestHelper.addRefund(randomAlphanumeric(10), "refund-2-provider-reference", 2L, REFUNDED, chargeId, randomAlphanumeric(10), now().minusHours(3));

        connectorRestApiClient
                .withAccountId(accountId)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("total", is(2))
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].transaction_type", is("charge"))
                .body("results[0].fee", is(100))
                .body("results[0].net_amount", is(6284)) //6234 + 150 - 100
                .body("results[1].charge_id", is(externalChargeId))
                .body("results[1].transaction_type", is("refund"))
                .body("results[1].amount", is(2))
                .body("results[1].fee", is(nullValue()))
                .body("results[1].net_amount", is(nullValue())); 
    }
    
    @Test
    public void shouldReturnExternalMetadataInSearchResultsV2IfExternalMetadataExists() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        ExternalMetadata externalMetadata = new ExternalMetadata(
                Map.of("key1", true, "key2", 123, "key3", "string1"));

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(100)
                .withStatus(AUTHORISATION_SUCCESS)
                .withReturnUrl(RETURN_URL)
                .withDescription("Test description")
                .withReference(ServicePaymentReference.of("ref"))
                .withLanguage(SupportedLanguage.ENGLISH)
                .withDelayedCapture(false)
                .withCorporateSurcharge(100L)
                .withExternalMetadata(externalMetadata)
                .build());

        connectorRestApiClient
                .withAccountId(accountId)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].metadata.key1", is(true))
                .body("results[0].metadata.key2", is(123))
                .body("results[0].metadata.key3", is("string1"));
    }

    @Test
    public void shouldNotReturnExternalMetadataInSearchResultsV2IfExternalMetadataDoesNotExists() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        ExternalMetadata nullExternalMetadata = null;

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(100)
                .withStatus(AUTHORISATION_SUCCESS)
                .withReturnUrl(RETURN_URL)
                .withDescription("Test description")
                .withReference(ServicePaymentReference.of("ref"))
                .withLanguage(SupportedLanguage.ENGLISH)
                .withDelayedCapture(false)
                .withCorporateSurcharge(100L)
                .build());
        
        connectorRestApiClient
                .withAccountId(accountId)
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].metadata", is(nullValue()));
    }

    @Test
    public void shouldReturnChargesWhen_PartialCaseInsensitiveReferenceSearchIsPerformed() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(100)
                .withStatus(AUTHORISATION_SUCCESS)
                .withReturnUrl(RETURN_URL)
                .withDescription("Test description")
                .withReference(ServicePaymentReference.of("partial-reference"))
                .withLanguage(SupportedLanguage.ENGLISH)
                .withDelayedCapture(false)
                .withCorporateSurcharge(100L)
                .build());

        chargeId = nextInt();
        externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(100)
                .withStatus(AUTHORISATION_SUCCESS)
                .withReturnUrl(RETURN_URL)
                .withDescription("Test description")
                .withReference(ServicePaymentReference.of("not-a-ref"))
                .withLanguage(SupportedLanguage.ENGLISH)
                .withDelayedCapture(false)
                .withCorporateSurcharge(100L)
                .build());

        connectorRestApiClient
                .withQueryParam(
                        "reference"
                , "pArTiAl")
                .getChargesV2()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("results[0].reference", is("partial-reference"));
    }
    
    private String addChargeAndCardDetails(Long chargeId, ChargeStatus status, ServicePaymentReference reference, String transactionId, ZonedDateTime fromDate,
                                           String cardBrand, String returnUrl, String email) {
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(chargeStatus)
                .withReturnUrl(returnUrl)
                .withTransactionId(transactionId)
                .withDescription("Test description")
                .withReference(reference)
                .withCreatedDate(fromDate)
                .withCardType(CardType.CREDIT)
                .withEmail(email)
                .withVersion(1)
                .withLanguage(SupportedLanguage.ENGLISH)
                .withDelayedCapture(false)
                .build());
        databaseTestHelper.addToken(chargeId, "tokenId");
        databaseTestHelper.addEvent(chargeId, chargeStatus.getValue());
        expiryDate = "03/18";
        databaseTestHelper.updateChargeCardDetails(chargeId, cardBrand, lastDigitsCardNumber, firstDigitsCardNumber, cardHolderName, expiryDate, null, "line1", null, "postcode", "city", null, "country");
        return externalChargeId;
    }

    private String addChargeAndCardDetails(Long chargeId, ChargeStatus status, ServicePaymentReference reference, String transactionId, ZonedDateTime fromDate,
                                           String cardBrand, String returnUrl, String email,
                                           String cardHolderName, String lastDigitsCardNumber,
                                           String cardType) {
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(chargeStatus)
                .withReturnUrl(returnUrl)
                .withTransactionId(transactionId)
                .withDescription("Test description")
                .withReference(reference)
                .withCreatedDate(fromDate)
                .withEmail(email)
                .withVersion(1)
                .withCardType(CardType.valueOf(cardType))
                .withLanguage(SupportedLanguage.ENGLISH)
                .withDelayedCapture(false)
                .build());
        databaseTestHelper.addToken(chargeId, "tokenId");
        databaseTestHelper.addEvent(chargeId, chargeStatus.getValue());
        expiryDate = "03/18";
        databaseTestHelper.updateChargeCardDetails(chargeId, cardBrand, lastDigitsCardNumber, firstDigitsCardNumber, cardHolderName, expiryDate, cardType, "line1", null, "postcode", "city", null, "country");
        return externalChargeId;
    }

    private String expectedChargesLocationFor(String accountId, String queryParams) {
        return "/v2/api/accounts/{accountId}/charges".replace("{accountId}", accountId)
                + queryParams;
    }

    private void createCharge(String externalChargeId, long chargeId) {
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(AUTHORISATION_SUCCESS)
                .withReturnUrl(RETURN_URL)
                .withDescription("Test description")
                .withReference(ServicePaymentReference.of("ref"))
                .withEmail(EMAIL)
                .withVersion(1)
                .withLanguage(SupportedLanguage.ENGLISH)
                .withDelayedCapture(false)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, "unknown-brand", "1234", "123456", "Mr. McPayment",
                "03/18", null, "line1", null, "postcode", "city", null, "country");
        databaseTestHelper.updateCorporateSurcharge(chargeId, 150L);
        databaseTestHelper.addToken(chargeId, "tokenId");
    }
}
