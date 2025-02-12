package uk.gov.pay.connector.refund.service;

import uk.gov.pay.connector.refund.model.domain.RefundEntity;

public interface RefundEntityFactory {
    
    RefundEntity create(long amount, String userExternalId, String refundUserEmail, String chargeExternalId);
    
}
