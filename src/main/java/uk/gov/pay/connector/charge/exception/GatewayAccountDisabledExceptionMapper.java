package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;

public class GatewayAccountDisabledExceptionMapper implements ExceptionMapper<GatewayAccountDisabledException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAccountDisabledExceptionMapper.class);

    private static final String RESPONSE_ERROR_MESSAGE = "This gateway account is disabled";

    @Override
    public Response toResponse(GatewayAccountDisabledException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.ACCOUNT_DISABLED, List.of(RESPONSE_ERROR_MESSAGE));

        return Response.status(FORBIDDEN)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
