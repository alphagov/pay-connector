package uk.gov.pay.connector.webhook.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.epdq.EpdqNotificationService;
import uk.gov.pay.connector.gateway.sandbox.SandboxNotificationService;
import uk.gov.pay.connector.gateway.smartpay.SmartpayNotificationService;
import uk.gov.pay.connector.gateway.stripe.StripeNotificationService;
import uk.gov.pay.connector.gateway.worldpay.WorldpayNotificationService;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.ResponseUtil.forbiddenErrorResponse;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

@Path("/")
public class NotificationResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationResource.class);

    private final WorldpayNotificationService worldpayNotificationService;
    private final EpdqNotificationService epdqNotificationService;
    private final SandboxNotificationService sandboxNotificationService;
    private final SmartpayNotificationService smartpayNotificationService;
    private final StripeNotificationService stripeNotificationService;

    @Inject
    public NotificationResource(WorldpayNotificationService worldpayNotificationService,
                                EpdqNotificationService epdqNotificationService,
                                SandboxNotificationService sandboxNotificationService,
                                SmartpayNotificationService smartpayNotificationService,
                                StripeNotificationService stripeNotificationService) {
        this.worldpayNotificationService = worldpayNotificationService;
        this.sandboxNotificationService = sandboxNotificationService;
        this.smartpayNotificationService = smartpayNotificationService;
        this.epdqNotificationService = epdqNotificationService;
        this.stripeNotificationService = stripeNotificationService;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @PermitAll
    @Path("/v1/api/notifications/smartpay")
    public Response authoriseSmartpayNotifications(String notification,
                                                   @HeaderParam("X-Forwarded-For") String forwardedIpAddresses) {
        LOGGER.info(String.format("Received notification for provider %s IP '%s'", SMARTPAY.getName(),  forwardedIpAddresses),
                kv(PROVIDER, SMARTPAY.getName()),
                kv("notification_source", forwardedIpAddresses));
        if (!smartpayNotificationService.handleNotificationFor(notification, forwardedIpAddresses)) {
            logRejectionMessage(forwardedIpAddresses, SMARTPAY);
            return forbiddenErrorResponse();
        }
        String response = "[accepted]";
        logResponseMessage(response, SMARTPAY);
        return Response.ok(response).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path("/v1/api/notifications/sandbox")
    public Response authoriseSandboxNotifications(@HeaderParam("Authorization") String authToken,
                                                  @HeaderParam("X-Forwarded-For") String forwardedIpAddresses) {

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
    public Response authoriseWorldpayNotifications(String notification, @HeaderParam("X-Forwarded-For") String forwardedIpAddresses) {
        if (!worldpayNotificationService.handleNotificationFor(forwardedIpAddresses, notification)) {
            logRejectionMessage(forwardedIpAddresses, WORLDPAY);
            return forbiddenErrorResponse();
        }
        String response = "[OK]";
        logResponseMessage(response, WORLDPAY);
        return Response.ok(response).build();
    }

    @POST
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Path("/v1/api/notifications/epdq")
    @Produces({TEXT_XML, APPLICATION_JSON})
    public Response authoriseEpdqNotifications(String notification,
                                               @HeaderParam("X-Forwarded-For") String forwardedIpAddresses) {
        if (!epdqNotificationService.handleNotificationFor(notification, forwardedIpAddresses)) {
            logRejectionMessage(forwardedIpAddresses, EPDQ);
            return forbiddenErrorResponse();
        }
        String response = "[OK]";
        logResponseMessage(response, EPDQ);
        return Response.ok(response).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path("/v1/api/notifications/stripe")
    @Produces({TEXT_XML, APPLICATION_JSON})
    public Response authoriseStripeNotifications(String notification,
                                                 @HeaderParam("Stripe-Signature") String signatureHeader,
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
