package uk.gov.pay.connector.service;

import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.IGatewayAccountDao;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccount;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static uk.gov.pay.connector.model.CancelRequest.cancelRequest;
import static uk.gov.pay.connector.model.CaptureRequest.captureRequest;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;
import static uk.gov.pay.connector.model.GatewayErrorType.ChargeNotFound;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardService {
    private static final String GATEWAY_ACCOUNT_ID_KEY = "gateway_account_id";
    private static final String GATEWAY_TRANSACTION_ID_KEY = "gateway_transaction_id";
    private static final String AMOUNT_KEY = "amount";

    private final Logger logger = LoggerFactory.getLogger(CardService.class);

    private static final ChargeStatus[] CANCELLABLE_STATES = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS, AUTHORISATION_SUBMITTED, READY_FOR_CAPTURE
    };

    private final IGatewayAccountDao accountDao;
    private final ChargeDao chargeDao;
    private final PaymentProviders providers;

    public CardService(IGatewayAccountDao accountDao, ChargeDao chargeDao, PaymentProviders providers) {
        this.accountDao = accountDao;
        this.chargeDao = chargeDao;
        this.providers = providers;
    }

    public Either<GatewayError, GatewayResponse> doAuthorise(String chargeId, Card cardDetails) {
        return chargeDao
                .findById(chargeId)
                .map(authoriseFor(chargeId, cardDetails))
                .orElseGet(chargeNotFound(chargeId));
    }

    public Either<GatewayError, GatewayResponse> doCapture(String chargeId) {
        return chargeDao
                .findById(chargeId)
                .map(captureFor(chargeId))
                .orElseGet(chargeNotFound(chargeId));
    }

    public Either<GatewayError, GatewayResponse> doCancel(String chargeId, String accountId) {
        return chargeDao
                .findChargeForAccount(chargeId, accountId)
                .map(cancelFor(chargeId))
                .orElseGet(chargeNotFound(chargeId));
    }

    private Function<Map<String, Object>, Either<GatewayError, GatewayResponse>> captureFor(String chargeId) {
        return charge -> hasStatus(charge, AUTHORISATION_SUCCESS) ?
                right(captureFor(chargeId, charge)) :
                left(captureErrorMessageFor((String) charge.get(STATUS_KEY)));
    }

    private Function<Map<String, Object>, Either<GatewayError, GatewayResponse>> authoriseFor(String chargeId, Card cardDetails) {
        return charge -> hasStatus(charge, ENTERING_CARD_DETAILS) ?
                right(authoriseFor(chargeId, cardDetails, charge)) :
                left(authoriseErrorMessageFor(chargeId));
    }

    private Function<Map<String, Object>, Either<GatewayError, GatewayResponse>> cancelFor(String chargeId) {
        return charge -> hasStatus(charge, CANCELLABLE_STATES) ?
                right(cancelFor(chargeId, charge)) :
                left(cancelErrorMessageFor(chargeId, (String) charge.get(STATUS_KEY)));
    }

    private GatewayResponse captureFor(String chargeId, Map<String, Object> charge) {
        String transactionId = String.valueOf(charge.get(GATEWAY_TRANSACTION_ID_KEY));
        CaptureRequest request = captureRequest(transactionId, String.valueOf(charge.get(AMOUNT_KEY)), getValidAccountForCharge().apply(chargeId));
        CaptureResponse response = paymentProviderFor(charge)
                .capture(request);

        ChargeStatus newStatus =
                response.isSuccessful() ?
                        CAPTURE_SUBMITTED :
                        CAPTURE_UNKNOWN;

        chargeDao.updateStatus(chargeId, newStatus);

        return response;
    }

    private Optional<GatewayAccount> findAccountByCharge(String chargeId) {
        Optional<Map<String, Object>> charge = chargeDao.findById(chargeId);
        if (!charge.isPresent()) {
            String errorMessage = String.format("No charge exists for this charge id %s.", chargeId);
            logger.error(errorMessage);
            return Optional.empty();
        }
        return accountDao.findById((String) charge.get().get("gateway_account_id"));
    }

    private GatewayResponse authoriseFor(String chargeId, Card cardDetails, Map<String, Object> charge) {

        AuthorisationRequest request = authorisationRequest(chargeId, String.valueOf(charge.get(AMOUNT_KEY)), cardDetails);
        AuthorisationResponse response = paymentProviderFor(charge)
                .authorise(request);

        if (response.getNewChargeStatus() != null) {
            chargeDao.updateStatus(chargeId, response.getNewChargeStatus());
            chargeDao.updateGatewayTransactionId(chargeId, response.getTransactionId());
        }

        return response;
    }

    private GatewayResponse cancelFor(String chargeId, Map<String, Object> charge) {
        CancelRequest request = cancelRequest(String.valueOf(charge.get(GATEWAY_TRANSACTION_ID_KEY)), getValidAccountForCharge().apply(chargeId));
        CancelResponse response = paymentProviderFor(charge).cancel(request);

        if (response.isSuccessful()) {
            chargeDao.updateStatus(chargeId, SYSTEM_CANCELLED);
        }
        return response;
    }

    private PaymentProvider paymentProviderFor(Map<String, Object> charge) {
        Optional<GatewayAccount> maybeAccount = accountDao.findById((String) charge.get(GATEWAY_ACCOUNT_ID_KEY));
        return providers.resolve(maybeAccount.get().getGatewayName());
    }

    private AuthorisationRequest authorisationRequest(String chargeId, String amountValue, Card card) {
        return new AuthorisationRequest(chargeId, card, amountValue, "This is the description", getValidAccountForCharge().apply(chargeId));
    }

    private Function<String, GatewayAccount> getValidAccountForCharge() {
        return chargeId -> {
            Optional<GatewayAccount> optionalServiceAccount = findAccountByCharge(chargeId);
            if (!optionalServiceAccount.isPresent()) {
                String errorMessage = String.format("No account exists for this charge %s.", chargeId);
                logger.error(errorMessage);
                throw new RuntimeException(errorMessage);
            }
            return optionalServiceAccount.get();
        };
    }

    private boolean hasStatus(Map<String, Object> charge, ChargeStatus... states) {
        Object currentStatus = charge.get(STATUS_KEY);
        return Arrays.stream(states)
                .anyMatch(status -> equalsIgnoreCase(status.getValue(), currentStatus.toString()));
    }

    private GatewayError captureErrorMessageFor(String currentStatus) {
        return baseGatewayError(format("Cannot capture a charge with status %s.", currentStatus));
    }

    private GatewayError authoriseErrorMessageFor(String chargeId) {
        return baseGatewayError(format("Charge not in correct state to be processed, %s", chargeId));
    }

    private GatewayError cancelErrorMessageFor(String chargeId, String status) {
        return baseGatewayError(format("Cannot cancel a charge id [%s]: status is [%s].", chargeId, status));
    }

    private Supplier<Either<GatewayError, GatewayResponse>> chargeNotFound(String chargeId) {
        return () -> left(new GatewayError(format("Charge with id [%s] not found.", chargeId), ChargeNotFound));
    }
}
