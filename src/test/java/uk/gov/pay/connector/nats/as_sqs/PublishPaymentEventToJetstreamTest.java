package uk.gov.pay.connector.nats.as_sqs;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.nio.charset.StandardCharsets;

import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.service.payments.commons.model.Source.CARD_API;

public class PublishPaymentEventToJetstreamTest {

    public static void main(String[] args) throws Exception {

        Options o = new Options.Builder().server("nats://localhost:60102")
                .userInfo("local", "pGEqNddjOog51XKGd0tCyqa4rk6TRCWa").build(); // pragma: allowlist secret
        try (Connection nc = Nats.connect(o)) {
            JetStream jsContext = nc.jetStream();

            for (int i=0; i<20; i++) {
                String chargeExternalId = RandomIdGenerator.newId();
                System.out.println("Charge external id: " + chargeExternalId);
                GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
                ChargeEntity chargeEntity = aValidChargeEntity()
                        .withExternalId(chargeExternalId)
                        .withAmount(100L)
                        .withDescription("Some description")
                        .withReference(ServicePaymentReference.of("Some reference"))
                        .withGatewayAccountEntity(gatewayAccountEntity)
                        .withPaymentProvider(SANDBOX.getName())
                        .withEmail("jane.doe@example.com")
                        .withSource(CARD_API)
                        .withStatus(CREATED)
                        .withGatewayTransactionId("1PROV")
                        .withCardDetails(new CardDetailsEntity())
                        .withServiceId("a-valid-external-service-id")
                        .build();

                jsContext.publish("payment.event", PaymentCreated.from(chargeEntity).toJsonString().getBytes(StandardCharsets.UTF_8));
                
                Thread.sleep(1000);
            }
        }
    }
}
