package uk.gov.pay.connector.resources;

import io.dropwizard.auth.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.service.StatusInquiryService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;


@Path("/")
public class NotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(NotificationResource.class);
    private StatusInquiryService service;

    public NotificationResource(StatusInquiryService service) {
        this.service = service;
    }


    @POST
    @Consumes(APPLICATION_JSON)
    @Path("v1/api/notifications/smartpay")
    public Response handleSmartpayNotification(@Auth String username, String notification) throws IOException {

        logger.info("Received notification from smartpay: " + notification);

        service.handleSmartpayNotification(notification);

        return Response.ok("[accepted]").build();
    }

    @POST
    @Consumes(TEXT_XML)
    @Path("v1/api/notifications/worldpay")
    public Response handleWorldpayNotification(String notification) {

        logger.info("Received notification from worldpay: " + notification);

        boolean handledNotification = service.handleWorldpayNotification(notification);

        return handledNotification ? Response.ok("[OK]").build() : errorResponse();
    }

    private Response errorResponse() {
        return Response.serverError().build();
    }
}