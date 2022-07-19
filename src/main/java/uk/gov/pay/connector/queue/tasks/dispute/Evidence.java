package uk.gov.pay.connector.queue.tasks.dispute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Evidence {

    @JsonProperty("uncategorized_text")
    private String uncategorizedText;

    public Evidence() {
        // for jackson
    }

    public String getUncategorizedText() {
        return uncategorizedText;
    }
}
