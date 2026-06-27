package com.yourorg.elasticcommon.index;

public class IndexNameStrategy {

    public String forCustomer(String customerId) {
        return "customer_data_" + customerId + "_v1";
    }
}
