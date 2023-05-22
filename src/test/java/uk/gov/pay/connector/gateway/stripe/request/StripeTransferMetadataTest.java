package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadataReason.NOT_DEFINED;
import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadataReason.TRANSFER_PAYMENT_AMOUNT;
import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadataReason.TRANSFER_REFUND_AMOUNT;

@ExtendWith(MockitoExtension.class)
class StripeTransferMetadataTest {

    @Test
    void shouldDeserialiseFromMetadata() {
        Map<String, String> stripeObjectMetadata = Map.of(
                "stripe_charge_id", "some-charge-id",
                "govuk_pay_transaction_external_id", "some-reconciliation-transaction-id",
                "reason", "transfer_payment_amount"
        );
        var stripeTransferMetadata = StripeTransferMetadata.from(stripeObjectMetadata);
        assertThat(stripeTransferMetadata.getStripeChargeId(), is("some-charge-id"));
        assertThat(stripeTransferMetadata.getGovukPayTransactionExternalId(), is("some-reconciliation-transaction-id"));
        assertThat(stripeTransferMetadata.getReason(), is(TRANSFER_PAYMENT_AMOUNT));
    }

    @Test
    void shouldDeserialiseFromMetadata_whenMetadataDoesNotIncludeReason() {
        Map<String, String> stripeObjectMetadata = Map.of(
                "stripe_charge_id", "some-charge-id",
                "govuk_pay_transaction_external_id", "some-reconciliation-transaction-id"
        );
        var stripeTransferMetadata = StripeTransferMetadata.from(stripeObjectMetadata);
        assertThat(stripeTransferMetadata.getStripeChargeId(), is("some-charge-id"));
        assertThat(stripeTransferMetadata.getGovukPayTransactionExternalId(), is("some-reconciliation-transaction-id"));
        assertThat(stripeTransferMetadata.getReason(), is(NOT_DEFINED));
    }

    @Test
    void shouldSerialiseToMetadata() {
        var stripeTransferMetadata = new StripeTransferMetadata("some-charge-id",
                "some-reconciliation-transaction-id",
                TRANSFER_REFUND_AMOUNT);
        var requestMap = stripeTransferMetadata.getParams();
        assertThat(requestMap.get("metadata[stripe_charge_id]"), is("some-charge-id"));
        assertThat(requestMap.get("metadata[govuk_pay_transaction_external_id]"), is("some-reconciliation-transaction-id"));
        assertThat(requestMap.get("metadata[reason]"), is("transfer_refund_amount"));
    }
}
