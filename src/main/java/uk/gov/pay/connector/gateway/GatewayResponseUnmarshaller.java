package uk.gov.pay.connector.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.util.XMLUnmarshallerException;

import static java.lang.String.format;

public class GatewayResponseUnmarshaller {

    private static final Logger logger = LoggerFactory.getLogger(GatewayResponseUnmarshaller.class);
    
    public static <T> T unmarshallResponse(GatewayClient.Response response, Class<T> unmarshallingTarget) throws GatewayException.GatewayErrorException {
        String payload = response.getEntity();
        logger.debug("response payload={}", payload);
        try {
            return XMLUnmarshaller.unmarshall(payload, unmarshallingTarget);
        } catch (XMLUnmarshallerException e) {
            String error = format("Could not unmarshall response %s.", payload);
            logger.error(error, e);
            throw new GatewayException.GatewayErrorException("Invalid Response Received From Gateway");
        }
    }
}
