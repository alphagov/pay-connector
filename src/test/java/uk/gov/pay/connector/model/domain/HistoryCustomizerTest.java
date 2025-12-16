package uk.gov.pay.connector.model.domain;

import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.history.HistoryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.model.domain.HistoryCustomizer;

import java.util.Vector;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryCustomizerTest {

    @Mock
    private ClassDescriptor mockClassDescriptor;

    @Test
    void shouldRegisterAHistoryPolicyToTheClassDescriptor() {
        Vector databaseTables = new Vector();
        databaseTables.add("table_name");

        ArgumentCaptor<HistoryPolicy> setHistoryPolicyArgumentCaptor = ArgumentCaptor.forClass(HistoryPolicy.class);
        when(mockClassDescriptor.getTableNames()).thenReturn(databaseTables);

        HistoryCustomizer historyCustomizer = new HistoryCustomizer();
        historyCustomizer.customize(mockClassDescriptor);

        verify(mockClassDescriptor).setHistoryPolicy(setHistoryPolicyArgumentCaptor.capture());
        HistoryPolicy setHistoryPolicyArgument = setHistoryPolicyArgumentCaptor.getValue();
        assertThat(setHistoryPolicyArgument.getHistoryTableNames().size(), is(1));
        assertThat(setHistoryPolicyArgument.getHistoryTableNames().getFirst(), is("TABLE_NAME_HISTORY"));
        assertThat(setHistoryPolicyArgument.getEndFieldName(), is("HISTORY_END_DATE"));
        assertThat(setHistoryPolicyArgument.getStartFieldName(), is("HISTORY_START_DATE"));
    }
}
