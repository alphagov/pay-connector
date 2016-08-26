package uk.gov.pay.connector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeExpiredRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

public abstract class CardService<T extends BaseResponse> {
    protected final ChargeDao chargeDao;
    protected final PaymentProviders providers;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected CardExecutorService cardExecutorService;
    protected ConfirmationDetailsService confirmationDetailsService;

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

    public CardService(ChargeDao chargeDao, PaymentProviders providers, ConfirmationDetailsService confirmationDetailsService) {
        this.chargeDao = chargeDao;
        this.providers = providers;
        this.confirmationDetailsService = confirmationDetailsService;
    }

    public CardService(ChargeDao chargeDao, PaymentProviders providers, ConfirmationDetailsService confirmationDetailsService, CardExecutorService cardExecutorService) {
        this(chargeDao, providers, confirmationDetailsService);
        this.cardExecutorService = cardExecutorService;
    }

    public ChargeEntity preOperation(ChargeEntity chargeEntity, OperationType operationType, ChargeStatus[] legalStatuses, ChargeStatus lockingStatus) {
        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

        logger.info(format("Card pre-operation - operation_type=%s, charge_external_id=%s, locking_status=%s",
                operationType.getValue(),
                chargeEntity.getExternalId(),
                lockingStatus));

        if (reloadedCharge.hasStatus(ChargeStatus.EXPIRED)) {
            throw new ChargeExpiredRuntimeException(operationType.getValue(), reloadedCharge.getExternalId());
        }
        if (!reloadedCharge.hasStatus(legalStatuses)) {
            if (reloadedCharge.hasStatus(lockingStatus)) {
                throw new OperationAlreadyInProgressRuntimeException(operationType.getValue(), reloadedCharge.getExternalId());
            }
            logger.error(format("Charge with id [%s] and with status [%s] should be in one of the following legal states, [%s]",
                    reloadedCharge.getExternalId(), reloadedCharge.getStatus(), getLegalStatusNames(legalStatuses)));
            throw new IllegalStateRuntimeException(reloadedCharge.getExternalId());
        }
        reloadedCharge.setStatus(lockingStatus);
        //todo do we want to store info in case capture fails? Then we might have to move this to postOperation
        confirmationDetailsService.doRemove(reloadedCharge);
        return reloadedCharge;
    }

    private String getLegalStatusNames(ChargeStatus[] legalStatuses) {
        return Stream.of(legalStatuses).map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }

    public PaymentProvider<T> getPaymentProviderFor(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName());
    }
}
