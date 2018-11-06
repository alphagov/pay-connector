package uk.gov.pay.connector.applepay;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseBaseService;
import uk.gov.pay.connector.paymentprocessor.service.CardExecutorService;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class AppleAuthoriseService extends CardAuthoriseBaseService<ApplePaymentData> {
    private static final DateTimeFormatter EXPIRY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/yy");

    @Inject
    AppleAuthoriseService(PaymentProviders providers, CardExecutorService cardExecutorService, ChargeService chargeService, Environment environment) {
        super(providers, cardExecutorService, chargeService, environment);
    }

    @Override
    @Transactional
    public ChargeEntity prepareChargeForAuthorisation(String chargeId, ApplePaymentData applePaymentData) {
        ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION);
        getPaymentProviderFor(charge).generateTransactionId().ifPresent(charge::setGatewayTransactionId);
        return charge;
    }

    @Override
    @Transactional
    public void processGatewayAuthorisationResponse(
            String chargeExternalId,
            ChargeStatus oldChargeStatus,
            ApplePaymentData applePaymentData,
            GatewayResponse<BaseAuthoriseResponse> operationResponse) {

        Optional<String> transactionId = extractTransactionId(chargeExternalId, operationResponse);
        ChargeStatus status = extractChargeStatus(operationResponse.getBaseResponse(), operationResponse.getGatewayError());

        AuthCardDetails fakeAuthCardDetails = new AuthCardDetails();
        fakeAuthCardDetails.setCardHolder(applePaymentData.getPaymentInfo().getCardholderName());
        fakeAuthCardDetails.setCardNo(applePaymentData.getPaymentInfo().getLastDigitsCardNumber());
        fakeAuthCardDetails.setPayersCardType(applePaymentData.getPaymentInfo().getCardType());
        fakeAuthCardDetails.setCardBrand(applePaymentData.getPaymentInfo().getBrand());
        fakeAuthCardDetails.setAddress(new Address());
        //todo check this in the db "08/18"
        fakeAuthCardDetails.setEndDate(applePaymentData.getApplicationExpirationDate().getDate().format(EXPIRY_DATE_FORMAT));
        fakeAuthCardDetails.setCorporateCard(false);
        ChargeEntity updatedCharge = chargeService.updateChargePostAuthorisation(
                chargeExternalId,
                status,
                transactionId,
                Optional.empty(),
                operationResponse.getSessionIdentifier(),
                fakeAuthCardDetails);

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

    @Override
    protected GatewayResponse<BaseAuthoriseResponse> authorise(ChargeEntity chargeEntity, ApplePaymentData authCardDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise(ApplePayGatewayRequest.valueOf(chargeEntity, authCardDetails));
    }
}
