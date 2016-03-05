package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.IChargeDao;
import uk.gov.pay.connector.dao.ChargeJpaDao;
import uk.gov.pay.connector.dao.GatewayAccountJpaDao;
import uk.gov.pay.connector.dao.IGatewayAccountDao;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.*;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;
import static uk.gov.pay.connector.model.CancelRequest.cancelRequest;
import static uk.gov.pay.connector.model.CaptureRequest.captureRequest;
import static uk.gov.pay.connector.model.GatewayError.*;
import static uk.gov.pay.connector.model.GatewayErrorType.ChargeNotFound;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardService {
    private final Logger logger = LoggerFactory.getLogger(CardService.class);

    private static final ChargeStatus[] CANCELLABLE_STATES = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS, AUTHORISATION_READY, READY_FOR_CAPTURE
    };

    private final GatewayAccountJpaDao accountDao;
    private final ChargeJpaDao chargeDao;
    private final PaymentProviders providers;

    @Inject
    public CardService(GatewayAccountJpaDao accountDao, ChargeJpaDao chargeDao, PaymentProviders providers) {
        this.accountDao = accountDao;
        this.chargeDao = chargeDao;
        this.providers = providers;
    }

    public Either<GatewayError, GatewayResponse> doAuthorise(Long chargeId, Card cardDetails) {

        Function<ChargeEntity, Either<GatewayError, GatewayResponse>> doAuthorise =
                (charge) -> {
                    Either<GatewayError, ChargeEntity> preAuthorised = preAuthorise(charge);
                    if (preAuthorised.isLeft())
                        return left(preAuthorised.left().value());

                    Either<GatewayError, AuthorisationResponse> authorised =
                            authorise(preAuthorised.right().value(), cardDetails);
                    if (authorised.isLeft())
                        return left(authorised.left().value());

                    Either<GatewayError, GatewayResponse> postAuthorised =
                            postAuthorise(preAuthorised.right().value(), authorised.right().value());
                    if (postAuthorised.isLeft())
                        return left(postAuthorised.left().value());

                    return right(authorised.right().value());
                };

        return chargeDao
                .findById(chargeId)
                .map(doAuthorise)
                .orElseGet(chargeNotFound(chargeId));
    }

    @Transactional
    public Either<GatewayError, ChargeEntity> preAuthorise(ChargeEntity charge) {
        ChargeEntity reloadedCharge = chargeDao.merge(charge);
        if (!hasStatus(reloadedCharge, ENTERING_CARD_DETAILS)) {
            if (hasStatus(reloadedCharge, AUTHORISATION_READY)) {
                return left(operationAlreadyInProgress(format("Authorisation for charge already in progress, %s",
                        reloadedCharge.getId())));
            }
            logger.error(format("Charge with id [%s] and with status [%s] should be in [ENTERING CARD DETAILS] for authorisation.",
                    reloadedCharge.getId(), reloadedCharge.getStatus()));
            return left(illegalStateError(format("Charge not in correct state to be processed, %s", reloadedCharge.getId())));
        }
        reloadedCharge.setStatus(AUTHORISATION_READY);

        return right(reloadedCharge);
    }

    private Either<GatewayError, AuthorisationResponse> authorise(ChargeEntity charge, Card cardDetails) {
        PaymentProvider paymentProvider = paymentProviderFor(charge);
        AuthorisationRequest request = authorisationRequest(charge.getId(), charge.getAmount(), cardDetails);
        AuthorisationResponse response = paymentProvider.authorise(request);
        return right(response);
    }

    @Transactional
    public Either<GatewayError, GatewayResponse> postAuthorise(ChargeEntity charge, AuthorisationResponse response) {
        ChargeEntity reloadedCharge = chargeDao.merge(charge);
        reloadedCharge.setStatus(response.getNewChargeStatus());
        reloadedCharge.setGatewayTransactionId(response.getTransactionId());

        return right(response);
    }

    public Either<GatewayError, GatewayResponse> doCapture(Long chargeId) {
        return chargeDao
                .findById(Long.valueOf(chargeId))
                .map(capture())
                .orElseGet(chargeNotFound(chargeId));
    }

    public Either<GatewayError, GatewayResponse> doCancel(Long chargeId, Long accountId) {
        return chargeDao
                .findChargeForAccount(Long.valueOf(chargeId), accountId)
                .map(cancel())
                .orElseGet(chargeNotFound(chargeId));
    }

    private Function<ChargeEntity, Either<GatewayError, GatewayResponse>> capture() {
        return charge -> hasStatus(charge, AUTHORISATION_SUCCESS) ?
                right(captureFor(charge)) :
                left(captureErrorMessageFor(charge.getStatus()));
    }

    private Function<ChargeEntity, Either<GatewayError, GatewayResponse>> authorise(Card cardDetails) {
        return charge -> hasStatus(charge, ENTERING_CARD_DETAILS) ?
                right(authoriseFor(cardDetails, charge)) :
                left(authoriseErrorMessageFor(charge.getId()));
    }

    private Function<ChargeEntity, Either<GatewayError, GatewayResponse>> cancel() {
        return charge -> hasStatus(charge, CANCELLABLE_STATES) ?
                right(cancelFor(charge)) :
                left(cancelErrorMessageFor(String.valueOf(charge.getId()), charge.getStatus()));
    }

    private GatewayResponse captureFor(ChargeEntity charge) {
        CaptureRequest request = CaptureRequest.valueOf(charge);
        CaptureResponse response = paymentProviderFor(charge)
                .capture(request);

        ChargeStatus newStatus =
                response.isSuccessful() ?
                        CAPTURE_SUBMITTED :
                        CAPTURE_UNKNOWN;

        charge.setStatus(newStatus);
        chargeDao.mergeChargeEntityWithChangedStatus(charge);

        return response;
    }

    private GatewayResponse authoriseFor(Card cardDetails, ChargeEntity charge) {

        AuthorisationRequest request = new AuthorisationRequest(charge, cardDetails);
        AuthorisationResponse response = paymentProviderFor(charge)
                .authorise(request);

        if (response.getNewChargeStatus() != null) {
            charge.setStatus(response.getNewChargeStatus());
            charge.setGatewayTransactionId(response.getTransactionId());
            chargeDao.mergeChargeEntityWithChangedStatus(charge);
        }

        return response;
    }

    private GatewayResponse cancelFor(ChargeEntity charge) {
        CancelRequest request = CancelRequest.valueOf(charge);
        CancelResponse response = paymentProviderFor(charge).cancel(request);

        if (response.isSuccessful()) {
            charge.setStatus(SYSTEM_CANCELLED);
            chargeDao.mergeChargeEntityWithChangedStatus(charge);
        }
        return response;
    }

    private PaymentProvider paymentProviderFor(ChargeEntity charge) {
        Optional<GatewayAccountEntity> maybeAccount = accountDao.findById(charge.getGatewayAccount().getId());
        return providers.resolve(maybeAccount.get().getGatewayName());
    }

    private AuthorisationRequest authorisationRequest(Long chargeId, Long amountValue, Card card) {
        return new AuthorisationRequest(chargeId.toString(), card, amountValue.toString(),
                "This is the description", getValidAccountForCharge().apply(chargeId));
    }

    private Function<Long, GatewayAccountEntity> getValidAccountForCharge() {
        return chargeId -> {
            GatewayAccountEntity gatewayAccount = findAccountByCharge(chargeId);
            if (gatewayAccount == null) {
                String errorMessage = String.format("No account exists for this charge %s.", chargeId);
                logger.error(errorMessage);
                throw new RuntimeException(errorMessage);
            }
            return gatewayAccount;
        };
    }

    private boolean hasStatus(ChargeEntity charge, ChargeStatus... states) {
        return Arrays.stream(states)
                .anyMatch(status -> equalsIgnoreCase(status.getValue(), charge.getStatus()));
    }

    private GatewayError captureErrorMessageFor(String currentStatus) {
        return baseGatewayError(format("Cannot capture a charge with status %s.", currentStatus));
    }

    private GatewayError authoriseErrorMessageFor(Long chargeId) {
        return baseGatewayError(format("Charge not in correct state to be processed, %d", chargeId));
    }

    private GatewayError cancelErrorMessageFor(Long chargeId, String status) {
        return baseGatewayError(format("Cannot cancel a charge id [%s]: status is [%s].", chargeId, status));
    }

    private Supplier<Either<GatewayError, GatewayResponse>> chargeNotFound(Long chargeId) {
        return () -> left(new GatewayError(format("Charge with id [%s] not found.", chargeId), ChargeNotFound));
    }
}
