package uk.gov.pay.connector.service.epdq;

import org.apache.http.NameValuePair;

import java.util.List;

public interface SignatureGenerator {

    String sign(List<NameValuePair> params, String passphrase);

}
