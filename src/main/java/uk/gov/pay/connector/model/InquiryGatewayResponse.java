package uk.gov.pay.connector.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.service.GatewayClient;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.unexpectedStatusCodeFromGateway;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.SUCCEDED;

public class InquiryGatewayResponse extends GatewayResponse {

    static private final Logger logger = LoggerFactory.getLogger(InquiryGatewayResponse.class);

    private String transactionId;
    private String newStatus;

    public InquiryGatewayResponse(ResponseStatus responseStatus, ErrorResponse error, String transactionId, String newStatus) {
        this.responseStatus = responseStatus;
        this.error = error;
        this.transactionId = transactionId;
        this.newStatus = newStatus;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public static InquiryGatewayResponse inquiryFailureResponse(ErrorResponse errorResponse) {
        return new InquiryGatewayResponse(FAILED, errorResponse, null, null);
    }

    public static InquiryGatewayResponse inquiryStatusUpdate(String transactionId, String newStatus) {
        return new InquiryGatewayResponse(SUCCEDED, null, transactionId, newStatus);
    }

    public static InquiryGatewayResponse errorInquiryResponse(GatewayClient.Response response) {
        logger.error(format("Error code received from gateway: %s.", response.getStatus()));
        return new InquiryGatewayResponse(FAILED, unexpectedStatusCodeFromGateway("Error processing status inquiry"), null, null);
    }
}
