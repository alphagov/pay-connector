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
import uk.gov.pay.connector.model.domain.Card3dsEntity;
import uk.gov.pay.connector.model.domain.CardEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.PersistedCard;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionOperation;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.HttpMethod.GET;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.REFUNDS_API_PATH;

public class TransactionSearchStrategy extends AbstractSearchStrategy<TransactionEntity> {

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
    protected List<TransactionEntity> findAllBy(ChargeSearchParams params) {
        return transactionDao.search(params);
    }

    @Override
    protected ChargeResponse buildResponse(UriInfo uriInfo, TransactionEntity transaction, Map<String, String> cardBrandToLabel) {
        ExternalTransactionState externalTransactionState;
        TransactionType transactionType;
        if (transaction.getOperation().equals(TransactionOperation.CHARGE)) {
            ChargeTransactionEntity chargeTransactionEntity = (ChargeTransactionEntity) transaction;
            ExternalChargeState externalChargeState = chargeTransactionEntity.getStatus().toExternal();
            externalTransactionState = new ExternalTransactionState(externalChargeState.getStatus(), externalChargeState.isFinished(), externalChargeState.getCode(), externalChargeState.getMessage());
            transactionType = TransactionType.PAYMENT;
        } else {
            RefundTransactionEntity refundTransactionEntity = (RefundTransactionEntity) transaction;
            ExternalRefundStatus externalRefundStatus = refundTransactionEntity.getStatus().toExternal();
            externalTransactionState = new ExternalTransactionState(externalRefundStatus.getStatus(), externalRefundStatus.isFinished());
            transactionType = TransactionType.REFUND;
        }

        PersistedCard cardDetails = new PersistedCard();
        PaymentRequestEntity paymentRequest = transaction.getPaymentRequest();
        ChargeTransactionEntity chargeTransaction = paymentRequest.getChargeTransaction();
        CardEntity card = chargeTransaction.getCard();
        if (card != null) {
            cardDetails.setCardBrand(cardBrandToLabel.get(card.getCardBrand()));
            cardDetails.setCardHolderName(card.getCardHolderName());
            cardDetails.setExpiryDate(card.getExpiryDate());
            cardDetails.setLastDigitsCardNumber(card.getLastDigitsCardNumber());
        }

        ChargeResponse.Auth3dsData auth3dsData = null;
        Card3dsEntity card3ds = paymentRequest.getChargeTransaction().getCard3ds();
        if (card3ds != null) {
            auth3dsData = new ChargeResponse.Auth3dsData();
            auth3dsData.setPaRequest(card3ds.getPaRequest());
            auth3dsData.setIssuerUrl(card3ds.getIssuerUrl());
        }

        return TransactionResponse.aTransactionResponseBuilder()
                .withChargeId(paymentRequest.getExternalId())
                .withAmount(transaction.getAmount())
                .withReference(paymentRequest.getReference())
                .withDescription(paymentRequest.getDescription())
                .withState(externalTransactionState)
                .withGatewayTransactionId(chargeTransaction.getGatewayTransactionId())
                .withProviderName(paymentRequest.getGatewayAccount().getGatewayName())
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(transaction.getCreatedDate()))
                .withReturnUrl(paymentRequest.getReturnUrl())
                .withEmail(chargeTransaction.getEmail())
                .withCardDetails(cardDetails)
                .withAuth3dsData(auth3dsData)
                .withTransactionType(transactionType.getValue())
                .withLink("self", GET, uriInfo.getBaseUriBuilder()
                        .path(CHARGE_API_PATH)
                        .build(paymentRequest.getGatewayAccount().getId(), paymentRequest.getExternalId()))
                .withLink("refunds", GET, uriInfo.getBaseUriBuilder()
                        .path(REFUNDS_API_PATH)
                        .build(paymentRequest.getGatewayAccount().getId(), paymentRequest.getExternalId()))
                .build();
    }
}
