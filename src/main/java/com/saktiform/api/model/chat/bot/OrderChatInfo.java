package com.saktiform.api.model.chat.bot;

public class OrderChatInfo {

    private String productName;
    private String customerName;

    public OrderChatInfo(String productName, String customerName) {
        this.productName = productName;
        this.customerName = customerName;
    }

    public String getProductName() {
        return productName;
    }

    public String getCustomerName() {
        return customerName;
    }
}

