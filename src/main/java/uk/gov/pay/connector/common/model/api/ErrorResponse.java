package uk.gov.pay.connector.common.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record ErrorResponse (
    @JsonProperty("error_identifier")
    @Schema(example = "GENERIC")
    ErrorIdentifier identifier,
    
    @JsonProperty("message")
    @ArraySchema(schema = @Schema(example = "error message"))
    List<String> messages,
    
    @JsonProperty("reason")
    @Schema(required = false, example = "Optional - ex: amount_not_available")
    String reason
) {
    public ErrorResponse(ErrorIdentifier identifier, List<String> messages) {
        this(identifier, messages, null);
    }

    public ErrorResponse(ErrorIdentifier identifier, String message) {
        this(identifier, List.of(message), null);
    }
    
    public ErrorResponse(ErrorIdentifier identifier, String message, String reason) {
        this(identifier, List.of(message), reason);
    }
}
