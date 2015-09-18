package uk.gov.pay.connector.service;

import fj.data.Either;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.Amount;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.model.CaptureRequest.captureRequest;
import static uk.gov.pay.connector.model.GatewayErrorType.BaseGatewayError;
import static uk.gov.pay.connector.model.GatewayErrorType.ChargeNotFound;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.STATUS_KEY;

public class CardProcessor {
    private final GatewayAccountDao accountDao;
    private final ChargeDao chargeDao;

    public CardProcessor(GatewayAccountDao accountDao, ChargeDao chargeDao) {
        this.accountDao = accountDao;
        this.chargeDao = chargeDao;
    }

    public Either<GatewayResponse, GatewayError> doAuthorise(String chargeId, Card cardDetails) throws PayDBIException {

        return chargeDao
                .findById(chargeId)
                .map(authoriseFor(chargeId, cardDetails))
                .orElseGet(chargeNotFound(chargeId));
    }

    public Either<GatewayResponse, GatewayError> doCapture(String chargeId) throws PayDBIException {

        return chargeDao
                .findById(chargeId)
                .map(captureFor(chargeId))
                .orElseGet(chargeNotFound(chargeId));
    }

    private Function<Map<String, Object>, Either<GatewayResponse, GatewayError>> captureFor(String chargeId) {
        return (charge) -> hasStatus(charge, AUTHORISATION_SUCCESS) ?
                left(capture(chargeId, charge)) :
                right(captureErrorMessageFor((String) charge.get(STATUS_KEY)));
    }

    private Function<Map<String, Object>, Either<GatewayResponse, GatewayError>> authoriseFor(String chargeId, Card cardDetails) {
        return (charge) -> hasStatus(charge, CREATED) ?
                left(authoriseCard(chargeId, cardDetails, charge)) :
                right(authoriseErrorMessageFor(chargeId));
    }


    private Supplier<Either<GatewayResponse, GatewayError>> chargeNotFound(String chargeId) {
        return () -> right(new GatewayError(formattedError("Charge with id [%s] not found.", chargeId), ChargeNotFound));
    }

    private GatewayResponse capture(String chargeId, Map<String, Object> charge) throws PayDBIException {

        CaptureRequest request = captureRequest(chargeId, String.valueOf(charge.get("amount")));
        CaptureResponse capture = paymentProviderFor(charge).capture(request);

        if (capture.isSuccessful()) {
            chargeDao.updateStatus(chargeId, capture.getNewChargeStatus());
        }
        return capture;
    }

    private GatewayResponse authoriseCard(String chargeId, Card cardDetails, Map<String, Object> charge) throws PayDBIException {

        String transactionId = randomUUID().toString(); //TODO: which transationId
        AuthorisationRequest authorisationRequest = getCardAuthorisationRequest(String.valueOf(charge.get("amount")), cardDetails, transactionId);

        AuthorisationResponse authorise = paymentProviderFor(charge).authorise(authorisationRequest);

        if (authorise.getNewChargeStatus() != null) {
            chargeDao.updateStatus(chargeId, authorise.getNewChargeStatus());
        }

        return authorise;
    }

    private PaymentProvider paymentProviderFor(Map<String, Object> charge) {
        Optional<Map<String, Object>> maybeAccount = accountDao.findById((String) charge.get("gateway_account_id"));
        String paymentProviderName = (String) maybeAccount.get().get("payment_provider");
        return PaymentProviderFactory.resolve(paymentProviderName).orElseThrow(unsupportedProvider(paymentProviderName));
    }

    private Supplier<RuntimeException> unsupportedProvider(String paymentProviderName) {
        return () -> new RuntimeException("Unsupported PaymentProvider " + paymentProviderName);
    }

    private AuthorisationRequest getCardAuthorisationRequest(String amountValue, Card card, String transactionId) {
        Amount amount = new Amount(amountValue);

        String description = "This is mandatory";
        return new AuthorisationRequest(card, amount, transactionId, description);
    }

    private boolean hasStatus(Map<String, Object> charge, ChargeStatus status) {
        return status.getValue().equals(charge.get(STATUS_KEY));
    }

    private GatewayError captureErrorMessageFor(String currentStatus) {
        return new GatewayError(formattedError("Cannot capture a charge with status %s.", currentStatus), BaseGatewayError);
    }

    private GatewayError authoriseErrorMessageFor(String chargeId) {
        return new GatewayError(formattedError("Card already processed for charge with id %s.", chargeId), BaseGatewayError);
    }

    private String formattedError(String messageTemplate, String... params) {
        return format(messageTemplate, params);
    }
}
