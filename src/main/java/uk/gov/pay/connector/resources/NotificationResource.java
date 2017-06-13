package uk.gov.pay.connector.resources;

import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.service.NotificationService;
import uk.gov.pay.connector.service.PaymentGatewayName;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.service.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.util.ResponseUtil.forbiddenErrorResponse;

@Path("/")
public class NotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(NotificationResource.class);

    private final NotificationService notificationService;

    @Inject
    public NotificationResource(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @PermitAll
    @Path(NOTIFICATIONS_SMARTPAY_API_PATH)
    public Response authoriseSmartpayNotifications(String notification) throws IOException {
        return handleNotification("not-required", "smartpay", notification);
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
        return handleNotification(ipAddress, "worldpay", notification);
    }

    @POST
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Path(NOTIFICATIONS_EPDQ_API_PATH)
    @Produces({TEXT_XML, APPLICATION_JSON})
    public Response authoriseEpdqNotifications(String notification, @HeaderParam("X-Forwarded-For") String ipAddress) throws IOException {
        return handleNotification(ipAddress, "epdq", notification);
    }

    private Response handleNotification(String ipAddress, String name, String notification) {
        PaymentGatewayName paymentGatewayName = PaymentGatewayName.valueFrom(name);
        if (!notificationService.handleNotificationFor(ipAddress, paymentGatewayName, notification)) {
            logger.error("Rejected notification for ip '{}'", ipAddress);
            return forbiddenErrorResponse();
        }
        String response = getResponseFor(paymentGatewayName);
        logger.info("Responding to notification from provider={} with 200 {}", name, response);
        return Response.ok(response).build();
    }

    private String getResponseFor(PaymentGatewayName provider) {
        if (provider == SMARTPAY) {
            return "[accepted]";
        }
        return "[OK]";
    }
}
