package com.saktiform.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saktiform.api.entity.*;
import com.saktiform.api.model.MediaObject;
import com.saktiform.api.model.PlatformIklan;
import com.saktiform.api.model.product.*;
import com.saktiform.api.repository.*;
import io.minio.errors.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProdukService {
    private final ProdukRepository produkRepository;
    private final FiturProdukRepository fiturProdukRepository;
    private final ProdukPembayaranRepository produkPembayaranRepository;
    private final ProdukFormConfigRepository produkFormConfigRepository;
    private final ProdukEkstraRepository produkEkstraRepository;
    private final ProdukTestimoniRepository produkTestimoniRepository;
    private final AtributProdukRepository atributProdukRepository;
    private final GambarProdukRepository gambarProdukRepository;
    private final GudangRepository gudangRepository;
    private final ProdukIklanRepository produkIklanRepository;
    private final WorkspaceRepository workspaceRepository;
    private final DomainRepository domainRepository;
    private final StorageService storageService;




    @Value("${saktiform.api.checkout.url}")
    private String checkoutUrl;

    public ProdukService(ProdukRepository produkRepository,
                         FiturProdukRepository fiturProdukRepository,
                         ProdukPembayaranRepository produkPembayaranRepository,
                         ProdukFormConfigRepository produkFormConfigRepository,
                         ProdukEkstraRepository produkEkstraRepository,
                         ProdukTestimoniRepository produkTestimoniRepository,
                         GambarProdukRepository gambarProdukRepository,
                         AtributProdukRepository atributProdukRepository,
                         GudangRepository gudangRepository,
                         ProdukIklanRepository produkIklanRepository,
                         WorkspaceRepository workspaceRepository,
                         StorageService storageService,
                         DomainRepository domainRepository) {
        this.produkRepository = produkRepository;
        this.fiturProdukRepository = fiturProdukRepository;
        this.produkPembayaranRepository = produkPembayaranRepository;
        this.produkFormConfigRepository = produkFormConfigRepository;
        this.produkEkstraRepository = produkEkstraRepository;
        this.produkTestimoniRepository = produkTestimoniRepository;
        this.gambarProdukRepository = gambarProdukRepository;
        this.atributProdukRepository = atributProdukRepository;
        this.gudangRepository = gudangRepository;
        this.produkIklanRepository = produkIklanRepository;
        this.workspaceRepository = workspaceRepository;
        this.domainRepository = domainRepository;
        this.storageService = storageService;
    }


    public Page<ProdukListDto> getProdukByWorkspace(Long idWorkspace, Integer page, Integer limit, String search){
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ProdukListDto> listProduk;

        if (search != null && !search.isEmpty()) {
             listProduk = produkRepository.findAllProdukListDtoSearch(idWorkspace, search.toLowerCase(), pageable);


        }else {
            listProduk = produkRepository.findAllProdukListDto(idWorkspace, pageable);
        }

        listProduk.forEach(data -> {
            var gambarProduk = gambarProdukRepository.findGambarProduksByIdProduk(data.getId());
            if (!gambarProduk.isEmpty()){
                data.setGambarProduk(storageService.getProdukPublicUrl(gambarProduk.get(0).getUrlGambar()));
            }
        });

        return listProduk;


    }

    public List<ProdukListDropdown> getProdukListDropdown(Long idWorkspace){
        return produkRepository.findAllProdukListDropdown(idWorkspace);
    }

    @Transactional
    public Object saveProduct(AddProdukDto data){

        Produk produk;
        if (data.getId() != null){
            produk = produkRepository.findById(data.getId()).get();
            fiturProdukRepository.deleteAllByIdProduk(produk.getId());
            atributProdukRepository.updateIsDeletedByIdProduk(true, produk.getId());
            produkPembayaranRepository.deleteAllByIdProduk(produk.getId());
            gambarProdukRepository.deleteGambarProdukByIdProduk(produk.getId());
            produkFormConfigRepository.deleteProdukFormConfigByIdProduk(produk.getId());
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


        for (var dataFormConfig : data.getFormConfig()){
            var config = new ProdukFormConfig();
            config.setIdProduk(savedProduk.getId());
            config.setLabel(dataFormConfig.getLabel());
            config.setPlaceholder(dataFormConfig.getPlaceholder());
            config.setTipeField(dataFormConfig.getTipeField());


            produkFormConfigRepository.save(config);
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

        var formConfig = produkFormConfigRepository.getProdukFormConfigsByIdProduk(idProduk);
        if (!formConfig.isEmpty()) {
            for (var data : formConfig){
                produkDetail.getFormConfig().add(
                        new ProdukFormConfigDto(
                                data.getTipeField(),
                                data.getLabel(),
                                data.getPlaceholder(),
                                data.getOrder(),
                                data.getIsMandatory()
                        )
                );
            }
        }


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

        return produkDetail;
    }

    public ProdukCheckoutDto getCheckoutProdukDetail(String urlCheckout){
        var produkDetail = new ProdukCheckoutDto();

        var produk = produkRepository.findByUrlCheckout(urlCheckout).get(0);
        if (produk == null) throw new RuntimeException("Produk tidak ditemukan");
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

        var formConfig = produkFormConfigRepository.getProdukFormConfigsByIdProduk(produk.getId());
        if (!formConfig.isEmpty()) {
            for (var data : formConfig){
                produkDetail.getFormConfig().add(
                        new ProdukFormConfigDto(
                                data.getTipeField(),
                                data.getLabel(),
                                data.getPlaceholder(),
                                data.getOrder(),
                                data.getIsMandatory()
                        )
                );
            }
        }


        var ekstra = produkEkstraRepository.getProdukEkstrasByIdProduk(produk.getId());
        if (ekstra.size() > 0) {
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
                produk.getEmbededCheckoutScript()
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
                                data.getConfig() != null ? data.getConfig() : null
                        )
                );
            }
        }




        var formConfig = produkFormConfigRepository.getProdukFormConfigsByIdProduk(idProduk);
        if (!formConfig.isEmpty()) {
            for (var data : formConfig){
                newProduct.getFormConfig().add(
                        new ProdukFormConfigDto(
                                data.getTipeField(),
                                data.getLabel(),
                                data.getPlaceholder(),
                                data.getOrder(),
                                data.getIsMandatory()
                        )
                );
            }
        }


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
