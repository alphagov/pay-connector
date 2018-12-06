package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentprocessor.model.AuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.List;
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

    public AuthorisationResponse doAuthorise(String chargeExternalId, AuthCardDetails authCardDetails) {
        return cardAuthoriseBaseService.executeAuthorise(chargeExternalId, () -> {
            final ChargeEntity charge = prepareChargeForAuthorisation(chargeExternalId, authCardDetails);
            PaymentProviderAuthorisationResponse operationResponse = getPaymentProviderFor(charge)
                    .authorise(CardAuthorisationGatewayRequest.valueOf(charge, authCardDetails));
            ChargeStatus oldChargeStatus = ChargeStatus.fromString(charge.getStatus());
            ChargeEntity updatedCharge = chargeService.updateChargePostAuthorisation(chargeExternalId, operationResponse, authCardDetails);

            boolean billingAddressSubmitted = updatedCharge.getCardDetails().getBillingAddress().isPresent();

            logAuthorisation(oldChargeStatus, operationResponse, updatedCharge, billingAddressSubmitted);
            emitAuthorisationMetric(operationResponse.getChargeStatus().toString(), updatedCharge.getGatewayAccount(), billingAddressSubmitted);

            return new AuthorisationResponse(operationResponse);
        });
    }

    @Transactional
    public ChargeEntity prepareChargeForAuthorisation(String chargeId, AuthCardDetails authCardDetails) {
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

    private void logAuthorisation(ChargeStatus oldChargeStatus, PaymentProviderAuthorisationResponse operationResponse, ChargeEntity updatedCharge, boolean billingAddressSubmitted) {
        logger.info("Authorisation {} for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                billingAddressSubmitted ? "with billing address" : "without billing address",
                updatedCharge.getExternalId(), updatedCharge.getPaymentGatewayName().getName(),
                operationResponse.getTransactionId().orElse("missing transaction ID"),
                updatedCharge.getGatewayAccount().getAnalyticsId(),
                updatedCharge.getGatewayAccount().getId(),
                operationResponse, oldChargeStatus, operationResponse.getChargeStatus());
    }

    private void emitAuthorisationMetric(String operationChargeStatus, GatewayAccountEntity gatewayAccountEntity, boolean billingAddressSubmitted) {
        metricRegistry.counter(String.format(
                "gateway-operations.%s.%s.%s.authorise.%s.result.%s",
                gatewayAccountEntity.getGatewayName(),
                gatewayAccountEntity.getType(),
                gatewayAccountEntity.getId(),
                billingAddressSubmitted ? "with-billing-address" : "without-billing-address",
                operationChargeStatus)).inc();
    }

    private PaymentProvider getPaymentProviderFor(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName());
    }
}
