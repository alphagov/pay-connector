package uk.gov.pay.connector.refund.service;

import uk.gov.pay.connector.refund.model.domain.RefundEntity;

public class WorldpayRefundEntityFactory implements RefundEntityFactory {

    @Override
    public RefundEntity create(long amount, String userExternalId, String refundUserEmail, String chargeExternalId) {
        var refundEntity = new RefundEntity(amount, userExternalId, refundUserEmail, chargeExternalId);
        // We set the Worldpay’s gateway transaction ID to be the same as the refund external ID
        refundEntity.setGatewayTransactionId(refundEntity.getExternalId());
        return refundEntity;
    }

}
