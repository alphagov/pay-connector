package uk.gov.pay.connector.refund.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.exception.RefundNotFoundForChargeException;
import uk.gov.pay.connector.refund.exception.RefundNotFoundRuntimeException;
import uk.gov.pay.connector.refund.model.domain.GithubAndZendeskCredential;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.service.RefundReversalService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
@Tag(name = "Refunds")
public class RefundReversalResource {
    private final ChargeService chargeService;

    private final GatewayAccountDao gatewayAccountDao;

    private final RefundReversalService refundReversalService;

    @Inject
    public RefundReversalResource(ChargeService chargeService, GatewayAccountDao gatewayAccountDao, RefundReversalService refundReversalService) {
        this.chargeService = chargeService;
        this.gatewayAccountDao = gatewayAccountDao;
        this.refundReversalService = refundReversalService;
    }

    @POST
    @Path("/v1/api/accounts/{gatewayAccountId}/charges/{chargeId}/refunds/{refundId}/reverse-failed")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Fix failed refund charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "‘Operation not available for Worldpay’"),
                    @ApiResponse(responseCode = "404", description = "Not found - gateway account or charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public Response reverseRefund(@Parameter(example = "1", description = "Gateway account ID")
                                  @PathParam("gatewayAccountId") Long gatewayAccountId,
                                  @Parameter(example = "2c6vtn9pth38ppbmnt20d57t49", description = "Charge external ID")
                                  @PathParam("chargeId") String chargeExternalId,
                                  @Parameter(example = "3c6vtn9pth38ppbmnt20d57t49", description = "Refund external ID")
                                  @PathParam("refundId") String refundExternalId,
                                  @Context UriInfo uriInfo,
                                  @Parameter
                                  GithubAndZendeskCredential githubAndZendeskCredential
    ) {

        Charge charge = chargeService.findCharge(chargeExternalId)
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));

        if (!PaymentGatewayName.STRIPE.getName().equals(charge.getPaymentGatewayName())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Operation only available for Stripe")
                    .build();
        }

        GatewayAccountEntity gatewayAccount = gatewayAccountDao.findById(gatewayAccountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));

        if (!charge.getGatewayAccountId().equals(gatewayAccount.getId())) {
            throw new RefundNotFoundForChargeException(refundExternalId, chargeExternalId, gatewayAccountId);
        }

        Refund refund = refundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId)
                .orElseThrow(() -> new RefundNotFoundRuntimeException(refundExternalId));

        if (!refund.getChargeExternalId().equals(chargeExternalId)) {
            throw new RefundNotFoundForChargeException(refundExternalId, chargeExternalId, gatewayAccountId);
        }

        String githubUserId = githubAndZendeskCredential.githubUserId();
        String zendeskUserId = githubAndZendeskCredential.zendeskTicketId();
        return refundReversalService.reverseFailedRefund(gatewayAccount, refund, charge, githubUserId, zendeskUserId);

    }
}
