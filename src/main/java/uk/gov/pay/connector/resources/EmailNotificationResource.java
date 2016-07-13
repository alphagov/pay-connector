package uk.gov.pay.connector.resources;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.EmailNotificationsDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.resources.ApiPaths.GATEWAY_ACCOUNTS_API_EMAIL_NOTIFICATION;
import static uk.gov.pay.connector.util.ResponseUtil.fieldsMissingResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class EmailNotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationResource.class);

    public static final String EMAIL_NOTIFICATION_FIELD_NAME = "custom-email-text";

    private final EmailNotificationsDao emailNotificationsDao;

    @Inject
    public EmailNotificationResource(EmailNotificationsDao emailNotificationsDao) {
        this.emailNotificationsDao = emailNotificationsDao;
    }

    @GET
    @Path(GATEWAY_ACCOUNTS_API_EMAIL_NOTIFICATION)
    @Produces(APPLICATION_JSON)
    public Response getEmailNotificationText(@PathParam("accountId") Long gatewayAccountId) {
        logger.info("Getting email notification text for account id {}", gatewayAccountId);

        return emailNotificationsDao.findByAccountId(gatewayAccountId)
                .map(emailNotificationEntity -> Response.ok().entity(emailNotificationEntity).build())
                .orElseGet(() -> notFoundResponse(format("Account with id %s not found.", gatewayAccountId)));
    }

    @POST
    @Path(GATEWAY_ACCOUNTS_API_EMAIL_NOTIFICATION)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response updateGatewayAccountEmailNotification(@PathParam("accountId") Long gatewayAccountId, Map<String, String> payload) {
        if (!payload.containsKey(EMAIL_NOTIFICATION_FIELD_NAME)) {
            return fieldsMissingResponse(Collections.singletonList(EMAIL_NOTIFICATION_FIELD_NAME));
        }

        String emailNotificationTemplate = payload.get(EMAIL_NOTIFICATION_FIELD_NAME);

        return emailNotificationsDao.findByAccountId(gatewayAccountId)
            .map(emailNotificationEntity -> {
                emailNotificationEntity.setTemplateBody(emailNotificationTemplate);
                return Response.ok().build();
            })
            .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }
}
