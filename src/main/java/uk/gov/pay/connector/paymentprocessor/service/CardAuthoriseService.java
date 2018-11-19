package uk.gov.pay.connector.paymentprocessor.service;

import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
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

public class CardAuthoriseService extends CardAuthoriseBaseService<AuthCardDetails> {

    private final CardTypeDao cardTypeDao;

    @Inject
    public CardAuthoriseService(CardTypeDao cardTypeDao,
                                PaymentProviders providers,
                                CardExecutorService cardExecutorService,
                                ChargeService chargeService,
                                Environment environment) {
        super(providers, cardExecutorService, chargeService, environment);
        this.cardTypeDao = cardTypeDao;
    }

    @Override
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

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise(CardAuthorisationGatewayRequest.valueOf(chargeEntity, authCardDetails));
    }

    @Override
    @Transactional
    public void processGatewayAuthorisationResponse(
            String chargeExternalId,
            ChargeStatus oldChargeStatus,
            AuthCardDetails authCardDetails,
            GatewayResponse<BaseAuthoriseResponse> operationResponse) {

        Optional<String> transactionId = extractTransactionId(chargeExternalId, operationResponse);
        ChargeStatus status = extractChargeStatus(operationResponse.getBaseResponse(), operationResponse.getGatewayError());

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
}
