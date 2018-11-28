package uk.gov.pay.connector.webhook.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.epdq.EpdqNotificationService;
import uk.gov.pay.connector.gateway.smartpay.SmartpayNotificationService;
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
import static uk.gov.pay.connector.util.ResponseUtil.forbiddenErrorResponse;

@Path("/")
public class NotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(NotificationResource.class);

    private final WorldpayNotificationService worldpayNotificationService;
    private final EpdqNotificationService epdqNotificationService;
    private final SmartpayNotificationService smartpayNotificationService;

    @Inject
    public NotificationResource(WorldpayNotificationService worldpayNotificationService,
                                EpdqNotificationService epdqNotificationService,
                                SmartpayNotificationService smartpayNotificationService) {
        this.worldpayNotificationService = worldpayNotificationService;
        this.smartpayNotificationService = smartpayNotificationService;
        this.epdqNotificationService = epdqNotificationService;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @PermitAll
    @Path("/v1/api/notifications/smartpay")
    public Response authoriseSmartpayNotifications(String notification) {
        smartpayNotificationService.handleNotificationFor(notification);
        String response = "[accepted]";
        logger.info("Responding to notification from provider=smartpay with 200 {}", response);
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
            logger.error("Rejected notification for ip '{}'", ipAddress);
            return forbiddenErrorResponse();
        }
        String response = "[OK]";
        logger.info("Responding to notification from provider={} with 200 {}", "worldpay", response);
        return Response.ok(response).build();
    }

    @POST
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Path("/v1/api/notifications/epdq")
    @Produces({TEXT_XML, APPLICATION_JSON})
    public Response authoriseEpdqNotifications(String notification, @HeaderParam("X-Forwarded-For") String ipAddress) {
        epdqNotificationService.handleNotificationFor(notification);
        String response = "[OK]";
        logger.info("Responding to notification from provider={} with 200 {}", "epdq", response);
        return Response.ok(response).build();
    }

}
