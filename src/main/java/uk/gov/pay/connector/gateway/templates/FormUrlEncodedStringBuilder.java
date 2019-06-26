package uk.gov.pay.connector.gateway.templates;

import org.apache.http.client.utils.URLEncodedUtils;
import uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinition;

import java.nio.charset.Charset;

public class FormUrlEncodedStringBuilder {

    private final EpdqPayloadDefinition payloadDefinition;
    private final Charset charset;

    public FormUrlEncodedStringBuilder(EpdqPayloadDefinition payloadDefinition, Charset charset) {
        this.payloadDefinition = payloadDefinition;
        this.charset = charset;
    }

    public String buildWith(EpdqOrderRequestBuilder.EpdqTemplateData templateData) {
        return URLEncodedUtils.format(payloadDefinition.extract(templateData), charset);
    }

}
