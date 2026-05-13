package uk.gov.pay.connector.gateway.adyen.model.json;

import java.util.Map;

public class ActionFixture {
        String type;
        String url;
        String method;
        Map<String,String> data;
        String paymentData;

    public static ActionFixture anAction() {
            return new ActionFixture();
        }
        
        public ActionFixture withType(String type) {
            this.type = type;
            return this;
        }

        public ActionFixture withUrl(String url) {
            this.url = url;
            return this;
        }

        public ActionFixture withMethod(String method) {
            this.method = method;
            return this;
        }

        public ActionFixture withData(Map<String, String> data) {
            this.data = data;
            return this;
        }

        public ActionFixture withPaymentData(String paymentData) {
            this.paymentData = paymentData;
            return this;
        }
        
        public Action build() {
            return new Action(type, url, method, data, paymentData);
        }
    }
