package uk.gov.pay.connector.service.epdq;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;

import java.util.List;
import java.util.StringJoiner;

public class EpdqSha512SignatureGenerator implements SignatureGenerator {

    @Override
    public String sign(List<NameValuePair> params, String passphrase) {
        if (StringUtils.isBlank(passphrase)) {
            throw new IllegalArgumentException("Passphrase must not be blank.");
        }

        StringJoiner input = new StringJoiner(passphrase, "", passphrase);
        params.forEach(param -> input.add(param.getName() + "=" + param.getValue()));
        return DigestUtils.sha512Hex(input.toString());
    }

}
