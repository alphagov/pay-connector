package uk.gov.pay.connector.resources;

import com.google.inject.persist.Transactional;
import io.dropwizard.jersey.PATCH;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.EmailNotificationsDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.builder.PatchRequestBuilder;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.model.domain.EmailNotificationType;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.builder.PatchRequestBuilder.aPatchRequestBuilder;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.fieldsMissingResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class EmailNotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationResource.class);

    // PP-4111 remove this after selfservice is merged
    public static final String EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD = "custom-email-text";
    public static final String EMAIL_NOTIFICATION_TEMPLATE_BODY = "template_body";
    public static final String EMAIL_NOTIFICATION_ENABLED = "enabled";

    private final EmailNotificationsDao emailNotificationsDao;
    private final GatewayAccountDao gatewayDao;

    @Inject
    public EmailNotificationResource(GatewayAccountDao gatewayDao, EmailNotificationsDao emailNotificationsDao) {
        this.emailNotificationsDao = emailNotificationsDao;
        this.gatewayDao = gatewayDao;
    }

    //PP-4111 backward compatibility, remove after selfservice is merged
    @GET
    @Path("/v1/api/accounts/{accountId}/email-notification")
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response getEmailNotificationText(@PathParam("accountId") Long gatewayAccountId) {
        logger.info("Getting email notification text for account id {}", gatewayAccountId);

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount ->
                        emailNotificationsDao.findByAccountId(gatewayAccount.getId())
                                .map(emailNotificationEntity -> Response.ok().entity(emailNotificationEntity).build())
                                .orElseGet(() -> Response.ok().build()))
                .orElseGet(() -> notFoundResponse(format("Account with id %s not found.", gatewayAccountId)));
    }


    // PP-4111 This is just used to update the template body. Will be removed after selfservice stops using it
    @POST
    @Path("/v1/api/accounts/{accountId}/email-notification")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response updateEmailNotification(@PathParam("accountId") Long gatewayAccountId, Map<String, String> payload) {
        if (!payload.containsKey(EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD)) {
            return fieldsMissingResponse(Collections.singletonList(EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD));
        }

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount ->
                        emailNotificationsDao.findByAccountId(gatewayAccountId).map(emailNotificationEntity -> {
                            emailNotificationEntity.setTemplateBody(payload.get(EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD));
                            return Response.ok().build();
                        }).orElseGet(() -> {
                            gatewayAccount.addNotification(EmailNotificationType.PAYMENT_CONFIRMED,
                                    new EmailNotificationEntity(gatewayAccount, payload.get(EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD)));
                            return Response.ok().build();
                        }))
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }


    //PP-4111 backward compatibility, remove once selfservice is merged
    @PATCH
    @Path("/v1/api/accounts/{accountId}/email-notification")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response oldEnableEmailNotification(@PathParam("accountId") Long gatewayAccountId, Map<String, String> emailPatchMap) {
        PatchRequestBuilder.PatchRequest emailPatchRequest;
        try {
            emailPatchRequest = aPatchRequestBuilder(emailPatchMap)
                    .withValidOps(Collections.singletonList("replace"))
                    .withValidPaths(
                            buildValidPaths(
                                    Arrays.asList(EmailNotificationType.PAYMENT_CONFIRMED.toString(), EmailNotificationType.REFUND_ISSUED.toString()),
                                    Arrays.asList(EMAIL_NOTIFICATION_ENABLED, EMAIL_NOTIFICATION_TEMPLATE_BODY)))
                    .build();
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse("Bad patch parameters" + emailPatchMap.toString());
        }

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount -> {
                    Pair<EmailNotificationType, String> patchInfo = getNotificationTypeFromPath(emailPatchRequest);
                    EmailNotificationType type = patchInfo.getLeft();
                    EmailNotificationEntity notificationEntity = Optional.ofNullable(gatewayAccount.getEmailNotifications().get(type))
                            .orElseGet(() -> {
                                //PP-4111 we are not going to backfill and add refund notifications for existing gateway accounts, so this is unfortunately needed
                                EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(gatewayAccount, null, false);
                                gatewayAccount.addNotification(type, emailNotificationEntity);
                                return  emailNotificationEntity;
                            });
                    patch(notificationEntity, patchInfo.getRight(), emailPatchRequest);
                    return Response.ok().build();
                })
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    private Pair<EmailNotificationType, String> getNotificationTypeFromPath(PatchRequestBuilder.PatchRequest emailPatchRequest) {
        String[] paths = emailPatchRequest.getPath().split("/");
        // PP-4111 remove after selfservice is merged
        if (paths.length < 2) {
            return Pair.of(EmailNotificationType.PAYMENT_CONFIRMED, "enabled");
        }
        return Pair.of(EmailNotificationType.fromString(paths[1]), paths[2]);

    }
    private void patch(EmailNotificationEntity emailNotificationEntity, String attribute, PatchRequestBuilder.PatchRequest emailPatchRequest) {
        switch (attribute) {
            case EMAIL_NOTIFICATION_ENABLED:
                emailNotificationEntity.setEnabled(Boolean.parseBoolean(emailPatchRequest.getValue()));
                break;
            case EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD:
            case EMAIL_NOTIFICATION_TEMPLATE_BODY:
                emailNotificationEntity.setTemplateBody(emailPatchRequest.getValue());
                break;
        }
    }

    private List<String> buildValidPaths(List<String> resources, List<String> attributes) {
        List<String> result = new ArrayList<>();
        resources.forEach(res -> attributes.forEach(att -> result.add("/" + res.toLowerCase() + "/" + att)));
        // PP-4111 remove this after selfservice is merged, backward compatible
        result.add(EMAIL_NOTIFICATION_ENABLED);
        return result;
    }


}
