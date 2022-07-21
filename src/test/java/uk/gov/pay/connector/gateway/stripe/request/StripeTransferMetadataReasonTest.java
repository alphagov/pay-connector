package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;


class StripeTransferMetadataReasonTest {
    
    @Test
    void shouldResolveEnumValueWhenExists() {
        assertThat(StripeTransferMetadataReason.fromString("transfer_fee_amount_for_failed_payment"), is(StripeTransferMetadataReason.TRANSFER_FEE_AMOUNT_FOR_FAILED_PAYMENT));
    }

    @Test
    void shouldResolveEnumValueWhenExists_ignoresCase() {
        assertThat(StripeTransferMetadataReason.fromString("TRANSFER_PAYMENT_AMOUNT"), is(StripeTransferMetadataReason.TRANSFER_PAYMENT_AMOUNT));
    }

    @Test
    void shouldResolveUnrecognisedValueWhenNotRecognisedEnumValue() {
        assertThat(StripeTransferMetadataReason.fromString("BLAH"), is(StripeTransferMetadataReason.UNRECOGNISED));
    }

    @Test
    void shouldResolveDefaultValueForNullValue() {
        assertThat(StripeTransferMetadataReason.fromString(null), is(StripeTransferMetadataReason.NOT_DEFINED));
    }
    
    @Test
    void shouldBeLowerCaseWhenConvertedToString() {
        assertThat(StripeTransferMetadataReason.TRANSFER_REFUND_AMOUNT.toString(), is(not(StripeTransferMetadataReason.TRANSFER_REFUND_AMOUNT.name())));
        assertThat(StripeTransferMetadataReason.TRANSFER_REFUND_AMOUNT.toString(), is(StripeTransferMetadataReason.TRANSFER_REFUND_AMOUNT.name().toLowerCase(Locale.ENGLISH)));
    }
}
