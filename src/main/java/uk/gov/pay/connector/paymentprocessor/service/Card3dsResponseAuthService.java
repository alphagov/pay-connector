package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Supplier;

public class Card3dsResponseAuthService {
    private final ChargeService chargeService;
    private final CardAuthoriseBaseService cardAuthoriseBaseService;
    private final PaymentProviders providers;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final MetricRegistry metricRegistry;
    
    @Inject
    public Card3dsResponseAuthService(PaymentProviders providers,
                                      ChargeService chargeService,
                                      CardAuthoriseBaseService cardAuthoriseBaseService,
                                      Environment environment) {
        this.providers = providers;
        this.chargeService = chargeService;
        this.metricRegistry = environment.metrics();
        this.cardAuthoriseBaseService = cardAuthoriseBaseService;
    }

    public GatewayResponse<BaseAuthoriseResponse> process3DSecure(String chargeId, Auth3dsDetails auth3DsDetails) {
        Supplier authorisationSupplier = () -> {
            final ChargeEntity charge = prepareChargeFor3DSecureProcessing(chargeId);
            GatewayResponse<BaseAuthoriseResponse> operationResponse = process3DSecure(charge, auth3DsDetails);
            processGateway3DSecureResponse(
                    charge.getExternalId(),
                    ChargeStatus.fromString(charge.getStatus()),
                    operationResponse);

            return operationResponse;
        };

        return cardAuthoriseBaseService.executeAuthorise(chargeId, authorisationSupplier);
    }


    private GatewayResponse<BaseAuthoriseResponse> process3DSecure(ChargeEntity chargeEntity, Auth3dsDetails auth3DsDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise3dsResponse(Auth3dsResponseGatewayRequest.valueOf(chargeEntity, auth3DsDetails));
    }

    private ChargeEntity prepareChargeFor3DSecureProcessing(String chargeId) {
        return chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION_3DS);
    }
    
    private void processGateway3DSecureResponse(
            String chargeExternalId,
            ChargeStatus oldChargeStatus, 
            GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        
            Optional<String> transactionId = operationResponse.getBaseResponse().map(BaseAuthoriseResponse::getTransactionId);
            ChargeStatus status = cardAuthoriseBaseService.extractChargeStatus(operationResponse.getBaseResponse(), Optional.empty());
            ChargeEntity updatedCharge = chargeService.updateChargePost3dsAuthorisation(chargeExternalId, status, transactionId);

            logger.info("3DS response authorisation for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    updatedCharge.getExternalId(), 
                    updatedCharge.getPaymentGatewayName().getName(), 
                    updatedCharge.getGatewayTransactionId(),
                    updatedCharge.getGatewayAccount().getAnalyticsId(), 
                    updatedCharge.getGatewayAccount().getId(),
                    operationResponse, oldChargeStatus, 
                    status);

            metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.authorise-3ds.result.%s",
                    updatedCharge.getGatewayAccount().getGatewayName(),
                    updatedCharge.getGatewayAccount().getType(),
                    updatedCharge.getGatewayAccount().getId(),
                    status.toString())).inc();
    }

    PaymentProvider<BaseAuthoriseResponse> getPaymentProviderFor(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName());
    }
}
