package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class StripeTransferMetadataReasonTest {
    
    @Test
    void shouldResolveEnumValueWhenExists() {
        assertThat(StripeTransferMetadataReason.fromString("TRANSFER_FEE_AMOUNT_FOR_FAILED_PAYMENT"), is(StripeTransferMetadataReason.TRANSFER_FEE_AMOUNT_FOR_FAILED_PAYMENT));
    }

    @Test
    void shouldResolveEnumValueWhenExists_ignoresCase() {
        assertThat(StripeTransferMetadataReason.fromString("transfer_fee_amount_for_failed_payment"), is(StripeTransferMetadataReason.TRANSFER_FEE_AMOUNT_FOR_FAILED_PAYMENT));
    }

    @Test
    void shouldResolveDefaultValueWhenNotExists() {
        assertThat(StripeTransferMetadataReason.fromString("BLAH"), is(StripeTransferMetadataReason.NOT_DEFINED));
    }

    @Test
    void shouldResolveDefaultValueForNullValue() {
        assertThat(StripeTransferMetadataReason.fromString(null), is(StripeTransferMetadataReason.NOT_DEFINED));
    }
}
