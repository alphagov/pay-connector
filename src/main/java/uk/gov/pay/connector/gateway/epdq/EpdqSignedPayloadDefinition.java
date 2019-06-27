package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinition;

import java.util.List;

public class EpdqSignedPayloadDefinition extends EpdqPayloadDefinition {

    static public class EpdqSignedPayloadDefinitionFactory {
        private SignatureGenerator signatureGenerator;

        static public EpdqSignedPayloadDefinitionFactory anEpdqSignedPayloadDefinitionFactory(SignatureGenerator signatureGenerator) {
            return new EpdqSignedPayloadDefinitionFactory(signatureGenerator);
        }

        public EpdqSignedPayloadDefinitionFactory(SignatureGenerator signatureGenerator) {
            this.signatureGenerator = signatureGenerator;
        }

        public EpdqSignedPayloadDefinition create(EpdqPayloadDefinition payloadDefinition) {
            return new EpdqSignedPayloadDefinition(this.signatureGenerator, payloadDefinition);
        }
    }

    final private SignatureGenerator signatureGenerator;
    final private EpdqPayloadDefinition payloadDefinition;

    private EpdqSignedPayloadDefinition(SignatureGenerator signatureGenerator, EpdqPayloadDefinition payloadDefinition) {
        this.signatureGenerator = signatureGenerator;
        this.payloadDefinition = payloadDefinition;
    }

    @Override
    public List<NameValuePair> extract(EpdqOrderRequestBuilder.EpdqTemplateData templateData) {
        List<NameValuePair> parameters = payloadDefinition.extract(templateData);
        return ImmutableList.<NameValuePair>builder()
                .addAll(parameters)
                .add(new BasicNameValuePair("SHASIGN", signatureGenerator.sign(parameters, templateData.getShaInPassphrase())))
                .build();
    }
}
