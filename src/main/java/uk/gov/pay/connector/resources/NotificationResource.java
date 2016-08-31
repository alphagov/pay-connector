package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.service.NotificationService;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.resources.PaymentGatewayName.SMARTPAY;

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
    @Path("v1/api/notifications/smartpay")
    public Response authoriseSmartpayNotifications(String notification) throws IOException {
        return handleNotification("smartpay", notification);
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path("v1/api/notifications/sandbox")
    public Response authoriseSandboxNotifications(String notification) throws IOException {
        return handleNotification("sandbox", notification);
    }

    @POST
    @Consumes(TEXT_XML)
    @Path("v1/api/notifications/worldpay")
    public Response authoriseWorldpayNotifications(String notification) throws IOException {
        return handleNotification("worldpay", notification);
    }

    private Response handleNotification(String name, String notification) {
        logger.info("Received notification from provider={}, notification={}", name, notification);
        PaymentGatewayName paymentGatewayName = PaymentGatewayName.valueFrom(name);
        notificationService.acceptNotificationFor(paymentGatewayName, notification);
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
