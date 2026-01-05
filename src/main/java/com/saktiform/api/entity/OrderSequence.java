package com.saktiform.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "order_sequence")
public class OrderSequence {
    @Id
    @Column(name = "seq_date", nullable = false)
    private String id;

    @NotNull
    @Column(name = "seq_value", nullable = false)
    private Long seqValue;

}