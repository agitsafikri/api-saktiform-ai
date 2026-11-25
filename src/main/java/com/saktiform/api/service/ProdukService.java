package com.saktiform.api.service;

import com.saktiform.api.entity.*;
import com.saktiform.api.model.ErrorDto;
import com.saktiform.api.model.Order.JenisPembayaran;
import com.saktiform.api.model.PlatformIklan;
import com.saktiform.api.model.product.*;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.formconfig.ProdukFormConfigService;
import com.saktiform.api.service.order.CodFeeCalculator;
import com.saktiform.api.util.ValidationException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProdukService {
    private final ProdukRepository produkRepository;
    private final FiturProdukRepository fiturProdukRepository;
    private final ProdukPembayaranRepository produkPembayaranRepository;
    private final ProdukEkstraRepository produkEkstraRepository;
    private final ProdukTestimoniRepository produkTestimoniRepository;
    private final AtributProdukRepository atributProdukRepository;
    private final GambarProdukRepository gambarProdukRepository;
    private final GudangRepository gudangRepository;
    private final ProdukIklanRepository produkIklanRepository;
    private final WorkspaceRepository workspaceRepository;
    private final DomainRepository domainRepository;
    private final StorageService storageService;
    private final ProdukFormConfigService produkFormConfigService;




    @Value("${saktiform.api.checkout.url}")
    private String checkoutUrl;

    public ProdukService(ProdukRepository produkRepository,
                         FiturProdukRepository fiturProdukRepository,
                         ProdukPembayaranRepository produkPembayaranRepository,
                         ProdukEkstraRepository produkEkstraRepository,
                         ProdukTestimoniRepository produkTestimoniRepository,
                         GambarProdukRepository gambarProdukRepository,
                         AtributProdukRepository atributProdukRepository,
                         GudangRepository gudangRepository,
                         ProdukIklanRepository produkIklanRepository,
                         WorkspaceRepository workspaceRepository,
                         StorageService storageService,
                         DomainRepository domainRepository,
                         ProdukFormConfigService produkFormConfigService) {
        this.produkRepository = produkRepository;
        this.fiturProdukRepository = fiturProdukRepository;
        this.produkPembayaranRepository = produkPembayaranRepository;
        this.produkEkstraRepository = produkEkstraRepository;
        this.produkTestimoniRepository = produkTestimoniRepository;
        this.gambarProdukRepository = gambarProdukRepository;
        this.atributProdukRepository = atributProdukRepository;
        this.gudangRepository = gudangRepository;
        this.produkIklanRepository = produkIklanRepository;
        this.workspaceRepository = workspaceRepository;
        this.domainRepository = domainRepository;
        this.storageService = storageService;
        this.produkFormConfigService = produkFormConfigService;
    }


    /**
     * List produk workspace. Rentang tanggal order masuk ({@code tanggalAwalOrder} /
     * {@code tanggalAkhirOrder}, boleh salah satu) hanya memengaruhi angka statistik
     * (totalOrder, totalDibayar, totalTerjual, rasioDibayar) — daftar, urutan, dan jumlah
     * halaman produk tidak berubah. Tanpa rentang, statistik diambil dari counter produk.
     */
    public Page<ProdukListDto> getProdukByWorkspace(Long idWorkspace, Integer page, Integer limit, String search,
                                                    LocalDateTime tanggalAwalOrder, LocalDateTime tanggalAkhirOrder){
        if (tanggalAwalOrder != null && tanggalAkhirOrder != null && tanggalAwalOrder.isAfter(tanggalAkhirOrder)) {
            throw new IllegalArgumentException("tanggalAwalOrder tidak boleh setelah tanggalAkhirOrder.");
        }

        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ProdukListDto> listProduk;

        if (search != null && !search.isEmpty()) {
             listProduk = produkRepository.findAllProdukListDtoSearch(idWorkspace, search.toLowerCase(), pageable);


        }else {
            listProduk = produkRepository.findAllProdukListDto(idWorkspace, pageable);
        }

        if (listProduk.isEmpty()) {
            return listProduk;
        }

        // Gambar pertama tiap produk diambil sekaligus untuk satu halaman (anti N+1).
        var produkIds = listProduk.getContent().stream().map(ProdukListDto::getId).toList();

        // Statistik untuk rentang tanggal order masuk: satu query agregat untuk satu halaman.
        // Produk tanpa order dalam rentang tidak ada di hasil → 0 / "0%".
        if (tanggalAwalOrder != null || tanggalAkhirOrder != null) {
            Map<UUID, ProdukOrderStatistikView> statistik = new HashMap<>();
            for (var row : produkRepository.getOrderStatistikByProduk(produkIds, tanggalAwalOrder, tanggalAkhirOrder)) {
                statistik.put(row.getIdProduk(), row);
            }
            listProduk.forEach(data -> {
                var row = statistik.get(data.getId());
                data.applyStatistik(row == null ? 0L : row.getTotalOrder(), row == null ? 0L : row.getTotalDibayar());
            });
        }

        Map<UUID, String> gambarPertama = new HashMap<>();
        for (var gambar : gambarProdukRepository.findByIdProdukInOrderByIdAsc(produkIds)) {
            gambarPertama.putIfAbsent(gambar.getIdProduk(), gambar.getUrlGambar());
        }

        // Base URL checkout sama untuk semua produk di workspace: cukup dihitung sekali.
        var workspace = workspaceRepository.findById(idWorkspace).get();
        String baseCheckoutUrl = workspace.getIdDomain() == null
                ? checkoutUrl
                : domainRepository.findById(workspace.getIdDomain()).get().getDomain();
        String checkoutPrefix = baseCheckoutUrl.endsWith("/") ? baseCheckoutUrl : baseCheckoutUrl + "/";

        listProduk.forEach(data -> {
            var urlGambar = gambarPertama.get(data.getId());
            if (urlGambar != null){
                data.setGambarProduk(storageService.getProdukPublicUrl(urlGambar));
            }

            data.setUrlCheckout(checkoutPrefix + data.getUrlCheckout());
        });

        return listProduk;


    }

    /** Batas atas {@code limit} untuk dropdown produk berpaginasi. */
    public static final int MAX_DROPDOWN_LIMIT = 100;

    private static final Sort DROPDOWN_SORT = Sort.by("namaProduk", "id");

    /**
     * Dropdown produk workspace, difilter nama (mengandung, tanpa peka huruf).
     *
     * <p>Tanpa {@code page}: mengembalikan seluruh hasil sebagai {@code List} — bentuk lama,
     * dipakai ModalEditPesanan. Dengan {@code page}: mengembalikan {@code Page} berukuran
     * {@code limit} (default 10, maks {@link #MAX_DROPDOWN_LIMIT}).
     */
    public Object getProdukListDropdown(Long idWorkspace, String search, Integer page, Integer limit){
        String keyword = search == null ? "" : search.trim();

        if (page == null) {
            return produkRepository.findAllProdukListDropdown(idWorkspace, keyword, DROPDOWN_SORT);
        }

        int size = limit == null ? 10 : limit;
        if (page < 1) {
            throw new IllegalArgumentException("page minimal 1.");
        }
        if (size < 1 || size > MAX_DROPDOWN_LIMIT) {
            throw new IllegalArgumentException("limit harus antara 1 dan " + MAX_DROPDOWN_LIMIT + ".");
        }
        return produkRepository.findPageProdukListDropdown(idWorkspace, keyword,
                PageRequest.of(page - 1, size, DROPDOWN_SORT));
    }

    /** Validasi config biaya COD pada setiap entri pembayaran COD (docs/prd/cod-fee.md §8). */
    private void validatePembayaranConfig(AddProdukDto data) {
        if (data.getPembayaran() == null) {
            return;
        }
        List<ErrorDto> errors = new ArrayList<>();
        for (int i = 0; i < data.getPembayaran().size(); i++) {
            var pembayaran = data.getPembayaran().get(i);
            if (pembayaran != null && JenisPembayaran.COD.name().equalsIgnoreCase(pembayaran.getTipe())) {
                errors.addAll(CodFeeCalculator.validateConfig(
                        "pembayaran[" + i + "].config", pembayaran.getConfig()));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    @Transactional
    public Object saveProduct(AddProdukDto data){
        // Validasi config biaya COD sebelum penulisan apa pun.
        validatePembayaranConfig(data);

        Produk produk;
        boolean isNewProduct = (data.getId() == null);
        if (data.getId() != null){
            produk = produkRepository.findById(data.getId()).get();
            fiturProdukRepository.deleteAllByIdProduk(produk.getId());
            atributProdukRepository.updateIsDeletedByIdProduk(true, produk.getId());
            produkPembayaranRepository.deleteAllByIdProduk(produk.getId());
            gambarProdukRepository.deleteGambarProdukByIdProduk(produk.getId());
            // Konfigurasi form SENGAJA tidak dihapus di sini. Pola delete-and-reinsert
            // membuat seluruh konfigurasi — termasuk System Field — lenyap ketika produk
            // disimpan dari layar yang tidak memuat formConfig. Penghapusan field hanya
            // lewat PUT /produk/{id}/form-config yang memiliki guard lengkap.
            produkEkstraRepository.deleteProdukEkstrasByIdProduk(produk.getId());
            produkTestimoniRepository.deleteProdukTestimoniByIdProduk(produk.getId());
        }else {
            produk = new Produk();
            produk.setCreatedAt(Instant.now());
            produk.setIsDeleted(false);
            produk.setOrderCount(0L);
            produk.setSoldCount(0L);
        }

        produk.setIdWorkspace(data.getIdWorkspace());
        produk.setNamaProduk(data.getNamaProduk());
        produk.setUrlCheckout(data.getUrlCheckout());




        produk.setIdGudang(data.getIdGudang());
        produk.setNarasiTombol(data.getNarasiTombol());

        if (data.getFacebookPixelId() != null &&
                !data.getFacebookPixelId().trim().isEmpty()){
            var produkIklan = produkIklanRepository.getProdukIklanByPlatformIklanAndIdIklan(PlatformIklan.FACEBOOK.name(), data.getFacebookPixelId().trim());
            if (produkIklan.isEmpty()){
                ProdukIklan produkIklanNew = new ProdukIklan();
                produkIklanNew.setPlatformIklan(PlatformIklan.FACEBOOK.name());
                produkIklanNew.setIdIklan(data.getFacebookPixelId().trim());
                produkIklanNew.setWorkspaceId(data.getIdWorkspace());
                produkIklanNew.setCreatedAt(Instant.now());
                produkIklanRepository.save(produkIklanNew);

                produk.setFacebookPixel(produkIklanNew.getId());
            }else {
                produk.setFacebookPixel(produkIklan.get(0).getId());
            }
        }else {
            produk.setFacebookPixel(null);
        }

        if (data.getGoogleGtmId()!=null && !data.getGoogleGtmId().trim().isEmpty()){
            var produkIklan = produkIklanRepository.getProdukIklanByPlatformIklanAndIdIklan(PlatformIklan.GOOGLE.name(), data.getGoogleGtmId().trim());
            if (produkIklan.isEmpty()){
                ProdukIklan produkIklanNew = new ProdukIklan();
                produkIklanNew.setPlatformIklan(PlatformIklan.GOOGLE.name());
                produkIklanNew.setIdIklan(data.getGoogleGtmId().trim());
                produkIklanNew.setWorkspaceId(data.getIdWorkspace());
                produkIklanNew.setCreatedAt(Instant.now());
                produkIklanRepository.save(produkIklanNew);

                produk.setGoogleGtm(produkIklanNew.getId());
            }else {
                produk.setGoogleGtm(produkIklan.getFirst().getId());
            }
        }else {
            produk.setGoogleGtm(null);
        }

        produk.setEmbededCheckoutScript(data.getEmbededCheckoutScript() != null ? data.getEmbededCheckoutScript() : null);
        produk.setEmbededPurchaseScript(data.getEmbededPurchaseScript() != null ? data.getEmbededPurchaseScript() : null);

        // Setelan tampilan checkout — null diperlakukan sebagai false (perilaku sebelum
        // fitur ini), sehingga klien lama yang tidak mengirimnya tidak berubah perilaku.
        produk.setHideFormLabel(Boolean.TRUE.equals(data.getHideFormLabel()));
        produk.setHidePrice(Boolean.TRUE.equals(data.getHidePrice()));

        produk.setUpdatedAt(Instant.now());

        var savedProduk = produkRepository.save(produk);


        for (var dataFitur : data.getPoinFitur()){
            var fiturProduk = new FiturProduk();
            fiturProduk.setIdProduk(savedProduk.getId());
            fiturProduk.setDeskripsi(dataFitur);


            fiturProdukRepository.save(fiturProduk);
        }



        for (var dataAtribut : data.getAtributProduk()){
            AtributProduk atributProduk;
            if(dataAtribut.getId() == null) {
               atributProduk = new AtributProduk();
               atributProduk.setUpdatedAt(Instant.now());
            }else {
                atributProduk = atributProdukRepository.findById(dataAtribut.getId()).get();
                atributProduk.setCreatedAt(Instant.now());
            }

            atributProduk.setId(dataAtribut.getId());
            atributProduk.setDeskripsi(dataAtribut.getDeskripsi());
            atributProduk.setHarga(dataAtribut.getHarga());
            atributProduk.setBerat(dataAtribut.getBerat());
            atributProduk.setIsDeleted(false);
            atributProduk.setIdProduk(savedProduk.getId());

            atributProdukRepository.save(atributProduk);
        }


        for (var dataPembayaran : data.getPembayaran()){
            var pembayaran = (ProdukPembayaran)produkPembayaranRepository.findProdukPembayaranByPembayaranAndIdProduk(dataPembayaran.getTipe(), savedProduk.getId());
            if (pembayaran == null) {
                pembayaran = new ProdukPembayaran();
                pembayaran.setCreatedAt(Instant.now());
            }
            pembayaran.setPembayaran(dataPembayaran.getTipe());
            pembayaran.setIdProduk(savedProduk.getId());
            pembayaran.setUpdatedAt(Instant.now());
            pembayaran.setConfig(dataPembayaran.getConfig());

            produkPembayaranRepository.save(pembayaran);
        }


        // Produk baru: enam System Field di-seed sistem, bukan dikirim klien.
        if (isNewProduct) {
            produkFormConfigService.seedSystemFields(savedProduk.getId());
        }

        // Konfigurasi form ikut disimpan di sini. Semantiknya merge: menambah dan
        // memperbarui, tidak pernah menghapus — sehingga menyimpan produk dari layar
        // yang tidak memuat seluruh field tidak menghilangkan konfigurasi. null maupun
        // larik kosong berarti "tidak ada perubahan konfigurasi".
        if (data.getFormConfig() != null && !data.getFormConfig().isEmpty()) {
            produkFormConfigService.applyFormConfigOnSave(savedProduk.getId(), data.getFormConfig());
        }


        if (data.getEkstra() != null){
            for (var dataEkstra : data.getEkstra()){
                var produkEkstra = new ProdukEkstra();
                produkEkstra.setIdProduk(savedProduk.getId());
                produkEkstra.setType(dataEkstra.getType());
                produkEkstra.setConfig(dataEkstra.getConfig());

                produkEkstraRepository.save(produkEkstra);
            }
        }


        if (data.getTestimoni() != null){
            for (var dataTestimoni : data.getTestimoni()){
                var testimoni = new ProdukTestimoni();
                testimoni.setIdProduk(savedProduk.getId());
                testimoni.setNama(dataTestimoni.getNama());
                testimoni.setPesan(dataTestimoni.getPesan());
                testimoni.setGambar(dataTestimoni.getUrlGambar() != null ? storageService.extractPathFromPublicUrl(dataTestimoni.getUrlGambar()) : null);

                produkTestimoniRepository.save(testimoni);
            }
        }


        if (data.getGambarProduk() != null){
            for (var dataGambar : data.getGambarProduk()){
                var gambar = new GambarProduk();
                gambar.setIdProduk(savedProduk.getId());
                gambar.setUrlGambar(storageService.extractPathFromPublicUrl(dataGambar));

                gambarProdukRepository.save(gambar);
            }
        }

        var workspace = workspaceRepository.findById(data.getIdWorkspace()).get();
        String produkCheckoutUrl;
        if(workspace.getIdDomain() == null){
            if(checkoutUrl.endsWith("/")){
                 produkCheckoutUrl = checkoutUrl + produk.getUrlCheckout();
            }else {
                 produkCheckoutUrl = checkoutUrl + "/" + produk.getUrlCheckout();
            }
        }else {
            var domain = domainRepository.findById(workspace.getIdDomain()).get();
            String checkoutUrlDomain = domain.getDomain();
            if(checkoutUrlDomain.endsWith("/")){
                produkCheckoutUrl = checkoutUrlDomain + produk.getUrlCheckout();
            }else {
                produkCheckoutUrl = checkoutUrlDomain + "/" + produk.getUrlCheckout();
            }
        }

        return produkCheckoutUrl;
    }


    public List<AtributProdukDto> getAtributProduk(UUID idProduk){

        return atributProdukRepository.getListAttributProduk(idProduk);
    }

    public List<String> getPembayaranProduk(UUID idProduk){

        return produkPembayaranRepository.getListPembayaranProduk(idProduk);
    }

    public ProdukDetailDto getProdukDetail(UUID idProduk){
        var produkDetail = new ProdukDetailDto();

        var produk = produkRepository.findById(idProduk).get();
        produkDetail.setId(produk.getId());
        produkDetail.setNamaProduk(produk.getNamaProduk());
        produkDetail.setUrlCheckout(produk.getUrlCheckout());

        var gambarProduk = gambarProdukRepository.findGambarProduksByIdProduk(idProduk);
        if (!gambarProduk.isEmpty()) {
            for (var data : gambarProduk){
                //produkDetail.getGambarProduk().add(apiUrl + data.getUrlGambar());
                produkDetail.getGambarProduk().add(storageService.getProdukPublicUrl(data.getUrlGambar()));
            }
        }


        var poinFitur = fiturProdukRepository.getFiturProduksByIdProduk(idProduk);
        if (!poinFitur.isEmpty()) {
            for (var data : poinFitur){
                produkDetail.getPoinFitur().add(data.getDeskripsi());
            }
        }

        var attributProduk = atributProdukRepository.getAtributProduksByIdProdukAndIsDeleted(produk.getId(), false);
        if (!attributProduk.isEmpty()) {
            for (var data : attributProduk){
                produkDetail.getAtributProduk().add(
                        new AtributProdukDto(
                                data.getId(),
                                data.getDeskripsi(),
                                data.getHarga(),
                                data.getBerat()
                        )
                );
            }
        }


        var pembayaran = produkPembayaranRepository.getProdukPembayaransByIdProduk(idProduk);
        if (!pembayaran.isEmpty()) {
            for (var data : pembayaran){
                produkDetail.getPembayaran().add(
                        new PembayaranDto(
                                data.getPembayaran(),
                                data.getConfig() != null ? data.getConfig() : null
                        )
                );
            }
        }


        if (produk.getIdGudang() != null) {
            var gudang = gudangRepository.findById(produk.getIdGudang());
            gudang.ifPresent(value -> produkDetail.setGudang(new GudangDto(
                    value.getId(),
                    value.getNamaGudang(),
                    value.getAlamat(),
                    value.getIdProvinsi(),
                    value.getIdKota(),
                    value.getIdKecamatan()
            )));
        }

        produkDetail.setFormConfig(produkFormConfigService.getAdminConfigList(idProduk));


        var ekstra = produkEkstraRepository.getProdukEkstrasByIdProduk(idProduk);
        if (!ekstra.isEmpty()) {
            for (var data : ekstra){
                produkDetail.getEkstra().add(
                        new ProdukEkstraDto(
                                data.getType(),
                                data.getConfig() != null ? data.getConfig() : null
                        )
                );
            }
        }

        var testimoni = produkTestimoniRepository.getProdukTestimoniByIdProduk(idProduk);
        if (!testimoni.isEmpty()){
            for(var data : testimoni){
//                produkDetail.getTestimoni().add(
//                        new ProdukTestimoniDto(
//                                data.getNama(),
//                                data.getPesan(),
//                                data.getGambar() != null ? apiUrl + data.getGambar() : null)
//                );
                produkDetail.getTestimoni().add(
                        new ProdukTestimoniDto(
                                data.getNama(),
                                data.getPesan(),
                                data.getGambar() != null ? storageService.getProdukPublicUrl(data.getGambar()) : null)
                );
            }
        }

        produkDetail.setNarasiTombol(produk.getNarasiTombol());

        var googleGtm = produkIklanRepository.getProdukIklanById(produk.getGoogleGtm());
        produkDetail.setIdGoogleGtmId(!googleGtm.isEmpty() ? googleGtm.getFirst().getIdIklan() : null);
        var facebookAds = produkIklanRepository.getProdukIklanById(produk.getFacebookPixel());
        produkDetail.setIdFacebookPixelId(!facebookAds.isEmpty() ? facebookAds.getFirst().getIdIklan() : null);
        produkDetail.setEmbededCheckoutScript(produk.getEmbededCheckoutScript());
        produkDetail.setEmbededPurchaseScript(produk.getEmbededPurchaseScript());
        produkDetail.setHideFormLabel(Boolean.TRUE.equals(produk.getHideFormLabel()));
        produkDetail.setHidePrice(Boolean.TRUE.equals(produk.getHidePrice()));

        return produkDetail;
    }

    public ProdukCheckoutDto getCheckoutProdukDetail(String urlCheckout){
        var produkDetail = new ProdukCheckoutDto();

        // Sebelumnya .get(0) langsung → IndexOutOfBoundsException saat URL tidak ada,
        // sehingga pesan "Produk tidak ditemukan" tidak pernah tercapai.
        var produk = produkRepository.findByUrlCheckout(urlCheckout).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Produk tidak ditemukan"));
        if (produk.getIsDeleted()) throw new RuntimeException("Produk tidak ditemukan");

        produkDetail.setId(produk.getId());
        produkDetail.setNamaProduk(produk.getNamaProduk());

        var gambarProduk = gambarProdukRepository.findGambarProduksByIdProduk(produk.getId());
        if (gambarProduk.size() > 0) {
            for (var data : gambarProduk){
                produkDetail.getGambarProduk().add(storageService.getProdukPublicUrl(data.getUrlGambar())  );
            }
        }


        var poinFitur = fiturProdukRepository.getFiturProduksByIdProduk(produk.getId());
        if (!poinFitur.isEmpty()) {
            for (var data : poinFitur){
                produkDetail.getPoinFitur().add(data.getDeskripsi());
            }
        }

        var attributProduk = atributProdukRepository.getAtributProduksByIdProdukAndIsDeleted(produk.getId(), false);

        if (!attributProduk.isEmpty()) {
            for (var data : attributProduk){
                produkDetail.getAtributProduk().add(
                        new AtributProdukDto(
                                data.getId(),
                                data.getDeskripsi(),
                                data.getHarga(),
                                data.getBerat()
                        )
                );
            }
        }


        var pembayaran = produkPembayaranRepository.getProdukPembayaransByIdProduk(produk.getId());
        if (!pembayaran.isEmpty()) {
            for (var data : pembayaran){
                produkDetail.getMetodePembayaran().add(
                        data.getPembayaran()
                );
            }
        }

        // Halaman checkout publik: hanya field aktif, sudah terurut menurut sortOrder.
        produkDetail.setFormConfig(produkFormConfigService.getActiveCheckoutConfig(produk.getId()));


        var ekstra = produkEkstraRepository.getProdukEkstrasByIdProduk(produk.getId());
        if (!ekstra.isEmpty()) {
            for (var data : ekstra){
                produkDetail.getEkstra().add(
                        new ProdukEkstraDto(
                                data.getType(),
                                data.getConfig() != null ? data.getConfig() : null
                        )
                );
            }
        }

        var testimoni = produkTestimoniRepository.getProdukTestimoniByIdProduk(produk.getId());
        if (!testimoni.isEmpty()){
            for(var data : testimoni){
                produkDetail.getTestimoni().add(
                        new ProdukTestimoniDto(
                                data.getNama(),
                                data.getPesan(),
                                data.getGambar() != null ? storageService.getProdukPublicUrl(data.getGambar())   : null)
                );
            }
        }

        produkDetail.setNarasiTombol(produk.getNarasiTombol());

        produkDetail.setEmbededCheckoutScript(produk.getEmbededCheckoutScript());
        produkDetail.setEmbededPurchaseScript(produk.getEmbededPurchaseScript());
        produkDetail.setHideFormLabel(Boolean.TRUE.equals(produk.getHideFormLabel()));
        produkDetail.setHidePrice(Boolean.TRUE.equals(produk.getHidePrice()));

        var googleGtm = produkIklanRepository.getProdukIklanById(produk.getGoogleGtm());
        var facebookAds = produkIklanRepository.getProdukIklanById(produk.getFacebookPixel());

        if(!googleGtm.isEmpty()){
            produkDetail.setIdGoogleGtmId(googleGtm.getFirst().getIdIklan());
        }
        if(!facebookAds.isEmpty()){
            produkDetail.setIdFacebookPixelId(facebookAds.getFirst().getIdIklan());
        }
        return produkDetail;
    }



    @Transactional
    public void copyProduk(UUID idProduk) throws Exception {
        var produk = produkRepository.findById(idProduk).get();

        String strProductName = produk.getNamaProduk();
        String cleanProductName = strProductName.replaceAll("(-copy)+$", "");
        var numberIdentikProdukName = produkRepository.countIdenticProductName(cleanProductName, produk.getIdWorkspace());

        String newProductName = cleanProductName + "-copy".repeat(numberIdentikProdukName );
        while (!produkRepository.findByNamaProduk(newProductName, produk.getIdWorkspace()).isEmpty()){
            numberIdentikProdukName++;
            newProductName = cleanProductName + "-copy".repeat(numberIdentikProdukName );
        }

        String strUrlCheckout = produk.getUrlCheckout();
        String cleanUrlCheckout = strUrlCheckout.replaceAll("-\\d+$", "");
        var numberIdentikProdukUrl = produkRepository.countIdenticProductUrl(cleanUrlCheckout);

        String newProductUrl = cleanUrlCheckout + "-" + (numberIdentikProdukUrl);

        while (!produkRepository.findByUrlCheckout(newProductUrl).isEmpty()){
            numberIdentikProdukUrl++;
            newProductUrl = cleanUrlCheckout + "-" + (numberIdentikProdukUrl);
        }
        String idFacebookPixel = null;
        if(produk.getFacebookPixel() != null){
            ProdukIklan produkIklan = produkIklanRepository.getProdukIklanById(produk.getFacebookPixel()).getFirst();

            idFacebookPixel = produkIklan.getIdIklan();
        }

        String googleGtm = null;
        if(produk.getGoogleGtm() != null){
            ProdukIklan produkIklan = produkIklanRepository.getProdukIklanById(produk.getGoogleGtm()).getFirst();

            googleGtm = produkIklan.getIdIklan();
        }



        AddProdukDto newProduct = new AddProdukDto(
             null,
                produk.getIdWorkspace(),
                newProductName,
                newProductUrl,
                produk.getIdGudang(),
                new ArrayList<>(),
                new ArrayList<>(),
                produk.getNarasiTombol(),
                idFacebookPixel,
                googleGtm,
                produk.getEmbededCheckoutScript(),
                produk.getEmbededCheckoutScript(),
                produk.getHideFormLabel(),
                produk.getHidePrice()
        );

        var gambarProduk = gambarProdukRepository.findGambarProduksByIdProduk(idProduk);
        if (!gambarProduk.isEmpty()) {
            for (var data : gambarProduk){
                newProduct.getGambarProduk().add(storageService.getProdukPublicUrl(storageService.copySameFolder(data.getUrlGambar())));
            }
        }


        var poinFitur = fiturProdukRepository.getFiturProduksByIdProduk(idProduk);
        if (!poinFitur.isEmpty()) {
            for (var data : poinFitur){
                newProduct.getPoinFitur().add(data.getDeskripsi());
            }
        }

        var attributProduk = atributProdukRepository.getAtributProduksByIdProdukAndIsDeleted(produk.getId(), false);
        if (!attributProduk.isEmpty()) {
            for (var data : attributProduk){
                newProduct.getAtributProduk().add(
                        new AtributProdukDto(
                                null,
                                data.getDeskripsi(),
                                data.getHarga(),
                                data.getBerat()
                        )
                );
            }
        }


        var pembayaran = produkPembayaranRepository.getProdukPembayaransByIdProduk(idProduk);
        if (!pembayaran.isEmpty()) {
            for (var data : pembayaran){
                newProduct.getPembayaran().add(
                        new PembayaranDto(
                                data.getPembayaran(),
                                // Config biaya COD lama yang tidak valid dibuang agar copy
                                // tidak gagal di validasi saveProduct (produk asal tidak diubah).
                                CodFeeCalculator.sanitizeForCopy(data.getPembayaran(), data.getConfig())
                        )
                );
            }
        }




        // Konfigurasi form disalin pada level entity setelah produk tersimpan (lihat
        // bawah). Menyalinnya lewat AddProdukDto akan kehilangan fieldKey, options,
        // isActive, dan validationRule karena ProdukFormConfigDto tidak membawanya.


        var ekstra = produkEkstraRepository.getProdukEkstrasByIdProduk(idProduk);
        if (!ekstra.isEmpty()) {
            for (var data : ekstra){
                newProduct.getEkstra().add(
                        new ProdukEkstraDto(
                                data.getType(),
                                data.getConfig() != null ? data.getConfig() : null
                        )
                );
            }
        }

        var testimoni = produkTestimoniRepository.getProdukTestimoniByIdProduk(idProduk);
        if (!testimoni.isEmpty()){
            for(var data : testimoni){
                newProduct.getTestimoni().add(
                        new ProdukTestimoniDto(
                                data.getNama(),
                                data.getPesan(),
                                data.getGambar() != null ?storageService.getProdukPublicUrl(storageService.copySameFolder(data.getGambar()))  : null)
                );
            }
        }


        saveProduct(newProduct);

        // Salin konfigurasi form lengkap (fieldKey, options, isActive, validationRule)
        // ke produk hasil duplikasi.
        var copied = produkRepository.findByUrlCheckoutAndIsDeleted(newProductUrl, Boolean.FALSE);
        if (copied != null) {
            produkFormConfigService.copyFormConfig(idProduk, copied.getId());
        }
    }

    public Produk findProdukByNamaProduk(String namaProduk, Long idWorkspace){
        return produkRepository.findByNamaProdukAndIsDeletedAndIdWorkspace(namaProduk, Boolean.FALSE, idWorkspace);
    }

    public Produk findProdukById(UUID idProduk){
        return produkRepository.findById(idProduk).get();
    }

    public Produk findProdukByUrlCheckout(String urlCheckout){
        return produkRepository.findByUrlCheckoutAndIsDeleted(urlCheckout, Boolean.FALSE);
    }

    @Transactional
    public void deleteProduk(List<UUID> idProduk){
        idProduk.forEach(id ->{
            var produk = produkRepository.findById(id).get();

            var gambarProduk = gambarProdukRepository.findGambarProduksByIdProduk(produk.getId());
            if (!gambarProduk.isEmpty()) {
                for (var data : gambarProduk){
                    storageService.removeFile(data.getUrlGambar());
                }
            }

            var testimoni = produkTestimoniRepository.getProdukTestimoniByIdProduk(produk.getId());
            if (!testimoni.isEmpty()){
                for(var data : testimoni){
                    if (data.getGambar() != null) storageService.removeFile(data.getGambar());
                }
            }


            produk.setIsDeleted(true);
            produkRepository.save(produk);
        });
    }
}
