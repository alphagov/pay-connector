package uk.gov.pay.connector.service.worldpay;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.IgnoredStatus;
import uk.gov.pay.connector.service.InterpretedStatus;
import uk.gov.pay.connector.service.MappedChargeStatus;
import uk.gov.pay.connector.service.MappedRefundStatus;
import uk.gov.pay.connector.service.UnknownStatus;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;

public class WorldpayStatusMapper {

}
