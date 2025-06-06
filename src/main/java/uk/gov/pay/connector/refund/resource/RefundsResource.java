package uk.gov.pay.connector.refund.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.refund.exception.RefundException;
import uk.gov.pay.connector.refund.model.RefundRequest;
import uk.gov.pay.connector.refund.model.RefundResponse;
import uk.gov.pay.connector.refund.model.RefundsResponse;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.ChargeRefundResponse;
import uk.gov.pay.connector.refund.service.RefundService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.MAX_AMOUNT;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.MIN_AMOUNT;
import static uk.gov.pay.connector.refund.exception.RefundException.ErrorCode.NOT_SUFFICIENT_AMOUNT_AVAILABLE;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithRefundNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

@Path("/")
@Tag(name = "Refunds")
public class RefundsResource {

    private final RefundService refundService;
    private final ChargeService chargeService;
    private final GatewayAccountService gatewayAccountService;
    private final ChargeDao chargeDao;

    @Inject
    public RefundsResource(RefundService refundService, ChargeService chargeService, GatewayAccountService gatewayAccountService, ChargeDao chargeDao) {
        this.refundService = refundService;
        this.chargeService = chargeService;
        this.gatewayAccountService = gatewayAccountService;
        this.chargeDao = chargeDao;
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Refund a charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"amount\":3444," +
                                    "    \"created_date\":\"2016-10-05T14:15:34.096Z\"," +
                                    "    \"refund_id\":\"vijjk08adovg10gfqc46joem2l\"," +
                                    "    \"user_external_id\":\"AA213FD51B3801043FBC\"," +
                                    "    \"status\":\"success\"," +
                                    "    \"_links\":{" +
                                    "        \"self\":{\"href\":\"https://connector.example.com/v1/api/accounts/1/charges/2c6vtn9pth38ppbmnt20d57t49/refunds/vijjk08adovg10gfqc46joem2l\"}," +
                                    "        \"payment\":{\"href\":\"https://connector.example.com/v1/api/accounts/1/charges/2c6vtn9pth38ppbmnt20d57t49\"}" +
                                    "    }" +
                                    "}"))),
                    @ApiResponse(responseCode = "400", description = "Bad request - Invalid fields or not sufficient amount available for refund"),
                    @ApiResponse(responseCode = "404", description = "Not found - gateway account or charge not found"),
                    @ApiResponse(responseCode = "412", description = "Precondition failed - Refund amount available does not match"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public Response submitRefund(@Parameter(example = "1", description = "Gateway account ID")
                                 @PathParam("accountId") Long accountId,
                                 @Parameter(example = "2c6vtn9pth38ppbmnt20d57t49", description = "Charge external ID")
                                 @PathParam("chargeId") String chargeExternalId,
                                 RefundRequest refundRequest, @Context UriInfo uriInfo) {
        validateRefundRequest(refundRequest.getAmount());
        
        GatewayAccountEntity account = gatewayAccountService.getGatewayAccount(accountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(accountId));

        ChargeRefundResponse refundServiceResponse = chargeService.findCharge(chargeExternalId, accountId)
                .map(charge -> refundService.submitRefund(account, charge, refundRequest))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
        
        GatewayRefundResponse refundResponse = refundServiceResponse.getGatewayRefundResponse();
        if (refundResponse.isSuccessful()) {
            return Response.accepted(RefundResponse.valueOf(refundServiceResponse.getRefundEntity(), accountId, uriInfo).serialize()).build();
        }
        return serviceErrorResponse(refundResponse.getError().map(GatewayError::getMessage).orElse("unknown error"));
    }



    @POST
    @Path("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}/refunds")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Refund a charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"amount\":3444," +
                                    "    \"created_date\":\"2016-10-05T14:15:34.096Z\"," +
                                    "    \"refund_id\":\"vijjk08adovg10gfqc46joem2l\"," +
                                    "    \"user_external_id\":\"AA213FD51B3801043FBC\"," +
                                    "    \"status\":\"success\"," +
                                    "    \"_links\":{" +
                                    "        \"self\":{\"href\":\"https://connector.example.com/v1/api/service/46eb1b601348499196c99de90482ee68/account/test/charges/2c6vtn9pth38ppbmnt20d57t49/refunds/vijjk08adovg10gfqc46joem2l\"}," +
                                    "        \"payment\":{\"href\":\"https://connector.example.com/v1/api/service/46eb1b601348499196c99de90482ee68/account/test/charges/2c6vtn9pth38ppbmnt20d57t49\"}" +
                                    "    }" +
                                    "}"))),
                    @ApiResponse(responseCode = "400", description = "Bad request - Invalid fields or not sufficient amount available for refund"),
                    @ApiResponse(responseCode = "404", description = "Not found - gateway account or charge not found"),
                    @ApiResponse(responseCode = "412", description = "Precondition failed - Refund amount available does not match"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public Response submitRefundByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service external ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account Type") @PathParam("accountType") GatewayAccountType accountType,
            @Parameter(example = "2c6vtn9pth38ppbmnt20d57t49", description = "Charge external ID") @PathParam("chargeId") String chargeExternalId,
            @Valid RefundRequest refundRequest, @Context UriInfo uriInfo
    ) {
        validateRefundRequest(refundRequest.getAmount());
        
        GatewayAccountEntity account = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));

        ChargeRefundResponse refundServiceResponse = chargeService.findCharge(chargeExternalId, account.getId())
                .map(charge -> refundService.submitRefund(account, charge, refundRequest))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
        
        GatewayRefundResponse gatewayRefundResponse = refundServiceResponse.getGatewayRefundResponse();
        
        if (gatewayRefundResponse.isSuccessful()) {
            return Response.accepted(RefundResponse.valueOf(refundServiceResponse.getRefundEntity(), serviceId, accountType, uriInfo).serialize()).build();
        }
        return serviceErrorResponse(gatewayRefundResponse.getError().map(GatewayError::getMessage).orElse("unknown error"));
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
    @Operation(
            summary = "Get a refund",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"_links\": {" +
                                    "            \"payment\": {" +
                                    "                \"href\": \"https://connector.example.com/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn\"" +
                                    "            }," +
                                    "            \"self\": {" +
                                    "                \"href\": \"https://connector.example.com/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn/refunds/vijjk08adovg10gfqc46joem2l\"" +
                                    "            }" +
                                    "        }," +
                                    "    \"amount\": 3444," +
                                    "    \"created_date\": \"2016-10-05T14:15:34.096Z\"," +
                                    "    \"refund_id\": \"vijjk08adovg10gfqc46joem2l\"," +
                                    "    \"user_external_id\": \"AA213FD51B3801043FBC\"," +
                                    "    \"status\": \"success\"" +
                                    "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge or refund not found")
            }
    )
    public Response getRefund(@Parameter(example = "1", description = "Gateway account ID")
                              @PathParam("accountId") Long accountId,
                              @Parameter(example = "uqu4s24383qkod35rsb06gv3cn", description = "Charge external ID")
                              @PathParam("chargeId") String chargeId,
                              @Parameter(example = "vijjk08adovg10gfqc46joem2l", description = "Refund external ID")
                              @PathParam("refundId") String refundId,
                              @Context UriInfo uriInfo) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> getRefundResponse(chargeEntity, refundId, accountId, uriInfo))
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @GET
    @Path("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}/refunds/{refundId}")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Get a refund",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"_links\": {" +
                                    "            \"payment\": {" +
                                    "                \"href\": \"https://connector.example.com/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn\"" +
                                    "            }," +
                                    "            \"self\": {" +
                                    "                \"href\": \"https://connector.example.com/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn/refunds/vijjk08adovg10gfqc46joem2l\"" +
                                    "            }" +
                                    "        }," +
                                    "    \"amount\": 3444," +
                                    "    \"created_date\": \"2016-10-05T14:15:34.096Z\"," +
                                    "    \"refund_id\": \"vijjk08adovg10gfqc46joem2l\"," +
                                    "    \"user_external_id\": \"AA213FD51B3801043FBC\"," +
                                    "    \"status\": \"success\"" +
                                    "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge or refund not found")
            }
    )
    public Response getRefund(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID") @PathParam("chargeId") String chargeId,
            @Parameter(example = "vijjk08adovg10gfqc46joem2l", description = "Refund external ID") @PathParam("refundId") String refundId,
            @Context UriInfo uriInfo) {
        return chargeDao.findByExternalIdAndServiceIdAndAccountType(chargeId, serviceId, accountType)
                .map(chargeEntity -> 
                        getRefundResponse(chargeEntity, refundId, serviceId, accountType, uriInfo)
                )
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    private Response getRefundResponse(ChargeEntity chargeEntity, String refundId, Long accountId, UriInfo uriInfo) {
        return refundService.findNotExpungedRefunds(chargeEntity.getExternalId()).stream()
                .filter(refundEntity -> refundEntity.getExternalId().equals(refundId))
                .findFirst()
                .map(refundEntity -> Response.ok(RefundResponse.valueOf(refundEntity, accountId, uriInfo).serialize()).build())
                .orElseGet(() -> responseWithRefundNotFound(refundId));
    }

    private Response getRefundResponse(ChargeEntity chargeEntity, String refundId, String serviceId, GatewayAccountType accountType, UriInfo uriInfo) {
        return refundService.findNotExpungedRefunds(chargeEntity.getExternalId()).stream()
                .filter(refundEntity -> refundEntity.getExternalId().equals(refundId))
                .findFirst()
                .map(refundEntity -> Response.ok(RefundResponse.valueOf(refundEntity, serviceId, accountType, uriInfo).serialize()).build())
                .orElseGet(() -> responseWithRefundNotFound(refundId));
    }
}
