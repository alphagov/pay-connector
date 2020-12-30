package uk.gov.pay.connector.paymentprocessor.model;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;

import java.util.Set;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;

public enum Exemption3ds {
    
    EXEMPTION_NOT_REQUESTED;
    
    private static final Set<ChargeStatus> EXEMPTION_NOT_REQUESTED_STATUSES = 
            Set.of(AUTHORISATION_SUCCESS, AUTHORISATION_REJECTED, AUTHORISATION_CANCELLED, AUTHORISATION_ERROR);

    public static Exemption3ds calculateExemption3ds(BaseAuthoriseResponse baseResponse, ChargeStatus status) {
        if (baseResponse instanceof WorldpayOrderStatusResponse) {
            
            var worldpayOrderStatusResponse = (WorldpayOrderStatusResponse) baseResponse;
            
            if (!worldpayOrderStatusResponse.hasExemptionResponse() && EXEMPTION_NOT_REQUESTED_STATUSES.contains(status)) {
                return EXEMPTION_NOT_REQUESTED;
            }
        }
        return null;
    }
}
