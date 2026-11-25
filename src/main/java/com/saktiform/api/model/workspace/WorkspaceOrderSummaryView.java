package com.saktiform.api.model.workspace;

/** Ringkasan jumlah order per workspace untuk dashboard (satu query, tiga angka). */
public interface WorkspaceOrderSummaryView {
    Long getTotalOrder();
    Long getPaidOrder();
    Long getUnpaidOrder();
}
