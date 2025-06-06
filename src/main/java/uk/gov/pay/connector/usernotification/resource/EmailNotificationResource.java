package uk.gov.pay.connector.usernotification.resource;

import com.google.inject.persist.Transactional;
import io.dropwizard.jersey.PATCH;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.usernotification.model.EmailNotificationPatchRequest;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class EmailNotificationResource {

    public static final String EMAIL_NOTIFICATION_TEMPLATE_BODY = "template_body";
    private static final String EMAIL_NOTIFICATION_ENABLED = "enabled";

    private final GatewayAccountService gatewayAccountService;

    @Inject
    public EmailNotificationResource(GatewayAccountService gatewayAccountService) {
        this.gatewayAccountService = gatewayAccountService;
    }

    @PATCH
    @Path("/v1/api/accounts/{accountId}/email-notification")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Enables/disables email notifications for gateway account",
            description = "Allowed paths <br>" +
                    " - /payment_confirmed/enabled (values true/false) <br>" +
                    " - /refund_issued/enabled (values true/false) <br>" +
                    " - /payment_confirmed/template_body<br>" +
                    " - /refund_issued/template_body",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "    \"op\":\"replace\", \"path\":\"/payment_confirmed/enabled\", \"value\": false" +
                    "}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Content - invalid or missing mandatory fields"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response enableEmailNotification(@Parameter(example = "1", description = "Gateway account ID")
                                            @PathParam("accountId") Long gatewayAccountId,
                                            @Valid EmailNotificationPatchRequest request) {

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(gatewayAccount -> {
                    NotificationPatchInfo patchInfo = getNotificationInfoFromPath(request);
                    EmailNotificationType type = patchInfo.getEmailNotificationType();
                    EmailNotificationEntity notificationEntity = Optional.ofNullable(gatewayAccount.getEmailNotifications().get(type))
                            .orElseGet(() -> {
                                //PP-4111 we are not going to backfill and add refund notifications for existing gateway accounts, so this is unfortunately needed
                                return newDisabledEmailNotificationEntityWithNoTemplate(gatewayAccount, type);
                            });
                    patch(notificationEntity, patchInfo);
                    return Response.ok().build();
                })
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    @PATCH
    @Path("/v1/api/service/{serviceId}/account/{accountType}/email-notification")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Enables/disables email notifications for gateway account",
            description = "Allowed paths <br>" +
                    " - /payment_confirmed/enabled (values true/false) <br>" +
                    " - /refund_issued/enabled (values true/false) <br>" +
                    " - /payment_confirmed/template_body<br>" +
                    " - /refund_issued/template_body",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "    \"op\":\"replace\", \"path\":\"/payment_confirmed/enabled\", \"value\": false" +
                    "}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Content - invalid or missing mandatory fields"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response enableEmailNotificationByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @Valid EmailNotificationPatchRequest request) {

        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(gatewayAccount -> {
                    NotificationPatchInfo patchInfo = getNotificationInfoFromPath(request);
                    EmailNotificationType notificationType = patchInfo.getEmailNotificationType();
                    
                    EmailNotificationEntity notificationEntity = Optional.ofNullable(gatewayAccount.getEmailNotifications().get(notificationType))
                            .orElseGet(() -> {
                                //PP-4111 we are not going to backfill and add refund notifications for existing gateway accounts, so this is unfortunately needed
                                return newDisabledEmailNotificationEntityWithNoTemplate(gatewayAccount, notificationType);
                            });
                    patch(notificationEntity, patchInfo);
                    return Response.ok().build();
                })
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));
    }

    private NotificationPatchInfo getNotificationInfoFromPath(EmailNotificationPatchRequest emailPatchRequest) {
        List<String> pathTokens = emailPatchRequest.pathTokens();
        return new NotificationPatchInfo(EmailNotificationType.fromString(pathTokens.get(0)), pathTokens.get(1), emailPatchRequest.getValue());
    }

    private void patch(EmailNotificationEntity emailNotificationEntity, NotificationPatchInfo patchInfo) {
        switch (patchInfo.getPath()) {
            case EMAIL_NOTIFICATION_ENABLED:
                emailNotificationEntity.setEnabled(Boolean.parseBoolean(patchInfo.getValue()));
                break;
            case EMAIL_NOTIFICATION_TEMPLATE_BODY:
                emailNotificationEntity.setTemplateBody(patchInfo.getValue());
                break;
        }
    }

    private EmailNotificationEntity newDisabledEmailNotificationEntityWithNoTemplate(GatewayAccountEntity gatewayAccount, EmailNotificationType type) {
        EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(gatewayAccount, null, false);
        gatewayAccount.addNotification(type, emailNotificationEntity);
        return emailNotificationEntity;
    }

    private class NotificationPatchInfo {

        private final EmailNotificationType emailNotificationType;
        private final String path;
        private final String value;

        NotificationPatchInfo(EmailNotificationType emailNotificationType, String path, String value) {
            this.emailNotificationType = emailNotificationType;
            this.path = path;
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        EmailNotificationType getEmailNotificationType() {
            return emailNotificationType;
        }

        public String getPath() {
            return path;
        }
    }

}
