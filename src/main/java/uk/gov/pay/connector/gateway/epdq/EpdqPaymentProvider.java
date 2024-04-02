package uk.gov.pay.connector.gateway.epdq;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
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
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

public class EpdqPaymentProvider implements PaymentProvider {

    public static final String ROUTE_FOR_MAINTENANCE_ORDER = "maintenancedirect.asp";
    
    @Inject
    public EpdqPaymentProvider() {}

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.EPDQ;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request, ChargeEntity charge) throws GatewayException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public GatewayResponse authoriseMotoApi(CardAuthorisationGatewayRequest request) {
        throw new UnsupportedOperationException("MOTO API payments are not supported for ePDQ");
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) throws GatewayException {
        throw new UnsupportedOperationException();
    }

    public ChargeQueryResponse queryPaymentStatus(ChargeQueryGatewayRequest chargeQueryGatewayRequest) throws GatewayException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canQueryPaymentStatus() {
        return true;
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(Charge charge, List<Refund> refundList) {
        return ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE;
    }

    @Override
    public EpdqAuthorisationRequestSummary generateAuthorisationRequestSummary(GatewayAccountEntity gatewayAccount, AuthCardDetails authCardDetails, boolean isSetUpAgreement) {
        return new EpdqAuthorisationRequestSummary(gatewayAccount, authCardDetails);
    }
}
