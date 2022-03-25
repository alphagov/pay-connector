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
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.Optional;

import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

public class CardAuthoriseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardAuthoriseService.class);
    
    private final CardTypeDao cardTypeDao;
    private final AuthorisationService authorisationService;
    private final ChargeService chargeService;
    private final PaymentProviders providers;
    private final AuthorisationLogger authorisationLogger;
    private final MetricRegistry metricRegistry;
    private final CardCaptureService cardCaptureService;

    @Inject
    public CardAuthoriseService(CardTypeDao cardTypeDao,
                                PaymentProviders providers,
                                AuthorisationService authorisationService,
                                ChargeService chargeService,
                                AuthorisationLogger authorisationLogger, 
                                Environment environment,
                                CardCaptureService cardCaptureService) {
        this.providers = providers;
        this.authorisationService = authorisationService;
        this.chargeService = chargeService;
        this.authorisationLogger = authorisationLogger;
        this.metricRegistry = environment.metrics();
        this.cardTypeDao = cardTypeDao;
        this.cardCaptureService = cardCaptureService;
    }
    
    public AuthorisationResponse doAuthorise(String chargeId, PaymentInstrumentEntity paymentInstrumentEntity) {
        return authorisationService.executeAuthorise(chargeId, () -> {
            LOGGER.info("Starting to do alt auth");

            GatewayResponse<BaseAuthoriseResponse> operationResponse = null;
            try {
                // doesn't get here - is failing on that for some reason
                LOGGER.info("mapped to auth details to do alt auth");
                
                // NEW
                final ChargeEntity charge = prepareChargeForAuthorisation(chargeId, paymentInstrumentEntity);
                
                // NEW
                var authCardDetails = AuthCardDetails.from(paymentInstrumentEntity);
                
//                charge.setPaymentInstrument(paymentInstrumentEntity);
                ChargeStatus newStatus;

                try {
                    LOGGER.info("Actuvally got to calling provider interface");

                    operationResponse = authoriseUserNotPresent(charge, authCardDetails);
                    LOGGER.info(String.format("user not present got through %s", operationResponse));

                    if (operationResponse.getBaseResponse().isEmpty()) {
                        operationResponse.throwGatewayError();
                    }
            
                    newStatus = operationResponse.getBaseResponse().get().authoriseStatus().getMappedChargeStatus();

                } catch (GatewayException e) {
                    newStatus = AuthorisationService.mapFromGatewayErrorException(e);
                    operationResponse = GatewayResponse.GatewayResponseBuilder.responseBuilder().withGatewayError(e.toGatewayError()).build();
                }

                LOGGER.info("through to 3ds bits");
                Optional<String> transactionId = authorisationService.extractTransactionId(charge.getExternalId(), operationResponse);
                Optional<ProviderSessionIdentifier> sessionIdentifier = operationResponse.getSessionIdentifier();
                Optional<Auth3dsRequiredEntity> auth3dsDetailsEntity =
                        operationResponse.getBaseResponse().flatMap(BaseAuthoriseResponse::extractAuth3dsRequiredDetails);

                LOGGER.info("through to updating bits");
                ChargeEntity updatedCharge = chargeService.updateChargePostCardAuthorisation(
                        charge.getExternalId(),
                        newStatus,
                        transactionId.orElse(null),
                        auth3dsDetailsEntity.orElse(null),
                        sessionIdentifier.orElse(null),
                        authCardDetails);

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
                        "gateway-operations.%s.%s.authorise.%s.result.%s",
                        updatedCharge.getPaymentProvider(),
                        updatedCharge.getGatewayAccount().getType(),
                        authorisationRequestSummary.billingAddress() == PRESENT ? "with-billing-address" : "without-billing-address",
                        newStatus.toString())).inc();
                
                // NEW
                cardCaptureService.markChargeAsEligibleForCapture(charge.getExternalId());
            } catch(Exception e) {
                LOGGER.error("Failure during auth not present flow", e);
            }
            return new AuthorisationResponse(operationResponse);
        }); 
    }

    public AuthorisationResponse doAuthorise(String chargeId, AuthCardDetails authCardDetails) {
        return authorisationService.executeAuthorise(chargeId, () -> {
            try {
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
                        charge.getExternalId(),
                        newStatus,
                        transactionId.orElse(null),
                        auth3dsDetailsEntity.orElse(null),
                        sessionIdentifier.orElse(null),
                        authCardDetails);

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
                        "gateway-operations.%s.%s.authorise.%s.result.%s",
                        updatedCharge.getPaymentProvider(),
                        updatedCharge.getGatewayAccount().getType(),
                        authorisationRequestSummary.billingAddress() == PRESENT ? "with-billing-address" : "without-billing-address",
                        newStatus.toString())).inc();

                return new AuthorisationResponse(operationResponse);
            } catch (Exception e) {
                LOGGER.error("Error during exception", e); 
                throw e;
            }
        });
    }

    @Transactional
    public ChargeEntity prepareChargeForAuthorisation(String chargeId, AuthCardDetails authCardDetails) {
        ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION);
        ensureCardBrandGateway3DSCompatibility(charge, authCardDetails.getCardBrand());
        getCorporateCardSurchargeFor(authCardDetails, charge).ifPresent(charge::setCorporateSurcharge);
        getPaymentProviderFor(charge).generateTransactionId().ifPresent(charge::setGatewayTransactionId);
        return charge;
    }

    // copying this to be lazy for now, this is a place where the charge is already naturally updated and avoided 
    // thinking through where would be best for this
    @Transactional
    public ChargeEntity prepareChargeForAuthorisation(String chargeId, PaymentInstrumentEntity paymentInstrumentEntity) {
        ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION);
        var authCardDetails = AuthCardDetails.from(paymentInstrumentEntity);
        charge.setPaymentInstrument(paymentInstrumentEntity);
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
    
    private GatewayResponse<BaseAuthoriseResponse> authoriseUserNotPresent(ChargeEntity charge, AuthCardDetails authCardDetails) throws GatewayException {
        return getPaymentProviderFor(charge).authoriseUserNotPresent(CardAuthorisationGatewayRequest.valueOf(charge, authCardDetails));
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
