package uk.gov.pay.connector.gateway.smartpay;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import java.util.Optional;
import java.util.StringJoiner;

public class Smartpay3dsAuthorisationResponse extends SmartpayBaseResponse implements BaseAuthoriseResponse {

    private static final String AUTHORISED = "Authorised";
    private static final String REFUSED = "Refused";

    @XmlPath("soap:Body/ns1:authorise3dResponse/ns1:paymentResult/ns1:resultCode/text()")
    private String result;

    @XmlPath("soap:Body/ns1:authorise3dResponse/ns1:paymentResult/ns1:pspReference/text()")
    private String pspReference;
    
    @Override
    public AuthoriseStatus authoriseStatus() {
        if (result == null) {
            return AuthoriseStatus.ERROR;
        }
        switch (result) {
            case AUTHORISED:
                return AuthoriseStatus.AUTHORISED;
            case REFUSED:
                return AuthoriseStatus.REJECTED;
            default:
                return AuthoriseStatus.ERROR;
        }
    }

    @Override
    public Optional<GatewayParamsFor3ds> getGatewayParamsFor3ds() {
        return Optional.empty();
    }

    @Override
    public String getTransactionId() {
        return pspReference;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "SmartPay 3DS authorisation response (", ")");
        if (StringUtils.isNotBlank(getTransactionId())) {
            joiner.add("pspReference: " + getTransactionId());
        }
        if (StringUtils.isNotBlank(result)) {
            joiner.add("resultCode: " + result);
        }
        if (StringUtils.isNotBlank(getErrorCode())) {
            joiner.add("faultcode: " + getErrorCode());
        }
        if (StringUtils.isNotBlank(getErrorMessage())) {
            joiner.add("faultstring: " + getErrorMessage());
        }
        return joiner.toString();
    }
}
