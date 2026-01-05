package com.saktiform.api.controller;


import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.model.JenisPembayaran;
import com.saktiform.api.model.Order.CreateOrderDto;
import com.saktiform.api.model.Order.UpdateOrderDto;
import com.saktiform.api.model.OrderStatus;
import com.saktiform.api.model.RestResponse;
import com.saktiform.api.service.OrderService;
import com.saktiform.api.util.MapperHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;
    private final JwtManager jwtManager;
    public OrderController(OrderService orderService, JwtManager jwtManager) {
        this.orderService = orderService;
        this.jwtManager = jwtManager;
    }



    @GetMapping("")
    public ResponseEntity<?> getOrderList(@RequestParam Long workspaceId,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer limit,
                                          @RequestParam(required = false) Integer idProvinsi,
                                          @RequestParam(required = false) Integer idKota,
                                          @RequestParam(required = false) Integer idKecamatan,
                                          @RequestParam(required = false) OrderStatus status,
                                          @RequestParam(required = false) JenisPembayaran jenisPembayaran,
                                          @RequestParam(required = false) Boolean statusEkspor,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAwalPaid,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAkhirPaid,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAwalOrder,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAkhirOrder){
        RestResponse rest = new RestResponse();
        try{
            var listOrder = orderService.getOrderList(workspaceId, page, limit, idProvinsi, idKota, idKecamatan, status == null? null : status.name(), jenisPembayaran == null? null : jenisPembayaran.name(), statusEkspor, tanggalAwalPaid, tanggalAkhirPaid, tanggalAwalOrder, tanggalAkhirOrder);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(listOrder);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/export")
    public ResponseEntity<?> exportOrder(@RequestParam Long workspaceId,
                                          @RequestParam(required = false) Integer idProvinsi,
                                          @RequestParam(required = false) Integer idKota,
                                          @RequestParam(required = false) Integer idKecamatan,
                                          @RequestParam(required = false) OrderStatus status,
                                          @RequestParam(required = false) JenisPembayaran jenisPembayaran,
                                          @RequestParam(required = false) boolean statusEkspor,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAwalPaid,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAkhirPaid,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAwalOrder,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAkhirOrder){
        RestResponse rest = new RestResponse();
        try{
            var file = orderService.exportOrder(workspaceId,  idProvinsi, idKota, idKecamatan, status == null? null : status.name(), jenisPembayaran == null? null : jenisPembayaran.name(), statusEkspor, tanggalAwalPaid, tanggalAkhirPaid, tanggalAwalOrder, tanggalAkhirOrder);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "Export Order.xlsx");

            return new ResponseEntity<>(file, headers, HttpStatus.OK);
        }catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/abandoned")
    public ResponseEntity<?> getAbandonedOrderList(@RequestParam Long workspaceId,
                                                   @RequestParam (defaultValue = "1") Integer page,
                                                   @RequestParam (defaultValue = "10") Integer limit,
                                                   @RequestParam(required = false) String namaKonsumen,
                                                   @RequestParam(required = false) String nomorWhatsapp){

        RestResponse rest = new RestResponse();
        try {
            rest.setSuccess(true);
            rest.setMessage("success");
            var listAbandonedOrder = orderService.getAbandonedList(page, limit, workspaceId, namaKonsumen, nomorWhatsapp);
            rest.setData(listAbandonedOrder);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }

    }

    @GetMapping("/abandoned/{idAbandonedOrder}")
    public ResponseEntity<?> getAbandonedOrderDetail(@PathVariable UUID idAbandonedOrder){

        RestResponse rest = new RestResponse();
        try {
            rest.setSuccess(true);
            rest.setMessage("success");
            var listAbandonedOrder = orderService.getAbandonedDetail(idAbandonedOrder);
            rest.setData(listAbandonedOrder);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }

    }

    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderDto data, BindingResult bindingResult){
        RestResponse rest = new RestResponse();
        if (bindingResult.hasErrors()) {

            orderService.saveAbandonedOrder(data);
            rest.setSuccess(false);
            rest.setMessage("Error Validasi");
            rest.setData(MapperHelper.getErrors(bindingResult.getAllErrors()));
            return ResponseEntity.badRequest().body(rest);
        }
        try{

            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(orderService.createOrder(data));
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }


    @PostMapping("/update")
    public ResponseEntity<?> updateOrder(@Valid @RequestBody UpdateOrderDto data, HttpServletRequest request, BindingResult bindingResult){
        RestResponse rest = new RestResponse();
        if (bindingResult.hasErrors()) {
            rest.setSuccess(false);
            rest.setMessage("Error Validasi");
            rest.setData(MapperHelper.getErrors(bindingResult.getAllErrors()));
            return ResponseEntity.badRequest().body(rest);
        }
        try{
            String username = jwtManager.getUsernameByToken(request.getHeader("Authorization").substring(7));

            orderService.updateOrder(data, username);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(null);
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
    public ResponseEntity<?> getDetailOrder(@PathVariable UUID id){
        RestResponse response = new RestResponse();

        try {
            response.setSuccess(true);
            response.setMessage("success");
            response.setData(orderService.getDetailOrder(id));
            return ResponseEntity.ok(response);
        }catch (Exception e){

            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<?> getOrderLogs(@PathVariable UUID id){
        RestResponse response = new RestResponse();
        try
            {
            response.setSuccess(true);
            response.setMessage("success");
            response.setData(orderService.getOrderLogs(id));
            return ResponseEntity.ok(response);
            }catch (Exception e){
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }






}
