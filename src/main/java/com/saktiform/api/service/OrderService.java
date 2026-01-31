package com.saktiform.api.service;

import com.saktiform.api.entity.*;
import com.saktiform.api.model.ConversationStatus;
import com.saktiform.api.model.Order.*;
import com.saktiform.api.model.location.CityDto;
import com.saktiform.api.model.location.DistrictDto;
import com.saktiform.api.model.location.ProvinceDto;
import com.saktiform.api.repository.OngkirRepository;
import com.saktiform.api.model.OrderStatus;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.chat.ChatEventPublisher;
import com.saktiform.api.service.chat.MessageConstructorHelper;
import com.saktiform.api.service.chat.WhatsappClientHelper;
import com.saktiform.api.util.PhoneNumberUtil;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OngkirRepository ongkirRepository;
    private final GudangRepository gudangRepository;
    private final ProdukRepository produkRepository;
    private final AtributProdukRepository attributProdukRepository;
    private final ProdukPembayaranRepository produkPembayaranRepository;
    private final AbandonedOrderRepository abandonedOrderRepository;
    private final ContactRepository contactRepository;
    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;
    private final AccountRepository accountRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final OrderSequenceRepository orderSequenceRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public OrderService(OrderRepository orderRepository,
                        OngkirRepository ongkirRepository,
                        GudangRepository gudangRepository,
                        ProdukRepository produkRepository,
                        ProdukPembayaranRepository produkPembayaranRepository,
                        AtributProdukRepository attributProdukRepository,
                        AbandonedOrderRepository abandonedOrderRepository,
                        ContactRepository contactRepository,
                        ProvinceRepository provinceRepository,
                        CityRepository cityRepository,
                        DistrictRepository districtRepository,
                        AccountRepository accountRepository,
                        OrderSequenceRepository orderSequenceRepository,
                        OrderHistoryRepository orderHistoryRepository) {
        this.orderSequenceRepository = orderSequenceRepository;
        this.orderRepository = orderRepository;
        this.ongkirRepository = ongkirRepository;
        this.gudangRepository = gudangRepository;
        this.produkRepository = produkRepository;
        this.produkPembayaranRepository = produkPembayaranRepository;
        this.attributProdukRepository = attributProdukRepository;
        this.abandonedOrderRepository = abandonedOrderRepository;
        this.contactRepository = contactRepository;
        this.provinceRepository = provinceRepository;
        this.cityRepository = cityRepository;
        this.districtRepository = districtRepository;
        this.accountRepository = accountRepository;
        this.orderHistoryRepository = orderHistoryRepository;
    }


//    @Transactional
//    public OrderCreatedResponse createOrder(CreateOrderDto data){
//        Order order = new Order();
//
//
//        order.setNamaPenerima(data.getNamaLengkap());
//        var phoneNumber = PhoneNumberUtil.normalizeToIndonesianFormat(data.getNomorWhatsapp());
//        order.setNomorWhatsapp(phoneNumber);
//
//
//
//        order.setOrderCode(generateOrderCode());
//        order.setAlamat(data.getAlamat());
//        order.setIdKota(data.getIdKota());
//        order.setIdProvinsi(data.getIdProvinsi());
//        order.setIdKecamatan(data.getIdKecamatan());
//        order.setStatus(OrderStatus.UNPAID.name());
//        order.setSource(data.getSource());
//
//
//        var produk = produkRepository.findById(data.getIdProduk()).get();
//        var gudang = gudangRepository.findById(produk.getIdGudang()).get();
//        var ongkir = ongkirRepository.findByIdOriginCityAndIdDistrict(gudang.getIdKota(), data.getIdKecamatan());
//        order.setOngkosKirim(ongkir.getOngkirValue().longValue());
//        order.setIdProduk(data.getIdProduk());
//
//        var contact = getContact(phoneNumber, data.getNamaLengkap(), produk.getIdWorkspace());
//        order.setIdContact(contact.getId());
//
//        var configPembayaran = produkPembayaranRepository.findByIdProdukAndPembayaran(data.getIdProduk(),data.getMetodePembayaran());
//        order.setPembayaran(configPembayaran.getPembayaran());
//        order.setConfigPembayaran(configPembayaran.getConfig());
//
//        var attributProduk = attributProdukRepository.findById(data.getIdAtributProduk());
//        order.setIdAtributProduk(attributProduk.get().getId());
//        order.setBerat(attributProduk.get().getBerat());
//        order.setHarga(attributProduk.get().getHarga());
//        order.setDeskripsiProduk(attributProduk.get().getDeskripsi());
//        order.setStatusEkspor(false);
//        order.setCreatedAt(LocalDateTime.now());
//
//        Conversation conversation = conversationOrderService.getConversationByIdContact(order.getIdContact());
//        if (conversation == null){
//            conversation = startConversation(order);
//        }
//
//        if(conversation != null){
//            order.setIdConversation(conversation.getId());
//        }
//
//
//
//
//        var savedOrder = orderRepository.save(order);
//
//        createLogs(savedOrder, "Pesanan dibuat");
//
//        //response confirmation message
//        var workspace = workspaceRepository.findById(produk.getIdWorkspace()).get();
//        var nomorWhatsapp = workspace.getWaba().getNomorWhatsapp();
//
//        var orderCreatedResponse = new OrderCreatedResponse();
//        orderCreatedResponse.setPhoneNumber(nomorWhatsapp);
//        orderCreatedResponse.setMessage(messageConstructorHelper.confirmationMessage(produk.getNamaProduk(), order.getNamaPenerima()));
//
//        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));
//        return orderCreatedResponse;
//    }

    @Transactional
    public Order createOrderInternal(CreateOrderDto data) {

        Order order = new Order();

        order.setNamaPenerima(data.getNamaLengkap());
        var phoneNumber = PhoneNumberUtil.normalizeToIndonesianFormat(data.getNomorWhatsapp());
        order.setNomorWhatsapp(phoneNumber);

        order.setOrderCode(generateOrderCode());
        order.setAlamat(data.getAlamat());
        order.setIdKota(data.getIdKota());
        order.setIdProvinsi(data.getIdProvinsi());
        order.setIdKecamatan(data.getIdKecamatan());
        order.setStatus(OrderStatus.UNPAID.name());
        order.setSource(data.getSource());

        var produk = produkRepository.findById(data.getIdProduk()).get();
        var gudang = gudangRepository.findById(produk.getIdGudang()).get();
        var ongkir = ongkirRepository
                .findByIdOriginCityAndIdDistrict(gudang.getIdKota(), data.getIdKecamatan());

        order.setOngkosKirim(ongkir.getOngkirValue().longValue());
        order.setIdProduk(data.getIdProduk());

        var contact = getContact(phoneNumber, data.getNamaLengkap(), produk.getIdWorkspace());
        order.setIdContact(contact.getId());

        var configPembayaran =
                produkPembayaranRepository.findByIdProdukAndPembayaran(
                        data.getIdProduk(), data.getMetodePembayaran()
                );

        order.setPembayaran(configPembayaran.getPembayaran());
        order.setConfigPembayaran(configPembayaran.getConfig());

        var atribut = attributProdukRepository.findById(data.getIdAtributProduk()).get();
        order.setIdAtributProduk(atribut.getId());
        order.setBerat(atribut.getBerat());
        order.setHarga(atribut.getHarga());
        order.setDeskripsiProduk(atribut.getDeskripsi());

        order.setCreatedAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);
        createLogs(saved, "Pesanan dibuat");

        return saved;
    }



    @Transactional
    public void updateOrder(UpdateOrderDto data, String actor){
        String recentOrderStatus = orderRepository.findOrderById(data.getId()).getStatus();
        Order order = new Order();

        order.setId(data.getId());
        order.setNamaPenerima(data.getNamaLengkap());
        var phoneNumber = PhoneNumberUtil.normalizeToIndonesianFormat(data.getNomorWhatsapp());
        order.setNomorWhatsapp(phoneNumber);




        order.setAlamat(data.getAlamat());
        order.setIdKota(data.getIdKota());
        order.setIdProvinsi(data.getIdProvinsi());
        order.setIdKecamatan(data.getIdKecamatan());
        order.setStatus(data.getStatus().name());
        if (!recentOrderStatus.equals(data.getStatus().name()) && data.getStatus().equals(OrderStatus.PAID)){
            order.setPaidAt(LocalDateTime.now());
        }

        var produk = produkRepository.findById(data.getIdProduk()).get();
        var gudang = gudangRepository.findById(produk.getIdGudang()).get();
        var ongkir = ongkirRepository.findByIdOriginCityAndIdDistrict(gudang.getIdKota(), data.getIdKecamatan());
        order.setOngkosKirim(ongkir.getOngkirValue().longValue());
        order.setIdProduk(data.getIdProduk());

        var contact = getContact(phoneNumber, data.getNamaLengkap(), produk.getIdWorkspace());
        order.setIdContact(contact.getId());

        var configPembayaran = produkPembayaranRepository.findByIdProdukAndPembayaran(data.getIdProduk(),data.getMetodePembayaran());
        order.setPembayaran(configPembayaran.getPembayaran());
        order.setConfigPembayaran(configPembayaran.getConfig());

        var attributProduk = attributProdukRepository.findById(data.getIdAtributProduk());
        order.setIdAtributProduk(attributProduk.get().getId());
        order.setBerat(attributProduk.get().getBerat());
        order.setHarga(attributProduk.get().getHarga());
        order.setDeskripsiProduk(attributProduk.get().getDeskripsi());

        order.setUpdatedAt(LocalDateTime.now());

        var savedOrder = orderRepository.save(order);

        String logs = String.format("Pesanan diubah oleh %s", actor);
        createLogs(savedOrder, logs);

        if(!recentOrderStatus.equals(savedOrder.getStatus())){
            logs = String.format("Status pesanan diubah dari %s ke %s oleh %s", recentOrderStatus, savedOrder.getStatus(), actor);
            createLogs(savedOrder, logs);
        }


    }

    public Page<OrderListDto> getOrderList(Long idWorkspace, Integer page, Integer limit,
                                           Integer idProvinsi, Integer idKota, Integer idKecamatan,
                                           String status, String jenisPembayaran, Boolean statusEkspor,
                                           LocalDateTime tanggalAwalPaid, LocalDateTime tanggalAkhirPaid,
                                           LocalDateTime tanggalAwalOrder, LocalDateTime tanggalAkhirOrder){

        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "created_at"));
        var tomorow = LocalDateTime.now().plusDays(1L);
        var sentinel = LocalDateTime.of(1970, 1,1,0,0,0);
        if (tanggalAkhirOrder != null && tanggalAkhirOrder.isAfter(LocalDateTime.now())){
            tanggalAkhirOrder = LocalDateTime.now();
        }
        if(tanggalAkhirPaid != null && tanggalAkhirPaid.isAfter(LocalDateTime.now())){
            tanggalAkhirPaid = LocalDateTime.now();
        }
        var listOrder = orderRepository.getOrderList(idWorkspace, idProvinsi, idKota, idKecamatan, status, jenisPembayaran, statusEkspor,
                tanggalAwalOrder ,
                tanggalAkhirOrder ,
                tanggalAwalPaid ,
                tanggalAkhirPaid,
                sentinel, tomorow,
                pageable);

        return listOrder;
    }

    public void saveAbandonedOrder(CreateOrderDto data){
        AbandonedOrder order = new AbandonedOrder();
        order.setNamaPenerima(data.getNamaLengkap());
        order.setNomorWhatsapp(PhoneNumberUtil.normalizeToIndonesianFormat(data.getNomorWhatsapp()));
        order.setAlamat(data.getAlamat());
        order.setIdKota(data.getIdKota());;
        order.setIdProvinsi(data.getIdProvinsi());
        order.setIdKecamatan(data.getIdKecamatan());
        var produk = produkRepository.findById(data.getIdProduk()).get();



        order.setIdProduk(data.getIdProduk());

        var configPembayaran = produkPembayaranRepository.findByIdProdukAndPembayaran(data.getIdProduk(),data.getMetodePembayaran());
        if(configPembayaran != null){
            order.setConfigPembayaran(configPembayaran.getConfig());
        }


        var attributProduk = attributProdukRepository.findById(data.getIdAtributProduk());
        if (attributProduk != null) {
            order.setBerat(attributProduk.get().getBerat());
            order.setHarga(attributProduk.get().getHarga());
            order.setDeskripsiProduk(attributProduk.get().getDeskripsi());
        }


        order.setCreatedAt(LocalDateTime.now());

        abandonedOrderRepository.save(order);
    }

    private Contact getContact(String phoneNumber, String namaKontak, Long idWorkspace){
        var contact = contactRepository.findByPhoneNumberAndIdWorkspace(phoneNumber, idWorkspace);

        if(contact == null){
            var newContact = new Contact();
            newContact.setNamaKontak(namaKontak);
            newContact.setPhoneNumber(phoneNumber);
            newContact.setCreatedAt(Instant.now());
            newContact.setIdWorkspace(idWorkspace);
            var savedContact = contactRepository.save(newContact);
            return savedContact;
        }

        contact.setNamaKontak(namaKontak);
        contact.setUpdatedAt(Instant.now());
        var savedContact = contactRepository.save(contact);


        return savedContact;
    }

//    private Conversation startConversation(Order order){
//        if(order.getNomorWhatsapp().startsWith("+62")){
//            Conversation conversation = conversationOrderService.getConversationByIdContact(order.getIdContact());
//
//            if (conversation == null){
//                Conversation newConversation = new Conversation();
//                newConversation.setIdContact(order.getIdContact());
//                newConversation.setStatus(ConversationStatus.UNASSIGNED.name());
//
//
//
//                return conversationOrderService.saveConversation(newConversation);
//            }
//            return conversation;
//        }
//
//        return null;
//    }

    public Object getAbandonedList(Integer page, Integer limit, Long idWorkspace, String namaKonsumen, String nomorWhatsapp){

        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return abandonedOrderRepository.getListAbandonedOrders(idWorkspace, pageable);

    }

    public AbandonedOrderDetailDto getAbandonedDetail(UUID id){

        return abandonedOrderRepository.getDetailAbandonedOrders(id);

    }



    public DetailOrderDto getDetailOrder(UUID id){
        var order = orderRepository.findOrderById(id);

        var detailOrder = new DetailOrderDto();
        detailOrder.setId(order.getId());
        detailOrder.setIdProduk(order.getIdProduk());
        detailOrder.setNamaProduk(produkRepository.findById(order.getIdProduk()).get().getNamaProduk());

        var attributProduk = attributProdukRepository.findById(order.getIdAtributProduk()).get();
        detailOrder.getAtributProduk().setId(attributProduk.getId());
        detailOrder.getAtributProduk().setDeskripsi(attributProduk.getDeskripsi());
        detailOrder.getAtributProduk().setHarga(attributProduk.getHarga());

        detailOrder.setDiskon(order.getDiskon());
        detailOrder.setOngkir(order.getOngkosKirim());

        detailOrder.setNamaPenerima(order.getNamaPenerima());
        detailOrder.setNomorWhatsapp(order.getNomorWhatsapp());

        detailOrder.setMetodePembayaran(order.getPembayaran());
        detailOrder.setProvinsi(new ProvinceDto(order.getIdProvinsi(), provinceRepository.findById(order.getIdProvinsi()).get().getProvinceName()));
        detailOrder.setKota(new CityDto(order.getIdKota(), cityRepository.findById(order.getIdKota()).get().getCityName()));
        detailOrder.setKecamatan(new DistrictDto(order.getIdKecamatan(), districtRepository.findById(order.getIdKecamatan()).get().getDistrictName()));
        detailOrder.setAlamat(order.getAlamat());
        detailOrder.setStatus(order.getStatus());
        detailOrder.setTanggalOrder(order.getCreatedAt().format(formatter));


        if(order.getHandleByAccount() != null){
            var account = accountRepository.findById(order.getLastHandleBy()).get();
            detailOrder.setHandleBy(account.getNama());
        }



        return detailOrder;
    }

    public List<OrderLogsDto> getOrderLogs(UUID orderId){
        return orderHistoryRepository.getOrderLogs(orderId);
    }

    private void createLogs(Order order, String logs){
        OrderHistory orderHistory = new OrderHistory();
        orderHistory.setIdOrder(order.getId());
        orderHistory.setLog(logs);
        orderHistory.setCreatedAt(LocalDateTime.now());

        orderHistoryRepository.save(orderHistory);
    }


    @Transactional
    public byte[] exportOrder(Long idWorkspace,
                              Integer idProvinsi, Integer idKota, Integer idKecamatan,
                              String status, String jenisPembayaran, Boolean statusEkspor,
                              LocalDateTime tanggalAwalPaid, LocalDateTime tanggalAkhirPaid,
                              LocalDateTime tanggalAwalOrder, LocalDateTime tanggalAkhirOrder) throws IOException {

        var tomorow = LocalDateTime.now().plusDays(1L);
        var sentinel = LocalDateTime.of(1970, 1,1,0,0,0);
        if (tanggalAkhirOrder != null && tanggalAkhirOrder.isAfter(LocalDateTime.now())){
            tanggalAkhirOrder = LocalDateTime.now();
        }
        if(tanggalAkhirPaid != null && tanggalAkhirPaid.isAfter(LocalDateTime.now())){
            tanggalAkhirPaid = LocalDateTime.now();
        }
        var listOrder = orderRepository.exportOrder(idWorkspace, idProvinsi, idKota, idKecamatan, status, jenisPembayaran, statusEkspor,
                tanggalAwalOrder ,
                tanggalAkhirOrder ,
                tanggalAwalPaid ,
                tanggalAkhirPaid,
                sentinel, tomorow
                );

        List<UUID> ids = listOrder.stream().map(ExportOrderListDto::getId).toList();
        var file = generateUserExcel(listOrder);
        orderRepository.markAsExported(ids);

        return file;

    }

    public byte[] generateUserExcel(List<ExportOrderListDto> orders) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Users");

            // Header
            Row header = sheet.createRow(0);
            String[] columns = {"Id Order", "product", "Name", "phone", "address", "province", "city", "district",
                    "status", "payment", "product_price", "cogs", "discount", "quantity", "notes", "courier", "shipping_cost", "gross_revenue",
                    "net_revenue", "created_at", "paid_at", "handled_by", "source", "variation", "weight", "original_shipping_cost" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }

            // Data
            int rowIdx = 1;
            for (ExportOrderListDto order : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(order.getOrderCode());
                row.createCell(1).setCellValue(order.getNamaProduk());
                row.createCell(2).setCellValue(order.getNamaCustomer());
                row.createCell(3).setCellValue(order.getNomorWhatsapp());
                row.createCell(4).setCellValue(order.getAlamat());
                row.createCell(5).setCellValue(order.getProvinsi());
                row.createCell(6).setCellValue(order.getKota());
                row.createCell(7).setCellValue(order.getKecamatan());
                row.createCell(8).setCellValue(order.getStatus());
                row.createCell(9).setCellValue(order.getJenisPembayaran());
                row.createCell(10).setCellValue(order.getHarga().toString());
                row.createCell(11).setCellValue("cogs");
                row.createCell(12).setCellValue(order.getDiskon() != null ? order.getDiskon().toString() : "0");
                row.createCell(13).setCellValue("1");
                row.createCell(14).setCellValue(order.getNotes() != null ? order.getNotes() : "");
                row.createCell(15).setCellValue("NINJA - STANDARD");
                row.createCell(16).setCellValue(order.getOngkir().toString());
                row.createCell(17).setCellValue("gross_revenue");
                row.createCell(18).setCellValue("net_revenue");
                row.createCell(19).setCellValue(
                        order.getTanggalOrder() != null ? order.getTanggalOrder() : ""
                );
                row.createCell(20).setCellValue(
                    order.getPaidAt()!= null ? order.getTanggalOrder() : ""
                );
                row.createCell(21).setCellValue(order.getHandleBy() != null ? order.getHandleBy() : "");
                row.createCell(22).setCellValue("source");
                row.createCell(23).setCellValue(order.getVariation() != null ? order.getVariation() : "");
                row.createCell(24).setCellValue(order.getBerat().toString());
                row.createCell(25).setCellValue(order.getOngkir().toString());
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    private String generateOrderCode(){
        YearMonth currentMonth = YearMonth.now();
        String yearMonthStr = currentMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));

        OrderSequence sequence = orderSequenceRepository.findById(yearMonthStr)
                .orElseGet(() -> {
                    OrderSequence orderSequence = new OrderSequence();
                    orderSequence.setId(yearMonthStr);
                    orderSequence.setSeqValue(0L);
                    return orderSequence;
                });

        Long nextNumber = sequence.getSeqValue() + 1;
        sequence.setSeqValue(nextNumber);
        orderSequenceRepository.save(sequence);

        return String.format("ORD-%s-%05d", yearMonthStr, nextNumber);
    }

    public List<ConversationOrderList> getConversationOrder(UUID idConversation) {
        return orderRepository.getConversationOrderList(idConversation);
    }

    @Transactional
    public void attachConversation(UUID orderId, UUID conversationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow();


        order.setIdConversation(conversationId);
    }
}
