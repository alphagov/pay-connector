package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public abstract class EpdqPayloadDefinition {

    static ParameterBuilder newParameterBuilder() {
        return new ParameterBuilder();
    }

    public static class ParameterBuilder {

        private List<BasicNameValuePair> parameters = new ArrayList<>();

        public ParameterBuilder add(String name, String value) {
            if (!isEmpty(value)) {
                parameters.add(new BasicNameValuePair(name, value));
            }
            return this;
        }

        public List<NameValuePair> build() {
            parameters.sort(Comparator.comparing(BasicNameValuePair::getName));
            return List.copyOf(parameters);
        }
        
    }
    
    public abstract List<NameValuePair> extract(EpdqOrderRequestBuilder.EpdqTemplateData templateData);
}
