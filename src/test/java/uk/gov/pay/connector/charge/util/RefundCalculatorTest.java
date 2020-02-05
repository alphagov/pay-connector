package uk.gov.pay.connector.charge.util;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class RefundCalculatorTest {
    @Test
    public void getTotalAmountToBeRefunded_shouldReturnFullAmount_whenNoCorporateSurchargeAndNoRefunds() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();

        long actualAmount = RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity, List.of());
        long expectedAmount = chargeEntity.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void getTotalAmountToBeRefunded_shouldReturnRemainingAmount_whenNoCorporateSurchargeAndHasRefunds() {
        RefundEntity refundedRefund = RefundEntityFixture.aValidRefundEntity().withAmount(10L).withStatus(RefundStatus.REFUNDED).build();
        RefundEntity createdRefund = RefundEntityFixture.aValidRefundEntity().withAmount(20L).withStatus(RefundStatus.CREATED).build();
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        List<RefundEntity> refundEntityList = List.of(refundedRefund, createdRefund);

        long actualAmount = RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity, refundEntityList);
        long expectedAmount = chargeEntity.getAmount() - refundedRefund.getAmount() - createdRefund.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void getTotalAmountToBeRefunded_shouldReturnFullAmountIncludingSurcharge_whenChargeHasCorporateSurchargeAndNoRefunds() {
        long corporateSurcharge = 250L;
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(corporateSurcharge).build();

        long actualAmount = RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity, List.of());
        long expectedAmount = chargeEntity.getAmount() + corporateSurcharge;

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void getTotalAmountToBeRefunded_shouldReturnRemainingAmountIncludingSurcharge_whenChargeHasCorporateSurchargeAndHasRefunds() {
        long corporateSurcharge = 250L;
        RefundEntity refundedRefund = RefundEntityFixture.aValidRefundEntity().withAmount(10L).withStatus(RefundStatus.REFUNDED).build();
        RefundEntity createdRefund = RefundEntityFixture.aValidRefundEntity().withAmount(20L).withStatus(RefundStatus.CREATED).build();
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCorporateSurcharge(corporateSurcharge)
                .build();

        long actualAmount = RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity, List.of(refundedRefund, createdRefund));
        long expectedAmount = chargeEntity.getAmount() + corporateSurcharge - refundedRefund.getAmount() - createdRefund.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void getRefundedAmount_shouldIncludeCreatedSubmittedAndRefundedRefunds() {
        RefundEntity createdRefund = RefundEntityFixture.aValidRefundEntity().withAmount(10L).withStatus(RefundStatus.CREATED).build();
        RefundEntity submittedRefund = RefundEntityFixture.aValidRefundEntity().withAmount(20L).withStatus(RefundStatus.REFUND_SUBMITTED).build();
        RefundEntity refundedRefund = RefundEntityFixture.aValidRefundEntity().withAmount(30L).withStatus(RefundStatus.REFUNDED).build();
        RefundEntity errorRefund = RefundEntityFixture.aValidRefundEntity().withAmount(30L).withStatus(RefundStatus.REFUND_ERROR).build();

        long actualAmount = RefundCalculator.getRefundedAmount(List.of(createdRefund, submittedRefund, refundedRefund, errorRefund));

        // refunds with status of REFUND_ERROR should not be included in returned amount
        long expectedAmount = createdRefund.getAmount() + submittedRefund.getAmount() + refundedRefund.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }
}
