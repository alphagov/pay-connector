package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class EpdqParameterBuilder {

    private List<NameValuePair> parameters;

    public EpdqParameterBuilder(List<NameValuePair> parameters) {
        this.parameters = new ArrayList<>(parameters);
    }

    public EpdqParameterBuilder() {
        this.parameters = new ArrayList<>();
    }

    public EpdqParameterBuilder add(String name, String value) {
        if (!isEmpty(value)) {
            parameters.add(new BasicNameValuePair(name, value));
        }
        return this;
    }

    public List<NameValuePair> build() {
        parameters.sort(Comparator.comparing(NameValuePair::getName));
        return List.copyOf(parameters);
    }

    static EpdqParameterBuilder newParameterBuilder() {
        return new EpdqParameterBuilder();
    }

    static EpdqParameterBuilder newParameterBuilder(List<NameValuePair> params) {
        return new EpdqParameterBuilder(params);
    }
}
