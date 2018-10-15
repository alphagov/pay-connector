package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.gateway.templates.PayloadDefinition;

public class EpdqSignedPayloadDefinition implements PayloadDefinition<EpdqOrderRequestBuilder.EpdqTemplateData> {

    static public class EpdqSignedPayloadDefinitionFactory {
        private SignatureGenerator signatureGenerator;

        static public EpdqSignedPayloadDefinitionFactory anEpdqSignedPayloadDefinitionFactory(SignatureGenerator signatureGenerator) {
            return new EpdqSignedPayloadDefinitionFactory(signatureGenerator);
        }

        public EpdqSignedPayloadDefinitionFactory(SignatureGenerator signatureGenerator) {
            this.signatureGenerator = signatureGenerator;
        }

        public EpdqSignedPayloadDefinition create(PayloadDefinition<EpdqOrderRequestBuilder.EpdqTemplateData> payloadDefinition) {
            return new EpdqSignedPayloadDefinition(this.signatureGenerator, payloadDefinition);
        }
    }

    final private SignatureGenerator signatureGenerator;
    final private PayloadDefinition<EpdqOrderRequestBuilder.EpdqTemplateData> payloadDefinition;

    private EpdqSignedPayloadDefinition(SignatureGenerator signatureGenerator, PayloadDefinition<EpdqOrderRequestBuilder.EpdqTemplateData> payloadDefinition) {
        this.signatureGenerator = signatureGenerator;
        this.payloadDefinition = payloadDefinition;
    }

    @Override
    public ImmutableList<NameValuePair> extract(EpdqOrderRequestBuilder.EpdqTemplateData templateData) {
        ImmutableList<NameValuePair> parameters = payloadDefinition.extract(templateData);
        return ImmutableList.<NameValuePair>builder()
                .addAll(parameters)
                .add(new BasicNameValuePair("SHASIGN", signatureGenerator.sign(parameters, templateData.getShaInPassphrase())))
                .build();
    }
}
