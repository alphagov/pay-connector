package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.RefundRequest;
import uk.gov.pay.connector.service.ChargeRefundService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.util.ResponseUtil.noContentResponse;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

@Path("/")
public class ChargeRefundsResource {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ChargeRefundService refundService;

    @Inject
    public ChargeRefundsResource(ChargeRefundService refundService) {
        this.refundService = refundService;
    }

    @POST
    @Path(REFUNDS_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response submitRefund(@PathParam("accountId") String accountId, @PathParam("chargeId") String chargeId, RefundRequest refund) {
        return refundService.doRefund(Long.valueOf(accountId), chargeId, Long.valueOf(refund.getAmount()))
                .map((response) -> response.isSuccessful() ? noContentResponse() :
                        serviceErrorResponse(response.getError().getMessage()))
                .orElseGet(() -> {
                    logger.error("Error during refund of charge {} - RefundService did not return a GatewayResponse", chargeId);
                    return serviceErrorResponse(format("something went wrong during refund of charge %s", chargeId));
                });
    }

    @GET
    @Path(REFUND_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response getRefund(@PathParam("accountId") String accountId, @PathParam("chargeId") String chargeId, @PathParam("refundId") String refundId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
