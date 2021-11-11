package uk.gov.pay.connector.charge.util;


import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


class RefundCalculatorTest {

    @Test
    void getTotalAmountToBeRefunded_shouldReturnFullAmount_whenNoCorporateSurchargeAndNoRefunds() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();

        long actualAmount = RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity, List.of());
        long expectedAmount = chargeEntity.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    void getTotalAmountToBeRefunded_shouldReturnRemainingAmount_whenNoCorporateSurchargeAndHasRefunds() {
        RefundEntity refundedRefund = RefundEntityFixture.aValidRefundEntity().withAmount(10L).withStatus(RefundStatus.REFUNDED).build();
        RefundEntity createdRefund = RefundEntityFixture.aValidRefundEntity().withAmount(20L).withStatus(RefundStatus.CREATED).build();
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        List<Refund> refundList = Stream.of(refundedRefund, createdRefund).map(Refund::from).collect(Collectors.toList());

        long actualAmount = RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity, refundList);
        long expectedAmount = chargeEntity.getAmount() - refundedRefund.getAmount() - createdRefund.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    void getTotalAmountToBeRefunded_shouldReturnFullAmountIncludingSurcharge_whenChargeHasCorporateSurchargeAndNoRefunds() {
        long corporateSurcharge = 250L;
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(corporateSurcharge).build();

        long actualAmount = RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity, List.of());
        long expectedAmount = chargeEntity.getAmount() + corporateSurcharge;

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    void getTotalAmountToBeRefunded_shouldReturnRemainingAmountIncludingSurcharge_whenChargeHasCorporateSurchargeAndHasRefunds() {
        long corporateSurcharge = 250L;
        RefundEntity refundedRefund = RefundEntityFixture.aValidRefundEntity().withAmount(10L).withStatus(RefundStatus.REFUNDED).build();
        RefundEntity createdRefund = RefundEntityFixture.aValidRefundEntity().withAmount(20L).withStatus(RefundStatus.CREATED).build();
        List<Refund> refundList = Stream.of(refundedRefund, createdRefund).map(Refund::from).collect(Collectors.toList());
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCorporateSurcharge(corporateSurcharge)
                .build();

        long actualAmount = RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity, refundList);
        long expectedAmount = chargeEntity.getAmount() + corporateSurcharge - refundedRefund.getAmount() - createdRefund.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    void getRefundedAmount_shouldIncludeCreatedSubmittedAndRefundedRefunds() {
        RefundEntity createdRefund = RefundEntityFixture.aValidRefundEntity().withAmount(10L).withStatus(RefundStatus.CREATED).build();
        RefundEntity submittedRefund = RefundEntityFixture.aValidRefundEntity().withAmount(20L).withStatus(RefundStatus.REFUND_SUBMITTED).build();
        RefundEntity refundedRefund = RefundEntityFixture.aValidRefundEntity().withAmount(30L).withStatus(RefundStatus.REFUNDED).build();
        RefundEntity errorRefund = RefundEntityFixture.aValidRefundEntity().withAmount(30L).withStatus(RefundStatus.REFUND_ERROR).build();

        long actualAmount = RefundCalculator.getRefundedAmount(Stream.of(createdRefund, submittedRefund, refundedRefund, errorRefund).map(Refund::from).collect(Collectors.toList()));

        // refunds with status of REFUND_ERROR should not be included in returned amount
        long expectedAmount = createdRefund.getAmount() + submittedRefund.getAmount() + refundedRefund.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }
}
