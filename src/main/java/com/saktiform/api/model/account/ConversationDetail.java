package com.saktiform.api.model.account;

import com.saktiform.api.model.label.response.LabelDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetail {
    UUID id;
    String namaKontak;
    String phoneNumber;
    String Status;
    String selectedOrder;
    String handledBy;
    List<LabelDto> labels;
}
