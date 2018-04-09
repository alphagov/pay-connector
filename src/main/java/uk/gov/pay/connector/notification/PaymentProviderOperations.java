package uk.gov.pay.connector.notification;

import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseAuthoriseResponse;
import uk.gov.pay.connector.service.BaseCaptureResponse;
import uk.gov.pay.connector.service.BaseResponse;

import java.util.Optional;

public interface PaymentProviderOperations extends PaymentProvider {

    Optional<String> generateTransactionId();

    <T extends BaseAuthoriseResponse> GatewayResponse<T> authorise(AuthorisationGatewayRequest request);

    <T extends BaseAuthoriseResponse> GatewayResponse<T> authorise3dsResponse(Auth3dsResponseGatewayRequest request);

    <T extends BaseCaptureResponse> GatewayResponse<T> capture(CaptureGatewayRequest request);

    <T extends BaseResponse> GatewayResponse<T> refund(RefundGatewayRequest request);

    <T extends BaseResponse> GatewayResponse<T> cancel(CancelGatewayRequest request);

    ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity);
}
