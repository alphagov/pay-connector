package uk.gov.pay.connector.charge.util;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class RefundCalculatorTest {
    @Test
    public void getTotalAmountToBeRefunded_shouldReturnFullAmount_whenNoCorporateSurchargeAndNoRefunds() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();

        long actualAmount = RefundCalculator.getTotalAmountToBeRefunded(chargeEntity);
        long expectedAmount = chargeEntity.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void getTotalAmountToBeRefunded_shouldReturnRemainingAmount_whenNoCorporateSurchargeAndHasRefunds() {
        RefundEntity refund1 = RefundEntityFixture.aValidRefundEntity().withAmount(10L).withStatus(RefundStatus.REFUNDED).build();
        RefundEntity refund2 = RefundEntityFixture.aValidRefundEntity().withAmount(20L).withStatus(RefundStatus.CREATED).build();
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withRefunds(Arrays.asList(refund1, refund2)).build();

        long actualAmount = RefundCalculator.getTotalAmountToBeRefunded(chargeEntity);
        long expectedAmount = chargeEntity.getAmount() - refund1.getAmount() - refund2.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void getTotalAmountToBeRefunded_shouldReturnFullAmountIncludingSurcharge_whenChargeHasCorporateSurchargeAndNoRefunds() {
        long corporateSurcharge = 250L;
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(corporateSurcharge).build();

        long actualAmount = RefundCalculator.getTotalAmountToBeRefunded(chargeEntity);
        long expectedAmount = chargeEntity.getAmount() + corporateSurcharge;

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void getTotalAmountToBeRefunded_shouldReturnRemainingAmountIncludingSurcharge_whenChargeHasCorporateSurchargeAndHasRefunds() {
        long corporateSurcharge = 250L;
        RefundEntity refund1 = RefundEntityFixture.aValidRefundEntity().withAmount(10L).withStatus(RefundStatus.REFUNDED).build();
        RefundEntity refund2 = RefundEntityFixture.aValidRefundEntity().withAmount(20L).withStatus(RefundStatus.CREATED).build();
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCorporateSurcharge(corporateSurcharge)
                .withRefunds(Arrays.asList(refund1, refund2))
                .build();

        long actualAmount = RefundCalculator.getTotalAmountToBeRefunded(chargeEntity);
        long expectedAmount = chargeEntity.getAmount() + corporateSurcharge - refund1.getAmount() - refund2.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void getRefundedAmount_shouldIncludeCreatedSubmittedAndRefundedRefunds() {
        RefundEntity refund1 = RefundEntityFixture.aValidRefundEntity().withAmount(10L).withStatus(RefundStatus.CREATED).build();
        RefundEntity refund2 = RefundEntityFixture.aValidRefundEntity().withAmount(20L).withStatus(RefundStatus.REFUND_SUBMITTED).build();
        RefundEntity refund3 = RefundEntityFixture.aValidRefundEntity().withAmount(30L).withStatus(RefundStatus.REFUNDED).build();
        RefundEntity refund4 = RefundEntityFixture.aValidRefundEntity().withAmount(30L).withStatus(RefundStatus.REFUND_ERROR).build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withRefunds(Arrays.asList(refund1, refund2, refund3, refund4))
                .build();

        long actualAmount = RefundCalculator.getRefundedAmount(chargeEntity);

        // refunds with status of REFUND_ERROR should not be included in returned amount
        long expectedAmount = refund1.getAmount() + refund2.getAmount() + refund3.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }
}
