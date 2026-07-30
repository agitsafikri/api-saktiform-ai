package com.saktiform.api.service.formconfig;

import com.saktiform.api.entity.ProdukFormConfig;
import com.saktiform.api.model.ErrorDto;
import com.saktiform.api.model.product.formconfig.*;
import com.saktiform.api.util.ValidationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validasi payload konfigurasi form ({@code PUT /produk/{id}/form-config}).
 *
 * <p>Seluruh pelanggaran dikumpulkan lalu dilempar sekaligus sebagai satu
 * {@link ValidationException} — tidak berhenti pada galat pertama — sehingga Admin
 * dapat memperbaiki semuanya dalam satu putaran.
 */
@Component
public class FormConfigValidator {

    public static final int MAX_ACTIVE_CUSTOM_FIELDS = 50;

    private static final int MAX_PATTERN_LENGTH = 200;
    private static final int MAX_LABEL_LENGTH = 150;

    /** Heuristik konstruksi regex yang berisiko catastrophic backtracking. */
    private static final Pattern RISKY_REGEX = Pattern.compile(
            "\\([^)]*[+*][^)]*\\)\\s*[+*]|\\[[^\\]]*\\][+*]\\s*[+*]");

    /**
     * Validasi mode <b>replace</b> ({@code PUT /produk/{id}/form-config}).
     *
     * <p>Daftar yang dikirim merepresentasikan keadaan akhir, sehingga keenam System
     * Field wajib hadir dan {@code fieldCategory} wajib eksplisit.
     *
     * @throws ValidationException bila ada pelanggaran (seluruh galat sekaligus)
     */
    public void validateReplace(Map<String, ProdukFormConfig> existing,
                                List<FormFieldRequest> incoming) {
        List<ErrorDto> errors = new ArrayList<>();
        validateSystemFieldsComplete(incoming, errors);
        validateNoDuplicateKeys(incoming, errors);

        for (int i = 0; i < incoming.size(); i++) {
            FormFieldRequest f = incoming.get(i);
            if (f.getFieldCategory() == FieldCategory.SYSTEM) {
                validateSystemField(f, i, errors);
            } else if (f.getFieldCategory() == FieldCategory.CUSTOM) {
                validateCustomField(f, i, errors);
            } else {
                errors.add(err("fields[" + i + "].fieldCategory",
                        FormConfigErrorCode.INVALID_FIELD_CATEGORY,
                        "Kategori field harus SYSTEM atau CUSTOM."));
            }
            validateCommon(f, i, errors);
        }

        validateActiveCustomLimit(incoming, errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Validasi mode <b>merge</b> ({@code POST /produk}).
     *
     * <p>Payload hanya menambah/memperbarui — tidak pernah menghapus — sehingga
     * kelengkapan System Field tidak diperiksa (System Field dijamin oleh seeding).
     * {@code fieldCategory} boleh kosong; kategorinya disimpulkan pemanggil melalui
     * kecocokan label sebelum entri diteruskan ke sini.
     *
     * @param resolved daftar yang kategorinya sudah ditentukan pemanggil
     */
    public void validateMerge(List<FormFieldRequest> resolved) {
        List<ErrorDto> errors = new ArrayList<>();
        validateNoDuplicateKeys(resolved, errors);

        for (int i = 0; i < resolved.size(); i++) {
            FormFieldRequest f = resolved.get(i);
            if (f.getFieldCategory() == FieldCategory.SYSTEM) {
                validateSystemField(f, i, errors);
            } else {
                validateCustomField(f, i, errors);
            }
            validateCommon(f, i, errors);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** Menegakkan batas jumlah Custom Field aktif atas keadaan akhir hasil merge. */
    public void validateActiveCustomCount(long activeCustomCount) {
        if (activeCustomCount > MAX_ACTIVE_CUSTOM_FIELDS) {
            throw new ValidationException(new ErrorDto("fields",
                    "Jumlah field tambahan aktif maksimum " + MAX_ACTIVE_CUSTOM_FIELDS + ".",
                    FormConfigErrorCode.CUSTOM_FIELD_LIMIT_EXCEEDED,
                    Map.of("limit", MAX_ACTIVE_CUSTOM_FIELDS, "actual", activeCustomCount)));
        }
    }

    // ── Aturan tingkat permintaan ──

    private void validateSystemFieldsComplete(List<FormFieldRequest> incoming, List<ErrorDto> errors) {
        Set<String> present = new HashSet<>();
        for (FormFieldRequest f : incoming) {
            if (f.getFieldCategory() == FieldCategory.SYSTEM && f.getFieldKey() != null) {
                present.add(f.getFieldKey().trim().toLowerCase(Locale.ROOT));
            }
        }
        List<String> missing = new ArrayList<>();
        for (SystemFormField sf : SystemFormField.values()) {
            if (!present.contains(sf.getKey())) {
                missing.add(sf.getKey());
            }
        }
        if (!missing.isEmpty()) {
            errors.add(new ErrorDto("fields",
                    "System Field berikut tidak boleh dihapus: " + String.join(", ", missing) + ".",
                    FormConfigErrorCode.SYSTEM_FIELD_NOT_DELETABLE,
                    Map.of("missingSystemFields", missing)));
        }
    }

    private void validateNoDuplicateKeys(List<FormFieldRequest> incoming, List<ErrorDto> errors) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < incoming.size(); i++) {
            String key = incoming.get(i).getFieldKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String lower = key.trim().toLowerCase(Locale.ROOT);
            if (!seen.add(lower)) {
                errors.add(err("fields[" + i + "].fieldKey",
                        FormConfigErrorCode.DUPLICATE_FIELD_KEY,
                        "Terdapat field key ganda: '" + key + "'."));
            }
        }
    }

    private void validateActiveCustomLimit(List<FormFieldRequest> incoming, List<ErrorDto> errors) {
        long active = incoming.stream()
                .filter(f -> f.getFieldCategory() == FieldCategory.CUSTOM)
                .filter(f -> f.getIsActive() == null || Boolean.TRUE.equals(f.getIsActive()))
                .count();
        if (active > MAX_ACTIVE_CUSTOM_FIELDS) {
            errors.add(new ErrorDto("fields",
                    "Jumlah field tambahan aktif maksimum " + MAX_ACTIVE_CUSTOM_FIELDS + ".",
                    FormConfigErrorCode.CUSTOM_FIELD_LIMIT_EXCEEDED,
                    Map.of("limit", MAX_ACTIVE_CUSTOM_FIELDS, "actual", active)));
        }
    }

    // ── Aturan umum per field ──

    private void validateCommon(FormFieldRequest f, int idx, List<ErrorDto> errors) {
        String p = "fields[" + idx + "]";
        String label = f.getLabel() == null ? "" : f.getLabel().trim();
        if (label.isEmpty()) {
            errors.add(err(p + ".label", FormConfigErrorCode.LABEL_REQUIRED,
                    "Label field wajib diisi."));
        } else if (label.length() > MAX_LABEL_LENGTH) {
            errors.add(err(p + ".label", FormConfigErrorCode.LABEL_TOO_LONG,
                    "Label maksimum " + MAX_LABEL_LENGTH + " karakter."));
        }
        if (StringUtils.hasText(f.getFieldKey())
                && !f.getFieldKey().trim().matches("^[a-z][a-z0-9_]*$")) {
            errors.add(err(p + ".fieldKey", FormConfigErrorCode.INVALID_FIELD_KEY_FORMAT,
                    "Format field key '" + f.getFieldKey() + "' tidak valid."));
        }
    }

    // ── System Field: abaikan bila sama, tolak bila berbeda ──

    private void validateSystemField(FormFieldRequest f, int idx, List<ErrorDto> errors) {
        Optional<SystemFormField> sys = SystemFormField.byKey(f.getFieldKey());
        if (sys.isEmpty()) {
            errors.add(err("fields[" + idx + "].fieldKey",
                    FormConfigErrorCode.UNKNOWN_SYSTEM_FIELD,
                    "System Field '" + f.getFieldKey() + "' tidak dikenal."));
            return;
        }
        SystemFormField def = sys.get();
        String key = def.getKey();

        rejectIfChanged(f.getFieldType(), def.getType(), "fieldType", key, idx, errors);
        rejectIfChanged(f.getIsRequired(), Boolean.TRUE, "isRequired", key, idx, errors);
        rejectIfChanged(f.getIsActive(), Boolean.TRUE, "isActive", key, idx, errors);

        if (f.getOptions() != null && !f.getOptions().isEmpty()) {
            errors.add(immutable(idx, key, "options"));
        }
        if (StringUtils.hasText(f.getDefaultValue())) {
            errors.add(immutable(idx, key, "defaultValue"));
        }
    }

    /**
     * Menolak hanya bila nilai yang dikirim <b>berbeda</b> dari nilai terkunci.
     * Nilai {@code null} (tidak dikirim) maupun nilai yang sama diterima, sehingga
     * frontend dapat mengirim kembali objek field secara utuh tanpa menyaringnya.
     */
    private <T> void rejectIfChanged(T incoming, T locked, String attr, String key,
                                     int idx, List<ErrorDto> errors) {
        if (incoming != null && !incoming.equals(locked)) {
            errors.add(immutable(idx, key, attr));
        }
    }

    private ErrorDto immutable(int idx, String key, String attr) {
        return new ErrorDto("fields[" + idx + "]." + attr,
                "Atribut '" + attr + "' pada System Field '" + key + "' tidak dapat diubah.",
                FormConfigErrorCode.SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE,
                Map.of("fieldKey", key, "attribute", attr));
    }

    // ── Custom Field ──

    private void validateCustomField(FormFieldRequest f, int idx, List<ErrorDto> errors) {
        String p = "fields[" + idx + "]";
        FormFieldType type = f.getFieldType();

        if (type == null) {
            errors.add(err(p + ".fieldType", FormConfigErrorCode.INVALID_FIELD_TYPE,
                    "Tipe field wajib diisi."));
            return;
        }
        if (type.isSystemOnly()) {
            errors.add(err(p + ".fieldType", FormConfigErrorCode.FIELD_TYPE_RESERVED_FOR_SYSTEM,
                    "Tipe field '" + type + "' hanya dapat dipakai oleh System Field."));
            return;
        }

        boolean hasOptions = f.getOptions() != null && !f.getOptions().isEmpty();
        if (type.isRequiresOptions() && !hasOptions) {
            errors.add(err(p + ".options", FormConfigErrorCode.OPTIONS_REQUIRED_FOR_TYPE,
                    "Tipe field '" + type + "' memerlukan minimal satu pilihan."));
        } else if (!type.isRequiresOptions() && hasOptions) {
            errors.add(err(p + ".options", FormConfigErrorCode.OPTIONS_NOT_ALLOWED_FOR_TYPE,
                    "Tipe field '" + type + "' tidak menerima daftar pilihan."));
        } else if (hasOptions) {
            validateOptions(type, f.getOptions(), p, errors);
        }

        validateValidationRule(type, f.getValidation(), p, errors);
        validateDefaultValue(type, f, p, errors);
    }

    private void validateOptions(FormFieldType type, List<OptionDto> options,
                                 String path, List<ErrorDto> errors) {
        int max = type.getMaxOptions();
        if (options.size() > max) {
            errors.add(err(path + ".options", FormConfigErrorCode.TOO_MANY_OPTIONS,
                    "Jumlah pilihan maksimum " + max + "."));
        }
        Set<String> values = new HashSet<>();
        for (int i = 0; i < options.size(); i++) {
            OptionDto o = options.get(i);
            String op = path + ".options[" + i + "]";
            if (o == null || !StringUtils.hasText(o.getLabel()) || !StringUtils.hasText(o.getValue())) {
                errors.add(err(op, FormConfigErrorCode.OPTION_INCOMPLETE,
                        "Setiap pilihan wajib memiliki label dan nilai."));
                continue;
            }
            if (o.getLabel().length() > 100 || o.getValue().length() > 100) {
                errors.add(err(op, FormConfigErrorCode.OPTION_TOO_LONG,
                        "Label dan nilai pilihan maksimum 100 karakter."));
            }
            if (!values.add(o.getValue().trim().toLowerCase(Locale.ROOT))) {
                errors.add(err(op + ".value", FormConfigErrorCode.DUPLICATE_OPTION_VALUE,
                        "Nilai pilihan '" + o.getValue() + "' ganda."));
            }
        }
    }

    private void validateValidationRule(FormFieldType type, ValidationRuleDto rule,
                                        String path, List<ErrorDto> errors) {
        if (rule == null) {
            return;
        }
        String p = path + ".validation";

        boolean lengthApplies = type == FormFieldType.TEXT || type == FormFieldType.TEXTAREA;
        if (!lengthApplies && (rule.getMinLength() != null || rule.getMaxLength() != null)) {
            errors.add(err(p, FormConfigErrorCode.INVALID_VALIDATION_RULE,
                    "Batas panjang hanya berlaku untuk tipe TEXT dan TEXTAREA."));
        }

        Integer min = rule.getMinLength();
        Integer max = rule.getMaxLength();
        if (min != null && min < 0) {
            errors.add(err(p + ".minLength", FormConfigErrorCode.INVALID_VALIDATION_RULE,
                    "Panjang minimum tidak boleh negatif."));
        }
        if (max != null && max > type.getMaxValueLength()) {
            errors.add(err(p + ".maxLength", FormConfigErrorCode.INVALID_VALIDATION_RULE,
                    "Panjang maksimum untuk tipe " + type + " adalah " + type.getMaxValueLength() + "."));
        }
        if (min != null && max != null && min > max) {
            errors.add(err(p, FormConfigErrorCode.INVALID_RANGE,
                    "Nilai minimum tidak boleh lebih besar dari maksimum."));
        }

        validatePattern(rule.getPattern(), p + ".pattern", errors);
    }

    /**
     * Pencegahan ReDoS pada saat konfigurasi disimpan. Bersifat penyaring kasar —
     * pertahanan sesungguhnya adalah batas waktu evaluasi saat submit order.
     */
    private void validatePattern(String pattern, String path, List<ErrorDto> errors) {
        if (!StringUtils.hasText(pattern)) {
            return;
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            errors.add(err(path, FormConfigErrorCode.INVALID_VALIDATION_RULE,
                    "Pola validasi maksimum " + MAX_PATTERN_LENGTH + " karakter."));
            return;
        }
        if (RISKY_REGEX.matcher(pattern).find()) {
            errors.add(err(path, FormConfigErrorCode.INVALID_VALIDATION_RULE,
                    "Pola validasi mengandung konstruksi yang berisiko lambat."));
            return;
        }
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            errors.add(err(path, FormConfigErrorCode.INVALID_VALIDATION_RULE,
                    "Pola validasi tidak valid."));
        }
    }

    private void validateDefaultValue(FormFieldType type, FormFieldRequest f,
                                      String path, List<ErrorDto> errors) {
        String dv = f.getDefaultValue();
        if (!StringUtils.hasText(dv)) {
            return;
        }
        if (type.isRequiresOptions()) {
            boolean found = f.getOptions() != null && f.getOptions().stream()
                    .anyMatch(o -> o != null && dv.equals(o.getValue()));
            if (!found) {
                errors.add(err(path + ".defaultValue", FormConfigErrorCode.INVALID_DEFAULT_VALUE,
                        "Nilai bawaan harus salah satu pilihan yang tersedia."));
            }
        } else if (dv.length() > type.getMaxValueLength()) {
            errors.add(err(path + ".defaultValue", FormConfigErrorCode.INVALID_DEFAULT_VALUE,
                    "Nilai bawaan maksimum " + type.getMaxValueLength() + " karakter."));
        }
    }

    private ErrorDto err(String field, String code, String message) {
        return new ErrorDto(field, message, code, null);
    }
}
