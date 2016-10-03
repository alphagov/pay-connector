package uk.gov.pay.connector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeExpiredRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.gov.pay.connector.model.domain.ChargeStatus.fromString;

public abstract class CardService<T extends BaseResponse> {
    protected final ChargeDao chargeDao;
    private final PaymentProviders providers;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected CardExecutorService cardExecutorService;
    protected ConfirmationDetailsService confirmationDetailsService;

    public enum OperationType {
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
        GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();

        logger.info("Card pre-operation - charge_external_id={}, charge_status={}, account_id={}, amount={}, operation_type={}, provider={}, provider_type={}, locking_status={}",
                chargeEntity.getExternalId(),
                fromString(chargeEntity.getStatus()),
                gatewayAccount.getId(),
                chargeEntity.getAmount(),
                operationType.getValue(),
                gatewayAccount.getGatewayName(),
                gatewayAccount.getType(),
                lockingStatus);

        if (reloadedCharge.hasStatus(ChargeStatus.EXPIRED)) {
            throw new ChargeExpiredRuntimeException(operationType.getValue(), reloadedCharge.getExternalId());
        }
        if (!reloadedCharge.hasStatus(legalStatuses)) {
            if (reloadedCharge.hasStatus(lockingStatus)) {
                throw new OperationAlreadyInProgressRuntimeException(operationType.getValue(), reloadedCharge.getExternalId());
            }
            logger.error("Charge is not in a legal status to do the pre-operation - charge_external_id={}, status={}, legal_states={}",
                    reloadedCharge.getExternalId(), reloadedCharge.getStatus(), getLegalStatusNames(legalStatuses));
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
