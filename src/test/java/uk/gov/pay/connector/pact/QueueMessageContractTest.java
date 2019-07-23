package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit.IgnoreNoPactsToVerify;
import au.com.dius.pact.provider.junit.PactRunner;
import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.loader.PactBroker;
import au.com.dius.pact.provider.junit.loader.PactBrokerAuth;
import au.com.dius.pact.provider.junit.target.AmqpTarget;
import au.com.dius.pact.provider.junit.target.Target;
import au.com.dius.pact.provider.junit.target.TestTarget;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureConfirmedEventDetails;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmed;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;

@RunWith(PactRunner.class)
@Provider("connector")
@PactBroker(scheme = "https", host = "pact-broker-test.cloudapps.digital", tags = {"${PACT_CONSUMER_TAG}", "test", "staging", "production"},
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"),
        consumers = {"ledger"})
@IgnoreNoPactsToVerify
public class QueueMessageContractTest {

    @TestTarget
    public final Target target = new AmqpTarget();

    private String resourceId = "anExternalResourceId";

    @PactVerifyProvider("a payment created message")
    public String verifyPaymentCreatedEvent() throws Exception{
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCorporateSurcharge(55L)
                .build();

        PaymentCreated paymentCreatedEvent = new PaymentCreated(
                resourceId,
                PaymentCreatedEventDetails.from(charge),
                ZonedDateTime.now()
        );

        return paymentCreatedEvent.toJsonString();
    }

    @PactVerifyProvider("a capture confirmed message")
    public String verifyCaptureConfirmedEvent() throws JsonProcessingException {
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withGatewayEventDate(ZonedDateTime.now())
                .build();

        CaptureConfirmed captureConfirmedEvent = new CaptureConfirmed(
                resourceId,
                CaptureConfirmedEventDetails.from(chargeEventEntity),
                ZonedDateTime.now()
        );

        return captureConfirmedEvent.toJsonString();
    }
}
