package com.saktiform.api.controller;


import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.model.Order.JenisPembayaran;
import com.saktiform.api.model.Order.BulkUpdateStatus;
import com.saktiform.api.model.Order.CreateOrderDto;
import com.saktiform.api.model.Order.DeleteAbandonedOrder;
import com.saktiform.api.model.Order.UpdateOrderDto;
import com.saktiform.api.model.Order.OrderStatus;
import com.saktiform.api.model.RestResponse;
import com.saktiform.api.service.order.OrderOrchestrationService;
import com.saktiform.api.service.order.OrderService;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;
    private final OrderOrchestrationService orderOrchestrationService;
    private final JwtManager jwtManager;
    public OrderController(OrderService orderService, JwtManager jwtManager, OrderOrchestrationService orderOrchestrationService) {
        this.orderService = orderService;
        this.jwtManager = jwtManager;
        this.orderOrchestrationService = orderOrchestrationService;
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
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAkhirOrder,
                                          @RequestParam(required = false) String search){
        RestResponse rest = new RestResponse();
        try{
            var listOrder = orderService.getOrderList(workspaceId, page, limit, idProvinsi, idKota, idKecamatan, status == null? null : status.name(), jenisPembayaran == null? null : jenisPembayaran.name(), statusEkspor, tanggalAwalPaid, tanggalAkhirPaid, tanggalAwalOrder, tanggalAkhirOrder, search);
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
                                          @RequestParam(required = false) Boolean statusEkspor,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAwalPaid,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAkhirPaid,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAwalOrder,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tanggalAkhirOrder,
                                          @RequestParam(required = false) String search){
        RestResponse rest = new RestResponse();
        try{
            var file = orderService.exportOrder(workspaceId,  idProvinsi, idKota, idKecamatan, status == null? null : status.name(), jenisPembayaran == null? null : jenisPembayaran.name(), statusEkspor, tanggalAwalPaid, tanggalAkhirPaid, tanggalAwalOrder, tanggalAkhirOrder, search);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            var filename = orderService.constructExportOrderFileName();
            headers.setContentDispositionFormData("attachment", filename);

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
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderDto data, BindingResult bindingResult, HttpServletRequest request){
        RestResponse rest = new RestResponse();
        if (bindingResult.hasErrors()) {

            orderService.saveAbandonedOrder(data);
            rest.setSuccess(false);
            rest.setMessage("Error Validasi");
            rest.setData(MapperHelper.getErrors(bindingResult.getAllErrors()));
            return ResponseEntity.badRequest().body(rest);
        }
        try{
            String ip = request.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isEmpty()) {
                ip = request.getHeader("X-Forwarded-For");
            }
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }

            String username = "Customer";
            if (!(request.getHeader("Authorization") == null)){
                username = jwtManager.getUsernameByToken(request.getHeader("Authorization").substring(7));
            }

            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(orderOrchestrationService.createOrder(data, username, ip));
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
            orderOrchestrationService.updateOrder(data, username);

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

    @PostMapping("/update-bulk")
    public ResponseEntity<?> orderBulkUpdateStatus(@Valid @RequestBody List<BulkUpdateStatus> data, HttpServletRequest request){
        RestResponse rest = new RestResponse();
        try{
            String username = jwtManager.getUsernameByToken(request.getHeader("Authorization").substring(7));

            orderService.updateOrderStatus(data, username);
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

    @GetMapping("/status")
    public ResponseEntity<?> getListOrderStatus(){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            var status = Arrays.stream(OrderStatus.values()).map(Enum::name).toList();
            restResponse.setData(status);
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @PostMapping("/abandoned/delete")
    public ResponseEntity<?> deleteAbandonedOrder(@RequestBody List<DeleteAbandonedOrder> idAbandonedOrder){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            orderService.deleteAbandonedOrder(idAbandonedOrder);
            restResponse.setData(null);
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }







}
