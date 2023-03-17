package uk.gov.pay.connector.gateway.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.WorldpayAuthorisationRejectedCodeMapper;
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

    private static final Set<String> SOFT_DECLINE_EXEMPTION_RESPONSE_RESULTS = Set.of("REJECTED", "OUT_OF_SCOPE");
    private static final String WORLDPAY_AUTHORISED_EVENT = "AUTHORISED";
    private static final String WORLDPAY_REFUSED_EVENT = "REFUSED";
    private static final String WORLDPAY_CANCELLED_EVENT = "CANCELLED";

    public static final String WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY = "paymentTokenID";

    public static final String WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY = "schemeTransactionIdentifier";

    @XmlPath("reply/orderStatus/@orderCode")
    private String transactionId;

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

    @XmlPath("reply/orderStatus/token/tokenDetails/paymentTokenID/text()")
    private String paymentTokenId;

    @XmlPath("reply/orderStatus/payment/schemeResponse/transactionIdentifier/text()")
    private String schemeTransactionIdentifier;

    @XmlPath("reply/orderStatus/token/tokenDetails/@tokenEvent")
    private String tokenEvent;

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
        return Optional.ofNullable(paymentTokenId).map(tokenId -> {
            Map<String, String> recurringAuthToken = new HashMap<>();
            recurringAuthToken.put(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, tokenId);

            Optional.ofNullable(schemeTransactionIdentifier)
                    .ifPresent(transactionIdentifier -> recurringAuthToken.put(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, transactionIdentifier));
            return recurringAuthToken;
        });
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
            joiner.add("Mapped rejection reason: " + WorldpayAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason(getRefusedReturnCode()).name());
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
        if (isNotBlank(paymentTokenId)) {
            joiner.add("paymentTokenId: present");
        }
        if (isNotBlank(schemeTransactionIdentifier)) {
            joiner.add("schemeTransactionIdentifier: present");
        }
        if (isNotBlank(tokenEvent)) {
            joiner.add("tokenEvent: " + tokenEvent);
        }
        if (isNotBlank(getErrorCode())) {
            joiner.add("error code: " + getErrorCode());
        }
        if (isNotBlank(getErrorMessage())) {
            joiner.add("error: " + getErrorMessage());
        }
        return joiner.toString();
    }

}
