package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.EpdqSha512SignatureGenerator;
import uk.gov.pay.connector.gateway.epdq.EpdqTemplateData;
import uk.gov.pay.connector.gateway.epdq.SignatureGenerator;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import javax.ws.rs.core.MediaType;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public abstract class EpdqPayloadDefinition {

    private static final SignatureGenerator signatureGenerator = new EpdqSha512SignatureGenerator();

    /**
     * ePDQ have never confirmed that they use Windows-1252 to decode
     * application/x-www-form-urlencoded payloads sent by us to them and use
     * Windows-1252 to encode application/x-www-form-urlencoded notification
     * payloads sent from them to us but experimentation — and specifically the
     * fact that ’ (that’s U+2019 right single quotation mark in Unicode
     * parlance) seems to encode to %92 — makes us believe that they do
     */
    private static final Charset EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET = Charset.forName("windows-1252");

    protected abstract List<NameValuePair> extract(EpdqTemplateData templateData);

    public GatewayOrder createGatewayOrder(EpdqTemplateData templateData) {
        templateData.setOperationType(getOperationType());
        List<NameValuePair> templateParams = extract(templateData);
        List<NameValuePair> requestParams = new ArrayList<>(templateParams);
        requestParams.add(new BasicNameValuePair("SHASIGN", signatureGenerator.sign(templateParams, templateData.getShaInPassphrase())));
        String payload = URLEncodedUtils.format(requestParams, EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
        return new GatewayOrder(
                getOrderRequestType(),
                payload,
                MediaType.APPLICATION_FORM_URLENCODED_TYPE
        );
    }

    protected abstract String getOperationType();

    protected abstract OrderRequestType getOrderRequestType();
}
