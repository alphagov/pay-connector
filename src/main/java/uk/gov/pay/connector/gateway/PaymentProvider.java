package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import java.util.Optional;

public interface PaymentProvider {

    PaymentGatewayName getPaymentGatewayName();

    Optional<String> generateTransactionId();

    GatewayResponse authorise(CardAuthorisationGatewayRequest request) throws GatewayException;

    ChargeQueryResponse queryPaymentStatus(ChargeEntity charge) throws GatewayException;

    Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request);

    GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletAuthorisationGatewayRequest request) throws GatewayException;

    CaptureResponse capture(CaptureGatewayRequest request);

    GatewayRefundResponse refund(RefundGatewayRequest request);

    GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) throws GatewayException;

    ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity);
}
