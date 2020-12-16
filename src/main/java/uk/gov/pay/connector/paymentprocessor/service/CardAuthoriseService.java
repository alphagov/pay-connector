package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.ArrayUtils;
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
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Optional;

import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

public class CardAuthoriseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardAuthoriseService.class);
    
    private final CardTypeDao cardTypeDao;
    private final AuthorisationService authorisationService;
    private final ChargeService chargeService;
    private final PaymentProviders providers;
    private final AuthorisationRequestSummaryStringifier authorisationRequestSummaryStringifier;
    private final AuthorisationRequestSummaryStructuredLogging authorisationRequestSummaryStructuredLogging;
    private final MetricRegistry metricRegistry;

    @Inject
    public CardAuthoriseService(CardTypeDao cardTypeDao,
                                PaymentProviders providers,
                                AuthorisationService authorisationService,
                                ChargeService chargeService,
                                AuthorisationRequestSummaryStringifier authorisationRequestSummaryStringifier,
                                AuthorisationRequestSummaryStructuredLogging authorisationRequestSummaryStructuredLogging,
                                Environment environment) {
        this.providers = providers;
        this.authorisationService = authorisationService;
        this.chargeService = chargeService;
        this.metricRegistry = environment.metrics();
        this.authorisationRequestSummaryStringifier = authorisationRequestSummaryStringifier;
        this.authorisationRequestSummaryStructuredLogging = authorisationRequestSummaryStructuredLogging;
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
                    charge.getExternalId(),
                    newStatus,
                    transactionId.orElse(null),
                    auth3dsDetailsEntity.orElse(null),
                    sessionIdentifier.orElse(null),
                    authCardDetails);

            var authorisationRequestSummary = generateAuthorisationRequestSummary(charge, authCardDetails);

            // Used by Splunk saved search
            var logMessage = String.format(Locale.UK, "Authorisation%s for %s (%s %s) for %s (%s) - %s .'. %s -> %s",
                    authorisationRequestSummaryStringifier.stringify(authorisationRequestSummary),
                    updatedCharge.getExternalId(), updatedCharge.getPaymentGatewayName().getName(),
                    transactionId.orElse("missing transaction ID"),
                    updatedCharge.getGatewayAccount().getAnalyticsId(), updatedCharge.getGatewayAccount().getId(),
                    operationResponse, ChargeStatus.fromString(charge.getStatus()), newStatus);

            var structuredLoggingArguments = ArrayUtils.addAll(
                    charge.getStructuredLoggingArgs(),
                    authorisationRequestSummaryStructuredLogging.createArgs(authorisationRequestSummary));

            LOGGER.info(logMessage, structuredLoggingArguments);

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
