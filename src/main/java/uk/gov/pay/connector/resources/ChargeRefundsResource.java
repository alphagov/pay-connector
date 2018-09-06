package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.RefundException;
import uk.gov.pay.connector.exception.RefundException.ErrorCode;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.RefundRequest;
import uk.gov.pay.connector.model.RefundResponse;
import uk.gov.pay.connector.model.RefundsResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseRefundResponse;
import uk.gov.pay.connector.service.ChargeRefundService;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Optional;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.exception.RefundException.ErrorCode.NOT_SUFFICIENT_AMOUNT_AVAILABLE;
import static uk.gov.pay.connector.resources.ChargesApiResource.MAX_AMOUNT;
import static uk.gov.pay.connector.resources.ChargesApiResource.MIN_AMOUNT;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithRefundNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

@Path("/")
public class ChargeRefundsResource {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ChargeRefundService refundService;
    private final ChargeDao chargeDao;

    @Inject
    public ChargeRefundsResource(ChargeRefundService refundService, ChargeDao chargeDao) {
        this.refundService = refundService;
        this.chargeDao = chargeDao;
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response submitRefund(@PathParam("accountId") Long accountId, @PathParam("chargeId") String chargeId, RefundRequest refundRequest, @Context UriInfo uriInfo) {
        validateRefundRequest(refundRequest.getAmount());
        return refundService.doRefund(accountId, chargeId, refundRequest)
                .map((refundServiceResponse) -> {
                    GatewayResponse<BaseRefundResponse> response = refundServiceResponse.getRefundGatewayResponse();
                    if (response.isSuccessful()) {
                        return Response.accepted(RefundResponse.valueOf(refundServiceResponse.getRefundEntity(), uriInfo).serialize()).build();
                    }
                    Optional<GatewayError> errorMaybe = response.getGatewayError();
                    String errorMessage = errorMaybe
                            .map(error -> errorMaybe.get().getMessage())
                            .orElse("unknown error");
                    return serviceErrorResponse(errorMessage);
                })
                .orElseGet(() -> {
                    logger.error("Error during refund of charge {} - RefundService did not return a Response", chargeId);
                    return serviceErrorResponse(format("something went wrong during refund of charge %s", chargeId));
                });
    }

    private void validateRefundRequest(long amount) {
        if (MAX_AMOUNT < amount) {
            throw RefundException.refundException("Not sufficient amount available for refund", NOT_SUFFICIENT_AMOUNT_AVAILABLE);
        }
        if (MIN_AMOUNT > amount) {
            throw RefundException.refundException("Validation error for amount. Minimum amount for a refund is " + MIN_AMOUNT, ErrorCode.MINIMUM_AMOUNT);
        }
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds/{refundId}")
    @Produces(APPLICATION_JSON)
    public Response getRefund(@PathParam("accountId") Long accountId, @PathParam("chargeId") String chargeId, @PathParam("refundId") String refundId, @Context UriInfo uriInfo) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> getRefundResponse(chargeEntity, refundId, uriInfo))
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds")
    @Produces(APPLICATION_JSON)
    public Response getRefunds(@PathParam("accountId") Long accountId, @PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> Response.ok(RefundsResponse.valueOf(chargeEntity, uriInfo).serialize()).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    private Response getRefundResponse(ChargeEntity chargeEntity, String refundId, UriInfo uriInfo) {
        return chargeEntity.getRefunds().stream()
                .filter(refundEntity -> refundEntity.getExternalId().equals(refundId))
                .findFirst()
                .map(refundEntity -> Response.ok(RefundResponse.valueOf(refundEntity, uriInfo).serialize()).build())
                .orElseGet(() -> responseWithRefundNotFound(refundId));
    }
}
