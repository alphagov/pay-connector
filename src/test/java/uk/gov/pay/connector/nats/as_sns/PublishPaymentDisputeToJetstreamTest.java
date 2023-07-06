package uk.gov.pay.connector.nats.as_sns;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PublishOptions;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.NatsMessage;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.events.model.dispute.DisputeCreated;
import uk.gov.pay.connector.gateway.stripe.response.StripeDisputeData;
import uk.gov.pay.connector.queue.tasks.dispute.BalanceTransaction;
import uk.gov.pay.connector.queue.tasks.dispute.EvidenceDetails;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static uk.gov.pay.connector.events.model.dispute.DisputeCreated.from;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;

public class PublishPaymentDisputeToJetstreamTest {

    public static final String STREAM = "card_payment_topic";
    public static final String SUBJECT = "disputes";
    public static void main(String[] args) throws Exception {

        Options o = new Options.Builder().server("nats://localhost:60102")
                .userInfo("local", "pGEqNddjOog51XKGd0tCyqa4rk6TRCWa").build(); // pragma: allowlist secret
//        Options o = new Options.Builder().server("nats://localhost:4223")
//                .connectionListener((conn, type) -> System.out.println(type))
//                .userInfo("test", "a very long s3cr3t! password").build();
        try (Connection nc = Nats.connect(o)) {
            JetStream js = nc.jetStream();
            
            createStreamIfNotExists(nc.jetStreamManagement(), STREAM, SUBJECT);
            
            for (int i=0; i<10; i++) {
                String disputeExternalId = RandomIdGenerator.newId();
                System.out.println("Dispute external id: " + disputeExternalId);

                LedgerTransaction transaction = aValidLedgerTransaction()
                        .withExternalId("payment-external-id")
                        .withGatewayTransactionId("payment-intent-id")
                        .withServiceId("service-id")
                        .isLive(true)
                        .build();
                BalanceTransaction balanceTransaction = new BalanceTransaction(6500L, 1500L, -8000L);
                EvidenceDetails evidenceDetails = new EvidenceDetails(1642679160L);
                StripeDisputeData stripeDisputeData = new StripeDisputeData("du_1LIaq8Dv3CZEaFO2MNQJK333",
                        "pi_123456789", "needs_response", 6500L, "fradulent", 1642579160L, List.of(balanceTransaction),
                        evidenceDetails, null, true);

                DisputeCreated disputeCreated = from(disputeExternalId, stripeDisputeData, transaction, Instant.ofEpochSecond(1642579160L));

                Message msg = NatsMessage.builder()
                        .subject(SUBJECT)
                        .data(disputeCreated.toJsonString(), StandardCharsets.UTF_8)
                        .build();

                PublishOptions publishOptions = PublishOptions.builder().stream(STREAM).build();
                js.publish(msg, publishOptions);
                
                Thread.sleep(1000);
            }
        }
    }

    private static void createStreamIfNotExists(JetStreamManagement jsm, String streamName, String subject) throws JetStreamApiException, IOException {
        if (getStreamInfoOrNullWhenNotExist(jsm, streamName) == null) {
            createStream(jsm, streamName, StorageType.Memory, subject);
        }
    }
    
    private static StreamInfo createStream(JetStreamManagement jsm, String streamName, StorageType storageType, String... subjects) throws IOException, JetStreamApiException {
        // Create a stream, here will use a file storage type, and one subject,
        // the passed subject.
        StreamConfiguration sc = StreamConfiguration.builder()
                .name(streamName)
                .storageType(storageType)
                .subjects(subjects)
                .maxAge(Duration.ofHours(3))
                .retentionPolicy(RetentionPolicy.Limits)
                .build();

        // Add or use an existing stream.
        StreamInfo si = jsm.addStream(sc);
        System.out.printf("Created stream '%s' with subject(s) %s\n",
                streamName, si.getConfiguration().getSubjects());

        return si;
    }

    private static StreamInfo getStreamInfoOrNullWhenNotExist(JetStreamManagement jsm, String streamName) throws IOException, JetStreamApiException {
        try {
            return jsm.getStreamInfo(streamName);
        }
        catch (JetStreamApiException jsae) {
            if (jsae.getErrorCode() == 404) {
                return null;
            }
            throw jsae;
        }
    }
}
