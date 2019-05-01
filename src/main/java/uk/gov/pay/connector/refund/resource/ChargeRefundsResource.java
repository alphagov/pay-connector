package uk.gov.pay.connector.refund.resource;

import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.refund.exception.RefundException;
import uk.gov.pay.connector.refund.model.RefundRequest;
import uk.gov.pay.connector.refund.model.RefundResponse;
import uk.gov.pay.connector.refund.model.RefundsResponse;
import uk.gov.pay.connector.refund.service.ChargeRefundService;

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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.MAX_AMOUNT;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.MIN_AMOUNT;
import static uk.gov.pay.connector.refund.exception.RefundException.ErrorCode.NOT_SUFFICIENT_AMOUNT_AVAILABLE;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithRefundNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

@Path("/")
public class ChargeRefundsResource {

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
        final ChargeRefundService.Response refundServiceResponse = refundService.doRefund(accountId, chargeId, refundRequest);
        GatewayRefundResponse refundResponse = refundServiceResponse.getGatewayRefundResponse();
        if (refundResponse.isSuccessful()) {
            return Response.accepted(RefundResponse.valueOf(refundServiceResponse.getRefundEntity(), uriInfo).serialize()).build();
        }
        return serviceErrorResponse(refundResponse.getError().map(GatewayError::getMessage).orElse("unknown error"));
    }

    private void validateRefundRequest(long amount) {
        if (MAX_AMOUNT < amount) {
            throw RefundException.notAvailableForRefundException("Not sufficient amount available for refund", NOT_SUFFICIENT_AMOUNT_AVAILABLE);
        }
        if (MIN_AMOUNT > amount) {
            throw RefundException.notAvailableForRefundException("Validation error for amount. Minimum amount for a refund is " + MIN_AMOUNT, RefundException.ErrorCode.MINIMUM_AMOUNT);
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
