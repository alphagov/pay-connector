package uk.gov.pay.connector.gateway.epdq;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

public class EpdqSha512SignatureGenerator implements SignatureGenerator {

    @Override
    public String sign(List<NameValuePair> params, String passphrase) {
        if (StringUtils.isBlank(passphrase)) {
            throw new IllegalArgumentException("Passphrase must not be blank.");
        }

        List<NameValuePair> normalisedParams = params.stream()
                .filter(param -> StringUtils.isNotEmpty(param.getValue()))
                .map(param -> new BasicNameValuePair(param.getName().toUpperCase(Locale.ENGLISH), param.getValue()))
                .sorted(comparing(BasicNameValuePair::getName))
                .collect(toList());

        StringJoiner input = new StringJoiner(passphrase, "", passphrase);
        normalisedParams.forEach(param -> input.add(param.getName() + "=" + param.getValue()));
        return DigestUtils.sha512Hex(input.toString());
    }

}
