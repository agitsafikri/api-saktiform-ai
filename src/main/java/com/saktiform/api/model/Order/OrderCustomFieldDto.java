package com.saktiform.api.model.Order;

import com.saktiform.api.model.product.formconfig.FormFieldType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Nilai Custom Field pada detail order.
 *
 * <p>{@code fieldLabel} berasal dari <b>snapshot</b> saat order dibuat, bukan dari
 * konfigurasi produk saat ini — order lama tetap menampilkan label sebagaimana yang
 * dilihat pelanggan ketika memesan.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCustomFieldDto implements Serializable {

    private String fieldKey;
    private String fieldLabel;
    private FormFieldType fieldType;
    private String value;
    private Integer sortOrder;
}
