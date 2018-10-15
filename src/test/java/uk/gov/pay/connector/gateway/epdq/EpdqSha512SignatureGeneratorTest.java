package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class EpdqSha512SignatureGeneratorTest {

    private final EpdqSha512SignatureGenerator epdqSha512SignatureGenerator = new EpdqSha512SignatureGenerator();

    @Test
    public void shouldConcatEachParameterAsNameThenEqualsThenValueThenPassphraseToProduceSha512SignatureAsHex() {
        List<NameValuePair> params = ImmutableList.of(
                new BasicNameValuePair("PARAM1", "Value1"),
                new BasicNameValuePair("PARAM2", "Value2"),
                new BasicNameValuePair("PARAM3", "Value3"));

        String passphrase = "MySuperSecretPassphrase";

        // SHA-512 hash of "PARAM1=Value1MySuperSecretPassphrasePARAM2=Value2MySuperSecretPassphrasePARAM3=Value3MySuperSecretPassphrase" as a hex string
        String expected = "753182e476fcaf50bc3cbd05d132f217cad1f83286f547e2a414ba9267bebd91c1247ab1e91c7d75981c3827255b588b178a987891c2357ad411c4418ebda4c9";

        String actual = epdqSha512SignatureGenerator.sign(params, passphrase);

        assertThat(actual, is(expected));
    }

    @Test
    public void shouldIgnoreEmptyParameters() {
        List<NameValuePair> params = ImmutableList.of(
                new BasicNameValuePair("PARAM1", "Value1"),
                new BasicNameValuePair("PARAM2", ""),
                new BasicNameValuePair("PARAM3", "Value3"));

        String passphrase = "MySuperSecretPassphrase";

        // SHA-512 hash of "PARAM1=Value1MySuperSecretPassphrasePARAM3=Value3MySuperSecretPassphrase" as a hex string
        String expected = "69b8bb7e1fbd7f6a619d0a32ce69e428322651d9e5a5677a9f2ebd1ba79178874f18edcdd400c83ba7591aeb2e29b54128051ade6e1b499fa726f1da4ef1f34f";

        String actual = epdqSha512SignatureGenerator.sign(params, passphrase);

        assertThat(actual, is(expected));
    }

    @Test
    public void shouldUpperCaseParameterNames() {
        List<NameValuePair> params = ImmutableList.of(
                new BasicNameValuePair("param1", "Value1"),
                new BasicNameValuePair("Param2", "Value2"),
                new BasicNameValuePair("pArAm3", "Value3"));

        String passphrase = "MySuperSecretPassphrase";

        // SHA-512 hash of "PARAM1=Value1MySuperSecretPassphrasePARAM2=Value2MySuperSecretPassphrasePARAM3=Value3MySuperSecretPassphrase" as a hex string
        String expected = "753182e476fcaf50bc3cbd05d132f217cad1f83286f547e2a414ba9267bebd91c1247ab1e91c7d75981c3827255b588b178a987891c2357ad411c4418ebda4c9";

        String actual = epdqSha512SignatureGenerator.sign(params, passphrase);

        assertThat(actual, is(expected));
    }

    @Test
    public void shouldSortParametersAlphabeticallyByName() {
        List<NameValuePair> params = ImmutableList.of(
                new BasicNameValuePair("BBB", "Value2"),
                new BasicNameValuePair("DDD", "Value4"),
                new BasicNameValuePair("CCC", "Value3"),
                new BasicNameValuePair("AAA", "Value1"));

        String passphrase = "MySuperSecretPassphrase";

        // SHA-512 hash of "AAA=Value1MySuperSecretPassphraseBBB=Value2MySuperSecretPassphraseCCC=Value3MySuperSecretPassphraseDDD=Value4MySuperSecretPassphrase" as a hex string
        String expected = "b4afdf2463a2d1ea6fa4a680b0fa1e574edf7a94541539e7b6fdc24a34f73b6868b09056c5e2a46280db7f316858bff77b3c78bc9050f6454d3eb98dc40d2662";

        String actual = epdqSha512SignatureGenerator.sign(params, passphrase);

        assertThat(actual, is(expected));
    }

    @Test
    public void shouldPerformAllNormalisations() {
        List<NameValuePair> params = ImmutableList.of(
                new BasicNameValuePair("bBb", "Value2"),
                new BasicNameValuePair("DDD", "Value4"),
                new BasicNameValuePair("CCC", ""),
                new BasicNameValuePair("aaa", "Value1"));

        String passphrase = "MySuperSecretPassphrase";

        // SHA-512 hash of "AAA=Value1MySuperSecretPassphraseBBB=Value2MySuperSecretPassphraseDDD=Value4MySuperSecretPassphrase" as a hex string
        String expected = "7dfff33e65499c7074bbe03b84ead5528bb97b40195a892f13775ca26588eda661e44cac205fd91f337f54fabb755211580221c6d4c99d444a1aef98eb5a4ad8";

        String actual = epdqSha512SignatureGenerator.sign(params, passphrase);

        assertThat(actual, is(expected));
    }

    @Test
    public void shouldNotModifyInputList() {
        List<NameValuePair> params = new ArrayList<>(ImmutableList.of(
                new BasicNameValuePair("bBb", "Value2"),
                new BasicNameValuePair("DDD", "Value4"),
                new BasicNameValuePair("CCC", ""),
                new BasicNameValuePair("aaa", "Value1")));

        List<NameValuePair> originalParams = new ArrayList<>(params);

        String passphrase = "MySuperSecretPassphrase";

        epdqSha512SignatureGenerator.sign(params, passphrase);

        assertThat(params, is(originalParams));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowAnIllegalArgumentExceptionIfPassphraseIsBlank() {
        List<NameValuePair> params = ImmutableList.of(
                new BasicNameValuePair("PARAM1", "Value1"),
                new BasicNameValuePair("PARAM2", "Value2"),
                new BasicNameValuePair("PARAM3", "Value3"));

        epdqSha512SignatureGenerator.sign(params, " ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowAnIllegalArgumentExceptionIfPassphraseIsNull() {
        List<NameValuePair> params = ImmutableList.of(
                new BasicNameValuePair("PARAM1", "Value1"),
                new BasicNameValuePair("PARAM2", "Value2"),
                new BasicNameValuePair("PARAM3", "Value3"));

        epdqSha512SignatureGenerator.sign(params, null);
    }

}
