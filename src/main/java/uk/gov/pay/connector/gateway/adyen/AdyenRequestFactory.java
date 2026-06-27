package uk.gov.pay.connector.gateway.adyen;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.request.json.Authorise3dsRequestPayload;
import uk.gov.pay.connector.gateway.adyen.request.json.Authorise3dsRequestPayload.Details;
import uk.gov.pay.connector.gateway.adyen.request.json.AuthoriseRequestPayload;
import uk.gov.pay.connector.gateway.adyen.request.json.BillingAddress;
import uk.gov.pay.connector.gateway.adyen.request.json.CancelRequestPayload;
import uk.gov.pay.connector.gateway.adyen.request.json.CaptureRequestPayload;
import uk.gov.pay.connector.gateway.adyen.request.json.PaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.RefundRequestPayload;
import uk.gov.pay.connector.gateway.adyen.response.json.BrowserInfo;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;

import java.util.HashMap;
import java.util.Map;

import static uk.gov.pay.connector.gateway.adyen.AdyenRecurringAuthTokenKeys.SHOPPER_REFERENCE;
import static uk.gov.pay.connector.gateway.adyen.AdyenRecurringAuthTokenKeys.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getMerchantAccountId;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRecurringProcessingModelMapper.fromAgreementPaymentType;

public class AdyenRequestFactory {

    private final ConnectorConfiguration configuration;

    public AdyenRequestFactory(ConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    public AuthoriseRequestPayload createPaymentRequest(CardAuthorisationGatewayRequest request) {
        var authCardDetails = request.getAuthCardDetails();
        boolean isMoto = "Moto".equals(getShopperInteraction(request));

        var mappedAddress = authCardDetails.getAddress()
                .map(AdyenRequestFactory::mapToBillingAddress)
                .orElse(null);

        var paymentMethod = PaymentMethod.card(authCardDetails.getCvc(),
                authCardDetails.getEndDate().getTwoDigitMonth(),
                authCardDetails.getEndDate().getFourDigitYear(),
                authCardDetails.getCardHolder(),
                authCardDetails.getCardNo());

        var adyenCredentials = mapToAdyenCredentials(request.getGatewayCredentials());

        String shopperReference = null;
        Boolean storePaymentMethod = null;
        String recurringProcessingModel = null;
        if (request.isSavePaymentInstrumentToAgreement()) {
            shopperReference = request.getAgreement()
                    .orElseThrow(() -> new IllegalArgumentException("Expected charge with savePaymentInstrumentToAgreement to have an agreement"))
                    .getExternalId();
            storePaymentMethod = true;
            recurringProcessingModel = fromAgreementPaymentType(request.getAgreementPaymentType());
        }

        return new AuthoriseRequestPayload(
                new Amount("GBP", Long.valueOf(request.getAmount())),
                mappedAddress,
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive()),
                paymentMethod,
                request.getGovUkPayPaymentId(),
                configuration.getLinks().getFrontendUrl(),
                getShopperInteraction(request),
                adyenCredentials.storeId(),
                "Web",
                new HashMap<>(Map.of("manualCapture", "true")),
                isMoto ? null : mapToBrowserInfo(authCardDetails),
                isMoto ? null : configuration.getLinks().getFrontendUrl(),
                isMoto ? null : request.getEmail(),
                isMoto ? null : authCardDetails.getIpAddress().orElse(null),
                shopperReference,
                storePaymentMethod,
                recurringProcessingModel
        );
    }

    public AuthoriseRequestPayload createRecurringPaymentRequest(RecurringPaymentAuthorisationGatewayRequest request) {
        var paymentInstrument = request.getPaymentInstrument()
                .orElseThrow(() -> new IllegalArgumentException("Expected request to have payment instrument but it does not"));
        var recurringAuthToken = paymentInstrument.getRecurringAuthToken()
                .orElseThrow(() -> new IllegalArgumentException("Payment instrument does not have recurring auth token set"));

        String shopperReference = recurringAuthToken.get(SHOPPER_REFERENCE);
        String storedPaymentMethodId = recurringAuthToken.get(STORED_PAYMENT_METHOD_ID);

        if (shopperReference == null || storedPaymentMethodId == null) {
            throw new IllegalArgumentException("Adyen recurring auth token is missing shopperReference or storedPaymentMethodId");
        }

        var adyenCredentials = mapToAdyenCredentials(request.getGatewayCredentials());

        return new AuthoriseRequestPayload(
                new Amount("GBP", Long.valueOf(request.getAmount())),
                null,
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive()),
                PaymentMethod.stored(storedPaymentMethodId),
                request.getGovUkPayPaymentId(),
                configuration.getLinks().getFrontendUrl(),
                "ContAuth",
                adyenCredentials.storeId(),
                "Web",
                new HashMap<>(Map.of("manualCapture", "true")),
                null,
                null,
                null,
                null,
                shopperReference,
                null,
                fromAgreementPaymentType(request.getAgreementPaymentType())
        );
    }

    private static String getShopperInteraction(CardAuthorisationGatewayRequest request) {
        return request.isMoto() ? "Moto" : "Ecommerce";
    }

    public CancelRequestPayload createPaymentCancelRequest(CancelGatewayRequest request) {
        return new CancelRequestPayload(
                request.getExternalChargeId(),
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive())
        );
    }

    public CaptureRequestPayload createCapturePayload(CaptureGatewayRequest request) {
        return new CaptureRequestPayload(
                new Amount("GBP", request.getAmount()),
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive())
        );
    }

    public RefundRequestPayload createRefundRequestPayload(RefundGatewayRequest request) {
        var adyenCredentials = mapToAdyenCredentials(request.getGatewayCredentials());
        return new RefundRequestPayload(
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive()),
                new Amount("GBP", Long.valueOf(request.getAmount())),
                request.getRefundExternalId(),
                adyenCredentials.storeId()
        );
    }

    public Authorise3dsRequestPayload createPaymentDetailsRequest(Auth3dsResponseGatewayRequest request) {
        return new Authorise3dsRequestPayload(
                new Details(request.getAuth3dsResult().getRedirectResult())
        );
    }

    private static BillingAddress mapToBillingAddress(Address address) {
        var northAmericanRegionMapper = new NorthAmericanRegionMapper();
        String stateOrProvince = northAmericanRegionMapper.getNorthAmericanRegionForCountry(address)
                .map(NorthAmericaRegion::getFullName)
                .orElse(null);
        return new BillingAddress(
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getCountry(),
                address.getPostcode(),
                stateOrProvince);
    }

    private static AdyenCredentials mapToAdyenCredentials(GatewayCredentials gatewayCredentials) {
        if (!(gatewayCredentials instanceof AdyenCredentials)) {
            throw new IllegalArgumentException("Expected provided GatewayCredentials to be of type AdyenCredentials");
        }
        return (AdyenCredentials) gatewayCredentials;
    }

    private BrowserInfo mapToBrowserInfo(AuthCardDetails authCardDetails) {
        return new BrowserInfo(
                authCardDetails.getAcceptHeader(),
                authCardDetails.getJsScreenColorDepth().map(Integer::valueOf).orElse(null),
                false,
                authCardDetails.getJsEnabled(),
                authCardDetails.getJsNavigatorLanguage().map(String::valueOf).orElse(null),
                authCardDetails.getJsScreenHeight().map(Integer::valueOf).orElse(null),
                authCardDetails.getJsScreenWidth().map(Integer::valueOf).orElse(null),
                authCardDetails.getJsTimezoneOffsetMins().map(Integer::valueOf).orElse(null),
                authCardDetails.getUserAgentHeader()
        );
    }
}
