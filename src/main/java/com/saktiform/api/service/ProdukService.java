package com.saktiform.api.service;

import com.saktiform.api.entity.*;
import com.saktiform.api.model.PlatformIklan;
import com.saktiform.api.model.product.*;
import com.saktiform.api.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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


    private final String uploadDir = "uploads/";
    private final String rootLocation = "src/main/resources/static/uploads";

    public ProdukService(ProdukRepository produkRepository,
                         FiturProdukRepository fiturProdukRepository,
                         ProdukPembayaranRepository produkPembayaranRepository,
                         ProdukFormConfigRepository produkFormConfigRepository,
                         ProdukEkstraRepository produkEkstraRepository,
                         ProdukTestimoniRepository produkTestimoniRepository,
                         GambarProdukRepository gambarProdukRepository,
                         AtributProdukRepository atributProdukRepository,
                         GudangRepository gudangRepository,
                         ProdukIklanRepository produkIklanRepository) {
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
    }


    public Page<ProdukListDto> getProdukByWorkspace(Long idWorkspace, Integer page, Integer limit, String search){
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "namaProduk"));

        if (search != null && !search.isEmpty()) {
            return produkRepository.findAllProdukListDtoSearch(idWorkspace, search.toLowerCase(), pageable);
        }else {
            return produkRepository.findAllProdukListDto(idWorkspace, pageable);
        }



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
        }

        produk.setIdWorkspace(data.getIdWorkspace());
        produk.setNamaProduk(data.getNamaProduk());
        produk.setUrlCheckout(data.getUrlCheckout());



        produk.setIdGudang(data.getIdGudang());
        produk.setNarasiTombol(data.getNarasiTombol());

        if (data.getFacebookPixelId()!=null){
            var produkIklan = produkIklanRepository.getProdukIklanByPlatformIklanAndIdIklan(PlatformIklan.FACEBOOK.name(), data.getFacebookPixelId());
            if (produkIklan.isEmpty()){
                ProdukIklan produkIklanNew = new ProdukIklan();
                produkIklanNew.setPlatformIklan(PlatformIklan.FACEBOOK.name());
                produkIklanNew.setIdIklan(data.getFacebookPixelId());
                produkIklanNew.setWorkspaceId(data.getIdWorkspace());
                produkIklanNew.setCreatedAt(Instant.now());
                produkIklanRepository.save(produkIklanNew);

                produk.setFacebookPixel(produkIklanNew.getId());
            }else {
                produk.setFacebookPixel(produkIklan.get(0).getId());
            }
        }

        if (data.getGoogleGtmId()!=null){
            var produkIklan = produkIklanRepository.getProdukIklanByPlatformIklanAndIdIklan(PlatformIklan.GOOGLE.name(), data.getFacebookPixelId());
            if (produkIklan.isEmpty()){
                ProdukIklan produkIklanNew = new ProdukIklan();
                produkIklanNew.setPlatformIklan(PlatformIklan.GOOGLE.name());
                produkIklanNew.setIdIklan(data.getFacebookPixelId());
                produkIklanNew.setWorkspaceId(data.getIdWorkspace());
                produkIklanNew.setCreatedAt(Instant.now());
                produkIklanRepository.save(produkIklanNew);

                produk.setGoogleGtm(produkIklanNew.getId());
            }else {
                produk.setGoogleGtm(produkIklan.getFirst().getId());
            }
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


        for (var dataEkstra : data.getEkstra()){
            var produkEkstra = new ProdukEkstra();
            produkEkstra.setIdProduk(savedProduk.getId());
            produkEkstra.setType(dataEkstra.getType());
            produkEkstra.setConfig(dataEkstra.getConfig());

            produkEkstraRepository.save(produkEkstra);
        }


        for (var dataTestimoni : data.getTestimoni()){
            var testimoni = new ProdukTestimoni();
            testimoni.setIdProduk(savedProduk.getId());
            testimoni.setNama(dataTestimoni.getNama());
            testimoni.setPesan(dataTestimoni.getPesan());
            testimoni.setGambar(dataTestimoni.getUrlGambar() != null ? dataTestimoni.getUrlGambar() : null);

            produkTestimoniRepository.save(testimoni);
        }


        for (var dataGambar : data.getGambarProduk()){
            var gambar = new GambarProduk();
            gambar.setIdProduk(savedProduk.getId());
            gambar.setUrlGambar(dataGambar);


            gambarProdukRepository.save(gambar);
        }
        return null;
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
                produkDetail.getGambarProduk().add(data.getUrlGambar());
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
                produkDetail.getTestimoni().add(
                        new ProdukTestimoniDto(
                                data.getNama(),
                                data.getPesan(),
                                data.getGambar() != null ? data.getGambar() : null)
                );
            }
        }

        produkDetail.setNarasiTombol(produk.getNarasiTombol());

        var googleGtm = produkIklanRepository.getProdukIklanById(produk.getGoogleGtm());
        produkDetail.setIdFacebookPixelId(!googleGtm.isEmpty() ? googleGtm.getFirst().getIdIklan() : null);
        var facebookAds = produkIklanRepository.getProdukIklanById(produk.getGoogleGtm());
        produkDetail.setIdGoogleGtmId(!facebookAds.isEmpty() ? facebookAds.getFirst().getIdIklan() : null);
        produkDetail.setEmbededCheckoutScript(produk.getEmbededCheckoutScript());
        produkDetail.setEmbededPurchaseScript(produk.getEmbededPurchaseScript());

        return produkDetail;
    }

    public ProdukCheckoutDto getCheckoutProdukDetail(String urlCheckout){
        var produkDetail = new ProdukCheckoutDto();

        var produk = produkRepository.findByUrlCheckout(urlCheckout);
        if (produk == null) throw new RuntimeException("Produk tidak ditemukan");
        if (produk.getIsDeleted()) throw new RuntimeException("Produk tidak ditemukan");

        produkDetail.setId(produk.getId());
        produkDetail.setNamaProduk(produk.getNamaProduk());

        var gambarProduk = gambarProdukRepository.findGambarProduksByIdProduk(produk.getId());
        if (gambarProduk.size() > 0) {
            for (var data : gambarProduk){
                produkDetail.getGambarProduk().add(data.getUrlGambar());
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
                                data.getGambar() != null ? data.getGambar() : null)
                );
            }
        }

        produkDetail.setNarasiTombol(produk.getNarasiTombol());


        return produkDetail;
    }

    public String saveFile(MultipartFile file){
        try {
            // Nama file unik
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            // Simpan ke folder "uploads" di luar JAR
            Path uploadPath = Paths.get("uploads").toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve(fileName);
            Files.write(filePath, file.getBytes());

            // URL publik (otomatis serve oleh Spring)
            String fileUrl = "/uploads/" + fileName;

            return fileUrl;

        } catch (IOException e) {
            throw new RuntimeException("Failed", e);
        }
    }

    @Transactional
    public void copyProduk(UUID idProduk){
        var produk = produkRepository.findById(idProduk).get();

        String strProductName = produk.getNamaProduk();
        String cleanProductName = strProductName.replaceAll("(-copy)+$", "");
        var numberIdentikProdukName = produkRepository.countIdenticProductName(cleanProductName);

        String newProductName = cleanProductName + "-copy".repeat(numberIdentikProdukName );


        String strUrlCheckout = produk.getUrlCheckout();
        String cleanUrlCheckout = strUrlCheckout.replaceAll("-\\d+$", "");
        var numberIdentikProdukUrl = produkRepository.countIdenticProductUrl(cleanUrlCheckout);

        String newProductUrl = cleanUrlCheckout + "-" + (numberIdentikProdukUrl);

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
                newProduct.getGambarProduk().add(data.getUrlGambar());
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
                                data.getGambar() != null ? data.getGambar() : null)
                );
            }
        }


        saveProduct(newProduct);
    }

    public Produk findProdukByNamaProduk(String namaProduk){
        return produkRepository.findByNamaProdukAndIsDeleted(namaProduk, Boolean.FALSE);
    }

    public Produk findProdukByUrlCheckout(String urlCheckout){
        return produkRepository.findByUrlCheckoutAndIsDeleted(urlCheckout, Boolean.FALSE);
    }

    public void deleteProduk(List<UUID> idProduk){
        produkRepository.updateIsDeletedByIdIn(true, idProduk);
    }
}
