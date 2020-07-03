package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.EpdqSha512SignatureGenerator;
import uk.gov.pay.connector.gateway.epdq.SignatureGenerator;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import javax.ws.rs.core.MediaType;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public abstract class EpdqPayloadDefinition {

    private static final SignatureGenerator SIGNATURE_GENERATOR = new EpdqSha512SignatureGenerator();

    /**
     * ePDQ have never confirmed that they use Windows-1252 to decode
     * application/x-www-form-urlencoded payloads sent by us to them and use
     * Windows-1252 to encode application/x-www-form-urlencoded notification
     * payloads sent from them to us but experimentation — and specifically the
     * fact that ’ (that’s U+2019 right single quotation mark in Unicode
     * parlance) seems to encode to %92 — makes us believe that they do
     */
    public static final Charset EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET = Charset.forName("windows-1252");

    protected String shaInPassphrase;

    protected abstract List<NameValuePair> extract();

    public GatewayOrder createGatewayOrder() {
        ArrayList<NameValuePair> params = new ArrayList<>(extract());
        String signature = SIGNATURE_GENERATOR.sign(params, getShaInPassphrase());
        params.add(new BasicNameValuePair("SHASIGN", signature));
        String payload = URLEncodedUtils.format(params, EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
        return new GatewayOrder(
                getOrderRequestType(),
                payload,
                MediaType.APPLICATION_FORM_URLENCODED_TYPE
        );
    }

    public abstract String getOperationType();

    protected abstract OrderRequestType getOrderRequestType();

    public void setShaInPassphrase(String shaInPassphrase) {
        this.shaInPassphrase = shaInPassphrase;
    }

    public String getShaInPassphrase() {
        return shaInPassphrase;
    }

}
