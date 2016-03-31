package uk.gov.pay.connector.service;

import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.resources.CardExecutorService;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.*;

abstract public class CardService {
    protected final ChargeDao chargeDao;
    protected final PaymentProviders providers;
    private final Logger logger = LoggerFactory.getLogger(CardCancelService.class);
    protected CardExecutorService cardExecutorService;

    protected enum OperationType {
        CAPTURE("Capture"),
        AUTHORISATION("Authorisation"),
        CANCELLATION("Cancellation");

        private String value;

        OperationType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public CardService(ChargeDao chargeDao, PaymentProviders providers) {
        this.chargeDao = chargeDao;
        this.providers = providers;
    }

    public CardService(ChargeDao chargeDao, PaymentProviders providers, CardExecutorService cardExecutorService) {
        this.chargeDao = chargeDao;
        this.providers = providers;
        this.cardExecutorService = cardExecutorService;
    }

    public Either<ErrorResponse, ChargeEntity> preOperation(ChargeEntity chargeEntity, OperationType operationType, ChargeStatus[] legalStatuses, ChargeStatus lockingStatus) {
        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
        if (reloadedCharge.hasStatus(ChargeStatus.EXPIRED)) {
            return left(chargeExpired(format("%s for charge failed as already expired, %s", operationType.getValue(), reloadedCharge.getExternalId())));
        }
        if (!reloadedCharge.hasStatus(legalStatuses)) {
            if (reloadedCharge.hasStatus(lockingStatus)) {
                return left(operationAlreadyInProgress(format("%s for charge already in progress, %s",
                        operationType.getValue(), reloadedCharge.getExternalId())));
            }
            logger.error(format("Charge with id [%s] and with status [%s] should be in one of the following legal states, [%s]",
                    reloadedCharge.getId(), reloadedCharge.getStatus(), legalStatuses));
            return left(illegalStateError(format("Charge not in correct state to be processed, %s", reloadedCharge.getExternalId())));
        }
        reloadedCharge.setStatus(lockingStatus);

        return right(reloadedCharge);
    }

    public PaymentProvider getPaymentProviderFor(ChargeEntity chargeEntity) {
        return providers.resolve(chargeEntity.getGatewayAccount().getGatewayName());
    }

}
