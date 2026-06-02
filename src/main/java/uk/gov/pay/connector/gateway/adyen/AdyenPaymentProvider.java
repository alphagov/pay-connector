package uk.gov.pay.connector.gateway.adyen;

import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.adyen.handler.AdyenAuthoriseHandler;
import uk.gov.pay.connector.gateway.adyen.handler.AdyenCancelHandler;
import uk.gov.pay.connector.gateway.adyen.handler.AdyenCaptureHandler;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.service.RefundEntityFactory;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;

public class AdyenPaymentProvider implements PaymentProvider {

    private final GatewayClient client;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final ConnectorConfiguration connectorConfiguration;
    private final AdyenAuthoriseHandler adyenAuthoriseHandler;
    private final AdyenCaptureHandler adyenCaptureHandler;
    private final AdyenCancelHandler adyenCancelHandler;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final RefundEntityFactory refundEntityFactory;

    @Inject
    public AdyenPaymentProvider(
            ConnectorConfiguration connectorConfiguration,
            Environment environment,
            GatewayClientFactory gatewayClientFactory,
            @Named("DefaultRefundEntityFactory") RefundEntityFactory refundEntityFactory,
            JsonObjectMapper jsonObjectMapper) {
        this.adyenGatewayConfig = connectorConfiguration.getAdyenGatewayConfig();
        this.connectorConfiguration = connectorConfiguration;
        this.client = gatewayClientFactory.createGatewayClient(ADYEN, environment.metrics());
        adyenAuthoriseHandler = new AdyenAuthoriseHandler(client, connectorConfiguration, jsonObjectMapper);
        adyenCaptureHandler = new AdyenCaptureHandler(client, connectorConfiguration, jsonObjectMapper);
        adyenCancelHandler = new AdyenCancelHandler(client, adyenGatewayConfig, new AdyenRequestFactory(connectorConfiguration));
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        this.refundEntityFactory = refundEntityFactory;
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return ADYEN;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public GatewayResponse authorise(CardAuthorisationGatewayRequest request, ChargeEntity charge) throws GatewayException {
        return adyenAuthoriseHandler.authorise(request);
    }

    @Override
    public GatewayResponse authoriseMotoApi(CardAuthorisationGatewayRequest request) throws GatewayException {
        return adyenAuthoriseHandler.authorise(request);
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeQueryGatewayRequest chargeQueryGatewayRequest) throws GatewayException {
        throw new UnsupportedOperationException("Operation for Adyen is not Implemented yet");
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        throw new UnsupportedOperationException("Operation for Adyen is not Implemented yet");
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return adyenCaptureHandler.capture(request);
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        throw new UnsupportedOperationException("Operation for Adyen is not Implemented yet");
    }

    @Override
    public boolean canQueryPaymentStatus() {
        return false;
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) throws GatewayException {
        return adyenCancelHandler.cancel(request);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(Charge charge, List<Refund> refundList) {
        return externalRefundAvailabilityCalculator.calculate(charge, refundList);
    }

    @Override
    public AdyenAuthorisationRequestSummary generateAuthorisationRequestSummary(ChargeEntity chargeEntity,
                                                                                AuthCardDetails authCardDetails,
                                                                                boolean isSetUpAgreement) {
        return new AdyenAuthorisationRequestSummary(authCardDetails, isSetUpAgreement);
    }

    @Override
    public RefundEntityFactory getRefundEntityFactory() {
        return refundEntityFactory;
    }
}
