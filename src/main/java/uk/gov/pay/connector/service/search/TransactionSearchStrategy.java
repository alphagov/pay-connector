package uk.gov.pay.connector.service.search;

import com.google.inject.Inject;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.TransactionDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.ChargeResponse.RefundSummary;
import uk.gov.pay.connector.model.TransactionResponse.TransactionResponseBuilder;
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
import java.util.Map;

import static javax.ws.rs.HttpMethod.GET;
import static uk.gov.pay.connector.model.TransactionResponse.aTransactionResponseBuilder;

public class TransactionSearchStrategy extends AbstractSearchStrategy<Transaction> implements SearchStrategy {

    private TransactionDao transactionDao;

    @Inject
    public TransactionSearchStrategy(TransactionDao transactionDao, CardTypeDao cardTypeDao) {
        super(cardTypeDao);
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
    protected ChargeResponse buildResponse(UriInfo uriInfo, Transaction transaction, Map<String, String> cardBrandToLabel) {
        ExternalTransactionState externalTransactionState;
        RefundSummary refundSummary = null;
        if (TransactionType.REFUND.getValue().equals(transaction.getTransactionType())) {
            ExternalRefundStatus externalRefundStatus = RefundStatus.fromString(transaction.getStatus()).toExternal();
            externalTransactionState = new ExternalTransactionState(externalRefundStatus.getStatus(), externalRefundStatus.isFinished());
            refundSummary = buildRefundSummary(transaction);
        } else {
            ExternalChargeState externalChargeState = ChargeStatus.fromString(transaction.getStatus()).toExternal();
            externalTransactionState = new ExternalTransactionState(externalChargeState.getStatusV2(), externalChargeState.isFinished(), externalChargeState.getCode(), externalChargeState.getMessage());
        }

        PersistedCard cardDetails = new PersistedCard();
        cardDetails.setCardBrand(transaction.getCardBrandLabel());
        cardDetails.setCardHolderName(transaction.getCardHolderName());
        cardDetails.setExpiryDate(transaction.getExpiryDate());
        cardDetails.setLastDigitsCardNumber(transaction.getLastDigitsCardNumber());

        TransactionResponseBuilder transactionResponseBuilder = aTransactionResponseBuilder()
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
                .withLanguage(transaction.getLanguage())
                .withDelayedCapture(transaction.isDelayedCapture())
                .withLink("self", GET, uriInfo.getBaseUriBuilder()
                        .path("/v1/api/accounts/{accountId}/charges/{chargeId}")
                        .build(transaction.getGatewayAccountId(), transaction.getExternalId()))
                .withLink("refunds", GET, uriInfo.getBaseUriBuilder()
                        .path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds")
                        .build(transaction.getGatewayAccountId(), transaction.getExternalId()));
        if (refundSummary != null) {
            transactionResponseBuilder.withRefunds(refundSummary);
        }
        return transactionResponseBuilder.build();
    }

    private ChargeResponse.RefundSummary buildRefundSummary(Transaction transaction) {
        ChargeResponse.RefundSummary refund = new ChargeResponse.RefundSummary();
        refund.setStatus(transaction.getStatus());
        refund.setUserExternalId(transaction.getUserExternalId());
        return refund;
    }
}
