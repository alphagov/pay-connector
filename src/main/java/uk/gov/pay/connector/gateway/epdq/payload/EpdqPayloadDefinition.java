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
import java.util.ArrayList;
import java.util.List;

import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET;

public abstract class EpdqPayloadDefinition {

    private static final SignatureGenerator SIGNATURE_GENERATOR = new EpdqSha512SignatureGenerator();
    
    protected abstract List<NameValuePair> extract(EpdqTemplateData templateData);

    public GatewayOrder createGatewayOrder(EpdqTemplateData templateData) {
        templateData.setOperationType(getOperationType());
        ArrayList<NameValuePair> params = new ArrayList<>(extract(templateData));
        String signature = SIGNATURE_GENERATOR.sign(params, templateData.getShaInPassphrase());
        params.add(new BasicNameValuePair("SHASIGN", signature));
        String payload = URLEncodedUtils.format(params, EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
        return new GatewayOrder(
                getOrderRequestType(),
                payload,
                MediaType.APPLICATION_FORM_URLENCODED_TYPE
        );
    }

    protected abstract String getOperationType();

    protected abstract OrderRequestType getOrderRequestType();
}
