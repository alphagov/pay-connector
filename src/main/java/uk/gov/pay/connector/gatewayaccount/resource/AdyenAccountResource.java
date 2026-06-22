package uk.gov.pay.connector.gatewayaccount.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.gatewayaccount.model.AdyenGatewayAccountRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.AdyenAccountSetupService;
import uk.gov.pay.connector.gatewayaccount.service.AdyenTestAccountService;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountSwitchPaymentProviderService;

import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

@Path("/")
@Tag(name = "Gateway accounts")
public class AdyenAccountResource {

    private final AdyenTestAccountService adyenTestAccountService;
    private final GatewayAccountService gatewayAccountService;
    private final AdyenAccountSetupService adyenAccountSetupService;
    private final GatewayAccountSwitchPaymentProviderService gatewayAccountSwitchPaymentProviderService;

    @Inject
    public AdyenAccountResource(AdyenTestAccountService adyenTestAccountService,
                                GatewayAccountService gatewayAccountService, AdyenAccountSetupService adyenAccountSetupService,
                                GatewayAccountSwitchPaymentProviderService gatewayAccountSwitchPaymentProviderService) {
        this.adyenTestAccountService = adyenTestAccountService;
        this.gatewayAccountService = gatewayAccountService;
        this.adyenAccountSetupService = adyenAccountSetupService;
        this.gatewayAccountSwitchPaymentProviderService = gatewayAccountSwitchPaymentProviderService;
    }

    @POST
    @Path("/v1/api/service/{serviceId}/request-adyen-test-account")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Creates an Adyen Test Account " +
                    "and associated entities",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "409", description = "Adyen account already exists"),
                    @ApiResponse(responseCode = "502", description = "Bad gateway"),
            }
    )
    public Response requestAdyenTestAccount(
            @Parameter(example = "service-external-id-123", description = "Service ID") 
            @PathParam("serviceId") String serviceId, 
            Map<String, String> payload) {

        gatewayAccountService.throwIfStripeOrAdyenTestAccountAlreadyExists(serviceId);

        var serviceName = payload.get("service_name");
        AdyenCredentials adyenCredentials = adyenTestAccountService.createTestAccount(serviceName);

        AdyenGatewayAccountRequest adyenGatewayAccountRequest = AdyenGatewayAccountRequest.Builder
                .anAdyenAccountRequest()
                .withProviderAccountType(TEST.toString())
                .withPaymentProvider(ADYEN.getName())
                .withServiceName(serviceName)
                .withServiceId(serviceId)
                .withCredentials(adyenCredentials)
                .withAllowApplePay(true)
                .withAllowGooglePay(true)
                .build();

        GatewayAccountEntity adyenTestGatewayAccount = gatewayAccountService.createGatewayAccount(adyenGatewayAccountRequest);
        adyenAccountSetupService.completeTestAccountSetup(adyenTestGatewayAccount);
        
        Map<String, String> response = Map.of("gateway_account_id", adyenTestGatewayAccount.getId().toString(),
                "legal_entity_id", adyenCredentials.legalEntityId(),
                "store_id", adyenCredentials.storeId(),
                "account_holder_id", adyenCredentials.accountHolderId(),
                "balance_account_id", adyenCredentials.balanceAccountId());
        return Response.ok(response).build();
    }

    @POST
    @Path("/v1/api/service/{serviceId}/switch-to-adyen-test-account")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Disables Stripe test account, creates an Adyen Test Account and associated entities",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request - account ineligible for switch"),
                    @ApiResponse(responseCode = "502", description = "Bad gateway"),
            }
    )
    public Response switchToAdyenTestAccount(
            @Parameter(example = "service-external-id-123", description = "Service ID")
            @PathParam("serviceId") String serviceId,
            Map<String, String> payload) {
        
        gatewayAccountService.throwIfNoStripeTestAccount(serviceId);
        var gatewayAccount = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, TEST)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId));

        var serviceName = payload.get("service_name");
        AdyenCredentials adyenCredentials = adyenTestAccountService.createTestAccount(serviceName);
        gatewayAccountSwitchPaymentProviderService.switchStripeTestAccountToAdyen(gatewayAccount, adyenCredentials);
        
        Map<String, String> response = Map.of("gateway_account_id", serviceId,
                "legal_entity_id", adyenCredentials.legalEntityId(),
                "store_id", adyenCredentials.storeId(),
                "account_holder_id", adyenCredentials.accountHolderId(),
                "balance_account_id", adyenCredentials.balanceAccountId());
        
        return Response.ok(response).build();
    }
}
