package uk.gov.pay.connector.model;

import org.slf4j.Logger;

import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.unexpectedStatusCodeFromGateway;

public class InquiryResponse implements GatewayResponse {
    private Boolean successful;
    private ErrorResponse error;
    private String transactionId;
    private String newStatus;

    public InquiryResponse(Boolean successful, ErrorResponse error, String transactionId, String newStatus) {
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
    public ErrorResponse getError() {
        return error;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public static InquiryResponse inquiryFailureResponse(ErrorResponse errorResponse) {
        return new InquiryResponse(false, errorResponse, null, null);
    }

    public static InquiryResponse inquiryStatusUpdate(String transactionId, String newStatus) {
        return new InquiryResponse(true, null, transactionId, newStatus);
    }

    public static InquiryResponse errorInquiryResponse(Logger logger, Response response) {
        logger.error(format("Error code received from gateway: %s.", response.getStatus()));
        return new InquiryResponse(false, unexpectedStatusCodeFromGateway("Error processing status inquiry"), null, null);
    }
}
