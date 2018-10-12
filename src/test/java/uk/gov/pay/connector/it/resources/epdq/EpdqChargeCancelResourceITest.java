package uk.gov.pay.connector.it.resources.epdq;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;

public class EpdqChargeCancelResourceITest extends ChargingITestBase {

    public EpdqChargeCancelResourceITest() {
        super("epdq");
    }

   
}
