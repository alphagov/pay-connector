package uk.gov.pay.connector.paymentprocessor.service;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;

import javax.inject.Inject;
import java.util.Optional;

import static uk.gov.pay.connector.paymentprocessor.model.OperationType.AUTHORISATION_3DS;

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

    public Gateway3DSAuthorisationResponse process3DSecureAuthorisation(String chargeId, Auth3dsDetails auth3DsDetails) {
        return cardAuthoriseBaseService.executeAuthorise(chargeId, () -> {

            final ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, AUTHORISATION_3DS);
            Gateway3DSAuthorisationResponse gateway3DSAuthorisationResponse =  providers
                    .byName(charge.getPaymentGatewayName())
                    .authorise3dsResponse(Auth3dsResponseGatewayRequest.valueOf(charge, auth3DsDetails));
            processGateway3DSecureResponse(
                    charge.getExternalId(),
                    ChargeStatus.fromString(charge.getStatus()),
                    gateway3DSAuthorisationResponse
            );

            return gateway3DSAuthorisationResponse;
        });
    }

    private void processGateway3DSecureResponse(
            String chargeExternalId,
            ChargeStatus oldChargeStatus,
            Gateway3DSAuthorisationResponse operationResponse
    ) {
        Optional<String> transactionId = operationResponse.getTransactionId();

        ChargeEntity updatedCharge = chargeService.updateChargePost3dsAuthorisation(
                chargeExternalId,
                operationResponse.getMappedChargeStatus(),
                AUTHORISATION_3DS,
                transactionId
        );
        cardAuthoriseBaseService.logAuthorisation("3DS response authorisation", updatedCharge, oldChargeStatus);
        cardAuthoriseBaseService.emitAuthorisationMetric(updatedCharge, "authorise-3ds");
    }
}
