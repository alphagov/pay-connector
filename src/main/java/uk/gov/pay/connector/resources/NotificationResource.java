package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.StatusResponse;
import uk.gov.pay.connector.service.PaymentProviders;
import uk.gov.pay.connector.service.StatusInquiryService;
import uk.gov.pay.connector.service.worldpay.WorldpayNotification;
import uk.gov.pay.connector.util.XMLUnmarshaller;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;

import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.WORLDPAY_PROVIDER;


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

        try {
            Boolean ok = service.handleWorldpayNotification(notification);
            return ok ? Response.ok("[OK]").build() : errorResponse();
        } catch (JAXBException e) {
            return errorResponse();
        }
    }

    private Response errorResponse() {
        return Response.serverError().build();
    }
}