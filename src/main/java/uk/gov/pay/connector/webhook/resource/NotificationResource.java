package uk.gov.pay.connector.webhook.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.sandbox.SandboxNotificationService;
import uk.gov.pay.connector.gateway.stripe.StripeNotificationService;
import uk.gov.pay.connector.gateway.worldpay.WorldpayNotificationService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import static io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_XML;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.ResponseUtil.forbiddenErrorResponse;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

@Path("/")
@Tag(name = "Notifications")
public class NotificationResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationResource.class);

    private final WorldpayNotificationService worldpayNotificationService;
    private final SandboxNotificationService sandboxNotificationService;
    private final StripeNotificationService stripeNotificationService;

    @Inject
    public NotificationResource(WorldpayNotificationService worldpayNotificationService,
                                SandboxNotificationService sandboxNotificationService,
                                StripeNotificationService stripeNotificationService) {
        this.worldpayNotificationService = worldpayNotificationService;
        this.sandboxNotificationService = sandboxNotificationService;
        this.stripeNotificationService = stripeNotificationService;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path("/v1/api/notifications/sandbox")
    @Operation(
            summary = "Handle sandbox notifications",
            description = "This endpoint returns a HTTP status code of 200 for authorized requests. This is used for testing purposes. " +
                    "Note that the authorization methods for each of the v1/api/notifications/<PSP> endpoints uses different authorization " +
                    "methods and a successful response from this endpoint does not guarantee that the other notifications endpoints are working as expected. " +
                    "It does provide assurance that the requests are being correctly proxied to Connector and that Connector is responding. " +
                    "<br>" +
                    "Requests are authorised either via the source IP address extracted from the HTTP x-forwarded-for header against the expected CIDRs " +
                    "from SANDBOX_ALLOWED_CIDRS or by validating the secret provided via the HTTP Authorization header against the secret within SANDBOX_AUTH_TOKEN. " +
                    "The latter use of the HTTP Authorization header provides a means to test the endpoint without needing to send the request from a fixed IP address." +
                    "<br>" +
                    "The request body is not deserialised or processed in any way.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - notification rejected")
            }
    )
    public Response authoriseSandboxNotifications(
            @Parameter(in = HEADER, example = "let-me-in") @HeaderParam("Authorization") String authToken,
            @Parameter(in = HEADER, example = "1.1.1.1, 3.3.3.3") @HeaderParam("X-Forwarded-For") String forwardedIpAddresses) {

        if (!sandboxNotificationService.handleNotificationFor(forwardedIpAddresses, authToken)) {
            logRejectionMessage(forwardedIpAddresses, SANDBOX);
            return forbiddenErrorResponse();
        }
        return Response.ok().build();
    }

    @POST
    @Consumes(TEXT_XML)
    @Path("/v1/api/notifications/worldpay")
    @Produces({TEXT_XML, APPLICATION_JSON})
    @Operation(
            summary = "Handle Worldpay notifications",
            description = "See https://github.com/alphagov/pay-connector/blob/master/src/test/resources/templates/worldpay/notification.txt for example notification",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - notification rejected")
            }
    )
    public Response authoriseWorldpayNotifications(@Parameter(example = "see https://github.com/alphagov/pay-connector/blob/master/src/test/resources/templates/worldpay/notification.xml for example notification")
                                                   String notification,
                                                   @Parameter(in = HEADER, example = "4.3.2.1")
                                                   @HeaderParam("X-Forwarded-For") String forwardedIpAddresses) {
        if (!worldpayNotificationService.handleNotificationFor(forwardedIpAddresses, notification)) {
            logRejectionMessage(forwardedIpAddresses, WORLDPAY);
            return forbiddenErrorResponse();
        }
        String response = "[OK]";
        logResponseMessage(response, WORLDPAY);
        return Response.ok(response).build();
    }
    
    @POST
    @Consumes(APPLICATION_JSON)
    @Path("/v1/api/notifications/stripe")
    @Produces({TEXT_XML, APPLICATION_JSON})
    @Operation(
            summary = "Handle Stripe notifications",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - notification rejected")
            }
    )
    public Response authoriseStripeNotifications(String notification,
                                                 @Parameter(example = "t=1492774577,v1=5257a869e7ecebeda32affa62cdca3fa51cad7e77a0e56ff536d0ce8e108d8bd,v0=6ffbb59b2300aae63f272406069a9788598b792a944a07aba816edb039989a39")
                                                 @HeaderParam("Stripe-Signature") String signatureHeader,
                                                 @Parameter(example = "1.2.3.4")
                                                 @HeaderParam("X-Forwarded-For") String forwardedIpAddresses) {
        if (!stripeNotificationService.handleNotificationFor(notification, signatureHeader, forwardedIpAddresses)) {
            logRejectionMessage(forwardedIpAddresses, STRIPE);
            return forbiddenErrorResponse();
        }
        String response = "[OK]";
        logResponseMessage(response, STRIPE);
        return Response.ok(response).build();
    }

    private void logRejectionMessage(String forwardedIpAddresses, PaymentGatewayName gateway) {
        LOGGER.info(String.format("Rejected notification from provider %s for IP '%s'", gateway.getName(), forwardedIpAddresses),
                kv(PROVIDER, gateway.getName()),
                kv("notification_source", forwardedIpAddresses));
    }

    private void logResponseMessage(String response, PaymentGatewayName gateway) {
        LOGGER.info(String.format("Responding to notification from provider %s with 200 %s", gateway.getName(), response),
                kv(PROVIDER, gateway.getName()));
    }
}
