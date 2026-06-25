package uk.gov.pay.connector.gateway.util;

import net.logstash.logback.argument.StructuredArgument;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public record AuthorisationRequestLog(
        String authorisationRequest,
        List<StructuredArgument> structuredArguments
) {
}
