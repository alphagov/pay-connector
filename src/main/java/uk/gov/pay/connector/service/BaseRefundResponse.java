package uk.gov.pay.connector.service;

import java.util.Optional;

public interface BaseRefundResponse extends BaseResponse {

    Optional<String> getReference();

}
