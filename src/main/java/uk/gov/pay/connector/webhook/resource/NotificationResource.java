package uk.gov.pay.connector.webhook.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.epdq.EpdqNotificationService;
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
import static uk.gov.pay.connector.util.ResponseUtil.forbiddenErrorResponse;

@Path("/")
public class NotificationResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationResource.class);

    private final WorldpayNotificationService worldpayNotificationService;
    private final EpdqNotificationService epdqNotificationService;
    private final SmartpayNotificationService smartpayNotificationService;
    private final StripeNotificationService stripeNotificationService;

    @Inject
    public NotificationResource(WorldpayNotificationService worldpayNotificationService,
                                EpdqNotificationService epdqNotificationService,
                                SmartpayNotificationService smartpayNotificationService,
                                StripeNotificationService stripeNotificationService) {
        this.worldpayNotificationService = worldpayNotificationService;
        this.smartpayNotificationService = smartpayNotificationService;
        this.epdqNotificationService = epdqNotificationService;
        this.stripeNotificationService = stripeNotificationService;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @PermitAll
    @Path("/v1/api/notifications/smartpay")
    public Response authoriseSmartpayNotifications(String notification, @HeaderParam("X-Forwarded-For") String ipAddress) {
        LOGGER.info(String.format("Received notification for smartpay ip '%s'", ipAddress), kv("notification_source", ipAddress));
        smartpayNotificationService.handleNotificationFor(notification);
        String response = "[accepted]";
        LOGGER.info("Responding to notification from provider=smartpay with 200 {}", response);
        return Response.ok(response).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path("/v1/api/notifications/sandbox")
    public Response authoriseSandboxNotifications(String notification) {
        return Response.ok().build();
    }

    @POST
    @Consumes(TEXT_XML)
    @Path("/v1/api/notifications/worldpay")
    @Produces({TEXT_XML, APPLICATION_JSON})
    public Response authoriseWorldpayNotifications(String notification, @HeaderParam("X-Forwarded-For") String ipAddress) {
        if (!worldpayNotificationService.handleNotificationFor(ipAddress, notification)) {
            LOGGER.info(String.format("Rejected notification from provider=Worldpay for IP '%s'", ipAddress), kv("notification_source", ipAddress));
            return forbiddenErrorResponse();
        }
        String response = "[OK]";
        LOGGER.info("Responding to notification from provider={} with 200 {}", "worldpay", response);
        return Response.ok(response).build();
    }

    @POST
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Path("/v1/api/notifications/epdq")
    @Produces({TEXT_XML, APPLICATION_JSON})
    public Response authoriseEpdqNotifications(String notification,
                                               @HeaderParam("X-Forwarded-For") String forwardedIpAddresses) {
        if (!epdqNotificationService.handleNotificationFor(notification, forwardedIpAddresses)) {
            LOGGER.info(String.format("Rejected notification from provider=Epdq for IP '%s'", forwardedIpAddresses), kv("notification_source", forwardedIpAddresses));
            return forbiddenErrorResponse();
        }
        String response = "[OK]";
        LOGGER.info("Responding to notification from provider={} with 200 {}", "epdq", response);
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
            LOGGER.info(String.format("Rejected notification from provider=Stripe for IP '%s'", forwardedIpAddresses), kv("notification_source", forwardedIpAddresses));
            return forbiddenErrorResponse();
        }
        String response = "[OK]";
        LOGGER.info("Responding to notification from provider=Stripe with 200 {}", response);
        return Response.ok(response).build();
    }
}
