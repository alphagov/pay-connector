package uk.gov.pay.connector.service.search;

import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.TransactionDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.TransactionResponse;
import uk.gov.pay.connector.model.TransactionType;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.model.api.ExternalTransactionState;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.PersistedCard;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.Transaction;
import uk.gov.pay.connector.model.domain.transaction.TransactionOperation;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.HttpMethod.GET;

public class TransactionSearchStrategy extends AbstractSearchStrategy<Transaction> {

    private final TransactionDao transactionDao;

    @Inject
    public TransactionSearchStrategy(TransactionDao transactionDao, CardTypeDao cardTypeDao) {
        super(cardTypeDao);
        this.transactionDao = transactionDao;
    }

    @Override
    protected long getTotalFor(ChargeSearchParams params) {
        return transactionDao.getTotal(params);
    }

    @Override
    protected List<Transaction> findAllBy(ChargeSearchParams params) {
        return transactionDao.search(params);
    }

    @Override
    protected ChargeResponse buildResponse(UriInfo uriInfo, Transaction transaction, Map<String, String> cardBrandToLabel) {
        ExternalTransactionState externalTransactionState;
        TransactionType transactionType;
        if (transaction.getTransactionType().equals(TransactionOperation.CHARGE.name())) {
            ExternalChargeState externalChargeState = ChargeStatus.valueOf(ChargeStatus.class, transaction.getStatus()).toExternal();
            externalTransactionState = new ExternalTransactionState(externalChargeState.getStatus(), externalChargeState.isFinished(), externalChargeState.getCode(), externalChargeState.getMessage());
            transactionType = TransactionType.PAYMENT;
        } else {
            ExternalRefundStatus externalRefundStatus = RefundStatus.valueOf(RefundStatus.class, transaction.getStatus()).toExternal();
            externalTransactionState = new ExternalTransactionState(externalRefundStatus.getStatus(), externalRefundStatus.isFinished());
            transactionType = TransactionType.REFUND;
        }

        PersistedCard cardDetails = new PersistedCard();
        cardDetails.setCardBrand(cardBrandToLabel.get(transaction.getCardBrand()));
        cardDetails.setCardHolderName(transaction.getCardHolderName());
        cardDetails.setExpiryDate(transaction.getExpiryDate());
        cardDetails.setLastDigitsCardNumber(transaction.getLastDigitsCardNumber());

        return TransactionResponse.aTransactionResponseBuilder()
                .withChargeId(transaction.getExternalId())
                .withAmount(transaction.getAmount())
                .withReference(transaction.getReference())
                .withDescription(transaction.getDescription())
                .withState(externalTransactionState)
                .withGatewayTransactionId(transaction.getGatewayTransactionId())
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(transaction.getCreatedDate()))
                .withEmail(transaction.getEmail())
                .withCardDetails(cardDetails)
                .withTransactionType(transactionType.getValue())
                .withLink("self", GET, uriInfo.getBaseUriBuilder()
                        .path("/v1/api/accounts/{accountId}/charges/{chargeId}")
                        .build(transaction.getGatewayAccountId(), transaction.getExternalId()))
                .withLink("refunds", GET, uriInfo.getBaseUriBuilder()
                        .path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds")
                        .build(transaction.getGatewayAccountId(), transaction.getExternalId()))
                .build();
    }
}
