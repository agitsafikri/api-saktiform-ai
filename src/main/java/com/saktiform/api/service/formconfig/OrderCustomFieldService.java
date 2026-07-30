package com.saktiform.api.service.formconfig;

import com.saktiform.api.entity.OrderCustomField;
import com.saktiform.api.model.Order.OrderCustomFieldDto;
import com.saktiform.api.model.product.formconfig.FormFieldType;
import com.saktiform.api.repository.OrderCustomFieldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Penyimpanan snapshot dan pembacaan nilai Custom Field sebuah order. */
@Service
public class OrderCustomFieldService {

    private final OrderCustomFieldRepository repository;

    public OrderCustomFieldService(OrderCustomFieldRepository repository) {
        this.repository = repository;
    }

    /** Menyimpan seluruh nilai tervalidasi dalam satu batch insert. */
    @Transactional
    public void saveSnapshot(UUID idOrder, UUID idProduk, List<ValidatedFieldValue> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<OrderCustomField> rows = values.stream().map(v -> {
            OrderCustomField r = new OrderCustomField();
            r.setIdOrder(idOrder);
            r.setIdProduk(idProduk);
            r.setFieldKey(v.getFieldKey());
            r.setFieldLabel(v.getFieldLabel());
            r.setFieldType(v.getFieldType() == null
                    ? FormFieldType.TEXT.name() : v.getFieldType().name());
            r.setFieldValue(v.getTextValue());
            r.setSortOrder(v.getSortOrder() == null ? 999 : v.getSortOrder());
            r.setCreatedAt(now);
            return r;
        }).collect(Collectors.toList());
        repository.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public List<OrderCustomFieldDto> findByOrder(UUID idOrder) {
        return repository.findByIdOrderOrderBySortOrderAscIdAsc(idOrder).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Batch fetch untuk daftar order. Disiapkan agar penambahan kolom Custom Field pada
     * daftar order kelak tidak menggoda pemanggilan {@link #findByOrder} di dalam loop.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<OrderCustomFieldDto>> findByOrders(Collection<UUID> idOrders) {
        if (idOrders == null || idOrders.isEmpty()) {
            return Map.of();
        }
        return repository.findByIdOrderInOrderBySortOrderAscIdAsc(idOrders).stream()
                .collect(Collectors.groupingBy(
                        OrderCustomField::getIdOrder,
                        Collectors.mapping(this::toDto, Collectors.toList())));
    }

    private OrderCustomFieldDto toDto(OrderCustomField e) {
        FormFieldType type = FormFieldType.parseStrict(e.getFieldType());
        if (type == null) {
            type = FormFieldType.TEXT;
        }
        return new OrderCustomFieldDto(
                e.getFieldKey(),
                e.getFieldLabel(),
                type,
                e.getFieldValue(),
                e.getSortOrder());
    }
}
