package uk.gov.pay.connector.gateway;

import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

public class TlsLoggingSocketStrategy extends DefaultClientTlsStrategy {

    private static final String[] ENABLED_TLS_PROTOCOLS = {"TLSv1.3"};
    private static final Logger LOGGER = LoggerFactory.getLogger(TlsLoggingSocketStrategy.class);


    private final String gatewayName;
    private final String operation;

    String[] getEnabledProtocols() {
        return ENABLED_TLS_PROTOCOLS.clone();
    }
    
    public TlsLoggingSocketStrategy(SSLContext sslContext, String gatewayName, String operation) {
        super(sslContext, ENABLED_TLS_PROTOCOLS, null, SSLBufferMode.STATIC, HostnameVerificationPolicy.BUILTIN, null);
        this.gatewayName = gatewayName;
        this.operation = operation;
    }

//    @Override
//    public SSLSocket upgrade(Socket socket,
//                             String target,
//                             int port,
//                             Object attachment,
//                             HttpContext context) throws IOException { //return super.upgrade(socket, target, port, attachment, context); }

    private void logTlsDetails() {
    LOGGER.atInfo().setMessage("TLS details for gateway: {} and operation: {}").addArgument(gatewayName).addArgument(operation).log();
    }
}
