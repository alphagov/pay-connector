package uk.gov.pay.connector.gateway.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseInquiryResponse;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trim;

@XmlRootElement(name = "paymentService")
public class WorldpayOrderStatusResponse implements BaseAuthoriseResponse, BaseCancelResponse, BaseInquiryResponse {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final Set<String> SOFT_DECLINE_EXEMPTION_RESPONSE_RESULTS = Set.of("REJECTED", "OUT_OF_SCOPE");
    private static final String WORLDPAY_AUTHORISED_EVENT = "AUTHORISED";
    private static final String WORLDPAY_REFUSED_EVENT = "REFUSED";
    private static final String WORLDPAY_CANCELLED_EVENT = "CANCELLED";

    @XmlPath("reply/orderStatus/@orderCode")
    private String transactionId;

    @XmlPath("reply/orderStatus/token/tokenDetails/paymentTokenID/text()")
    private String paymentTokenId;

    // get transaction identifier to be passed with subsequent requests
    // get "CONFLICT"/ "CREATED" status to check to see if we should be storing a new payment isntrument - if it's conflict don't save new payment instrument refer to a previous
    // will need to think carefully about how that works across multiple gateway accounts/ services but I think we should be OK as the user is providing their details and getting through to auth
    
    // this is probably only provided _after_ auth
    @XmlPath("reply/orderStatus/payment/schemeResponse/transactionIdentifier/text()")
    private String schemeTransactionIdentifier;
    
    @XmlPath("reply/orderStatus/token/tokenDetails/@tokenEvent")
    private String tokenEvent;
    
    @XmlPath("reply/orderStatus/payment/lastEvent/text()")
    private String lastEvent;

    @XmlPath("reply/orderStatus/payment/ISO8583ReturnCode/@description")
    private String refusedReturnCodeDescription;

    @XmlPath("reply/orderStatus/payment/ISO8583ReturnCode/@code")
    private String refusedReturnCode;
    
    private String errorCode;

    private String errorMessage;

    @XmlPath("/reply/orderStatus/requestInfo/request3DSecure/paRequest/text()")
    private String paRequest;

    private String issuerUrl;

    private String challengeAcsUrl;

    @XmlPath("/reply/orderStatus/exemptionResponse/@result")
    private String exemptionResponseResult;

    @XmlPath("/reply/orderStatus/exemptionResponse/@reason")
    private String exemptionResponseReason;

    @XmlPath("/reply/orderStatus/challengeRequired/threeDSChallengeDetails/transactionId3DS/text()")
    private String challengeTransactionId;

    @XmlPath("/reply/orderStatus/challengeRequired/threeDSChallengeDetails/payload/text()")
    private String challengePayload;

    @XmlPath("/reply/orderStatus/challengeRequired/threeDSChallengeDetails/threeDSVersion/text()")
    private String threeDsVersion;

    @XmlPath("reply/error/@code")
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @XmlPath("reply/orderStatus/error/@code")
    public void setOrderStatusErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @XmlPath("reply/error/text()")
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @XmlPath("reply/orderStatus/error/text()")
    public void setOrderStatusErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @XmlPath("/reply/orderStatus/requestInfo/request3DSecure/issuerURL/text()")
    public void set3dsIssuerUrl(String issuerUrl) {
        this.issuerUrl = issuerUrl != null ? issuerUrl.trim() : null;
    }

    @XmlPath("/reply/orderStatus/challengeRequired/threeDSChallengeDetails/acsURL/text()")
    public void setChallengeAcsUrl(String challengeAcsUrl) {
        this.challengeAcsUrl = challengeAcsUrl != null ? challengeAcsUrl.trim() : null;
    }
    
    public boolean isSoftDecline() {
        return Optional.ofNullable(lastEvent).map("REFUSED"::equals).orElse(false)
                && Optional.ofNullable(exemptionResponseResult).map(SOFT_DECLINE_EXEMPTION_RESPONSE_RESULTS::contains).orElse(false);
    }

    public void setLastEvent(String lastEvent) {
        this.lastEvent = lastEvent;
    }

    public void setExemptionResponseResult(String exemptionResponseResult) {
        this.exemptionResponseResult = exemptionResponseResult;
    }

    public Optional<String> getExemptionResponseResult() {
        return Optional.ofNullable(exemptionResponseResult);
    }

    public String getExemptionResponseReason() {
        return exemptionResponseReason;
    }

    public String getPaRequest() {
        return paRequest;
    }

    public String getIssuerUrl() {
        return issuerUrl;
    }

    public Optional<String> getLastEvent() {
        return Optional.ofNullable(lastEvent).map(r -> r.isBlank() ? null : lastEvent);
    }

    public String getChallengeAcsUrl() {
        return challengeAcsUrl;
    }

    public String getChallengeTransactionId() {
        return challengeTransactionId;
    }

    public String getChallengePayload() {
        return challengePayload;
    }

    public String getThreeDsVersion() {
        return threeDsVersion;
    }

    public String getRefusedReturnCodeDescription() {
        return refusedReturnCodeDescription;
    }

    public String getRefusedReturnCode() {
        return refusedReturnCode;
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        if ((is3dsVersionOneRequired()) || is3dsFlexChallengeRequired()) {
            return AuthoriseStatus.REQUIRES_3DS;
        }

        if (WORLDPAY_AUTHORISED_EVENT.equals(lastEvent)) {
            return AuthoriseStatus.AUTHORISED;
        }

        if (WORLDPAY_REFUSED_EVENT.equals(lastEvent)) {
            return AuthoriseStatus.REJECTED;
        }

        if (WORLDPAY_CANCELLED_EVENT.equals(lastEvent)) {
            return AuthoriseStatus.CANCELLED;
        }

        return AuthoriseStatus.ERROR;
    }

    @Override
    public CancelStatus cancelStatus() {
        return CancelStatus.CANCELLED;
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public String getErrorCode() {
        return trim(errorCode);
    }

    @Override
    public String getErrorMessage() {
        return trim(errorMessage);
    }

    public Optional<Gateway3dsRequiredParams> getGatewayParamsFor3ds() {
        if (is3dsVersionOneRequired()) {
            return Optional.of(new Worldpay3dsRequiredParams(issuerUrl, paRequest));
        }
        if (is3dsFlexChallengeRequired()) {
            return Optional.of(new Worldpay3dsFlexRequiredParams(
                    challengeAcsUrl,
                    challengeTransactionId,
                    challengePayload,
                    threeDsVersion));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Map<String, String>> getGatewayRecurringAuthToken() {
        // we only want to actually create the payment instrument if this is a new token to worldpay, 
        // otherwise this card has already been used to set up a token (we've likely received "CONFLICT") and we should 
        // be using that payment instrument
        
        // if that's the case we probably want to _lookup_ what that payment instrument is
//        if (tokenEvent != null && tokenEvent.equals("NEW")) {
            
//        }
        logger.info("Scheme transaction identifier from response {}", schemeTransactionIdentifier);
        logger.info("Payment token ID from response {}", paymentTokenId);
        logger.info("Token event from response {}", tokenEvent);

        Map<String, String> recurringAuthToken = new HashMap<>();
        Optional.ofNullable(paymentTokenId).ifPresent(paymentTokenId -> recurringAuthToken.put("payment_token_id", paymentTokenId));
        
        Optional.ofNullable(schemeTransactionIdentifier).ifPresent(schemeTransactionIdentifier -> recurringAuthToken.put("scheme_transaction_identifier", schemeTransactionIdentifier));
        
        // for now only return anything if we got a payment token id
        if (paymentTokenId != null) {
            return Optional.of(recurringAuthToken);
        } else {
            return Optional.empty();
        }
//        return Optional.ofNullable(paymentTokenId).map(paymentTokenId ->
//                Map.of("payment_token_id", paymentTokenId)
//        );
    }

    private boolean is3dsVersionOneRequired() {
        return issuerUrl != null && paRequest != null;
    }

    private boolean is3dsFlexChallengeRequired() {
        return challengeAcsUrl != null &&
                challengeTransactionId != null &&
                challengePayload != null &&
                threeDsVersion != null;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Worldpay authorisation response (", ")");
        if (isNotBlank(getTransactionId())) {
            joiner.add("orderCode: " + getTransactionId());
        }
        if (isNotBlank(lastEvent)) {
            joiner.add("lastEvent: " + lastEvent);
        }
        if (isNotBlank(getRefusedReturnCode())) {
            joiner.add("ISO8583ReturnCode code: " + getRefusedReturnCode());
        }
        if (isNotBlank(getRefusedReturnCodeDescription())) {
            joiner.add("ISO8583ReturnCode description: " + getRefusedReturnCodeDescription());
        }
        if (isNotBlank(issuerUrl)) {
            joiner.add("issuerURL: " + issuerUrl);
        }
        if (isNotBlank(paRequest)) {
            joiner.add("paRequest: present");
        }
        if (isNotBlank(challengeAcsUrl)) {
            joiner.add("threeDSChallengeDetails acsUrl: " + challengeAcsUrl);
        }
        if (isNotBlank(challengeTransactionId)) {
            joiner.add("threeDSChallengeDetails transactionId3DS: " + challengeTransactionId);
        }
        if (isNotBlank(threeDsVersion)) {
            joiner.add("threeDSChallengeDetails threeDSVersion: " + threeDsVersion);
        }
        if (isNotBlank(exemptionResponseResult)) {
            joiner.add("exemptionResponse result: " + exemptionResponseResult);
        }
        if (isNotBlank(exemptionResponseReason)) {
            joiner.add("exemptionResponse reason: " + exemptionResponseReason);
        }
        if (isNotBlank(getErrorCode())) {
            joiner.add("error code: " + getErrorCode());
        }
        if (isNotBlank(getErrorMessage())) {
            joiner.add("error: " + getErrorMessage());
        }
        if (isNotBlank(getPaymentTokenId())) {
            joiner.add("payment token id: " + getPaymentTokenId());
        }
        if (isNotBlank(getSchemeTransactionIdentifier())) {
            joiner.add("scheme transaction identifier: " + getSchemeTransactionIdentifier());
        }
        if (isNotBlank(getTokenEvent())) {
            joiner.add("token event: " + getTokenEvent());
        }
        return joiner.toString();
    }

    public String getPaymentTokenId() {
        return paymentTokenId;
    }

    public String getSchemeTransactionIdentifier() {
        return schemeTransactionIdentifier;
    }

    public String getTokenEvent() {
        return tokenEvent;
    }
}
