package uk.gov.pay.connector.service.search;

import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.TransactionDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.TransactionType;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.model.api.ExternalTransactionState;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.PersistedCard;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.Transaction;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.ws.rs.core.UriInfo;
import java.util.List;

import static javax.ws.rs.HttpMethod.GET;
import static uk.gov.pay.connector.model.TransactionResponse.aTransactionResponseBuilder;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.REFUNDS_API_PATH;

public class TransactionSearchStrategy extends AbstractSearchStrategy<Transaction> implements SearchStrategy {

    private TransactionDao transactionDao;

    public TransactionSearchStrategy(TransactionDao transactionDao) {
        this.transactionDao = transactionDao;
    }

    @Override
    protected long getTotalFor(ChargeSearchParams params) {
        return transactionDao.getTotalFor(params.getGatewayAccountId(), params);
    }

    @Override
    protected List<Transaction> findAllBy(ChargeSearchParams params) {
        return transactionDao.findAllBy(params.getGatewayAccountId(), params);
    }

    @Override
    protected ChargeResponse buildResponse(UriInfo uriInfo, Transaction transaction) {

        ExternalTransactionState externalTransactionState;
        if (TransactionType.REFUND.getValue().equals(transaction.getTransactionType())) {
            ExternalRefundStatus externalRefundStatus = RefundStatus.fromString(transaction.getStatus()).toExternal();
            externalTransactionState = new ExternalTransactionState(externalRefundStatus.getStatus(), externalRefundStatus.isFinished());
        } else {
            ExternalChargeState externalChargeState = ChargeStatus.fromString(transaction.getStatus()).toExternal();
            externalTransactionState = new ExternalTransactionState(externalChargeState.getStatus(), externalChargeState.isFinished(), externalChargeState.getCode(), externalChargeState.getMessage());
        }

        PersistedCard cardDetails = new PersistedCard();
        cardDetails.setCardBrand(transaction.getCardBrandLabel());
        cardDetails.setCardHolderName(transaction.getCardHolderName());
        cardDetails.setExpiryDate(transaction.getExpiryDate());
        cardDetails.setLastDigitsCardNumber(transaction.getLastDigitsCardNumber());

        return aTransactionResponseBuilder()
                .withTransactionType(transaction.getTransactionType())
                .withAmount(transaction.getAmount())
                .withState(externalTransactionState)
                .withCardDetails(cardDetails)
                .withChargeId(transaction.getExternalId())
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(transaction.getCreatedDate()))
                .withDescription(transaction.getDescription())
                .withReference(transaction.getReference())
                .withEmail(transaction.getEmail())
                .withGatewayTransactionId(transaction.getGatewayTransactionId())
                .withLink("self", GET, uriInfo.getBaseUriBuilder()
                        .path(CHARGE_API_PATH)
                        .build(transaction.getGatewayAccountId(), transaction.getExternalId()))
                .withLink("refunds", GET, uriInfo.getBaseUriBuilder()
                        .path(REFUNDS_API_PATH)
                        .build(transaction.getGatewayAccountId(), transaction.getExternalId()))
                .build();
    }
}
