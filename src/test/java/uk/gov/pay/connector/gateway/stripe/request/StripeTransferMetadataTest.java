package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(MockitoJUnitRunner.class)
public class StripeTransferMetadataTest {

    @Test
    public void shouldDeserialiseFromMetadata() {
        Map<String, String> stripeObjectMetadata = Map.of(
                "stripe_charge_id", "some-charge-id",
                "govuk_pay_transaction_external_id", "some-reconciliation-transaction-id",
                "reason", "TRANSFER_REFUND_AMOUNT"
        );
        var stripeTransferMetadata = StripeTransferMetadata.from(stripeObjectMetadata);
        assertThat(stripeTransferMetadata.getStripeChargeId(), is("some-charge-id"));
        assertThat(stripeTransferMetadata.getGovukPayTransactionExternalId(), is("some-reconciliation-transaction-id"));
        assertThat(stripeTransferMetadata.getReason(), is(StripeTransferMetadataReason.TRANSFER_REFUND_AMOUNT));
    }

    @Test
    public void shouldDeserialiseFromMetadata_whenMetadataDoesNotIncludeReason() {
        Map<String, String> stripeObjectMetadata = Map.of(
                "stripe_charge_id", "some-charge-id",
                "govuk_pay_transaction_external_id", "some-reconciliation-transaction-id"
        );
        var stripeTransferMetadata = StripeTransferMetadata.from(stripeObjectMetadata);
        assertThat(stripeTransferMetadata.getStripeChargeId(), is("some-charge-id"));
        assertThat(stripeTransferMetadata.getGovukPayTransactionExternalId(), is("some-reconciliation-transaction-id"));
        assertThat(stripeTransferMetadata.getReason(), is(StripeTransferMetadataReason.NOT_DEFINED));
    }

    @Test
    public void shouldSerialiseToMetadata() {
        var stripeTransferMetadata = new StripeTransferMetadata("some-charge-id",
                "some-reconciliation-transaction-id",
                StripeTransferMetadataReason.TRANSFER_REFUND_AMOUNT);
        var requestMap = stripeTransferMetadata.getParams();
        assertThat(requestMap.get("metadata[stripe_charge_id]"), is("some-charge-id"));
        assertThat(requestMap.get("metadata[govuk_pay_transaction_external_id]"), is("some-reconciliation-transaction-id"));
        assertThat(requestMap.get("metadata[reason]"), is("TRANSFER_REFUND_AMOUNT"));
    }
}
