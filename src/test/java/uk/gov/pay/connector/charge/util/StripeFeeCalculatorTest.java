package uk.gov.pay.connector.charge.util;


import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity.ChargeEventEntityBuilder.aChargeEventEntity;

class StripeFeeCalculatorTest {

    private final Long stripeFee = 10L;
    private final Double feePercentage = 0.09;
    
    private CaptureGatewayRequest request;
    private CaptureGatewayRequest requestForChargeWith3ds;

    @BeforeEach
    void setUp() {
        ChargeEntity chargeWithNo3ds = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(100000L)
                .build();
        request = CaptureGatewayRequest.valueOf(chargeWithNo3ds);

        ChargeEntity chargeWith3ds = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(100000L)
                .withEvents(List.of(aChargeEventEntity()
                        .withStatus(AUTHORISATION_3DS_REQUIRED).build()))
                .build();
        requestForChargeWith3ds = CaptureGatewayRequest.valueOf(chargeWith3ds);
    }

    @Test
    void shouldReturnCorrectConnectFee() {
        Long result = StripeFeeCalculator.getTotalAmountForConnectFee(stripeFee, request, feePercentage);
        assertThat(result, is(100L));
    }

    @Test
    void shouldReturnFeeListForChargeWithNo3ds() {
        List<Fee> feeList = StripeFeeCalculator.getFeeList(stripeFee, request, feePercentage, 5, 10);
        assertThat(feeList.size(), is(2));
        assertThat(feeList.get(0).feeType(), Is.is(FeeType.TRANSACTION));
        assertThat(feeList.get(0).amount(), Is.is(100L));
        assertThat(feeList.get(1).feeType(), Is.is(FeeType.RADAR));
        assertThat(feeList.get(1).amount(), Is.is(5L));
    }

    @Test
    void shouldReturnFeeListForChargeWith3ds() {
        List<Fee> feeList = StripeFeeCalculator.getFeeList(stripeFee, requestForChargeWith3ds, feePercentage, 5, 10);
        assertThat(feeList.size(), is(3));
        assertThat(feeList.get(0).feeType(), Is.is(FeeType.TRANSACTION));
        assertThat(feeList.get(0).amount(), Is.is(100L));
        assertThat(feeList.get(1).feeType(), Is.is(FeeType.RADAR));
        assertThat(feeList.get(1).amount(), Is.is(5L));
        assertThat(feeList.get(2).feeType(), Is.is(FeeType.THREE_D_S));
        assertThat(feeList.get(2).amount(), Is.is(10L));
    }

    @Test
    void shouldReturnTotalAmountForListOfFees() {
        List<Fee> feeList = List.of(
                Fee.of(FeeType.TRANSACTION, 100L),
                Fee.of(FeeType.RADAR, 50L),
                Fee.of(FeeType.THREE_D_S, 25L)
        );

        Long totalFeeAmount = StripeFeeCalculator.getTotalFeeAmount(feeList);

        assertThat(totalFeeAmount, is(175L));
    }
}
