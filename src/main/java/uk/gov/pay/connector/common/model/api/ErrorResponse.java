package uk.gov.pay.connector.common.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class ErrorResponse {
    
    @JsonProperty("error_identifier")
    private final ErrorIdentifier identifier;
    
    @JsonProperty("message")
    private final List<String> messages;
    
    @JsonProperty("reason")
    private final String reason;
    
    public ErrorResponse(ErrorIdentifier identifier, List<String> messages, String reason) {
        this.identifier = identifier;
        this.messages = messages;
        this.reason = reason;
    }
    
    public ErrorResponse(ErrorIdentifier identifier, List<String> messages) {
        this(identifier, messages, null);
    }

    public ErrorResponse(ErrorIdentifier identifier, String message) {
        this(identifier, List.of(message), null);
    }
    
    public ErrorResponse(ErrorIdentifier identifier, String message, String reason) {
        this(identifier, List.of(message), reason);
    }

    public ErrorIdentifier getIdentifier() {
        return identifier;
    }

    public List<String> getMessages() {
        return messages;
    }

    public String getReason() {
        return reason;
    }
}
