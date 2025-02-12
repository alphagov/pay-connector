package uk.gov.pay.connector.refund.service;

import uk.gov.pay.connector.refund.model.domain.RefundEntity;

public class DefaultRefundEntityFactory implements RefundEntityFactory {

    @Override
    public RefundEntity create(long amount, String userExternalId, String refundUserEmail, String chargeExternalId) {
        return new RefundEntity(amount, userExternalId, refundUserEmail, chargeExternalId);
    }
 
}
