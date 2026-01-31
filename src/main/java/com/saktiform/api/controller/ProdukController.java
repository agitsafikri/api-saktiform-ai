package com.saktiform.api.controller;

import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.product.AddProdukDto;
import com.saktiform.api.service.ProdukService;
import com.saktiform.api.util.MapperHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/produk")
public class ProdukController {
    private final ProdukService produkService;
    private final JwtManager jwtManager;

    public ProdukController(ProdukService produkService, JwtManager jwtManager) {
        this.produkService = produkService;
        this.jwtManager = jwtManager;
    }

    @PostMapping()
    public ResponseEntity<?> addProduk(@Valid @RequestBody AddProdukDto data, BindingResult bindingResult) {


        RestResponse rest = new RestResponse();

        if (bindingResult.hasErrors()) {
            rest.setSuccess(false);
            rest.setMessage("Error Validasi");
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

    @PostMapping(value = ("/uploadFile"), consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        RestResponse response = new RestResponse();
        try {
            // buat folder kalau belum ada
            String url = produkService.saveFile(file);

            if (url.toLowerCase().equals("failed")) {
                throw new Exception("Failed to upload file");
            }
            response.setSuccess(true);
            response.setData(url);
            response.setMessage("Upload success");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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

}
