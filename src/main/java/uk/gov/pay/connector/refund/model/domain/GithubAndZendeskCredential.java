package uk.gov.pay.connector.refund.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubAndZendeskCredential(@JsonProperty("zendesk_ticket_id") String zendeskTicketId,
                                         @JsonProperty("github_user_id") String githubUserId) {

}
