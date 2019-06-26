package uk.gov.pay.connector.gateway.epdq.payload;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public abstract class EpdqPayloadDefinition {

    static ParameterBuilder newParameterBuilder() {
        return new ParameterBuilder();
    }

    public static class ParameterBuilder {

        private ImmutableList.Builder<NameValuePair> parameters = new ImmutableList.Builder<>();

        public ParameterBuilder add(String name, String value) {
            if (!isEmpty(value)) {
                parameters.add(new BasicNameValuePair(name, value));
            }
            return this;
        }

        public ImmutableList<NameValuePair> build() {
            return parameters.build();
        }
        
    }
    
    public abstract ImmutableList<NameValuePair> extract(EpdqOrderRequestBuilder.EpdqTemplateData templateData);
}
