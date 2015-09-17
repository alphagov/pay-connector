package uk.gov.pay.connector.resources;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.model.Browser;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.model.CardAuthorisationRequest;
import uk.gov.pay.connector.model.CardAuthorisationResponse;
import uk.gov.pay.connector.model.ChargeStatus;
import uk.gov.pay.connector.model.Session;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviderFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.ChargeStatus.STATUS_KEY;
import static uk.gov.pay.connector.resources.CardDetailsValidator.isWellFormattedCardDetails;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class CardResource {

    private final Logger logger = LoggerFactory.getLogger(CardResource.class);
    private final GatewayAccountDao accountDao;
    private final ChargeDao chargeDao;

    public CardResource(ChargeDao chargeDao, GatewayAccountDao accountDao) {
        this.accountDao = accountDao;
        this.chargeDao = chargeDao;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/cards")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response authoriseCharge(@PathParam("chargeId") String chargeId, Card cardDetails) throws PayDBIException {

        if (!isWellFormattedCardDetails(cardDetails)) {
            return responseWithError("Values do not match expected format/length.");
        }

        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        if (!maybeCharge.isPresent()) {
            return responseWithChargeNotFound(logger, chargeId);
        }

        Map<String, Object> charge = maybeCharge.get();
        if (!hasStatus(charge, CREATED)) {
            return responseWithCardAlreadyProcessed(chargeId);
        }

        return authoriseCard(chargeId, cardDetails, charge);
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/capture")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureCharge(@PathParam("chargeId") String chargeId) throws PayDBIException {

        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        if (!maybeCharge.isPresent()) {
            return responseWithChargeNotFound(logger, chargeId);
        }

        Map<String, Object> charge = maybeCharge.get();
        if (!hasStatus(charge, AUTHORISATION_SUCCESS)) {
            return responseWithError(format("Cannot capture a charge with status %s.", (String) charge.get(STATUS_KEY)));
        }

        return capture(chargeId, charge);
    }

    private Response capture(String chargeId, Map<String, Object> charge) throws PayDBIException {

        PaymentProvider paymentProvider = resolvePaymentProviderFor(charge);

        CaptureRequest request = new CaptureRequest(new Amount(String.valueOf(charge.get("amount"))), chargeId);
        CaptureResponse captureResponse = paymentProvider.capture(request);

        if (captureResponse.isSuccessful()) {
            chargeDao.updateStatus(chargeId, CAPTURED);
            return Response.noContent().build();
        }

        return responseWithError("Capture error :"+captureResponse.getErrorMessage());
    }

    private Response authoriseCard(String chargeId, Card cardDetails, Map<String, Object> charge) throws PayDBIException {

        PaymentProvider paymentProvider = resolvePaymentProviderFor(charge);

        String transactionId = randomUUID().toString(); //TODO: which transationId
        CardAuthorisationRequest authorisationRequest = getCardAuthorisationRequest(String.valueOf(charge.get("amount")), cardDetails, transactionId);

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

    private boolean hasStatus(Map<String, Object> charge, ChargeStatus status) {
        return status.getValue().equals(charge.get(STATUS_KEY));
    }

    private Response responseWithError(String msg) {
        return badRequestResponse(logger, msg);
    }

    private Response responseWithCardAlreadyProcessed(String chargeId) {
        return responseWithError(String.format("Card already processed for charge with id %s.", chargeId));
    }

}
