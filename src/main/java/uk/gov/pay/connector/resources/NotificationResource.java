package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.service.StatusInquiryService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.TEXT_XML;


@Path("/v1/api/notifications/worldpay")
public class NotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(NotificationResource.class);
    private StatusInquiryService service;

    public NotificationResource(StatusInquiryService service) {
        this.service = service;
    }

    @POST
    @Consumes(TEXT_XML)
    public Response handleNotification(String notification) {

        logger.info("Received notification from worldpay: " + notification);

        boolean handledNotification = service.handleWorldpayNotification(notification);


            return handledNotification ? Response.ok("[OK]").build() : errorResponse();
    }

    private Response errorResponse() {
        return Response.serverError().build();
    }
}