package uk.gov.pay.connector.charge.service;

import com.google.inject.Inject;
import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.charge.dao.TransactionDao;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.ChargeResponse.RefundSummary;
import uk.gov.pay.connector.charge.model.TransactionResponse.TransactionResponseBuilder;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;
import uk.gov.pay.connector.charge.model.domain.Transaction;
import uk.gov.pay.connector.charge.model.domain.TransactionType;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.common.service.search.AbstractSearchStrategy;
import uk.gov.pay.connector.common.service.search.SearchStrategy;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import javax.ws.rs.core.UriInfo;
import java.util.List;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static uk.gov.pay.connector.charge.model.TransactionResponse.aTransactionResponseBuilder;

public class TransactionSearchStrategy extends AbstractSearchStrategy<Transaction, ChargeResponse> implements SearchStrategy {

    private TransactionDao transactionDao;

    @Inject
    public TransactionSearchStrategy(TransactionDao transactionDao) {
        this.transactionDao = transactionDao;
    }

    @Override
    public long getTotalFor(SearchParams params) {
        return transactionDao.getTotalFor(params.getGatewayAccountId(), params);
    }

    @Override
    public List<Transaction> findAllBy(SearchParams params) {
        return transactionDao.findAllBy(params.getGatewayAccountId(), params);
    }

    @Override
    public ChargeResponse buildResponse(UriInfo uriInfo, Transaction transaction) {
        ExternalTransactionState externalTransactionState;
        RefundSummary refundSummary = null;
        if (TransactionType.REFUND == transaction.getTransactionType()) {
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
        cardDetails.setFirstDigitsCardNumber(transaction.getFirstDigitsCardNumber());

        TransactionResponseBuilder transactionResponseBuilder = aTransactionResponseBuilder()
                .withTransactionType(transaction.getTransactionType())
                .withAmount(transaction.getAmount())
                .withState(externalTransactionState)
                .withCardDetails(cardDetails)
                .withChargeId(transaction.getExternalId())
                .withCreatedDate(transaction.getCreatedDate())
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
                        .build(transaction.getGatewayAccountId(), transaction.getExternalId()))
                .withWalletType(transaction.getWalletType())
                .withFee(transaction.getFeeAmount().orElse(null));

        if (ChargeStatus.AWAITING_CAPTURE_REQUEST.getValue().equals(transaction.getStatus())) {
            transactionResponseBuilder
                    .withLink("capture", POST, uriInfo.getBaseUriBuilder()
                            .path("/v1/api/accounts/{accountId}/charges/{chargeId}/capture")
                            .build(transaction.getGatewayAccountId(), transaction.getExternalId()));
        }
        if (refundSummary != null) {
            transactionResponseBuilder.withRefunds(refundSummary);
        }

        if (TransactionType.CHARGE == transaction.getTransactionType()) {
            transaction.getCorporateSurcharge().ifPresent(surcharge -> {
                if (surcharge > 0) {
                    transactionResponseBuilder
                            .withCorporateCardSurcharge(surcharge)
                            .withTotalAmount(CorporateCardSurchargeCalculator.getTotalAmountFor(transaction));
                }
            });
        }
        
        transaction.getNetAmount().ifPresent(transactionResponseBuilder::withNetAmount);

        return transactionResponseBuilder.build();
    }

    private RefundSummary buildRefundSummary(Transaction transaction) {
        RefundSummary refund = new RefundSummary();
        refund.setStatus(transaction.getStatus());
        refund.setUserExternalId(transaction.getUserExternalId());
        return refund;
    }
}
