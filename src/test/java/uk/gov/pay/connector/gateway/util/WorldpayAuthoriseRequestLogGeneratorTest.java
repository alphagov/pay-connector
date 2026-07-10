package uk.gov.pay.connector.gateway.util;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthoriseRequest;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.BILLING_ADDRESS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.CORPORATE_CARD;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.DATA_FOR_3DS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.EMAIL;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.IP_ADDRESS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.MOTO;
import static uk.gov.pay.connector.gateway.util.WorldpayAuthoriseRequestLogGenerator.GATEWAY_REQUEST_RECORD;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

class WorldpayAuthoriseRequestLogGeneratorTest {

    private static final String IP = "203.0.113.1";

    private final WorldpayAuthoriseRequestLogGenerator  generator = new WorldpayAuthoriseRequestLogGenerator();

    @Test
    public void generatesWorldpayMotoAuthoriseRequestLogWithCorporateCard() {
        WorldpayMotoAuthoriseRequest request = new WorldpayMotoAuthoriseRequest(
                "username", "password", "merchant code", "order code",
                "description", "1000", "4242424242424242",
                "12", "2030", "Cardholder Name", "123");

        AuthCardDetails authCardDetails = anAuthCardDetails().withCorporateCard(true).withIpAddress(IP).build();

        AuthorisationRequestLog result = generator.generate(request, authCardDetails);

        assertThat(result.authorisationRequest(), is(
                " without billing address and with corporate card and with remote IP " + IP));

        assertThat(result.structuredArguments(), containsInAnyOrder(
                        kv(GATEWAY_REQUEST_RECORD, true),
                        kv(BILLING_ADDRESS, false),
                        kv(CORPORATE_CARD, true),
                        kv(EMAIL, false),
                        kv(DATA_FOR_3DS, false),
                        kv(MOTO, true),
                        kv(IP_ADDRESS, IP)));
    }

    @Test
    public void generatesWorldpayMotoAuthoriseRequestLogWithoutCorporateCard() {
        WorldpayMotoAuthoriseRequest request = new WorldpayMotoAuthoriseRequest(
                "username", "password", "merchant code", "order code",
                "description", "1000", "4242424242424242",
                "12", "2030", "Cardholder Name", "123");

        AuthCardDetails authCardDetails = anAuthCardDetails().withCorporateCard(false).withIpAddress(IP).build();

        AuthorisationRequestLog result = generator.generate(request, authCardDetails);

        assertThat(result.authorisationRequest(), is(" without billing address and with remote IP " + IP));

        assertThat(result.structuredArguments(), containsInAnyOrder(
                kv(GATEWAY_REQUEST_RECORD, true),
                kv(BILLING_ADDRESS, false),
                kv(CORPORATE_CARD, false),
                kv(EMAIL, false),
                kv(DATA_FOR_3DS, false),
                kv(MOTO, true),
                kv(IP_ADDRESS, IP)));
    }

    @Test
    public void generatesWorldpayMotoAuthoriseRequestLogWithoutIPAddress() {
        WorldpayMotoAuthoriseRequest request = new WorldpayMotoAuthoriseRequest(
                "username", "password", "merchant code", "order code",
                "description", "1000", "4242424242424242",
                "12", "2030", "Cardholder Name", "123");

        AuthCardDetails authCardDetails = anAuthCardDetails().withCorporateCard(false).build();

        AuthorisationRequestLog result = generator.generate(request, authCardDetails);

        assertThat(result.authorisationRequest(), is(" without billing address"));

        assertThat(result.structuredArguments(), containsInAnyOrder(
                kv(GATEWAY_REQUEST_RECORD, true),
                kv(BILLING_ADDRESS, false),
                kv(CORPORATE_CARD, false),
                kv(EMAIL, false),
                kv(DATA_FOR_3DS, false),
                kv(MOTO, true)));
    }

}
