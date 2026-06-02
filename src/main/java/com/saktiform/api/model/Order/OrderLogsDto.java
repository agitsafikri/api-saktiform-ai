package com.saktiform.api.model.Order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderLogsDto {
    String log;
    String time;

    public OrderLogsDto(String log, Instant time) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        this.log = log;
        this.time = time.atZone(ZoneId.of("Asia/Jakarta"))
                .format(formatter);

    }

}
