package uk.gov.pay.connector.model.domain;

import junit.framework.TestCase;
import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.history.HistoryPolicy;
import org.eclipse.persistence.internal.helper.DatabaseTable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Vector;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HistoryCustomizerTest extends TestCase {

    @Mock
    private ClassDescriptor mockClassDescriptor;

    @Test
    public void shouldRegisterAHistoryPolicyToTheClassDescriptor() {
        Vector databaseTables = new Vector();
        databaseTables.add("table_name");

        ArgumentCaptor<HistoryPolicy> setHistoryPolicyArgumentCaptor = ArgumentCaptor.forClass(HistoryPolicy.class);
        when(mockClassDescriptor.getTableNames()).thenReturn(databaseTables);

        HistoryCustomizer historyCustomizer = new HistoryCustomizer();
        historyCustomizer.customize(mockClassDescriptor);

        verify(mockClassDescriptor).setHistoryPolicy(setHistoryPolicyArgumentCaptor.capture());
        HistoryPolicy setHistoryPolicyArgument = setHistoryPolicyArgumentCaptor.getValue();
        assertThat(setHistoryPolicyArgument.getHistoryTableNames().size(), is(1));
        assertThat(setHistoryPolicyArgument.getHistoryTableNames().get(0), is("TABLE_NAME_HISTORY"));
        assertThat(setHistoryPolicyArgument.getEndFieldName(), is("HISTORY_END_DATE"));
        assertThat(setHistoryPolicyArgument.getStartFieldName(), is("HISTORY_START_DATE"));
    }
}