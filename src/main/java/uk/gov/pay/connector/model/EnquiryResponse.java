package uk.gov.pay.connector.model;

import org.slf4j.Logger;

import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;

public class EnquiryResponse implements GatewayResponse {
    private Boolean successful;
    private GatewayError error;
    private String transactionId;
    private String newStatus;

    public EnquiryResponse(Boolean successful, GatewayError error, String transactionId, String newStatus) {
        this.successful = successful;
        this.error = error;
        this.transactionId = transactionId;
        this.newStatus = newStatus;
    }

    @Override
    public Boolean isSuccessful() {
        return successful;
    }

    @Override
    public GatewayError getError() {
        return error;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public static EnquiryResponse enquiryFailureResponse(GatewayError gatewayError) {
        return new EnquiryResponse(false, gatewayError, null, null);
    }

    public static EnquiryResponse statusUpdate(String transactionId, String newStatus) {
        return new EnquiryResponse(true, null, transactionId, newStatus);
    }

    public static EnquiryResponse errorEnquiryResponse(Logger logger, Response response) {
        logger.error(format("Error code received from gateway: %s.", response.getStatus()));
        return new EnquiryResponse(false, baseGatewayError("Error processing request"), null, null);
    }
}
