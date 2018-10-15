package uk.gov.pay.connector.gateway.smartpay;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Optional;
import java.util.StringJoiner;

@XmlRootElement(name = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
public class SmartpayAuthorisationResponse extends SmartpayBaseResponse implements BaseAuthoriseResponse {

    private static final String AUTHORISED = "Authorised";
    private static final String REDIRECT_SHOPPER = "RedirectShopper";
    private static final String REFUSED = "Refused";

    @XmlPath("soap:Body/ns1:authoriseResponse/ns1:paymentResult/ns1:resultCode/text()")
    private String result;

    @XmlPath("soap:Body/ns1:authoriseResponse/ns1:paymentResult/ns1:pspReference/text()")
    private String pspReference;

    @XmlPath("soap:Body/ns1:authoriseResponse/ns1:paymentResult/ns1:issuerUrl/text()")
    private String issuerUrl;

    @XmlPath("soap:Body/ns1:authoriseResponse/ns1:paymentResult/ns1:paRequest/text()")
    private String paRequest;

    @XmlPath("soap:Body/ns1:authoriseResponse/ns1:paymentResult/ns1:md/text()")
    private String md;

    public String getPspReference() {
        return pspReference;
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        if (result == null) {
            return AuthoriseStatus.ERROR;
        }
        switch (result) {
            case AUTHORISED:
                return AuthoriseStatus.AUTHORISED;
            case REDIRECT_SHOPPER:
                return AuthoriseStatus.REQUIRES_3DS;
            case REFUSED:
                return AuthoriseStatus.REJECTED;
            default:
                return AuthoriseStatus.ERROR;
        }
    }

    @Override
    public Optional<GatewayParamsFor3ds> getGatewayParamsFor3ds() {
        return Optional.of(new SmartpayParamsFor3ds(issuerUrl, paRequest, md));
    }

    @Override
    public String getTransactionId() {
        return pspReference;
    }

    public String getIssuerUrl() {
        return issuerUrl;
    }

    public String getPaRequest() {
        return paRequest;
    }

    public String getMd() {
        return md;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "SmartPay authorisation response (", ")");
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
