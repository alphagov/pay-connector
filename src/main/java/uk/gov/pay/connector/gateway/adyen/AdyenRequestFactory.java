package uk.gov.pay.connector.gateway.adyen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenBrowserInfo;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenCredentialsHelper;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenMerchantAccountHelper;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.util.ChargeFrontendUrlHelper;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;
import uk.gov.service.payments.commons.model.AgreementPaymentType;

import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class AdyenRequestFactory {

    public static final String STORED_PAYMENT_METHOD_ID = "storedPaymentMethodId";
    public static final String SHOPPER_REFERENCE_DELIMITER = "-";

    private final ConnectorConfiguration configuration;
    private final AdyenMerchantAccountHelper adyenMerchantAccountHelper;
    private final AdyenCredentialsHelper adyenCredentialsHelper;
    private final ChargeFrontendUrlHelper chargeFrontendUrlHelper;
    private final AdyenBrowserInfoFactory adyenBrowserInfoFactory;
    private final Logger LOGGER = LoggerFactory.getLogger(AdyenRequestFactory.class);

    public AdyenRequestFactory(ConnectorConfiguration configuration) {
        this.configuration = configuration;
        this.adyenMerchantAccountHelper = new AdyenMerchantAccountHelper(configuration);
        this.adyenCredentialsHelper = new AdyenCredentialsHelper();
        this.chargeFrontendUrlHelper = new ChargeFrontendUrlHelper(configuration);
        this.adyenBrowserInfoFactory = new AdyenBrowserInfoFactory();
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

        String shopperReference = null;
        Boolean storePaymentMethod = null;
        String recurringProcessingModel = null;
        if (request.isSavePaymentInstrumentToAgreement()) {
           var agreementExternalId = request.getAgreement()
                    .orElseThrow(() -> new IllegalArgumentException("Expected charge with savePaymentInstrumentToAgreement to have an agreement"))
                    .getExternalId();
            shopperReference = createShopperReferenceForRecurringPayments(agreementExternalId, request.getGovUkPayPaymentId());
            storePaymentMethod = true;
            recurringProcessingModel = fromAgreementPaymentType(request.getAgreementPaymentType());
        }

        return new AuthoriseRequestPayload(
                new Amount("GBP", Long.valueOf(request.getAmount())),
                mappedAddress,
                adyenMerchantAccountHelper.getMerchantAccount(request.getGatewayAccount()),
                paymentMethod,
                request.getGovUkPayPaymentId(),
                chargeFrontendUrlHelper.getFrontendUrlForCharge(request.getGovUkPayPaymentId()) + "/3ds_required_in/adyen", 
                getShopperInteraction(request),
                adyenCredentialsHelper.getStore(request),
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

    public AuthoriseRequestPayload createRecurringPaymentRequest(RecurringPaymentAuthorisationGatewayRequest request){
        var paymentInstrument = request.getPaymentInstrument()
                .orElseThrow(() -> new IllegalArgumentException("Expected request to have payment instrument but it does not"));
        var recurringAuthToken = paymentInstrument.getRecurringAuthToken()
                .orElseThrow(() -> new IllegalArgumentException("Payment instrument does not have recurring auth token set"));

        String shopperReference = createShopperReferenceForRecurringPayments(request.getAgreementId(), paymentInstrument.getChargeExternalId());
        String storedPaymentMethodId = recurringAuthToken.get(STORED_PAYMENT_METHOD_ID);

        if (isBlank(storedPaymentMethodId)) {
            throw new IllegalArgumentException("Adyen recurring auth token is missing storedPaymentMethodId");
        }

        return new AuthoriseRequestPayload(
                new Amount("GBP", Long.valueOf(request.getAmount())),
                null,
                adyenMerchantAccountHelper.getMerchantAccount(request.getGatewayAccount()),
                PaymentMethod.stored(storedPaymentMethodId),
                request.getGovUkPayPaymentId(),
                configuration.getLinks().getFrontendUrl(),
                "ContAuth",
                adyenCredentialsHelper.getStore(request),
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

    private String createShopperReferenceForRecurringPayments(String agreementId, String chargeExternalId) {
        if (agreementId == null || chargeExternalId == null || isBlank(agreementId) || isBlank(chargeExternalId)) {
            throw new IllegalArgumentException("shopperReference could not be derived as charge external ID or agreement external ID are missing");
        }
        return agreementId + SHOPPER_REFERENCE_DELIMITER + chargeExternalId;
    }

    private static String getShopperInteraction(CardAuthorisationGatewayRequest request) {
        return request.isMoto() ? "Moto" : "Ecommerce";
    }

    public CancelRequestPayload createPaymentCancelRequest(CancelGatewayRequest request) {
        return new CancelRequestPayload(
                request.getExternalChargeId(),
                adyenMerchantAccountHelper.getMerchantAccount(request.getGatewayAccount())
        );
    }

    public CaptureRequestPayload createCapturePayload(CaptureGatewayRequest request) {
        return new CaptureRequestPayload(
                new Amount("GBP", request.getAmount()),
                adyenMerchantAccountHelper.getMerchantAccount(request.getGatewayAccount())
        );
    }

    public RefundRequestPayload createRefundRequestPayload(RefundGatewayRequest request) {
        return new RefundRequestPayload(
                adyenMerchantAccountHelper.getMerchantAccount(request.getGatewayAccount()),
                new Amount("GBP", Long.valueOf(request.getAmount())),
                request.getRefundExternalId(),
                adyenCredentialsHelper.getStore(request)
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

    private AdyenBrowserInfo mapToBrowserInfo(AuthCardDetails authCardDetails) {
        return adyenBrowserInfoFactory.create(authCardDetails);
    }

    String fromAgreementPaymentType(AgreementPaymentType agreementPaymentType) {
        return switch (agreementPaymentType) {
            case null -> "UnscheduledCardOnFile";
            case RECURRING, INSTALMENT -> "Subscription";
            default -> "UnscheduledCardOnFile";
        };
    }
}
