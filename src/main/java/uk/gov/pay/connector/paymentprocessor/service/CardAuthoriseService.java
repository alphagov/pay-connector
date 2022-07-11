package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationTimedOutException;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeEligibleForCaptureService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
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
import uk.gov.pay.connector.paymentprocessor.exception.AuthorisationExecutorTimedOutException;
import uk.gov.pay.connector.paymentprocessor.model.AuthoriseRequest;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

public class CardAuthoriseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardAuthoriseService.class);

    private final CardTypeDao cardTypeDao;
    private final AuthorisationService authorisationService;
    private final ChargeService chargeService;
    private final PaymentProviders providers;
    private final AuthorisationLogger authorisationLogger;
    private final ChargeEligibleForCaptureService chargeEligibleForCaptureService;
    private final MetricRegistry metricRegistry;

    @Inject
    public CardAuthoriseService(CardTypeDao cardTypeDao,
                                PaymentProviders providers,
                                AuthorisationService authorisationService,
                                ChargeService chargeService,
                                AuthorisationLogger authorisationLogger,
                                ChargeEligibleForCaptureService chargeEligibleForCaptureService,
                                Environment environment) {
        this.cardTypeDao = cardTypeDao;
        this.providers = providers;
        this.authorisationService = authorisationService;
        this.chargeService = chargeService;
        this.authorisationLogger = authorisationLogger;
        this.chargeEligibleForCaptureService = chargeEligibleForCaptureService;
        this.metricRegistry = environment.metrics();
    }

    public AuthorisationResponse doAuthoriseWeb(String chargeId, AuthCardDetails authCardDetails) {
        return authorisationService.executeAuthorise(chargeId, () -> doAuthorise(chargeId, authCardDetails));
    }

    private AuthorisationResponse doAuthorise(String chargeId, AuthCardDetails authCardDetails) {
        final ChargeEntity charge = prepareChargeForAuthorisation(chargeId, authCardDetails);

        GatewayResponse<BaseAuthoriseResponse> operationResponse;
        ChargeStatus newStatus;

        try {
            PaymentProvider paymentProvider = getPaymentProviderFor(charge);
            CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(charge, authCardDetails);

            switch(charge.getAuthorisationMode()) {
                case WEB:
                    operationResponse = (GatewayResponse<BaseAuthoriseResponse>) paymentProvider.authorise(request, charge);
                    break;
                default:
                    throw new IllegalArgumentException("Authorise operation does not support authorisation mode");
            }

            if (operationResponse.getBaseResponse().isEmpty()) {
                operationResponse.throwGatewayError();
            }

            newStatus = operationResponse.getBaseResponse().get().authoriseStatus().getMappedChargeStatus();

        } catch (GatewayException e) {
            newStatus = AuthorisationService.mapFromGatewayErrorException(e);
            operationResponse = GatewayResponse.GatewayResponseBuilder.responseBuilder().withGatewayError(e.toGatewayError()).build();
        }

        return updateChargePostAuthorisation(authCardDetails, charge, operationResponse, newStatus);
    }

    public AuthorisationResponse doAuthoriseMotoApi(ChargeEntity chargeEntity, CardInformation cardInformation, AuthoriseRequest authoriseRequest) {
        AuthCardDetails authCardDetails = AuthCardDetails.of(authoriseRequest, chargeEntity, cardInformation);
        final ChargeEntity charge = prepareChargeForAuthorisation(chargeEntity.getExternalId(), authCardDetails);

        try {
            Pair<GatewayResponse<BaseAuthoriseResponse>, ChargeStatus> result = authorisationService.executeAuthoriseSync(() -> authoriseMotoApi(charge, authCardDetails));

            GatewayResponse<BaseAuthoriseResponse> operationResponse = result.getLeft();
            ChargeStatus newStatus = result.getRight();

            AuthorisationResponse authorisationResponse = updateChargePostAuthorisation(authCardDetails, charge, operationResponse, newStatus);

            authorisationResponse.getAuthoriseStatus().ifPresent(authoriseStatus -> {
                if (authoriseStatus.getMappedChargeStatus() == AUTHORISATION_SUCCESS) {
                    chargeEligibleForCaptureService.markChargeAsEligibleForCapture(chargeEntity.getExternalId());
                }
            });

            return authorisationResponse;
        } catch (AuthorisationExecutorTimedOutException e) {
            ChargeEntity updatedCharge = chargeService.updateChargePostCardAuthorisation(
                    charge.getExternalId(),
                    AUTHORISATION_TIMEOUT,
                    null,
                    null,
                    null,
                    authCardDetails,
                    null);

            LOGGER.info("Attempt to authorise charge synchronously timed out.");

            var authorisationRequestSummary = generateAuthorisationRequestSummary(charge, authCardDetails);
            incrementMetricsPostAuthorisation(AUTHORISATION_TIMEOUT, updatedCharge, authorisationRequestSummary);
            throw new AuthorisationTimedOutException();
        }
    }

    private Pair<GatewayResponse<BaseAuthoriseResponse>, ChargeStatus> authoriseMotoApi(ChargeEntity charge, AuthCardDetails authCardDetails) {
        GatewayResponse<BaseAuthoriseResponse> operationResponse;
        ChargeStatus newStatus;

        try {
            PaymentProvider paymentProviderFor = getPaymentProviderFor(charge);
            CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(charge, authCardDetails);
            operationResponse = (GatewayResponse<BaseAuthoriseResponse>) paymentProviderFor.authoriseMotoApi(request);

            if (operationResponse.getBaseResponse().isEmpty()) {
                operationResponse.throwGatewayError();
            }

            newStatus = operationResponse.getBaseResponse().get().authoriseStatus().getMappedChargeStatus();

        } catch (GatewayException e) {
            newStatus = AuthorisationService.mapFromGatewayErrorException(e);
            operationResponse = GatewayResponse.GatewayResponseBuilder.responseBuilder().withGatewayError(e.toGatewayError()).build();
        }

        return Pair.of(operationResponse, newStatus);
    }

    private AuthorisationResponse updateChargePostAuthorisation(AuthCardDetails authCardDetails, ChargeEntity charge, GatewayResponse<BaseAuthoriseResponse> operationResponse, ChargeStatus newStatus) {
        Optional<String> transactionId = authorisationService.extractTransactionId(charge.getExternalId(), operationResponse);
        Optional<ProviderSessionIdentifier> sessionIdentifier = operationResponse.getSessionIdentifier();
        Optional<Auth3dsRequiredEntity> auth3dsDetailsEntity =
                operationResponse.getBaseResponse().flatMap(BaseAuthoriseResponse::extractAuth3dsRequiredDetails);

        Optional<Map<String, String>> maybeToken = operationResponse.getBaseResponse().flatMap(BaseAuthoriseResponse::getGatewayRecurringAuthToken);

        ChargeEntity updatedCharge = chargeService.updateChargePostCardAuthorisation(
                charge.getExternalId(),
                newStatus,
                transactionId.orElse(null),
                auth3dsDetailsEntity.orElse(null),
                sessionIdentifier.orElse(null),
                authCardDetails,
                maybeToken.orElse(null));

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

        incrementMetricsPostAuthorisation(newStatus, updatedCharge, authorisationRequestSummary);

        return new AuthorisationResponse(operationResponse);
    }

    private void incrementMetricsPostAuthorisation(ChargeStatus newStatus, ChargeEntity updatedCharge, AuthorisationRequestSummary authorisationRequestSummary) {
        metricRegistry.counter(String.format(
                "gateway-operations.%s.%s.authorise.%s.result.%s",
                updatedCharge.getPaymentProvider(),
                updatedCharge.getGatewayAccount().getType(),
                authorisationRequestSummary.billingAddress() == PRESENT ? "with-billing-address" : "without-billing-address",
                newStatus.toString())).inc();
    }

    @Transactional
    public ChargeEntity prepareChargeForAuthorisation(String chargeId, AuthCardDetails authCardDetails) {
        ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION);
        ensureCardBrandGateway3DSCompatibility(charge, authCardDetails.getCardBrand());

        if (charge.getAuthorisationMode() == AuthorisationMode.WEB) {
            getCorporateCardSurchargeFor(authCardDetails, charge).ifPresent(charge::setCorporateSurcharge);
        }

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

    private PaymentProvider getPaymentProviderFor(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName());
    }

    private AuthorisationRequestSummary generateAuthorisationRequestSummary(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        return getPaymentProviderFor(chargeEntity).generateAuthorisationRequestSummary(chargeEntity.getGatewayAccount(), authCardDetails);
    }

}
