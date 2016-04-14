package uk.gov.pay.connector.exception;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

public abstract class ConnectorRuntimeException extends WebApplicationException {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    public ConnectorRuntimeException(final Response response) {
        super(response);
    }
}