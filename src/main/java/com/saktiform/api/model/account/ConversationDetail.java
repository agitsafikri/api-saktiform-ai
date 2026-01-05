package com.saktiform.api.model.account;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetail {
    UUID id;
    String namaKontak;
    String Status;
    String selectedOrder;
    String handledBy;
}
