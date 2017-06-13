package uk.gov.pay.connector.service;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

public class BaseStatusMapperTest {

  @Test
  public void shouldStatusBeDeferredIfMappedAsDeferred()
  {
    CancelStatusResolver mockedCancelStatusResolver = mock(CancelStatusResolver.class);
    when(mockedCancelStatusResolver.resolve(any(ChargeEntity.class))).thenReturn(Optional.of(ChargeStatus.USER_CANCELLED));

    BaseStatusMapper<String> statusMapper =
        BaseStatusMapper
            .<String>builder()
            .mapDeferred("DEFERRED_STATUS", mockedCancelStatusResolver)
            .build();

    InterpretedStatus deferredStatus = statusMapper.from("DEFERRED_STATUS");

    assertThat(deferredStatus.isDeferred(), is(true));
  }

  @Test
  public void shouldStatusBeIgnoredIfMappedAsIgnore()
  {
    BaseStatusMapper<String> statusMapper =
        BaseStatusMapper
            .<String>builder()
            .ignore("IGNORED_STATUS")
            .build();

    InterpretedStatus ignoredStatus = statusMapper.from("IGNORED_STATUS");

    assertThat(ignoredStatus.isIgnored(), is(true));
  }
}