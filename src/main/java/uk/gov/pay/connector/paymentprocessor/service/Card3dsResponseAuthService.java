package uk.gov.pay.connector.paymentprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(Card3dsResponseAuthService.class);

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
            return authoriseAndProcess3DS(auth3DsDetails, charge);
        });
    }

    public Gateway3DSAuthorisationResponse process3DSecureAuthorisationWithoutLocking(String chargeId, Auth3dsDetails auth3DsDetails) {
        return cardAuthoriseBaseService.executeAuthorise(chargeId, () -> {
            final ChargeEntity charge = chargeService.findChargeById(chargeId);
            return authoriseAndProcess3DS(auth3DsDetails, charge);
        });
    }

    private Gateway3DSAuthorisationResponse authoriseAndProcess3DS(Auth3dsDetails auth3DsDetails, ChargeEntity charge) {
        Gateway3DSAuthorisationResponse gateway3DSAuthorisationResponse = providers
                .byName(charge.getPaymentGatewayName())
                .authorise3dsResponse(Auth3dsResponseGatewayRequest.valueOf(charge, auth3DsDetails));

        processGateway3DSecureResponse(
                charge.getExternalId(),
                ChargeStatus.fromString(charge.getStatus()),
                gateway3DSAuthorisationResponse
        );

        return gateway3DSAuthorisationResponse;
    }

    private void processGateway3DSecureResponse(
            String chargeExternalId,
            ChargeStatus oldChargeStatus,
            Gateway3DSAuthorisationResponse operationResponse
    ) {
        Optional<String> transactionId = operationResponse.getTransactionId();

        LOGGER.info("3DS response authorisation for {} - {} .'. about to attempt charge update from {} -> {}",
                chargeExternalId,
                operationResponse,
                oldChargeStatus,
                operationResponse.getMappedChargeStatus());

        ChargeEntity updatedCharge = chargeService.updateChargePost3dsAuthorisation(
                chargeExternalId,
                operationResponse.getMappedChargeStatus(),
                AUTHORISATION_3DS,
                transactionId
        );

        LOGGER.info("3DS response authorisation for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                updatedCharge.getExternalId(),
                updatedCharge.getPaymentGatewayName().getName(),
                updatedCharge.getGatewayTransactionId(),
                updatedCharge.getGatewayAccount().getAnalyticsId(),
                updatedCharge.getGatewayAccount().getId(),
                operationResponse,
                oldChargeStatus,
                updatedCharge.getStatus()
        );

        cardAuthoriseBaseService.emitAuthorisationMetric(updatedCharge, "authorise-3ds");
    }
}
