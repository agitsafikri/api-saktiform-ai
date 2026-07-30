package com.saktiform.api.controller;

import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.model.ErrorDto;
import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.product.AddProdukDto;
import com.saktiform.api.model.product.formconfig.FormConfigRequest;
import com.saktiform.api.service.ProdukService;
import com.saktiform.api.service.formconfig.ProdukFormConfigService;
import com.saktiform.api.util.MapperHelper;
import com.saktiform.api.util.NotFoundException;
import com.saktiform.api.util.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/produk")
public class ProdukController {
    private static final Logger log = LoggerFactory.getLogger(ProdukController.class);

    private final ProdukService produkService;
    private final JwtManager jwtManager;
    private final ProdukFormConfigService produkFormConfigService;

    public ProdukController(ProdukService produkService,
                            JwtManager jwtManager,
                            ProdukFormConfigService produkFormConfigService) {
        this.produkService = produkService;
        this.jwtManager = jwtManager;
        this.produkFormConfigService = produkFormConfigService;
    }

    @PostMapping()
    public ResponseEntity<?> addProduk(@Valid @RequestBody AddProdukDto data, BindingResult bindingResult) {


        RestResponse rest = new RestResponse();

        if (bindingResult.hasErrors()) {
            rest.setSuccess(false);
            var errors = MapperHelper.getErrors(bindingResult.getAllErrors());
            var error =  errors.stream()
                    .map(ErrorDto::getMessage)
                    .filter(msg -> StringUtils.hasText(msg))
                    .collect(Collectors.joining(", "));

            rest.setMessage(error.isEmpty()? "Invalid input" : error);
            rest.setData(MapperHelper.getErrors(bindingResult.getAllErrors()));
            return ResponseEntity.badRequest().body(rest);
        }

        try {

            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(produkService.saveProduct(data));
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping()
    public  ResponseEntity<?> getProduk(@RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "10") Integer limit,
                                        @RequestParam (required = false) String search,
                                        @RequestParam Long workspaceId,
                                        HttpServletRequest request){

        RestResponse rest = new RestResponse();
        try {
            var listProduk = produkService.getProdukByWorkspace(workspaceId, page, limit, search);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(listProduk);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetailProduk(@PathVariable UUID id){
        RestResponse response = new RestResponse();
        try{

            response.setSuccess(true);
            response.setMessage("Success");
            var produkDetail = produkService.getProdukDetail(id);
            response.setData(produkDetail);
            return ResponseEntity.ok(response);
        }catch (Exception e){
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Konfigurasi form lengkap untuk layar konfigurasi dashboard — memuat System Field
     * maupun Custom Field, aktif maupun nonaktif, beserta izin ubah/hapus per field.
     */
    @GetMapping("/{id}/form-config")
    public ResponseEntity<?> getFormConfig(@PathVariable UUID id,
                                           @RequestParam Long workspaceId) {
        RestResponse response = new RestResponse();
        try {
            response.setSuccess(true);
            response.setMessage("Success");
            response.setData(produkFormConfigService.getFormConfig(id, workspaceId));
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            log.error("getFormConfig gagal. idProduk={}", id, e);
            response.setSuccess(false);
            response.setMessage("Gagal memuat konfigurasi form.");
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Menyimpan keseluruhan konfigurasi form (full replace dengan upsert by field key).
     * Seluruh galat validasi dikembalikan sekaligus pada {@code data}.
     */
    @PutMapping("/{id}/form-config")
    public ResponseEntity<?> saveFormConfig(@PathVariable UUID id,
                                            @RequestParam Long workspaceId,
                                            @Valid @RequestBody FormConfigRequest body,
                                            BindingResult bindingResult) {
        RestResponse response = new RestResponse();

        if (bindingResult.hasErrors()) {
            var errors = MapperHelper.getErrors(bindingResult.getAllErrors());
            var message = errors.stream()
                    .map(ErrorDto::getMessage)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining(", "));
            response.setSuccess(false);
            response.setMessage(message.isEmpty() ? "Invalid input" : message);
            response.setData(errors);
            return ResponseEntity.badRequest().body(response);
        }

        try {
            response.setSuccess(true);
            response.setMessage("Konfigurasi form berhasil disimpan.");
            response.setData(produkFormConfigService.saveFormConfig(id, workspaceId, body));
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            response.setSuccess(false);
            response.setMessage("Validation failed");
            response.setData(e.getErrors());
            return ResponseEntity.badRequest().body(response);
        } catch (NotFoundException e) {
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            log.error("saveFormConfig gagal. idProduk={}", id, e);
            response.setSuccess(false);
            response.setMessage("Gagal menyimpan konfigurasi form.");
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}/attribut")
    public ResponseEntity<?> getAttributProduk(@PathVariable UUID id){
        RestResponse response = new RestResponse();
        try{
            response.setSuccess(true);
            response.setMessage("Success");
            var produkDetail = produkService.getAtributProduk(id);
            response.setData(produkDetail);
            return ResponseEntity.ok(response);
        }catch (Exception e){
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}/pembayaran")
    public ResponseEntity<?> getPembayaranProduk(@PathVariable UUID id){
        RestResponse response = new RestResponse();
        try{
            response.setSuccess(true);
            response.setMessage("Success");
            var produkDetail = produkService.getPembayaranProduk(id);
            response.setData(produkDetail);
            return ResponseEntity.ok(response);
        }catch (Exception e){
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/checkout")
    public ResponseEntity<?> getCheckoutProduk(@RequestParam String urlCheckout){
        RestResponse response = new RestResponse();
        try{
            response.setSuccess(true);
            response.setMessage("Success");
            var produkDetail = produkService.getCheckoutProdukDetail(urlCheckout);
            response.setData(produkDetail);
            return ResponseEntity.ok(response);
        }catch (Exception e){
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteProduk(@RequestBody List<UUID> idProduk) {
        RestResponse response = new RestResponse();
        try {
            response.setSuccess(true);
            response.setMessage("success");
            produkService.deleteProduk(idProduk);
            return ResponseEntity.ok(response);
        }catch (Exception e){
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/copy")
    public ResponseEntity<?> copyProduk(@RequestParam UUID idProduk){
        RestResponse response = new RestResponse();
        try {
            response.setSuccess(true);
            response.setMessage("success");
            produkService.copyProduk(idProduk);
            return ResponseEntity.ok(response);
        }catch (Exception e){
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/list-dropdown")
    public  ResponseEntity<?> getProdukListDropdown(@RequestParam Long workspaceId){

        RestResponse rest = new RestResponse();
        try {
            var listProduk = produkService.getProdukListDropdown(workspaceId);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(listProduk);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

}
