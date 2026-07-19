package com.saktiform.api.model.label.response;

import com.saktiform.api.entity.ConversationLabel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Response label (dipakai list master, list per conversation, & item conversation). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabelDto {

    private Long id;
    private String name;
    private String colorHex;

    public static LabelDto from(ConversationLabel l) {
        return new LabelDto(l.getId(), l.getName(), l.getColorHex());
    }

    public static LabelDto from(ConversationLabelProjection p) {
        return new LabelDto(p.getId(), p.getName(), p.getColorHex());
    }
}
