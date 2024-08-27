package uk.gov.pay.connector.gatewayaccount.resource;

import com.stripe.model.Account;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.charge.exception.ConflictWebApplicationException;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.gatewayaccount.model.StripeGatewayAccountRequest;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.StripeAccountService;
import uk.gov.pay.connector.gatewayaccount.service.StripeAccountSetupService;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

@Path("/")
@Tag(name = "Gateway accounts")
public class StripeAccountResource {

    private final StripeAccountService stripeAccountService;
    private final StripeAccountSetupService stripeAccountSetupService;
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public StripeAccountResource(StripeAccountService stripeAccountService, 
                                 StripeAccountSetupService stripeAccountSetupService,
                                 GatewayAccountService gatewayAccountService) {
        this.stripeAccountService = stripeAccountService;
        this.stripeAccountSetupService = stripeAccountSetupService;
        this.gatewayAccountService = gatewayAccountService;
    }

    @POST
    @Path("/v1/api/service/{serviceId}/request-stripe-test-account")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "1) Creates a Stripe Connect Account " +
                    "2) Creates a gateway account in connector and links this with the Stripe Connect Account id " +
                    "3) Disables the old sandbox account",
            responses = {
                    @ApiResponse(responseCode = "201", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Not found - Account with serviceId does not exist"),
                    @ApiResponse(responseCode = "409", description = "Stripe Connect Account already exists, or existing test account is not a Sandbox one"),
            }
    )
    public Response requestStripeTestAccount(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Context UriInfo uriInfo
    ) {
        var sandboxGatewayAccount = getSandboxGatewayAccount(serviceId);

        Account stripeTestConnectAccount = stripeAccountService.createTestAccount(sandboxGatewayAccount.getServiceName());
        stripeAccountService.createDefaultPersonForAccount(stripeTestConnectAccount.getId());

        var stripeCredentials = new StripeCredentials();
        stripeCredentials.setStripeAccountId(stripeTestConnectAccount.getId());
        StripeGatewayAccountRequest stripeGatewayAccountRequest = StripeGatewayAccountRequest.Builder.aStripeGatewayAccountRequest()
                .withProviderAccountType(TEST.toString())
                .withDescription(String.format("Stripe test account for service %s", sandboxGatewayAccount.getServiceName()))
                .withServiceName(sandboxGatewayAccount.getServiceName())
                .withServiceId(sandboxGatewayAccount.getServiceId())
                .withDescription(sandboxGatewayAccount.getDescription())
                .withAnalyticsId(sandboxGatewayAccount.getAnalyticsId())
                .withPaymentProvider(STRIPE.getName())
                .withRequires3ds(sandboxGatewayAccount.isRequires3ds())
                .withAllowApplePay(sandboxGatewayAccount.isAllowApplePay())
                .withAllowGooglePay(sandboxGatewayAccount.isAllowGooglePay())
                .withCredentials(stripeCredentials)
                .build();
        
        GatewayAccountEntity stripeTestGatewayAccount = gatewayAccountService.createGatewayAccount(stripeGatewayAccountRequest);
        stripeAccountSetupService.completeTestAccountSetup(stripeTestGatewayAccount);
        gatewayAccountService.disableAccount(sandboxGatewayAccount.getId(), String.format("Superseded by Stripe test account [ext id: %s]", stripeTestGatewayAccount.getExternalId()));

        Map<String, String> response = Map.of("stripe_connect_account_id", stripeTestConnectAccount.getId(),
                "gateway_account_id", stripeTestGatewayAccount.getId().toString(),
                "gateway_account_external_id", stripeTestGatewayAccount.getExternalId());
        return Response.ok(response).build();
    }

    private GatewayAccountEntity getSandboxGatewayAccount(String serviceId) {
        var maybeSandboxTestAccount = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, TEST)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, TEST));

        if (maybeSandboxTestAccount.getGatewayName().equalsIgnoreCase("stripe")) {
            throw new ConflictWebApplicationException("Cannot request Stripe test account because a Stripe test account already exists.");
        }

        if (!maybeSandboxTestAccount.getGatewayName().equalsIgnoreCase("sandbox")) {
            throw new ConflictWebApplicationException("Cannot request Stripe test account because existing test account is not a Sandbox one.");
        }
        return maybeSandboxTestAccount;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/stripe-account")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieves Stripe Connect account information for a given gateway account ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = StripeAccountResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - Account does not exist or not a stripe gateway account or account does not have Stripe credentials, ")
            }
    )
    public StripeAccountResponse getStripeAccount(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathParam("accountId") Long accountId) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .filter(GatewayAccountEntity::isStripeGatewayAccount)
                .flatMap(stripeAccountService::buildStripeAccountResponse)
                .orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/v1/api/service/{serviceId}/account/{accountType}/stripe-account")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieves Stripe Connect account information for a given service ID and account type (test|live)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = StripeAccountResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - Service does not exist or service does not have a Stripe gateway account of this type")
            }
    )
    public StripeAccountResponse getStripeAccountByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(serviceId, accountType);
                })
                .filter(GatewayAccountEntity::isStripeGatewayAccount)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(String.format("Gateway account for service ID [%s] and account type [%s] is not a Stripe account", serviceId, accountType));
                })
                .flatMap(stripeAccountService::buildStripeAccountResponse)
                .orElseThrow(() -> new NotFoundException(String.format("Stripe gateway account for service ID [%s] and account type [%s] does not have Stripe credentials", serviceId, accountType)));
    }

}
