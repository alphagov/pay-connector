package uk.gov.pay.connector.service.epdq;

import com.google.common.base.Strings;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import uk.gov.pay.connector.model.ChargeStatusRequest;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toMap;
import static uk.gov.pay.connector.service.epdq.EpdqPaymentProvider.EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET;

public class EpdqNotification implements ChargeStatusRequest {

    static final String SHASIGN_KEY = "SHASIGN";
    private static final String PAYID_KEY = "PAYID";
    private static final String PAYIDSUB_KEY = "PAYIDSUB";
    private static final String STATUS_KEY = "STATUS";

    private final List<NameValuePair> paramsList;

    private final String status;
    private final String payId;
    private final String payIdSub;
    private final String shaSign;

    private Optional<ChargeStatus> chargeStatus = Optional.empty();

    public EpdqNotification(String payload) {
        try {
            paramsList = URLEncodedUtils.parse(payload, EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);

            Map<String, String> params = paramsList.stream()
                    .collect(toMap(NameValuePair::getName, NameValuePair::getValue));

            status = params.get(STATUS_KEY);
            payId = params.get(PAYID_KEY);
            payIdSub = params.get(PAYIDSUB_KEY);
            shaSign = params.get(SHASIGN_KEY);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not decode ePDQ notification payload as "
                    + EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET.name() + " application/x-www-form-urlencoded");
        }
    }

    public List<NameValuePair> getParams() {
        return paramsList;
    }

    public String getShaSign() {
        return shaSign;
    }

    public String getPayId() {
        return payId;
    }

    public String getPayIdSub() {
        return payIdSub;
    }

    public String getReference() {
        if (Strings.isNullOrEmpty(payId) || Strings.isNullOrEmpty(payIdSub)) {
            return "";
        }
        return payId + "/" + payIdSub;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String getTransactionId() {
        return getPayId();
    }

    @Override
    public Optional<ChargeStatus> getChargeStatus() {
        return chargeStatus;
    }

    public void setChargeStatus(Optional<ChargeStatus> chargeStatus) {
        this.chargeStatus = chargeStatus;
    }

}
