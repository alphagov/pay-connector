package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.Address;
import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.model.Browser;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.model.CardAuthorisationRequest;
import uk.gov.pay.connector.model.CardAuthorisationResponse;
import uk.gov.pay.connector.model.ChargeStatus;
import uk.gov.pay.connector.model.GatewayAccount;
import uk.gov.pay.connector.model.Session;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviderFactory;
import uk.gov.pay.connector.utils.EnvironmentUtils;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.Address.anAddress;
import static uk.gov.pay.connector.model.Card.aCard;
import static uk.gov.pay.connector.model.ChargeStatus.STATUS_KEY;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_ADDRESS_CITY;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_ADDRESS_COUNTRY;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_ADDRESS_COUNTY;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_ADDRESS_LINE1;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_ADDRESS_LINE2;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_ADDRESS_LINE3;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_ADDRESS_POSTCODE;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_CARDHOLDER_NAME;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_CARD_NUMBER;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_CVC;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_EXPIRY_DATE;
import static uk.gov.pay.connector.resources.CardDetailsValidator.isWellFormattedCardDetails;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class CardDetailsResource {
    public static final String CARD_AUTH_FRONTEND_PATH = "/v1/frontend/charges/{chargeId}/cards";

    private final Logger logger = LoggerFactory.getLogger(CardDetailsResource.class);
    private final GatewayAccountDao accountDao;
    private final ChargeDao chargeDao;

    public CardDetailsResource(ChargeDao chargeDao, GatewayAccountDao accountDao) {
        this.accountDao = accountDao;
        this.chargeDao = chargeDao;
    }

    @POST
    @Path(CARD_AUTH_FRONTEND_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response addCardDetailsForCharge(@PathParam("chargeId") String chargeId, Map<String, Object> cardDetails) {

        if (!isWellFormattedCardDetails(cardDetails)) {
            return responseWithError("Values do not match expected format/length.");
        }

        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);

        if (!maybeCharge.isPresent()) {
            return responseWithChargeNotFound(logger, chargeId);
        }

        Map<String, Object> charge = maybeCharge.get();
        if (!hasStatusCreated(charge)) {
            return responseWithCardAlreadyProcessed(chargeId);
        }

        return authoriseCard(chargeId, cardDetails, charge);
    }

    private Response authoriseCard(String chargeId, Map<String, Object> cardDetails, Map<String, Object> charge) throws PayDBIException {

        PaymentProvider paymentProvider = resolvePaymentProviderFor(charge);

        String transactionId = randomUUID().toString(); //TODO: which transationId

        CardAuthorisationRequest authorisationRequest = getCardAuthorisationRequest(String.valueOf(charge.get("amount")), getCard(cardDetails), transactionId);

        CardAuthorisationResponse authorise = paymentProvider.authorise(authorisationRequest);

        if (authorise.getNewChargeStatus() != null) {
            chargeDao.updateStatus(chargeId, authorise.getNewChargeStatus());
        }

        return authorise.isSuccessful() ? Response.noContent().build() : responseWithError(authorise.getErrorMessage());
    }

    private PaymentProvider resolvePaymentProviderFor(Map<String, Object> charge) {
        Optional<Map<String, Object>> maybeAccount = accountDao.findById((String) charge.get("gateway_account_id"));
        String paymentProviderName = (String) maybeAccount.get().get("payment_provider");
        return PaymentProviderFactory.resolve(paymentProviderName).orElseThrow(unsupportedProvider(paymentProviderName));
    }

    private Supplier<RuntimeException> unsupportedProvider(String paymentProviderName) {
        return () -> new RuntimeException("Unsupported PaymentProvider " + paymentProviderName);
    }

    private CardAuthorisationRequest getCardAuthorisationRequest(String amountValue, Card card, String transactionId) {
        String userAgentHeader = "Mozilla/5.0 (Windows; U; Windows NT 5.1;en-GB; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)";
        String acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

        Session session = new Session("123.123.123.123", "0215ui8ib1");
        Browser browser = new Browser(acceptHeader, userAgentHeader);
        Amount amount = new Amount(amountValue);

        String description = "This is mandatory";
        return new CardAuthorisationRequest(card, session, browser, amount, transactionId, description);
    }

    private Card getCard(Map<String, Object> cardDetails) {
        String cvc = (String) cardDetails.get(FIELD_CVC);
        String expiryDate = (String) cardDetails.get(FIELD_EXPIRY_DATE);
        String cardNo = (String) cardDetails.get(FIELD_CARD_NUMBER);
        String cardHoderName = (String) cardDetails.get(FIELD_CARDHOLDER_NAME);

        Address address = getAddress((Map<String, Object>) cardDetails.get("address"));

        return aCard()
                .withCardDetails(cardHoderName, cardNo, cvc, expiryDate)
                .withAddress(address);
    }

    private Address getAddress(Map<String, Object> cardDetails) {
        return anAddress().withLine1((String) cardDetails.get(FIELD_ADDRESS_LINE1))
                .withLine2((String) cardDetails.get(FIELD_ADDRESS_LINE2))
                .withLine2((String) cardDetails.get(FIELD_ADDRESS_LINE3))
                .withZip((String) cardDetails.get(FIELD_ADDRESS_POSTCODE))
                .withCity((String) cardDetails.get(FIELD_ADDRESS_CITY))
                .withCounty((String) cardDetails.get(FIELD_ADDRESS_COUNTY))
                .withCountry((String) cardDetails.get(FIELD_ADDRESS_COUNTRY));
    }

    private boolean hasStatusCreated(Map<String, Object> charge) {
        return ChargeStatus.CREATED.getValue().equals(charge.get(STATUS_KEY));
    }

    private Response responseWithError(String msg) {
        return badRequestResponse(logger, msg);
    }

    private Response responseWithCardAlreadyProcessed(String chargeId) {
        return responseWithError(String.format("Card already processed for charge with id %s.", chargeId));
    }

    static class CardDetailsResourceKeys {
        public static final String FIELD_CVC = "cvc";
        public static final String FIELD_EXPIRY_DATE = "expiry_date";
        public static final String FIELD_CARD_NUMBER = "card_number";
        public static final String FIELD_CARDHOLDER_NAME = "cardholder_name";
        public static final String FIELD_ADDRESS_LINE1 = "line1";
        public static final String FIELD_ADDRESS_LINE2 = "line2";
        public static final String FIELD_ADDRESS_LINE3 = "line3";
        public static final String FIELD_ADDRESS_POSTCODE = "postcode";
        public static final String FIELD_ADDRESS_CITY = "city";
        public static final String FIELD_ADDRESS_COUNTY = "county";
        public static final String FIELD_ADDRESS_COUNTRY = "country";
    }
}
