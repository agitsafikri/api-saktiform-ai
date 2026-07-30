package com.saktiform.api.service.formconfig;

import com.saktiform.api.entity.ProdukFormConfig;
import com.saktiform.api.model.ErrorDto;
import com.saktiform.api.model.Order.CustomFieldValueDto;
import com.saktiform.api.model.product.formconfig.*;
import com.saktiform.api.repository.ProdukFormConfigRepository;
import com.saktiform.api.util.TextSanitizer;
import com.saktiform.api.util.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validasi nilai Custom Field pada saat submit order.
 *
 * <p>Konfigurasi selalu dibaca ulang dari basis data — metadata apa pun yang dikirim
 * klien diabaikan sepenuhnya. Endpoint {@code POST /order/create} bersifat publik.
 */
@Component
public class CustomFieldValueValidator {

    private static final Logger log = LoggerFactory.getLogger(CustomFieldValueValidator.class);

    /**
     * Sumber order yang <b>tidak</b> dikenai pemeriksaan wajib-isi: keduanya merupakan
     * entri oleh agen/sistem, bukan pengisian form oleh pelanggan. Memaksakan required
     * akan memblokir operasional agen.
     */
    private static final Set<String> SOURCES_SKIP_REQUIRED = Set.of("CST_CHAT", "ADM_ABANDONED");

    /** Batas waktu evaluasi regex — pertahanan ReDoS yang sesungguhnya. */
    private static final long PATTERN_TIMEOUT_MS = 100L;

    private final ProdukFormConfigRepository configRepository;
    private final ExecutorService patternExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "custom-field-pattern");
                t.setDaemon(true);
                return t;
            });

    public CustomFieldValueValidator(ProdukFormConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * @return nilai tervalidasi &amp; ternormalisasi siap di-snapshot; kosong bila tidak ada
     * @throws ValidationException bila ada pelanggaran (seluruh galat sekaligus)
     */
    public List<ValidatedFieldValue> validate(UUID idProduk,
                                              List<CustomFieldValueDto> payload,
                                              String source) {
        List<ErrorDto> errors = new ArrayList<>();
        List<ValidatedFieldValue> result = new ArrayList<>();

        List<ProdukFormConfig> activeConfigs =
                configRepository.findByIdProdukAndIsActiveTrueOrderBySortOrderAscIdAsc(idProduk);

        // Entri ganda dengan fieldKey sama: entri terakhir menang.
        Map<String, Object> byKey = new LinkedHashMap<>();
        if (payload != null) {
            for (CustomFieldValueDto item : payload) {
                if (item == null || item.getFieldKey() == null) {
                    continue;
                }
                byKey.put(item.getFieldKey().trim().toLowerCase(Locale.ROOT), item.getValue());
            }
        }

        boolean enforceRequired = !SOURCES_SKIP_REQUIRED.contains(
                source == null ? "" : source.trim().toUpperCase(Locale.ROOT));

        // System Field di dalam customFields ditolak keras — indikasi penyalahgunaan,
        // bukan cache lama.
        for (String k : byKey.keySet()) {
            if (SystemFormField.isSystemKey(k)) {
                errors.add(err(k, FormConfigErrorCode.SYSTEM_FIELD_IN_CUSTOM_PAYLOAD,
                        "Field '" + k + "' merupakan System Field dan tidak dapat dikirim "
                                + "melalui customFields."));
            }
        }

        // Iterasi berbasis KONFIGURASI, bukan payload — sehingga field wajib yang tidak
        // dikirim sama sekali tetap terdeteksi.
        for (ProdukFormConfig cfg : activeConfigs) {
            if (cfg.getFieldCategory() != FieldCategory.CUSTOM) {
                continue;
            }
            String key = cfg.getFieldKey() == null
                    ? null : cfg.getFieldKey().toLowerCase(Locale.ROOT);
            if (key == null) {
                continue;
            }
            Object raw = byKey.remove(key);

            if (isEmpty(raw)) {
                if (Boolean.TRUE.equals(cfg.getIsRequired()) && enforceRequired) {
                    errors.add(err(cfg.getFieldKey(), FormConfigErrorCode.REQUIRED_FIELD_MISSING,
                            cfg.getLabel() + " wajib diisi."));
                }
                continue; // tidak membuat baris untuk nilai kosong
            }

            try {
                result.add(normalizeAndValidate(cfg, raw));
            } catch (ValidationException e) {
                errors.addAll(e.getErrors());
            }
        }

        // Sisa payload = fieldKey tak dikenal atau field nonaktif. Diabaikan (lenient):
        // halaman checkout bisa saja ter-cache sebelum Admin mengubah konfigurasi, dan
        // menolak pesanan karena alasan administratif berarti kehilangan penjualan.
        byKey.keySet().stream()
                .filter(k -> !SystemFormField.isSystemKey(k))
                .forEach(k -> log.warn("Custom field tidak dikenal diabaikan. idProduk={} fieldKey={}",
                        idProduk, k));

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return result;
    }

    // ────────────────────────────── Normalisasi per tipe ──────────────────────────────

    private ValidatedFieldValue normalizeAndValidate(ProdukFormConfig cfg, Object raw) {
        FormFieldType type = cfg.getFieldType();
        return switch (type) {
            case TEXT, TEXTAREA -> {
                String s = TextSanitizer.sanitize(String.valueOf(raw));
                if (type == FormFieldType.TEXT) {
                    s = s.replaceAll("\\s*\\n\\s*", " ").trim();
                }
                checkLength(cfg, type, s);
                checkPattern(cfg, s);
                yield ValidatedFieldValue.of(cfg, s);
            }
            case SELECT -> {
                String s = String.valueOf(raw).trim();
                Set<String> allowed = allowedValues(cfg);
                if (!allowed.contains(s)) {
                    throw single(cfg, FormConfigErrorCode.VALUE_NOT_IN_OPTIONS,
                            "Nilai '" + s + "' tidak tersedia pada pilihan " + cfg.getLabel() + ".",
                            Map.of("allowedValues", new ArrayList<>(allowed)));
                }
                yield ValidatedFieldValue.of(cfg, s);
            }
            default -> throw single(cfg, FormConfigErrorCode.INVALID_FIELD_TYPE,
                    "Tipe field '" + type + "' tidak didukung untuk field tambahan.", null);
        };
    }

    private Set<String> allowedValues(ProdukFormConfig cfg) {
        if (cfg.getOptions() == null) {
            return Set.of();
        }
        return cfg.getOptions().stream()
                .filter(Objects::nonNull)
                .map(OptionDto::getValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void checkLength(ProdukFormConfig cfg, FormFieldType type, String value) {
        int hardMax = type.getMaxValueLength();
        ValidationRuleDto rule = cfg.getValidationRule();
        Integer min = rule == null ? null : rule.getMinLength();
        Integer max = rule == null ? null : rule.getMaxLength();

        if (value.length() > hardMax) {
            throw single(cfg, FormConfigErrorCode.VALUE_RULE_VIOLATION,
                    cfg.getLabel() + " maksimum " + hardMax + " karakter.", null);
        }
        if (min != null && value.length() < min) {
            throw single(cfg, FormConfigErrorCode.VALUE_RULE_VIOLATION,
                    cfg.getLabel() + " minimal " + min + " karakter.", null);
        }
        if (max != null && value.length() > max) {
            throw single(cfg, FormConfigErrorCode.VALUE_RULE_VIOLATION,
                    cfg.getLabel() + " maksimum " + max + " karakter.", null);
        }
    }

    /** Evaluasi regex dengan batas waktu agar pola yang lambat tidak memblokir request. */
    private void checkPattern(ProdukFormConfig cfg, String value) {
        ValidationRuleDto rule = cfg.getValidationRule();
        if (rule == null || rule.getPattern() == null || rule.getPattern().isBlank()) {
            return;
        }
        String pattern = rule.getPattern();
        Future<Boolean> task = patternExecutor.submit(
                () -> Pattern.compile(pattern).matcher(value).matches());
        try {
            if (!task.get(PATTERN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw single(cfg, FormConfigErrorCode.VALUE_RULE_VIOLATION,
                        cfg.getLabel() + " tidak sesuai format yang ditentukan.", null);
            }
        } catch (TimeoutException e) {
            task.cancel(true);
            log.warn("Evaluasi pola validasi melampaui batas waktu. fieldKey={}", cfg.getFieldKey());
            throw single(cfg, FormConfigErrorCode.VALUE_RULE_VIOLATION,
                    cfg.getLabel() + " tidak dapat divalidasi. Hubungi penjual.", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw single(cfg, FormConfigErrorCode.VALUE_RULE_VIOLATION,
                    cfg.getLabel() + " tidak dapat divalidasi.", null);
        } catch (ExecutionException e) {
            log.warn("Pola validasi gagal dievaluasi. fieldKey={}", cfg.getFieldKey(), e);
            throw single(cfg, FormConfigErrorCode.VALUE_RULE_VIOLATION,
                    cfg.getLabel() + " tidak dapat divalidasi.", null);
        }
    }

    /**
     * Perlakuan "kosong". Angka {@code 0} dan boolean {@code false} adalah nilai
     * <b>terisi</b> — pemeriksaan berbasis truthiness akan membuang keduanya.
     */
    private boolean isEmpty(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return s.trim().isEmpty();
        }
        if (v instanceof Collection<?> c) {
            return c.isEmpty();
        }
        return false;
    }

    private ValidationException single(ProdukFormConfig cfg, String code, String message,
                                       Map<String, Object> meta) {
        return new ValidationException(new ErrorDto(cfg.getFieldKey(), message, code, meta));
    }

    private ErrorDto err(String field, String code, String message) {
        return new ErrorDto(field, message, code, null);
    }
}
