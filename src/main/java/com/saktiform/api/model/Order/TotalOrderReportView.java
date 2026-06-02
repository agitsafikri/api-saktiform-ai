package com.saktiform.api.model.Order;

public interface TotalOrderReportView {

    String getDate();        // hasil format tanggal
    Long getJumlahOrder();   // COUNT(*)
    Long getJumlahBayar();
}

