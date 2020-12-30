package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.UpdateChargePostAuthorisation;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.Optional;

import static uk.gov.pay.connector.charge.service.UpdateChargePostAuthorisation.UpdateChargePostAuthorisationBuilder.anUpdateChargePostAuthorisation;
import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.calculateExemption3ds;

public class CardAuthoriseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardAuthoriseService.class);
    
    private final CardTypeDao cardTypeDao;
    private final AuthorisationService authorisationService;
    private final ChargeService chargeService;
    private final PaymentProviders providers;
    private final AuthorisationLogger authorisationLogger;
    private final MetricRegistry metricRegistry;

    @Inject
    public CardAuthoriseService(CardTypeDao cardTypeDao,
                                PaymentProviders providers,
                                AuthorisationService authorisationService,
                                ChargeService chargeService,
                                AuthorisationLogger authorisationLogger, 
                                Environment environment) {
        this.providers = providers;
        this.authorisationService = authorisationService;
        this.chargeService = chargeService;
        this.authorisationLogger = authorisationLogger;
        this.metricRegistry = environment.metrics();
        this.cardTypeDao = cardTypeDao;
    }

    public AuthorisationResponse doAuthorise(String chargeId, AuthCardDetails authCardDetails) {
        return authorisationService.executeAuthorise(chargeId, () -> {

            final ChargeEntity charge = prepareChargeForAuthorisation(chargeId, authCardDetails);
            GatewayResponse<BaseAuthoriseResponse> operationResponse;
            ChargeStatus newStatus;

            try {
                operationResponse = authorise(charge, authCardDetails);

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
            
            ChargeEntity updatedCharge = chargeService.updateChargePostCardAuthorisation(
                    getUpdateChargePostAuthorisation(
                            charge.getExternalId(), 
                            newStatus, 
                            transactionId.orElse(null), 
                            auth3dsDetailsEntity.orElse(null), 
                            sessionIdentifier.orElse(null), 
                            authCardDetails, 
                            operationResponse));

            var authorisationRequestSummary = generateAuthorisationRequestSummary(charge, authCardDetails);
            
            authorisationLogger.logChargeAuthorisation(
                    LOGGER,
                    authorisationRequestSummary,
                    updatedCharge,
                    transactionId.orElse("missing transaction ID"),
                    operationResponse,
                    charge.getChargeStatus(),
                    newStatus
            );
            
            metricRegistry.counter(String.format(
                    "gateway-operations.%s.%s.%s.authorise.%s.result.%s",
                    updatedCharge.getGatewayAccount().getGatewayName(),
                    updatedCharge.getGatewayAccount().getType(),
                    updatedCharge.getGatewayAccount().getId(),
                    authorisationRequestSummary.billingAddress() == PRESENT ? "with-billing-address" : "without-billing-address",
                    newStatus.toString())).inc();

            return new AuthorisationResponse(operationResponse);
        });
    }
    
    private UpdateChargePostAuthorisation getUpdateChargePostAuthorisation(String chargeExternalId, 
                                                                           ChargeStatus newStatus, 
                                                                           String transactionId, 
                                                                           Auth3dsRequiredEntity auth3dsRequiredEntity, 
                                                                           ProviderSessionIdentifier sessionIdentifier, 
                                                                           AuthCardDetails authCardDetails, 
                                                                           GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        return anUpdateChargePostAuthorisation()
                .withChargeExternalId(chargeExternalId)
                .withStatus(newStatus)
                .withTransactionId(transactionId)
                .withAuth3dsRequiredDetails(auth3dsRequiredEntity)
                .withSessionIdentifier(sessionIdentifier)
                .withAuthCardDetails(authCardDetails)
                .withExemption3ds(operationResponse.getBaseResponse().map(r -> calculateExemption3ds(r, newStatus)).orElse(null))
                .build();
    }

    @Transactional
    public ChargeEntity prepareChargeForAuthorisation(String chargeId, AuthCardDetails authCardDetails) {
        ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION);
        ensureCardBrandGateway3DSCompatibility(charge, authCardDetails.getCardBrand());
        getCorporateCardSurchargeFor(authCardDetails, charge).ifPresent(charge::setCorporateSurcharge);
        getPaymentProviderFor(charge).generateTransactionId().ifPresent(charge::setGatewayTransactionId);
        return charge;
    }

    private void ensureCardBrandGateway3DSCompatibility(ChargeEntity chargeEntity, String cardBrand) {
        if (gatewayCardBrand3DSMismatch(chargeEntity, cardBrand)) {
            LOGGER.error("AuthCardDetails authorisation failed pre operation. Card brand requires 3ds but Gateway account has 3ds disabled - charge_external_id={}, operation_type={}, card_brand={}",
                    chargeEntity.getExternalId(), OperationType.AUTHORISATION.getValue(), cardBrand);
            chargeService.transitionChargeState(chargeEntity, ChargeStatus.AUTHORISATION_ABORTED);
            throw new IllegalStateRuntimeException(chargeEntity.getExternalId());
        }
    }

    private boolean gatewayCardBrand3DSMismatch(ChargeEntity chargeEntity, String cardBrand) {
        return !chargeEntity.getGatewayAccount().isRequires3ds() && cardBrandRequires3ds(cardBrand);
    }

    private boolean cardBrandRequires3ds(String cardBrand) {
        return cardTypeDao.findByBrand(cardBrand).stream().anyMatch(CardTypeEntity::isRequires3ds);
    }

    private GatewayResponse<BaseAuthoriseResponse> authorise(ChargeEntity charge, AuthCardDetails authCardDetails) throws GatewayException {
        return getPaymentProviderFor(charge).authorise(CardAuthorisationGatewayRequest.valueOf(charge, authCardDetails));
    }
    
    private PaymentProvider getPaymentProviderFor(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName());
    }

    private AuthorisationRequestSummary generateAuthorisationRequestSummary(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        return getPaymentProviderFor(chargeEntity).generateAuthorisationRequestSummary(chargeEntity, authCardDetails);
    }

}
