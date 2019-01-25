package uk.gov.pay.connector.wallets;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseBaseService;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class WalletAuthoriseService {
    private static final DateTimeFormatter EXPIRY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/yy");
    private final CardAuthoriseBaseService cardAuthoriseBaseService;
    private final ChargeService chargeService;
    private final PaymentProviders paymentProviders;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private MetricRegistry metricRegistry;

    @Inject
    public WalletAuthoriseService(PaymentProviders paymentProviders,
                           ChargeService chargeService,
                           CardAuthoriseBaseService cardAuthoriseBaseService,
                           Environment environment) {
        this.paymentProviders = paymentProviders;
        this.cardAuthoriseBaseService = cardAuthoriseBaseService;
        this.chargeService = chargeService;
        this.metricRegistry = environment.metrics();
    }

    public GatewayResponse<BaseAuthoriseResponse> doAuthorise(String chargeId, WalletAuthorisationData walletAuthorisationData) {
        return cardAuthoriseBaseService.executeAuthorise(chargeId, () -> {
            final ChargeEntity charge = prepareChargeForAuthorisation(chargeId);
            GatewayResponse<BaseAuthoriseResponse> operationResponse = authorise(charge, walletAuthorisationData);
            processGatewayAuthorisationResponse(
                    charge.getExternalId(),
                    ChargeStatus.fromString(charge.getStatus()),
                    walletAuthorisationData,
                    operationResponse);

            return operationResponse;
        });
    }

    @Transactional
    private ChargeEntity prepareChargeForAuthorisation(String chargeId) {
        ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION);
        getPaymentProviderFor(charge)
                .generateTransactionId()
                .ifPresent(charge::setGatewayTransactionId);
        return charge;
    }

    private void processGatewayAuthorisationResponse(
            String chargeExternalId,
            ChargeStatus oldChargeStatus,
            WalletAuthorisationData walletAuthorisationData,
            GatewayResponse<BaseAuthoriseResponse> operationResponse) {

        logger.info("Processing gateway auth response for {}", walletAuthorisationData.getWalletType().toString());
        Optional<String> transactionId = cardAuthoriseBaseService.extractTransactionId(chargeExternalId, operationResponse);
        ChargeStatus status = cardAuthoriseBaseService.extractChargeStatus(
                operationResponse.getBaseResponse(),
                operationResponse.getGatewayError());

        AuthCardDetails authCardDetailsToBePersisted = authCardDetailsFor(walletAuthorisationData);
        ChargeEntity updatedCharge = chargeService.updateChargePostWalletAuthorisation(
                chargeExternalId,
                status,
                transactionId,
                Optional.empty(),
                operationResponse.getSessionIdentifier(),
                authCardDetailsToBePersisted,
                walletAuthorisationData.getWalletType());

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

    protected GatewayResponse<BaseAuthoriseResponse> authorise(ChargeEntity chargeEntity, WalletAuthorisationData walletAuthorisationData) {
        logger.info("Authorising charge for {}", walletAuthorisationData.getWalletType().toString());
        WalletAuthorisationGatewayRequest authorisationGatewayRequest = 
                WalletAuthorisationGatewayRequest.valueOf(chargeEntity, walletAuthorisationData.getWalletTemplateData());
        return getPaymentProviderFor(chargeEntity)
                .authoriseWallet(authorisationGatewayRequest);
    }

    private AuthCardDetails authCardDetailsFor(WalletAuthorisationData walletAuthorisationData) {
        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardHolder(walletAuthorisationData.getPaymentInfo().getCardholderName());
        authCardDetails.setCardNo(walletAuthorisationData.getPaymentInfo().getLastDigitsCardNumber());
        authCardDetails.setPayersCardType(walletAuthorisationData.getPaymentInfo().getCardType());
        authCardDetails.setCardBrand(walletAuthorisationData.getPaymentInfo().getBrand());
        authCardDetails.setEndDate(walletAuthorisationData.getCardExpiryDate().format(EXPIRY_DATE_FORMAT));
        authCardDetails.setCorporateCard(false);
        return authCardDetails;
    }

    private PaymentProvider getPaymentProviderFor(ChargeEntity chargeEntity) {
        return paymentProviders.byName(chargeEntity.getPaymentGatewayName());
    }

}
