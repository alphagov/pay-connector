package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.request.RecoupFeeRequest;
import uk.gov.pay.connector.gateway.model.response.RecoupFeeResponse;

public interface FeeProcessor {
    Long calculateFee(Long chargeAmount, Long baseFee);
    RecoupFeeResponse recoupFee(RecoupFeeRequest recoupFeeRequest);
}
