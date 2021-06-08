package uk.gov.pay.connector.gatewayaccount.resource;

import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.worldpay.Worldpay3dsFlexCredentialsValidationService;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCredentialsValidationService;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsRequest;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.Worldpay3dsFlexCredentialsService;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class GatewayAccountCredentialsResource {
    private final GatewayAccountService gatewayAccountService;
    private final GatewayAccountCredentialsService gatewayAccountCredentialsService;
    private final Worldpay3dsFlexCredentialsService worldpay3dsFlexCredentialsService;
    private final Worldpay3dsFlexCredentialsValidationService worldpay3dsFlexCredentialsValidationService;
    private final WorldpayCredentialsValidationService worldpayCredentialsValidationService;
    private final GatewayAccountCredentialsRequestValidator gatewayAccountCredentialsRequestValidator;

    @Inject
    public GatewayAccountCredentialsResource(GatewayAccountService gatewayAccountService,
                                             GatewayAccountCredentialsService gatewayAccountCredentialsService,
                                             Worldpay3dsFlexCredentialsService worldpay3dsFlexCredentialsService,
                                             Worldpay3dsFlexCredentialsValidationService worldpay3dsFlexCredentialsValidationService,
                                             WorldpayCredentialsValidationService worldpayCredentialsValidationService,
                                             GatewayAccountCredentialsRequestValidator gatewayAccountCredentialsRequestValidator) {
        this.gatewayAccountService = gatewayAccountService;
        this.worldpay3dsFlexCredentialsService = worldpay3dsFlexCredentialsService;
        this.worldpay3dsFlexCredentialsValidationService = worldpay3dsFlexCredentialsValidationService;
        this.worldpayCredentialsValidationService = worldpayCredentialsValidationService;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
        this.gatewayAccountCredentialsRequestValidator = gatewayAccountCredentialsRequestValidator;
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/3ds-flex-credentials")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public Response createOrUpdateWorldpay3dsCredentials(@PathParam("accountId") Long gatewayAccountId,
                                                         @Valid Worldpay3dsFlexCredentialsRequest worldpay3dsCredentials) {

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .filter(gatewayAccountEntity ->
                        gatewayAccountEntity.getGatewayName().equals(PaymentGatewayName.WORLDPAY.getName()))
                .map(gatewayAccountEntity -> {
                    worldpay3dsFlexCredentialsService.setGatewayAccountWorldpay3dsFlexCredentials(worldpay3dsCredentials,
                            gatewayAccountEntity);
                    return Response.ok().build();
                })
                .orElseGet(() -> notFoundResponse("Not a Worldpay gateway account"));
    }


    @POST
    @Path("/v1/api/accounts/{accountId}/worldpay/check-3ds-flex-config")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public ValidationResult validateWorldpay3dsCredentials(@PathParam("accountId") Long gatewayAccountId,
                                                           @Valid Worldpay3dsFlexCredentialsRequest worldpay3dsCredentials) {
        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(gatewayAccountEntity ->
                        worldpay3dsFlexCredentialsValidationService.validateCredentials(gatewayAccountEntity, Worldpay3dsFlexCredentials.from(worldpay3dsCredentials)))
                .map(ValidationResult::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/worldpay/check-credentials")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public ValidationResult validateWorldpayCredentials(@PathParam("accountId") Long gatewayAccountId,
                                                        @Valid WorldpayCredentials worldpayCredentials) {
        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(gatewayAccountEntity -> worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayCredentials))
                .map(ValidationResult::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/credentials")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createGatewayAccountCredentials(@PathParam("accountId") Long gatewayAccountId, @Valid GatewayAccountCredentialsRequest gatewayAccountCredentialsRequest) {
        gatewayAccountCredentialsRequestValidator.validateCreate(gatewayAccountCredentialsRequest);

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(gatewayAccount -> {
                    Map<String, String> credentials = gatewayAccountCredentialsRequest.getCredentialsAsMap() == null ? Map.of() : gatewayAccountCredentialsRequest.getCredentialsAsMap();
                    gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccount, gatewayAccountCredentialsRequest.getPaymentProvider(), credentials);
                    return Response.ok().build();
                })
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    private class ValidationResult {
        private final String result;

        private ValidationResult(boolean isValid) {
            this.result = isValid ? "valid" : "invalid";
        }

        public String getResult() {
            return result;
        }
    }
}
