package uk.gov.pay.connector.paymentprocessor.service;

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

public class Card3dsResponseAuthService {
    private final ChargeService chargeService;
    private final CardAuthoriseBaseService cardAuthoriseBaseService;
    private final PaymentProviders providers;

    @Inject
    public Card3dsResponseAuthService(PaymentProviders providers,
                                      ChargeService chargeService,
                                      CardAuthoriseBaseService cardAuthoriseBaseService
    ) {
        this.providers = providers;
        this.chargeService = chargeService;
        this.cardAuthoriseBaseService = cardAuthoriseBaseService;
    }

    public GatewayResponse<BaseAuthoriseResponse> process3DSecureAuthorisation(String chargeId, Auth3dsDetails auth3DsDetails) {
        return cardAuthoriseBaseService.executeAuthorise(chargeId, () -> {
            final ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION_3DS);
            GatewayResponse<BaseAuthoriseResponse> operationResponse = providers
                    .byName(charge.getPaymentGatewayName())
                    .authorise3dsResponse(Auth3dsResponseGatewayRequest.valueOf(charge, auth3DsDetails));
            processGateway3DSecureResponse(charge.getExternalId(), ChargeStatus.fromString(charge.getStatus()), operationResponse);

            return operationResponse;
        });
    }
    
    private void processGateway3DSecureResponse(String chargeExternalId, ChargeStatus oldChargeStatus, GatewayResponse<BaseAuthoriseResponse> operationResponse) {
            Optional<String> transactionId = operationResponse.getBaseResponse().map(BaseAuthoriseResponse::getTransactionId);
            ChargeStatus status = cardAuthoriseBaseService.extractChargeStatus(operationResponse.getBaseResponse(), Optional.empty());
            
            ChargeEntity updatedCharge = chargeService.updateChargePost3dsAuthorisation(chargeExternalId, status, transactionId);

            cardAuthoriseBaseService.logAuthorisation("3DS response authorisation", updatedCharge, oldChargeStatus, operationResponse);
            cardAuthoriseBaseService.emitAuthorisationMetric(updatedCharge, "authorise-3ds");
    }
}
