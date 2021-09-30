package uk.gov.pay.connector.wallets;

import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.wallets.model.StripeWalletAuthorisationRequest;

import javax.inject.Inject;
import java.util.Optional;

public class StripeWalletService {
    private ChargeDao chargeDao;
    private ChargeService chargeService;
    StripePaymentProvider stripePaymentProvider;
    private AuthorisationService authorisationService;

    @Inject
    public StripeWalletService(ChargeDao chargeDao,
                               ChargeService chargeService,
                               StripePaymentProvider stripePaymentProvider,
                               AuthorisationService authorisationService) {
        this.chargeDao = chargeDao;
        this.chargeService = chargeService;
        this.stripePaymentProvider = stripePaymentProvider;
        this.authorisationService = authorisationService;
    }

    public AuthorisationResponse authorise(String chargeExternalId, StripeWalletAuthorisationRequest stripeWalletAuthorisationRequest) {
        // TODO: Get payment method details from Stripe and store on charge + transition charge to payment details entered.
        return authorisationService.executeAuthorise(chargeExternalId, () -> {
            ChargeEntity charge = chargeService.lockChargeForProcessing(chargeExternalId, OperationType.AUTHORISATION);
            GatewayResponse<BaseAuthoriseResponse> operationResponse;
            ChargeStatus newStatus;

            try {
                operationResponse = stripePaymentProvider.authoriseWalletPayment(charge, stripeWalletAuthorisationRequest.getPaymentMethodId());

                if (operationResponse.getBaseResponse().isEmpty()) {
                    operationResponse.throwGatewayError();
                }

                newStatus = operationResponse.getBaseResponse().get().authoriseStatus().getMappedChargeStatus();

            } catch (GatewayException e) {
                newStatus = AuthorisationService.mapFromGatewayErrorException(e);
                operationResponse = GatewayResponse.GatewayResponseBuilder.responseBuilder().withGatewayError(e.toGatewayError()).build();
            }

            Optional<String> transactionId = authorisationService.extractTransactionId(charge.getExternalId(), operationResponse);
            Optional<ProviderSessionIdentifier> sessionIdentifier = operationResponse.getSessionIdentifier();
            Optional<Auth3dsRequiredEntity> auth3dsDetailsEntity =
                    operationResponse.getBaseResponse().flatMap(BaseAuthoriseResponse::extractAuth3dsRequiredDetails);

            chargeService.updateChargePostCardAuthorisation(
                    charge.getExternalId(),
                    newStatus,
                    transactionId.orElse(null),
                    auth3dsDetailsEntity.orElse(null),
                    sessionIdentifier.orElse(null),
                    null);

            return new AuthorisationResponse(operationResponse);
        });
    }
}
