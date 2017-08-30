package uk.gov.pay.connector.model.domain;

import org.eclipse.persistence.config.DescriptorCustomizer;
import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.history.HistoryPolicy;
import org.eclipse.persistence.internal.helper.DatabaseTable;

public class HistoryCustomizer implements DescriptorCustomizer {

    @Override
    public void customize(ClassDescriptor descriptor) {
        for (Object tableName: descriptor.getTableNames() ) {
            HistoryPolicy policy = new HistoryPolicy();
            policy.addHistoryTableName(String.format("%s_HISTORY", ((String) tableName).toUpperCase()));
            policy.addStartFieldName("HISTORY_START_DATE");
            policy.addEndFieldName("HISTORY_END_DATE");
            descriptor.setHistoryPolicy(policy);
        }
    }
}
