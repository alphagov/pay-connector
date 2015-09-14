package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.model.Browser;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.model.CardAuthorisationRequest;
import uk.gov.pay.connector.model.CardAuthorisationResponse;
import uk.gov.pay.connector.model.ChargeStatus;
import uk.gov.pay.connector.model.GatewayAccount;
import uk.gov.pay.connector.model.Session;
import uk.gov.pay.connector.service.GatewayResolverFactory;
import uk.gov.pay.connector.service.PaymentProvider;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.Card.aCard;
import static uk.gov.pay.connector.model.ChargeStatus.STATUS_KEY;
import static uk.gov.pay.connector.resources.CardDetailsValidator.CARD_NUMBER_FIELD;
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

        CardAuthorisationRequest authorisationRequest = getCardAuthorisationRequest(String.valueOf(charge.get("amount")), getCard(cardDetails));

        GatewayAccount gatewayAccount = new GatewayAccount();
        
        CardAuthorisationResponse authorise = paymentProvider.authorise(gatewayAccount, authorisationRequest);

        if (authorise.getNewChargeStatus() != null) {
            chargeDao.updateStatus(chargeId, authorise.getNewChargeStatus());
        }

        return authorise.isSuccessful() ? Response.noContent().build() : responseWithError(authorise.getErrorMessage());
    }

    private PaymentProvider resolvePaymentProviderFor(Map<String, Object> charge) {
        Optional<Map<String, Object>> maybeAccount = accountDao.findById((String) charge.get("gateway_account_id"));
        String paymentProviderName = (String) maybeAccount.get().get("payment_provider");
        return GatewayResolverFactory.resolve(paymentProviderName);
    }

    private CardAuthorisationRequest getCardAuthorisationRequest(String amountValue, Card card) {
        String userAgentHeader = "Mozilla/5.0 (Windows; U; Windows NT 5.1;en-GB; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)";
        String acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

        Session session = new Session("123.123.123.123", "0215ui8ib1");
        Browser browser = new Browser(acceptHeader, userAgentHeader);
        Amount amount = new Amount(amountValue);

        String transactionId = "MyUniqueTransactionId!";
        String description = "This is mandatory";
        return new CardAuthorisationRequest(card, session, browser, amount, transactionId, description);
    }

    private Card getCard(Map<String, Object> cardDetails) {
        String cvc = (String) cardDetails.get("cvc");
        String expiryDate = (String) cardDetails.get("expiry_date");
        String cardNo = (String) cardDetails.get(CARD_NUMBER_FIELD);

        return aCard()
                .withCardDetails("Mr. Payment", cardNo, cvc, expiryDate)
                .withAddressLine1("123 My Street")
                .withAddressLine2("This road")
                .withAddressZip("SW8URR")
                .withAddressCity("London")
                .withAddressState("London state");
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
}
