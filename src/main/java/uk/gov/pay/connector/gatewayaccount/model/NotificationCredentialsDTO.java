package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.usernotification.model.domain.NotificationCredentials;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class NotificationCredentialsDTO {

    @JsonProperty("userName")
    private final String userName;

    public NotificationCredentialsDTO(String userName) {
        this.userName = userName;
    }

    public static NotificationCredentialsDTO from(NotificationCredentials entity) {
        return new NotificationCredentialsDTO(entity.getUserName());
    }

    public String getUserName() {
        return userName;
    }
}
