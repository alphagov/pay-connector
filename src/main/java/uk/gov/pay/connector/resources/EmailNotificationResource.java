package uk.gov.pay.connector.resources;

import com.google.inject.persist.Transactional;
import io.dropwizard.jersey.PATCH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.EmailNotificationsDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.PatchRequestBuilder;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.PatchRequestBuilder.aPatchRequestBuilder;
import static uk.gov.pay.connector.resources.ApiPaths.GATEWAY_ACCOUNTS_API_EMAIL_NOTIFICATION;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class EmailNotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationResource.class);

    public static final String EMAIL_NOTIFICATION_TEMPLATE_BODY = "custom-email-text";
    public static final String EMAIL_NOTIFICATION_ENABLED = "enabled";

    private final EmailNotificationsDao emailNotificationsDao;
    private final GatewayAccountDao gatewayDao;

    @Inject
    public EmailNotificationResource(GatewayAccountDao gatewayDao, EmailNotificationsDao emailNotificationsDao) {
        this.emailNotificationsDao = emailNotificationsDao;
        this.gatewayDao = gatewayDao;
    }

    @GET
    @Path(GATEWAY_ACCOUNTS_API_EMAIL_NOTIFICATION)
    @Produces(APPLICATION_JSON)
    public Response getEmailNotificationText(@PathParam("accountId") Long gatewayAccountId) {
        logger.info("Getting email notification text for account id {}", gatewayAccountId);

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount ->
                        emailNotificationsDao.findByAccountId(gatewayAccount.getId())
                                .map(emailNotificationEntity -> Response.ok().entity(emailNotificationEntity).build())
                                .orElseGet(() -> Response.ok().build()))
                .orElseGet(() -> notFoundResponse(format("Account with id %s not found.", gatewayAccountId)));
    }

    @POST
    @Path(GATEWAY_ACCOUNTS_API_EMAIL_NOTIFICATION)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response updateEmailNotification(@PathParam("accountId") Long gatewayAccountId, Map<String, String> payload) {
        if (!payload.containsKey(EMAIL_NOTIFICATION_TEMPLATE_BODY)) {
            return fieldsMissingResponse(Collections.singletonList(EMAIL_NOTIFICATION_TEMPLATE_BODY));
        }

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount ->
                        emailNotificationsDao.findByAccountId(gatewayAccountId).map(emailNotificationEntity -> {
                            emailNotificationEntity.setTemplateBody(payload.get(EMAIL_NOTIFICATION_TEMPLATE_BODY));
                            return Response.ok().build();
                        }).orElseGet(() -> {
                            gatewayAccount.setEmailNotification(
                                    new EmailNotificationEntity(gatewayAccount, payload.get(EMAIL_NOTIFICATION_TEMPLATE_BODY)));
                            return Response.ok().build();
                        }))
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    @PATCH
    @Path(GATEWAY_ACCOUNTS_API_EMAIL_NOTIFICATION)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response enableEmailNotification(@PathParam("accountId") Long gatewayAccountId, Map<String, String> emailPatchMap) {
        PatchRequestBuilder.PatchRequest emailPatchRequest;

        try {
            emailPatchRequest = aPatchRequestBuilder(emailPatchMap)
                    .withValidOps(Collections.singletonList("replace"))
                    .withValidPaths(Collections.singletonList(EMAIL_NOTIFICATION_ENABLED))
                    .build();
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse("Bad patch parameters" + emailPatchMap.toString());
        }

        return emailNotificationsDao.findByAccountId(gatewayAccountId)
                .map(emailNotificationEntity -> {
                    emailNotificationEntity.setEnabled(Boolean.parseBoolean(emailPatchRequest.getValue()));
                    return Response.ok().build();
                })
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }
}
