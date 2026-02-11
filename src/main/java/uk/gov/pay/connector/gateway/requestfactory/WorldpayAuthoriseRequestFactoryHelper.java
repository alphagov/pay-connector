package uk.gov.pay.connector.gateway.requestfactory;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayAddress;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest.Placement.AUTHORISATION;
import static uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest.Placement.OPTIMISED;
import static uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest.Type.CP;
import static uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest.Type.OP;

public class WorldpayAuthoriseRequestFactoryHelper {

    private final AcceptLanguageHeaderParser acceptLanguageHeaderParser;
    private final NorthAmericanRegionMapper northAmericanRegionMapper;

    public WorldpayAuthoriseRequestFactoryHelper(AcceptLanguageHeaderParser acceptLanguageHeaderParser,
                                                 NorthAmericanRegionMapper northAmericanRegionMapper) {
        this.acceptLanguageHeaderParser = acceptLanguageHeaderParser;
        this.northAmericanRegionMapper = northAmericanRegionMapper;
    }
    
    public String getOrderCodeOrThrow(CardAuthorisationGatewayRequest request) {
        return request.getTransactionId().orElseThrow(() -> new IllegalArgumentException(
                "Charge " + request.getGovUkPayPaymentId() + " does not have transaction ID set"));
    }

    public WorldpayMerchantCodeCredentials getWorldpayMerchantCodeCredentials(CardAuthorisationGatewayRequest request) {
        return AuthUtil.getWorldpayMerchantCodeCredentials(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment());
    }

    public String getDescription(CardAuthorisationGatewayRequest request) {
        if (request.getGatewayAccount().isSendReferenceToGateway()) {
            return request.getReference().toString();
        }
        return request.getDescription();
    }

    public Optional<String> getEmailIfEnabledAndAvailable(CardAuthorisationGatewayRequest request) {
        if (request.getGatewayAccount().isSendPayerEmailToGateway()) {
            return Optional.ofNullable(request.getEmail());
        }
        return Optional.empty();
    }

    public Optional<String> getIpAddressIfEnabledAndAvailable(CardAuthorisationGatewayRequest request) {
        if (request.getGatewayAccount().isSendPayerIpAddressToGateway()) {
            return request.getAuthCardDetails().getIpAddress();
        }
        return Optional.empty();
    }

    public Optional<String> getPreferredLanguageTagIfRequired(CardAuthorisationGatewayRequest request) {
        if (!is3dsFlexDeviceDataCollectionResultAvailable(request) && is3dsFlexEnabled(request)) {
            String acceptLanguageHeader = request.getAuthCardDetails().getAcceptLanguageHeader();
            String languageTag = acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader(acceptLanguageHeader);
            return Optional.of(languageTag);
        }
        return Optional.empty();
    }

    public String getAgreementIdOrThrow(CardAuthorisationGatewayRequest request) {
        return request.getAgreement()
                .map(AgreementEntity::getExternalId)
                .orElseThrow(() -> new IllegalArgumentException("Recurring payment with no agreement"));
    }

    public Optional<String> getCustomerInitiatedReason(CardAuthorisationGatewayRequest request) {
        return switch (request.getAgreementPaymentType()) {
            case RECURRING -> Optional.of("RECURRING");
            case INSTALMENT -> Optional.of("INSTALMENT");
            case UNSCHEDULED -> Optional.of("UNSCHEDULED");
            case null -> Optional.empty();
        };
    }

    public Optional<WorldpayAddress> newWorldpayAddress(CardAuthorisationGatewayRequest request) {
        return request.getAuthCardDetails().getAddress().map(address -> new WorldpayAddress(
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                northAmericanRegionMapper.getNorthAmericanRegionForCountry(address)
                        .map(NorthAmericaRegion::getAbbreviation).orElse(null),
                address.getPostcode(),
                address.getCountry()
        ));
    }

    public Optional<WorldpayExemptionRequest> newWorldpayExemptionRequest(CardAuthorisationGatewayRequest request) {
        if (isCorporateExemptionsEnabled(request) && isCorporateCard(request)) {
            return Optional.of(new WorldpayExemptionRequest(CP, AUTHORISATION));
        }

        if (isExemptionEngineEnabled(request)) {
            return Optional.of(new WorldpayExemptionRequest(OP, OPTIMISED));
        }

        return Optional.empty();
    }

    public boolean is3dsRequired(CardAuthorisationGatewayRequest request) {
        return is3dsFlexDeviceDataCollectionResultAvailable(request) || request.getGatewayAccount().isRequires3ds();
    }
    
    public boolean is3dsFlexRequest(CardAuthorisationGatewayRequest request) {
        return is3dsFlexEnabled(request) || is3dsFlexDeviceDataCollectionResultAvailable(request);
    }

    public boolean isExemptionEngineEnabled(CardAuthorisationGatewayRequest request) {
        var gatewayAccount = request.getGatewayAccount();
        return gatewayAccount.isRequires3ds() && gatewayAccount.getWorldpay3dsFlexCredentials()
                .map(Worldpay3dsFlexCredentials::isExemptionEngineEnabled)
                .orElse(false);
    }

    public boolean isCorporateExemptionsEnabled(CardAuthorisationGatewayRequest request) {
        var gatewayAccount = request.getGatewayAccount();
        return gatewayAccount.isRequires3ds() && gatewayAccount.getWorldpay3dsFlexCredentials()
                .map(Worldpay3dsFlexCredentials::isCorporateExemptionsEnabled)
                .orElse(false);
    }

    public boolean isCorporateCard(CardAuthorisationGatewayRequest request) {
        return request.getAuthCardDetails().isCorporateCard();
    }

    private boolean is3dsFlexEnabled(CardAuthorisationGatewayRequest request) {
        return request.getGatewayAccount().getIntegrationVersion3ds() == 2;
    }

    private boolean is3dsFlexDeviceDataCollectionResultAvailable(CardAuthorisationGatewayRequest request) {
        return request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().isPresent();
    }

}
