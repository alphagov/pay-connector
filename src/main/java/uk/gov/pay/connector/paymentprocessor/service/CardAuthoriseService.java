package uk.gov.pay.connector.paymentprocessor.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor;

public class CardAuthoriseService extends CardAuthoriseBaseService<AuthCardDetails> {

    private final CardTypeDao cardTypeDao;

    @Inject
    public CardAuthoriseService(ChargeDao chargeDao,
                                ChargeEventDao chargeEventDao,
                                CardTypeDao cardTypeDao,
                                PaymentProviders providers,
                                CardExecutorService cardExecutorService,
                                ChargeService chargeService,
                                Environment environment) {
        super(chargeDao, chargeEventDao, providers, cardExecutorService, chargeService, environment);
        this.cardTypeDao = cardTypeDao;
    }

    @Transactional
    public ChargeEntity preOperation(String chargeId, AuthCardDetails authCardDetails) {

        return chargeDao.findByExternalId(chargeId).map(chargeEntity -> {

            String cardBrand = authCardDetails.getCardBrand();

            if (!chargeEntity.getGatewayAccount().isRequires3ds() && cardBrandRequires3ds(cardBrand)) {

                chargeEntity.setStatus(AUTHORISATION_ABORTED);

                logger.error("AuthCardDetails authorisation failed pre operation. Card brand requires 3ds but Gateway account has 3ds disabled - charge_external_id={}, operation_type={}, card_brand={}",
                        chargeEntity.getExternalId(), OperationType.AUTHORISATION.getValue(), cardBrand);

                chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
            } else {
                lockChargeForProcessing(chargeEntity, OperationType.AUTHORISATION, getLegalStates(), AUTHORISATION_READY);

                getCorporateCardSurchargeFor(authCardDetails, chargeEntity).ifPresent(chargeEntity::setCorporateSurcharge);

                getPaymentProviderFor(chargeEntity).generateTransactionId().ifPresent(chargeEntity::setGatewayTransactionId);
            }

            return chargeEntity;

        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private boolean cardBrandRequires3ds(String cardBrand) {
        List<CardTypeEntity> cardTypes = cardTypeDao.findByBrand(cardBrand).stream()
                .filter(cardTypeEntity -> cardTypeEntity.getBrand().equals(cardBrand))
                .collect(Collectors.toList());
        return cardTypes.stream().anyMatch(CardTypeEntity::isRequires3ds);
    }

    public GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise(AuthorisationGatewayRequest.valueOf(chargeEntity, authCardDetails));
    }

    @Override
    protected List<ChargeStatus> getLegalStates() {
        return ImmutableList.of(
                ENTERING_CARD_DETAILS
        );
    }

    @Transactional
    public void processGatewayAuthorisationResponse(String chargeId, AuthCardDetails authCardDetails, GatewayResponse<BaseAuthoriseResponse> operationResponse) {

        chargeDao.findByExternalId(chargeId).map(chargeEntity -> {

            Optional<String> transactionId = operationResponse.getBaseResponse().map(BaseAuthoriseResponse::getTransactionId);
            if (!transactionId.isPresent() || StringUtils.isBlank(transactionId.get())) {
                logger.warn("AuthCardDetails authorisation response received with no transaction id. -  charge_external_id={}",
                        chargeEntity.getExternalId());
            }

            ChargeStatus status = determineChargeStatus(operationResponse.getBaseResponse(), operationResponse.getGatewayError());

            logger.info("Authorisation for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    chargeEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(),
                    transactionId.orElse("missing transaction ID"),
                    chargeEntity.getGatewayAccount().getAnalyticsId(), chargeEntity.getGatewayAccount().getId(),
                    operationResponse, chargeEntity.getStatus(), status);

            Optional<Auth3dsDetailsEntity> auth3dsDetailsEntity = extractAuth3dsDetails(operationResponse);

            chargeService.updateChargePostAuthorisation(status, chargeEntity, transactionId,
                    auth3dsDetailsEntity, operationResponse.getSessionIdentifier(),
                    authCardDetails);

            metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.authorise.result.%s",
                    chargeEntity.getGatewayAccount().getGatewayName(),
                    chargeEntity.getGatewayAccount().getType(),
                    chargeEntity.getGatewayAccount().getId(),
                    status.toString())).inc();

            return chargeEntity;

        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private Optional<Auth3dsDetailsEntity> extractAuth3dsDetails(GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        return operationResponse.getBaseResponse()
                .map(response -> {
                    return response.getGatewayParamsFor3ds().map(gatewayParamsFor3ds ->
                            Optional.of(gatewayParamsFor3ds.toAuth3dsDetailsEntity()))
                            .orElse(Optional.empty());
                })
                .orElse(Optional.empty());
    }
}
