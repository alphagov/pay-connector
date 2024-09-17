package uk.gov.pay.connector.events.eventdetails.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class RefundStatusCorrectedToErrorByAdminEventDetails extends EventDetails {
    
    String updatedReason;
    String adminGithubId;
    String zendeskId;

    public RefundStatusCorrectedToErrorByAdminEventDetails( String updatedReason, String adminGithubId, String zendeskId) {
        this.updatedReason = updatedReason;
        this.adminGithubId = adminGithubId;
        this.zendeskId = zendeskId;
    }

    public String getUpdatedReason() {
        return updatedReason;
    }

    public void setUpdatedReason(String updatedReason) {
        this.updatedReason = updatedReason;
    }

    public String getAdminGithubId() {
        return adminGithubId;
    }

    public void setAdminGithubId(String adminGithubId) {
        this.adminGithubId = adminGithubId;
    }

    public String getZendeskId() {
        return zendeskId;
    }

    public void setZendeskId(String zendeskId) {
        this.zendeskId = zendeskId;
    }
}
