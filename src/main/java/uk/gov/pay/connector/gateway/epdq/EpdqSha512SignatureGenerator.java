package uk.gov.pay.connector.gateway.epdq;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.List;
import java.util.Locale;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

public class EpdqSha512SignatureGenerator implements SignatureGenerator {

    @Override
    public String sign(List<NameValuePair> params, String passphrase) {
        if (StringUtils.isBlank(passphrase)) {
            throw new IllegalArgumentException("Passphrase must not be blank.");
        }

        String stringToBeHashed = params.stream()
                .filter(param -> StringUtils.isNotEmpty(param.getValue()))
                .map(param -> new BasicNameValuePair(param.getName().toUpperCase(Locale.ENGLISH), param.getValue()))
                .sorted(comparing(NameValuePair::getName))
                .map(param -> param.getName() + "=" + param.getValue())
                .collect(joining(passphrase, "", passphrase));

        return DigestUtils.sha512Hex(stringToBeHashed);
    }

}
