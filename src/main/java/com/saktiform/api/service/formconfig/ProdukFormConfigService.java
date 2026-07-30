package com.saktiform.api.service.formconfig;

import com.saktiform.api.entity.Produk;
import com.saktiform.api.entity.ProdukFormConfig;
import com.saktiform.api.model.ErrorDto;
import com.saktiform.api.model.product.ProdukFormConfigDto;
import com.saktiform.api.model.product.formconfig.*;
import com.saktiform.api.repository.OrderCustomFieldRepository;
import com.saktiform.api.repository.ProdukFormConfigRepository;
import com.saktiform.api.repository.ProdukRepository;
import com.saktiform.api.util.NotFoundException;
import com.saktiform.api.util.TextSanitizer;
import com.saktiform.api.util.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Logika inti fitur Konfigurasi Form Produk: seeding, self-healing, pembacaan,
 * dan penyimpanan konfigurasi.
 */
@Service
public class ProdukFormConfigService {

    private static final Logger log = LoggerFactory.getLogger(ProdukFormConfigService.class);

    /** Atribut yang boleh diubah pada System Field. */
    private static final List<String> SYSTEM_EDITABLE =
            List.of("label", "placeholder", "helpText", "sortOrder");

    /** Atribut yang boleh diubah pada Custom Field yang belum dipakai order. */
    private static final List<String> CUSTOM_EDITABLE = List.of(
            "label", "placeholder", "helpText", "sortOrder",
            "isRequired", "isActive", "defaultValue", "options", "validation", "fieldType");

    private final ProdukFormConfigRepository repository;
    private final OrderCustomFieldRepository orderCustomFieldRepository;
    private final ProdukRepository produkRepository;
    private final FormConfigValidator validator;
    private final FieldKeyGenerator keyGenerator;

    public ProdukFormConfigService(ProdukFormConfigRepository repository,
                                   OrderCustomFieldRepository orderCustomFieldRepository,
                                   ProdukRepository produkRepository,
                                   FormConfigValidator validator,
                                   FieldKeyGenerator keyGenerator) {
        this.repository = repository;
        this.orderCustomFieldRepository = orderCustomFieldRepository;
        this.produkRepository = produkRepository;
        this.validator = validator;
        this.keyGenerator = keyGenerator;
    }

    // ────────────────────────────── Seeding & self-healing ──────────────────────────────

    /** Membuat enam System Field untuk produk yang baru dibuat. */
    @Transactional
    public void seedSystemFields(UUID idProduk) {
        Instant now = Instant.now();
        List<ProdukFormConfig> rows = Arrays.stream(SystemFormField.values())
                .map(sf -> newSystemRow(idProduk, sf, now))
                .collect(Collectors.toList());
        try {
            repository.saveAll(rows);
        } catch (DataIntegrityViolationException e) {
            // Sudah ada (mis. seeding paralel) — itu memang tujuannya. Bukan kegagalan.
            log.debug("Seeding System Field produk {} dilewati: baris sudah ada", idProduk);
        }
    }

    /**
     * Melengkapi System Field yang belum ada pada sebuah produk.
     *
     * <p>Dipanggil dari jalur baca sehingga invarian "selalu ada enam System Field"
     * tidak bergantung pada keberhasilan backfill massal. Idempoten; unique index
     * {@code (id_produk, lower(field_key))} menjadi backstop bila dua permintaan
     * bersamaan sama-sama mendeteksi field yang hilang.
     */
    @Transactional
    public List<ProdukFormConfig> ensureSystemFields(UUID idProduk, List<ProdukFormConfig> existing) {
        Set<String> present = existing.stream()
                .map(ProdukFormConfig::getFieldKey)
                .filter(Objects::nonNull)
                .map(k -> k.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<ProdukFormConfig> missing = Arrays.stream(SystemFormField.values())
                .filter(sf -> !present.contains(sf.getKey()))
                .map(sf -> newSystemRow(idProduk, sf, Instant.now()))
                .collect(Collectors.toList());

        if (missing.isEmpty()) {
            return existing;
        }

        log.warn("Self-healing: menambahkan {} System Field yang hilang pada produk {}",
                missing.size(), idProduk);
        try {
            repository.saveAll(missing);
        } catch (DataIntegrityViolationException e) {
            log.debug("Self-healing produk {} bertabrakan dengan proses lain — dibaca ulang", idProduk);
            return repository.findByIdProdukOrderBySortOrderAscIdAsc(idProduk);
        }

        List<ProdukFormConfig> merged = new ArrayList<>(existing);
        merged.addAll(missing);
        merged.sort(bySortOrderThenId());
        return merged;
    }

    private ProdukFormConfig newSystemRow(UUID idProduk, SystemFormField sf, Instant now) {
        ProdukFormConfig r = new ProdukFormConfig();
        r.setIdProduk(idProduk);
        r.setFieldKey(sf.getKey());
        r.setFieldCategory(FieldCategory.SYSTEM);
        r.setFieldType(sf.getType());
        r.setLabel(sf.getDefaultLabel());
        r.setPlaceholder(sf.getDefaultPlaceholder());
        r.setHelpText(null);
        r.setIsRequired(true);
        r.setIsActive(true);
        r.setSortOrder(sf.getDefaultSortOrder());
        r.setOptions(null);
        r.setDefaultValue(null);
        r.setValidationRule(sf.defaultValidationRule());
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        return r;
    }

    // ────────────────────────────── Baca ──────────────────────────────

    /**
     * Konfigurasi lengkap untuk layar konfigurasi dashboard.
     *
     * <p>Sengaja <b>tidak</b> {@code readOnly = true}: memanggil {@link #ensureSystemFields}
     * yang menulis.
     */
    @Transactional
    public FormConfigResponse getFormConfig(UUID idProduk, Long workspaceId) {
        Produk produk = requireProdukInWorkspace(idProduk, workspaceId);

        List<ProdukFormConfig> rows = repository.findByIdProdukOrderBySortOrderAscIdAsc(idProduk);
        rows = ensureSystemFields(idProduk, rows);

        Map<String, Long> usage = safeUsageByProduk(idProduk);

        FormConfigResponse res = new FormConfigResponse();
        res.setIdProduk(idProduk);
        res.setNamaProduk(produk.getNamaProduk());
        res.setCustomFieldLimit(FormConfigValidator.MAX_ACTIVE_CUSTOM_FIELDS);
        res.setFields(rows.stream().map(r -> toConfigDto(r, usage)).collect(Collectors.toList()));
        res.setTotalField(res.getFields().size());
        res.setTotalCustomFieldActive((int) rows.stream()
                .filter(r -> r.getFieldCategory() == FieldCategory.CUSTOM)
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .count());
        return res;
    }

    /** Daftar field untuk layar detail produk (dashboard). */
    @Transactional
    public List<FormFieldConfigDto> getAdminConfigList(UUID idProduk) {
        List<ProdukFormConfig> rows = repository.findByIdProdukOrderBySortOrderAscIdAsc(idProduk);
        rows = ensureSystemFields(idProduk, rows);
        Map<String, Long> usage = safeUsageByProduk(idProduk);
        return rows.stream().map(r -> toConfigDto(r, usage)).collect(Collectors.toList());
    }

    /**
     * Daftar field aktif untuk halaman checkout publik — sudah tersaring dan terurut,
     * sehingga klien cukup merender sesuai urutan larik.
     */
    @Transactional
    public List<FormFieldCheckoutDto> getActiveCheckoutConfig(UUID idProduk) {
        List<ProdukFormConfig> all = repository.findByIdProdukOrderBySortOrderAscIdAsc(idProduk);
        all = ensureSystemFields(idProduk, all);
        return all.stream()
                .filter(r -> !Boolean.FALSE.equals(r.getIsActive()))
                .sorted(bySortOrderThenId())
                .map(this::toCheckoutDto)
                .collect(Collectors.toList());
    }

    /** Konfigurasi aktif sebagai entity — dipakai validator nilai saat submit order. */
    @Transactional(readOnly = true)
    public List<ProdukFormConfig> findActiveConfig(UUID idProduk) {
        return repository.findByIdProdukAndIsActiveTrueOrderBySortOrderAscIdAsc(idProduk);
    }

    // ────────────────────────────── Simpan ──────────────────────────────

    /**
     * Menyimpan keseluruhan konfigurasi: upsert by field key, hapus yang hilang dari
     * payload (bila diizinkan), lalu normalkan {@code sortOrder} menjadi 1..N.
     * Seluruhnya dalam satu transaksi.
     */
    @Transactional
    public FormConfigSaveResponse saveFormConfig(UUID idProduk, Long workspaceId,
                                                 FormConfigRequest request) {
        requireProdukInWorkspace(idProduk, workspaceId);

        List<ProdukFormConfig> current = repository.findByIdProdukOrderBySortOrderAscIdAsc(idProduk);
        current = ensureSystemFields(idProduk, current);

        Map<String, ProdukFormConfig> byKey = current.stream()
                .filter(c -> StringUtils.hasText(c.getFieldKey()))
                .collect(Collectors.toMap(
                        c -> c.getFieldKey().toLowerCase(Locale.ROOT),
                        c -> c,
                        (a, b) -> a));

        // 1) Validasi struktural — melempar bila ada pelanggaran
        validator.validateReplace(byKey, request.getFields());

        // 2) Bangkitkan field key untuk entri baru
        Set<String> usedKeys = new HashSet<>(byKey.keySet());
        for (FormFieldRequest f : request.getFields()) {
            if (!StringUtils.hasText(f.getFieldKey())) {
                String key = keyGenerator.generate(f.getLabel(), usedKeys);
                f.setFieldKey(key);
                usedKeys.add(key);
            }
        }

        // 3) Tentukan kandidat hapus & tegakkan aturan penghapusan
        Set<String> incomingKeys = request.getFields().stream()
                .map(f -> f.getFieldKey().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> toDelete = byKey.keySet().stream()
                .filter(k -> !incomingKeys.contains(k))
                .collect(Collectors.toList());
        guardDeletable(idProduk, byKey, toDelete);

        // 4) Terapkan — sortOrder dinormalkan dari urutan entri payload
        Instant now = Instant.now();
        List<ProdukFormConfig> toSave = new ArrayList<>();
        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        int order = 1;

        for (FormFieldRequest f : request.getFields()) {
            String lower = f.getFieldKey().toLowerCase(Locale.ROOT);
            ProdukFormConfig row = byKey.get(lower);
            if (row == null) {
                row = new ProdukFormConfig();
                row.setIdProduk(idProduk);
                row.setCreatedAt(now);
                created.add(f.getFieldKey());
            } else {
                updated.add(f.getFieldKey());
            }
            applyRequest(row, f, order++);
            row.setUpdatedAt(now);
            toSave.add(row);
        }

        repository.saveAll(toSave);
        if (!toDelete.isEmpty()) {
            repository.deleteByIdProdukAndFieldKeyIn(idProduk, toDelete);
        }

        Map<String, Long> usage = safeUsageByProduk(idProduk);
        FormConfigSaveResponse res = new FormConfigSaveResponse();
        res.setIdProduk(idProduk);
        res.setCreated(created);
        res.setUpdated(updated);
        res.setDeleted(toDelete);
        res.setFields(toSave.stream().map(r -> toConfigDto(r, usage)).collect(Collectors.toList()));
        res.setTotalField(toSave.size());
        return res;
    }

    /**
     * Menyalin atribut request ke entity.
     *
     * <p>Untuk kategori SYSTEM, nilai terkunci <b>dipaksa dari enum</b> dan bukan disalin
     * dari payload — pertahanan berlapis: seandainya ada celah pada validator, jalur
     * penulisan tetap tidak dapat menghasilkan System Field yang menyimpang.
     */
    private void applyRequest(ProdukFormConfig row, FormFieldRequest f, int sortOrder) {
        row.setLabel(TextSanitizer.sanitizeSingleLine(f.getLabel()));
        row.setPlaceholder(TextSanitizer.sanitizeToNull(f.getPlaceholder()));
        row.setHelpText(TextSanitizer.sanitizeToNull(f.getHelpText()));
        row.setSortOrder(sortOrder);

        if (f.getFieldCategory() == FieldCategory.SYSTEM) {
            SystemFormField def = SystemFormField.byKey(f.getFieldKey()).orElseThrow();
            row.setFieldKey(def.getKey());
            row.setFieldCategory(FieldCategory.SYSTEM);
            row.setFieldType(def.getType());
            row.setIsRequired(true);
            row.setIsActive(true);
            row.setOptions(null);
            row.setDefaultValue(null);
            row.setValidationRule(def.defaultValidationRule());
        } else {
            row.setFieldKey(f.getFieldKey());
            row.setFieldCategory(FieldCategory.CUSTOM);
            row.setFieldType(f.getFieldType());
            row.setIsRequired(Boolean.TRUE.equals(f.getIsRequired()));
            row.setIsActive(f.getIsActive() == null || f.getIsActive());
            row.setOptions(f.getFieldType() != null && f.getFieldType().isRequiresOptions()
                    ? sanitizeOptions(f.getOptions())
                    : null);
            row.setDefaultValue(TextSanitizer.sanitizeToNull(f.getDefaultValue()));
            ValidationRuleDto rule = f.getValidation();
            row.setValidationRule(rule == null || rule.isEmpty() ? null : rule);
        }
    }

    private List<OptionDto> sanitizeOptions(List<OptionDto> options) {
        if (options == null) {
            return null;
        }
        return options.stream()
                .filter(Objects::nonNull)
                .map(o -> new OptionDto(
                        TextSanitizer.sanitizeSingleLine(o.getLabel()),
                        TextSanitizer.sanitizeSingleLine(o.getValue())))
                .collect(Collectors.toList());
    }

    /**
     * Menegakkan: System Field tidak boleh dihapus, dan Custom Field yang sudah dipakai
     * order tidak boleh dihapus permanen.
     */
    private void guardDeletable(UUID idProduk, Map<String, ProdukFormConfig> byKey,
                                List<String> toDelete) {
        if (toDelete.isEmpty()) {
            return;
        }
        List<ErrorDto> errors = new ArrayList<>();

        for (String k : toDelete) {
            if (SystemFormField.isSystemKey(k)) {
                errors.add(new ErrorDto("fields",
                        "System Field '" + k + "' tidak dapat dihapus.",
                        FormConfigErrorCode.SYSTEM_FIELD_NOT_DELETABLE,
                        Map.of("fieldKey", k)));
            }
        }

        // Satu kueri agregat untuk seluruh kandidat — bukan count per field di dalam loop
        Map<String, Long> usage = orderCustomFieldRepository
                .countUsageByProdukAndFieldKeys(idProduk, toDelete).stream()
                .collect(Collectors.toMap(
                        OrderCustomFieldRepository.FieldUsageProjection::getFieldKey,
                        OrderCustomFieldRepository.FieldUsageProjection::getUsageCount));

        for (String k : toDelete) {
            Long used = usage.get(k);
            if (used != null && used > 0) {
                ProdukFormConfig row = byKey.get(k);
                String label = row != null && StringUtils.hasText(row.getLabel()) ? row.getLabel() : k;
                errors.add(new ErrorDto(k,
                        "Field '" + label + "' sudah dipakai oleh " + used + " pesanan sehingga "
                                + "tidak dapat dihapus. Nonaktifkan field bila Anda tidak ingin "
                                + "menampilkannya lagi.",
                        FormConfigErrorCode.FIELD_IN_USE,
                        Map.of("usageCount", used, "suggestedAction", "DEACTIVATE")));
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    // ────────────────────────────── Salin & jalur inline ──────────────────────────────

    /**
     * Menyalin seluruh konfigurasi ke produk hasil duplikasi.
     *
     * <p>Konfigurasi target dibersihkan lebih dahulu karena produk hasil duplikasi baru
     * saja menerima seeding enam System Field — tanpa pembersihan, penyalinan akan
     * bertabrakan dengan unique index {@code (id_produk, lower(field_key))}. Aman karena
     * produk target baru dibuat dan belum memiliki order.
     */
    @Transactional
    public void copyFormConfig(UUID sourceIdProduk, UUID targetIdProduk) {
        List<ProdukFormConfig> source =
                repository.findByIdProdukOrderBySortOrderAscIdAsc(sourceIdProduk);
        if (source.isEmpty()) {
            return; // target sudah punya hasil seeding; tidak ada yang perlu disalin
        }

        List<ProdukFormConfig> existingTarget =
                repository.findByIdProdukOrderBySortOrderAscIdAsc(targetIdProduk);
        if (!existingTarget.isEmpty()) {
            repository.deleteAll(existingTarget);
            repository.flush();
        }

        Instant now = Instant.now();
        List<ProdukFormConfig> copies = new ArrayList<>();
        int order = 1;
        for (ProdukFormConfig s : source) {
            ProdukFormConfig c = new ProdukFormConfig();
            c.setIdProduk(targetIdProduk);
            c.setFieldKey(s.getFieldKey());
            c.setFieldCategory(s.getFieldCategory());
            c.setTipeField(s.getTipeField());
            c.setLabel(s.getLabel());
            c.setPlaceholder(s.getPlaceholder());
            c.setHelpText(s.getHelpText());
            c.setIsRequired(s.getIsRequired());
            c.setIsActive(s.getIsActive());
            c.setSortOrder(order++);
            c.setOptions(s.getOptions());
            c.setDefaultValue(s.getDefaultValue());
            c.setValidationRule(s.getValidationRule());
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            copies.add(c);
        }
        repository.saveAll(copies);
    }

    /**
     * Menerapkan konfigurasi form yang dikirim bersamaan dengan {@code POST /produk}.
     *
     * <p><b>Semantik merge, bukan replace.</b> Payload hanya menambah dan memperbarui;
     * tidak pernah menghapus. Penghapusan field hanya melalui {@link #saveFormConfig},
     * yang memiliki guard "System Field tidak dapat dihapus" dan "Custom Field yang sudah
     * dipakai tidak dapat dihapus permanen". Dengan begitu, menyimpan produk dari layar
     * yang tidak memuat seluruh field tidak akan pernah menghilangkan konfigurasi.
     *
     * <p><b>Pencocokan entri</b> dilakukan berurutan:
     * <ol>
     *   <li>{@code fieldKey} diisi → cocokkan berdasarkan kunci;</li>
     *   <li>kategori {@code SYSTEM} tanpa kunci → cocokkan label terhadap System Field;</li>
     *   <li>kategori kosong (payload lama) → cocokkan label terhadap field mana pun;</li>
     *   <li>kategori {@code CUSTOM} → cocokkan label terhadap Custom Field saja;</li>
     *   <li>tidak ada yang cocok → dibuat sebagai Custom Field baru.</li>
     * </ol>
     *
     * <p><b>Urutan akhir</b> ditentukan oleh nilai urutan efektif tiap field:
     * {@code sortOrder} eksplisit bila dikirim; bila tidak, entri payload ditempatkan
     * setelah seluruh field existing sambil mempertahankan urutan pengirimannya; field
     * yang tidak disebut payload memakai urutannya saat ini. Pada nilai yang sama, entri
     * payload menang. Hasilnya lalu dinormalkan menjadi 1..N.
     *
     * <p>Konsekuensinya: klien yang mengirim daftar lengkap memperoleh urutan persis
     * seperti yang dikirim; klien yang hanya mengirim Custom Field memperoleh field
     * tersebut setelah System Field; dan klien lama yang mengirim sebagian field beserta
     * {@code order} tetap memperoleh posisi yang ia minta.
     */
    @Transactional
    public void applyFormConfigOnSave(UUID idProduk, List<FormFieldRequest> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }

        List<ProdukFormConfig> current = repository.findByIdProdukOrderBySortOrderAscIdAsc(idProduk);
        current = ensureSystemFields(idProduk, current);

        Map<String, ProdukFormConfig> byKey = new HashMap<>();
        Map<String, ProdukFormConfig> byLabel = new HashMap<>();
        for (ProdukFormConfig c : current) {
            if (StringUtils.hasText(c.getFieldKey())) {
                byKey.put(c.getFieldKey().toLowerCase(Locale.ROOT), c);
            }
            byLabel.putIfAbsent(keyGenerator.normalizeLabel(c.getLabel()), c);
        }

        // 1) Tentukan pasangan entri payload -> baris existing (atau null = field baru)
        Set<String> usedKeys = new HashSet<>(byKey.keySet());
        List<ProdukFormConfig> matchedRows = new ArrayList<>();
        List<FormFieldRequest> resolved = new ArrayList<>();

        for (FormFieldRequest f : incoming) {
            if (f == null || !StringUtils.hasText(f.getLabel())) {
                continue;
            }
            ProdukFormConfig match = resolveMatch(f, byKey, byLabel);

            if (match != null) {
                f.setFieldKey(match.getFieldKey());
                f.setFieldCategory(match.getFieldCategory());
                if (match.getFieldCategory() == FieldCategory.CUSTOM && f.getFieldType() == null) {
                    f.setFieldType(match.getFieldType());
                }
            } else {
                f.setFieldCategory(FieldCategory.CUSTOM);
                if (f.getFieldType() == null) {
                    f.setFieldType(FormFieldType.TEXT);
                }
                String key = keyGenerator.generate(f.getLabel(), usedKeys);
                f.setFieldKey(key);
                usedKeys.add(key);
            }
            matchedRows.add(match);
            resolved.add(f);
        }

        if (resolved.isEmpty()) {
            return;
        }

        // 2) Validasi (mode merge — kelengkapan System Field tidak diperiksa)
        validator.validateMerge(resolved);

        // 3) Susun urutan akhir (lihat aturan pada Javadoc)
        Set<String> mentioned = resolved.stream()
                .map(f -> f.getFieldKey().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<ProdukFormConfig> untouched = current.stream()
                .filter(c -> c.getFieldKey() == null
                        || !mentioned.contains(c.getFieldKey().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        int maxExisting = current.stream()
                .map(ProdukFormConfig::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        List<OrderedField> ordered = new ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            FormFieldRequest f = resolved.get(i);
            // Tanpa sortOrder eksplisit, entri payload ditempatkan setelah field existing
            // sambil mempertahankan urutan pengirimannya.
            int key = f.getSortOrder() != null ? f.getSortOrder() : maxExisting + 1 + i;
            ordered.add(new OrderedField(matchedRows.get(i), f, key, true, i));
        }
        for (ProdukFormConfig c : untouched) {
            int key = c.getSortOrder() == null ? Integer.MAX_VALUE : c.getSortOrder();
            ordered.add(new OrderedField(c, null, key, false,
                    c.getId() == null ? 0 : c.getId().intValue()));
        }
        // Pada nilai urutan yang sama, entri payload menang — pengguna baru saja
        // menyatakan maksudnya secara eksplisit.
        ordered.sort(Comparator
                .comparingInt((OrderedField o) -> o.sortKey)
                .thenComparing(o -> o.fromPayload ? 0 : 1)
                .thenComparingInt(o -> o.tieBreak));

        Instant now = Instant.now();
        List<ProdukFormConfig> toSave = new ArrayList<>();
        int order = 1;
        for (OrderedField o : ordered) {
            ProdukFormConfig row = o.row;
            if (o.request == null) {
                row.setSortOrder(order++);
                row.setUpdatedAt(now);
            } else {
                if (row == null) {
                    row = new ProdukFormConfig();
                    row.setIdProduk(idProduk);
                    row.setCreatedAt(now);
                }
                applyRequest(row, o.request, order++);
                row.setUpdatedAt(now);
            }
            toSave.add(row);
        }

        // 4) Batas jumlah Custom Field aktif dinilai atas keadaan akhir
        long activeCustom = toSave.stream()
                .filter(r -> r.getFieldCategory() == FieldCategory.CUSTOM)
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .count();
        validator.validateActiveCustomCount(activeCustom);

        repository.saveAll(toSave);
    }

    /** Entri sementara untuk menyusun urutan akhir pada {@link #applyFormConfigOnSave}. */
    private static final class OrderedField {
        final ProdukFormConfig row;
        final FormFieldRequest request;   // null = field existing yang tidak disebut payload
        final int sortKey;
        final boolean fromPayload;
        final int tieBreak;

        OrderedField(ProdukFormConfig row, FormFieldRequest request, int sortKey,
                     boolean fromPayload, int tieBreak) {
            this.row = row;
            this.request = request;
            this.sortKey = sortKey;
            this.fromPayload = fromPayload;
            this.tieBreak = tieBreak;
        }
    }

    /** Lihat aturan pencocokan pada {@link #applyFormConfigOnSave}. */
    private ProdukFormConfig resolveMatch(FormFieldRequest f,
                                          Map<String, ProdukFormConfig> byKey,
                                          Map<String, ProdukFormConfig> byLabel) {
        if (StringUtils.hasText(f.getFieldKey())) {
            return byKey.get(f.getFieldKey().trim().toLowerCase(Locale.ROOT));
        }
        String label = keyGenerator.normalizeLabel(f.getLabel());

        if (f.getFieldCategory() == FieldCategory.SYSTEM) {
            return SystemFormField.matchByLabel(label)
                    .map(sf -> byKey.get(sf.getKey()))
                    .orElse(null);
        }
        ProdukFormConfig candidate = byLabel.get(label);
        if (candidate == null) {
            return null;
        }
        // Kategori CUSTOM eksplisit tidak boleh menabrak System Field yang berlabel sama.
        if (f.getFieldCategory() == FieldCategory.CUSTOM
                && candidate.getFieldCategory() == FieldCategory.SYSTEM) {
            return null;
        }
        return candidate;
    }

    // ────────────────────────────── Pemetaan & util ──────────────────────────────

    private FormFieldConfigDto toConfigDto(ProdukFormConfig r, Map<String, Long> usage) {
        boolean isSystem = r.getFieldCategory() == FieldCategory.SYSTEM;
        Long used = usage == null ? null : usage.getOrDefault(r.getFieldKey(), 0L);

        List<String> editable;
        if (isSystem) {
            editable = SYSTEM_EDITABLE;
        } else if (used != null && used > 0) {
            // Mengubah tipe field yang sudah punya data historis membuat nilai lama
            // tidak dapat dirender dengan benar — konsekuensi logis dari snapshot.
            editable = CUSTOM_EDITABLE.stream()
                    .filter(a -> !"fieldType".equals(a))
                    .collect(Collectors.toList());
        } else {
            editable = CUSTOM_EDITABLE;
        }

        FormFieldConfigDto dto = new FormFieldConfigDto();
        dto.setFieldKey(r.getFieldKey());
        dto.setFieldCategory(r.getFieldCategory());
        dto.setFieldType(r.getFieldType());
        dto.setLabel(r.getLabel());
        dto.setPlaceholder(r.getPlaceholder());
        dto.setHelpText(r.getHelpText());
        dto.setIsRequired(Boolean.TRUE.equals(r.getIsRequired()));
        dto.setIsActive(!Boolean.FALSE.equals(r.getIsActive()));
        dto.setDefaultValue(r.getDefaultValue());
        dto.setOptions(r.getOptions());
        dto.setSortOrder(r.getSortOrder());
        dto.setValidation(effectiveValidationRule(r));
        dto.setDataSource(SystemFormField.byKey(r.getFieldKey())
                .map(SystemFormField::getDataSource).orElse(null));
        dto.setUsageCount(used);
        dto.setEditableAttributes(editable);
        dto.setDeletable(!isSystem && used != null && used == 0);
        return dto;
    }

    private FormFieldCheckoutDto toCheckoutDto(ProdukFormConfig r) {
        FormFieldCheckoutDto dto = new FormFieldCheckoutDto();
        dto.setFieldKey(r.getFieldKey());
        dto.setFieldCategory(r.getFieldCategory());
        dto.setFieldType(r.getFieldType());
        dto.setLabel(r.getLabel());
        dto.setPlaceholder(r.getPlaceholder());
        dto.setHelpText(r.getHelpText());
        dto.setIsRequired(Boolean.TRUE.equals(r.getIsRequired()));
        dto.setDefaultValue(r.getDefaultValue());
        dto.setOptions(r.getOptions());
        dto.setSortOrder(r.getSortOrder());
        dto.setValidation(effectiveValidationRule(r));
        dto.setDataSource(SystemFormField.byKey(r.getFieldKey())
                .map(SystemFormField::getDataSource).orElse(null));
        return dto;
    }

    /**
     * Aturan validasi efektif sebuah field.
     *
     * <p>Untuk System Field aturannya <b>selalu</b> berasal dari {@link SystemFormField},
     * bukan dari kolom — nilainya terkunci, dan baris hasil seeding lewat skrip migrasi
     * tidak mengisi kolom {@code validation_rule}. Membacanya dari enum membuat seluruh
     * produk konsisten tanpa bergantung pada jalur mana yang membuat barisnya.
     */
    private ValidationRuleDto effectiveValidationRule(ProdukFormConfig r) {
        if (r.getFieldCategory() == FieldCategory.SYSTEM) {
            return SystemFormField.byKey(r.getFieldKey())
                    .map(SystemFormField::defaultValidationRule)
                    .orElse(r.getValidationRule());
        }
        return r.getValidationRule();
    }

    /**
     * usageCount seluruh field satu produk. Kegagalan agregasi tidak boleh menggagalkan
     * pembacaan konfigurasi — {@code usageCount} bersifat informatif.
     *
     * @return {@code null} bila gagal dihitung
     */
    private Map<String, Long> safeUsageByProduk(UUID idProduk) {
        try {
            return orderCustomFieldRepository.countUsageByProduk(idProduk).stream()
                    .collect(Collectors.toMap(
                            OrderCustomFieldRepository.FieldUsageProjection::getFieldKey,
                            OrderCustomFieldRepository.FieldUsageProjection::getUsageCount));
        } catch (Exception e) {
            log.warn("Gagal menghitung usageCount untuk produk {} — dilewati", idProduk, e);
            return null;
        }
    }

    private Produk requireProdukInWorkspace(UUID idProduk, Long workspaceId) {
        Produk p = produkRepository.findById(idProduk)
                .orElseThrow(() -> new NotFoundException("Produk tidak ditemukan."));
        // Pesan identik untuk "tidak ada" dan "milik tenant lain" — 404, bukan 403.
        if (workspaceId != null && !Objects.equals(p.getIdWorkspace(), workspaceId)) {
            throw new NotFoundException("Produk tidak ditemukan.");
        }
        if (Boolean.TRUE.equals(p.getIsDeleted())) {
            throw new NotFoundException("Produk tidak ditemukan.");
        }
        return p;
    }

    private Comparator<ProdukFormConfig> bySortOrderThenId() {
        return Comparator
                .comparing(ProdukFormConfig::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProdukFormConfig::getId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
