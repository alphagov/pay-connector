package uk.gov.pay.connector.gateway;

import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.instanceOf;

class ClientFactoryTest {

    @Test
    void shouldRestrictGatewayTlsConnectionsToTls13Only() throws NoSuchAlgorithmException {
        TlsSocketStrategy tlsSocketStrategy = ClientFactory.createTlsSocketStrategy("worldpay", "capture");

        assertThat(tlsSocketStrategy, instanceOf(TlsLoggingSocketStrategy.class));
        assertThat(((TlsLoggingSocketStrategy) tlsSocketStrategy).getEnabledProtocols(), arrayContaining("TLSv1.3"));
        //assertThat(
    }
}
