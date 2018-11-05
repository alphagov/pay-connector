package uk.gov.pay.connector.paymentprocessor.service;

import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.Optional;

public class Card3dsResponseAuthService extends CardAuthoriseBaseService<Auth3dsDetails> {
    @Inject
    public Card3dsResponseAuthService(PaymentProviders providers,
                                      CardExecutorService cardExecutorService,
                                      ChargeService chargeService,
                                      Environment environment) {
        super(providers, cardExecutorService, chargeService, environment);
    }

    @Override
    @Transactional
    public ChargeEntity prepareChargeForAuthorisation(String chargeId, Auth3dsDetails auth3DsDetails) {
        return chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION_3DS);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(ChargeEntity chargeEntity, Auth3dsDetails auth3DsDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise3dsResponse(Auth3dsResponseGatewayRequest.valueOf(chargeEntity, auth3DsDetails));
    }
    
    @Override
    @Transactional
    public void processGatewayAuthorisationResponse(
            ChargeEntity oldCharge, 
            Auth3dsDetails auth3DsDetails, 
            GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        
            Optional<String> transactionId = operationResponse.getBaseResponse().map(BaseAuthoriseResponse::getTransactionId);
            ChargeStatus status = extractChargeStatus(operationResponse.getBaseResponse(), Optional.empty());
            ChargeEntity updatedCharge = chargeService.updateChargePost3dsAuthorisation(oldCharge.getExternalId(), status, transactionId);

            logger.info("3DS response authorisation for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    updatedCharge.getExternalId(), 
                    updatedCharge.getPaymentGatewayName().getName(), 
                    updatedCharge.getGatewayTransactionId(),
                    updatedCharge.getGatewayAccount().getAnalyticsId(), 
                    updatedCharge.getGatewayAccount().getId(),
                    operationResponse, oldCharge.getStatus(), 
                    status);


            metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.authorise-3ds.result.%s",
                    updatedCharge.getGatewayAccount().getGatewayName(),
                    updatedCharge.getGatewayAccount().getType(),
                    updatedCharge.getGatewayAccount().getId(),
                    status.toString())).inc();
    }
}
