package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor;

public class CardAuthoriseService {

    private final CardTypeDao cardTypeDao;
    private final CardAuthoriseBaseService cardAuthoriseBaseService;
    private final ChargeService chargeService;
    private final PaymentProviders providers;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private MetricRegistry metricRegistry;
    
    @Inject
    public CardAuthoriseService(CardTypeDao cardTypeDao,
                                PaymentProviders providers,
                                CardAuthoriseBaseService cardAuthoriseBaseService,
                                ChargeService chargeService,
                                Environment environment) {
        this.providers = providers;
        this.cardAuthoriseBaseService = cardAuthoriseBaseService;
        this.chargeService = chargeService;
        this.metricRegistry = environment.metrics();        
        this.cardTypeDao = cardTypeDao;
    }

    public GatewayResponse<BaseAuthoriseResponse> doAuthorise(String chargeId, AuthCardDetails authCardDetails) {
        return cardAuthoriseBaseService.executeAuthorise(chargeId, () -> {
            final ChargeEntity charge = prepareChargeForAuthorisation(chargeId, authCardDetails);
            GatewayResponse<BaseAuthoriseResponse> operationResponse = authorise(charge, authCardDetails);
            processGatewayAuthorisationResponse(
                    charge.getExternalId(),
                    ChargeStatus.fromString(charge.getStatus()),
                    authCardDetails,
                    operationResponse);

            return operationResponse;
        });
    }

    private ChargeEntity prepareChargeForAuthorisation(String chargeId, AuthCardDetails authCardDetails) {
        ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION);
        ensureCardBrandGateway3DSCompatibility(charge, authCardDetails.getCardBrand());
        getCorporateCardSurchargeFor(authCardDetails, charge).ifPresent(charge::setCorporateSurcharge);
        getPaymentProviderFor(charge)
                .generateTransactionId().ifPresent(charge::setGatewayTransactionId);
        
        return charge;
    }

    private void ensureCardBrandGateway3DSCompatibility(ChargeEntity chargeEntity, String cardBrand) {
        if (gatewayCardBrand3DSMismatch(chargeEntity, cardBrand)) {
            logger.error("AuthCardDetails authorisation failed pre operation. Card brand requires 3ds but Gateway account has 3ds disabled - charge_external_id={}, operation_type={}, card_brand={}",
                    chargeEntity.getExternalId(), OperationType.AUTHORISATION.getValue(), cardBrand);
            chargeService.abortCharge(chargeEntity);
            throw new IllegalStateRuntimeException(chargeEntity.getExternalId());
        }
    }

    private boolean gatewayCardBrand3DSMismatch(ChargeEntity chargeEntity, String cardBrand) {
        return !chargeEntity.getGatewayAccount().isRequires3ds() && cardBrandRequires3ds(cardBrand);
    }

    private boolean cardBrandRequires3ds(String cardBrand) {
        List<CardTypeEntity> cardTypes = cardTypeDao.findByBrand(cardBrand).stream()
                .filter(cardTypeEntity -> cardTypeEntity.getBrand().equals(cardBrand))
                .collect(Collectors.toList());
        
        return cardTypes.stream().anyMatch(CardTypeEntity::isRequires3ds);
    }

    public GatewayResponse<BaseAuthoriseResponse> authorise(ChargeEntity charge, AuthCardDetails authCardDetails) {
        return getPaymentProviderFor(charge)
                .authorise(CardAuthorisationGatewayRequest.valueOf(charge, authCardDetails));
    }

    private void processGatewayAuthorisationResponse(
            String chargeExternalId,
            ChargeStatus oldChargeStatus,
            AuthCardDetails authCardDetails,
            GatewayResponse<BaseAuthoriseResponse> operationResponse) {

        Optional<String> transactionId = cardAuthoriseBaseService.extractTransactionId(chargeExternalId, operationResponse);
        ChargeStatus status = cardAuthoriseBaseService.extractChargeStatus(operationResponse.getBaseResponse(), operationResponse.getGatewayError());

        ChargeEntity updatedCharge = chargeService.updateChargePostAuthorisation(
                chargeExternalId, 
                status, 
                transactionId,
                extractAuth3dsDetails(operationResponse), 
                operationResponse.getSessionIdentifier(),
                authCardDetails);
        
        logger.info("Authorisation for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                updatedCharge.getExternalId(), updatedCharge.getPaymentGatewayName().getName(),
                transactionId.orElse("missing transaction ID"),
                updatedCharge.getGatewayAccount().getAnalyticsId(), updatedCharge.getGatewayAccount().getId(),
                operationResponse, oldChargeStatus, status);
        
        metricRegistry.counter(String.format(
                "gateway-operations.%s.%s.%s.authorise.result.%s",
                updatedCharge.getGatewayAccount().getGatewayName(),
                updatedCharge.getGatewayAccount().getType(),
                updatedCharge.getGatewayAccount().getId(),
                status.toString())).inc();
    }

    private Optional<Auth3dsDetailsEntity> extractAuth3dsDetails(GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        return operationResponse.getBaseResponse()
                .flatMap(BaseAuthoriseResponse::getGatewayParamsFor3ds)
                .map(GatewayParamsFor3ds::toAuth3dsDetailsEntity);
    }

    private PaymentProvider<BaseAuthoriseResponse> getPaymentProviderFor(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName());
    }
}
