package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.service.EpdqNotificationService;
import uk.gov.pay.connector.service.SmartpayNotificationService;
import uk.gov.pay.connector.service.worldpay.WorldpayNotificationService;
import uk.gov.pay.connector.service.PaymentGatewayName;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.resources.ApiPaths.NOTIFICATIONS_EPDQ_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.NOTIFICATIONS_SANDBOX_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.NOTIFICATIONS_SMARTPAY_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.NOTIFICATIONS_WORLDPAY_API_PATH;
import static uk.gov.pay.connector.service.PaymentGatewayName.SMARTPAY;
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
        this.epdqNotificationService = epdqNotificationService;
        this.smartpayNotificationService = smartpayNotificationService;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @PermitAll
    @Path(NOTIFICATIONS_SMARTPAY_API_PATH)
    public Response authoriseSmartpayNotifications(String notification) throws IOException {
        smartpayNotificationService.handleNotificationFor(notification);
        String response = "[accepted]";
        logger.info("Responding to notification from provider={} with 200 {}", "smartpay", response);
        return Response.ok(response).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path(NOTIFICATIONS_SANDBOX_API_PATH)
    public Response authoriseSandboxNotifications(String notification) throws IOException {
        return Response.ok().build();
    }

    @POST
    @Consumes(TEXT_XML)
    @Path(NOTIFICATIONS_WORLDPAY_API_PATH)
    @Produces({TEXT_XML, APPLICATION_JSON})
    public Response authoriseWorldpayNotifications(String notification, @HeaderParam("X-Forwarded-For") String ipAddress) throws IOException {
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
    @Path(NOTIFICATIONS_EPDQ_API_PATH)
    @Produces({TEXT_XML, APPLICATION_JSON})
    public Response authoriseEpdqNotifications(String notification) throws IOException {
        epdqNotificationService.handleNotificationFor(notification);
        String response = "[OK]";
        logger.info("Responding to notification from provider={} with 200 {}", "epdq", response);
        return Response.ok(response).build();
    }

}
