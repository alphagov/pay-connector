package uk.gov.pay.connector.gateway.model.response;

import java.util.Optional;

public interface BaseRefundResponse extends BaseResponse {

    Optional<String> getReference();

}
