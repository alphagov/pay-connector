package uk.gov.pay.connector.service.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class EpdqSha512SignatureGeneratorTest {

    private EpdqSha512SignatureGenerator epdqSha512SignatureGenerator = new EpdqSha512SignatureGenerator();

    @Test
    public void shouldConcatEachParameterAsNameThenEqualsThenValueThenPassphraseToProduceSha512SignatureAsHex() {
        List<NameValuePair> params = ImmutableList.of(
                new BasicNameValuePair("Param1", "Value1"),
                new BasicNameValuePair("Param2", "Value2"),
                new BasicNameValuePair("Param3", "Value3"));

        String passphrase = "MySuperSecretPassphrase";

        // SHA-512 hash of "Param1=Value1MySuperSecretPassphraseParam2=Value2MySuperSecretPassphraseParam3=Value3MySuperSecretPassphrase" as a hex string
        String expected = "24b018e911c15df663240c1dca979b93d51d1e222980bc1f8efe4c481d06eb47b31526c73999554a4b95313cf8bdbfdbc8b740f714a6b429eff44901a96d96f6";

        String actual = epdqSha512SignatureGenerator.sign(params, passphrase);

        assertThat(actual, is(expected));
    }

}
