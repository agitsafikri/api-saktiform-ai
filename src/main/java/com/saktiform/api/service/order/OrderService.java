package com.saktiform.api.service.order;

import com.saktiform.api.entity.*;
import com.saktiform.api.model.Order.*;
import com.saktiform.api.model.account.Role;
import com.saktiform.api.model.chat.bot.OrderChatInfo;
import com.saktiform.api.model.location.CityDto;
import com.saktiform.api.model.location.DistrictDto;
import com.saktiform.api.model.location.ProvinceDto;
import com.saktiform.api.repository.OngkirRepository;
import com.saktiform.api.model.Order.OrderStatus;
import com.saktiform.api.model.product.formconfig.FormConfigErrorCode;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.formconfig.OrderCustomFieldService;
import com.saktiform.api.util.PhoneNumberUtil;
import com.saktiform.api.util.ValidationException;
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
import java.time.temporal.ChronoUnit;
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
    private final OrderCustomFieldService orderCustomFieldService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final DateTimeFormatter formatterSecond = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
                        OrderHistoryRepository orderHistoryRepository,
                        OrderCustomFieldService orderCustomFieldService) {
        this.orderCustomFieldService = orderCustomFieldService;
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

    @Transactional
    public Order createOrderInternal(CreateOrderDto data, String actor, String ipAddress) {

        Order order = new Order();

        if(data.getSource().equals("ADM_ABANDONED")){
            abandonedOrderRepository.deletedAbandonedOrder(data.getNamaLengkap(), data.getNomorWhatsapp().substring(data.getNomorWhatsapp().length() - 4));
        }

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
        order.setCreatedAt(Instant.now());

        var produk = produkRepository.findById(data.getIdProduk()).orElseThrow();
        produk.setOrderCount(produk.getOrderCount() + 1);
        var gudang = gudangRepository.findById(produk.getIdGudang()).get();
        var ongkir = ongkirRepository
                .findByIdOriginCityAndIdDistrict(gudang.getIdKota(), data.getIdKecamatan());

        // Tanpa guard ini, kecamatan tanpa data ongkir menghasilkan NullPointerException
        // yang sampai ke pelanggan sebagai pesan galat kosong.
        if (ongkir == null || ongkir.getOngkirValue() == null) {
            throw new ValidationException("district",
                    FormConfigErrorCode.SHIPPING_RATE_NOT_FOUND,
                    "Ongkos kirim untuk kecamatan yang dipilih belum tersedia. "
                            + "Silakan hubungi penjual.");
        }

        order.setOngkosKirim(ongkir.getOngkirValue().longValue());
        order.setIdProduk(data.getIdProduk());

        var contact = getOrCreateContact(phoneNumber, data.getNamaLengkap(), produk.getIdWorkspace());
        order.setIdContact(contact.getId());

        var configPembayaran =
                produkPembayaranRepository.findByIdProdukAndPembayaran(
                        data.getIdProduk(), data.getMetodePembayaran()
                );

        if (configPembayaran.getConfig() != null) {
            order.setConfigPembayaran(configPembayaran.getConfig());
        }

        if(configPembayaran.getConfig() != null && configPembayaran.getPembayaran().equals(JenisPembayaran.COD.name())){
            if(!configPembayaran.getConfig().isEmpty()){
                var vtipeBiaya = configPembayaran.getConfig().get("tipeBiaya");
                String tipeBiaya = vtipeBiaya == null?null:vtipeBiaya.toString();
                var vminimalBiaya = configPembayaran.getConfig().get("minimalBiaya");
                Integer minimalBiaya = vminimalBiaya == null?null:Integer.valueOf(vminimalBiaya.toString());
                var vmaksimalBiaya = configPembayaran.getConfig().get("maksimalBiaya");
                Integer maksimalBiaya = vmaksimalBiaya == null?null:Integer.valueOf(vmaksimalBiaya.toString());
                var vpersentaseBiaya = configPembayaran.getConfig().get("persentaseBiaya");
                Double persentaseBiaya = vpersentaseBiaya == null?null:Double.valueOf(vpersentaseBiaya.toString());

                if(tipeBiaya != null  && minimalBiaya != null && maksimalBiaya != null && persentaseBiaya == null){
                    if(tipeBiaya.equalsIgnoreCase("hanya-produk")){
                        Double penambahanBiaya = order.getHarga() * persentaseBiaya / 100;
                        if(penambahanBiaya < minimalBiaya){
                            penambahanBiaya = minimalBiaya.doubleValue();
                        }else if(penambahanBiaya > maksimalBiaya){
                            penambahanBiaya = maksimalBiaya.doubleValue();
                        }

                        order.setOngkosKirim(order.getOngkosKirim() + penambahanBiaya.longValue());
                    }else if(tipeBiaya.equalsIgnoreCase("produk-pengiriman")){
                        Double penambahanBiaya = (order.getHarga() + order.getOngkosKirim()) * persentaseBiaya / 100;
                        if(penambahanBiaya < minimalBiaya){
                            penambahanBiaya = minimalBiaya.doubleValue();
                        }else if(penambahanBiaya > maksimalBiaya){
                            penambahanBiaya = maksimalBiaya.doubleValue();
                        }

                        order.setOngkosKirim(order.getOngkosKirim() + penambahanBiaya.longValue());
                    }
                }

            }
        }
        order.setPembayaran(configPembayaran.getPembayaran());



        var atribut = attributProdukRepository.findById(data.getIdAtributProduk()).get();
        order.setIdAtributProduk(atribut.getId());
        order.setBerat(atribut.getBerat());
        order.setHarga(atribut.getHarga());
        order.setDeskripsiProduk(atribut.getDeskripsi());

        order.setCreatedAt(Instant.now());

        Order saved = orderRepository.save(order);

        switch (order.getSource()){
            case "ADM_ABANDONED":
                createLogs(saved, "Pesanan dibuat dari abandoned order oleh " + actor);
                break;
            case "CST_CHAT":
                createLogs(saved, "Pesanan dibuat dari chat oleh " + actor);
                break;
            case "FORM":
                createLogs(saved, "Pesanan dibuat dari form oleh " + actor +" dari ip: "+ ipAddress);
                break;
            default:
                createLogs(saved, "Pesanan dibuat");
                break;
        }


        return saved;
    }



    @Transactional
    public Order updateOrder(UpdateOrderDto data, String actor){
        var handledBy = accountRepository.findByUsername(actor).get();

        Order order = orderRepository.findOrderById(data.getId());
        if (!handledBy.getRole().name().equals(Role.OWNER.name())){
            if(order.getLastHandleBy() != null && order.getLastHandleBy() != handledBy.getId()){
                throw new RuntimeException("Pesanan ini sudah diproses oleh " + order.getHandleByAccount().getUsername());
            }
        }
        String recentOrderStatus = order.getStatus();
        order.setNamaPenerima(data.getNamaLengkap());
        var phoneNumber = PhoneNumberUtil.normalizeToIndonesianFormat(data.getNomorWhatsapp());
        order.setNomorWhatsapp(phoneNumber);




        order.setAlamat(data.getAlamat());
        order.setIdKota(data.getIdKota());
        order.setIdProvinsi(data.getIdProvinsi());
        order.setIdKecamatan(data.getIdKecamatan());
        order.setStatus(data.getStatus().name());
        order.setNotes(data.getNotes());
        order.setDiskon(data.getDiskon()!=null?data.getDiskon():0);
        if (!data.getStatus().equals(OrderStatus.PAID)){
            order.setLastHandleBy(null);
            order.setPaidAt(null);
        }
        if (!recentOrderStatus.equals(data.getStatus().name()) && data.getStatus().equals(OrderStatus.PAID)){
            order.setLastHandleBy(handledBy.getId());
            order.setPaidAt(Instant.now());
        }



        var produk = produkRepository.findById(data.getIdProduk()).get();
        var gudang = gudangRepository.findById(produk.getIdGudang()).get();
        var ongkir = ongkirRepository.findByIdOriginCityAndIdDistrict(gudang.getIdKota(), data.getIdKecamatan());
        order.setOngkosKirim(ongkir.getOngkirValue().longValue());
        order.setIdProduk(data.getIdProduk());

        var contact = getOrCreateContact(phoneNumber, data.getNamaLengkap(), produk.getIdWorkspace());
        order.setIdContact(contact.getId());
        if(contact.getId() != order.getIdContact()){
            order.setIdConversation(null);
        }

        var configPembayaran = produkPembayaranRepository.findByIdProdukAndPembayaran(data.getIdProduk(),data.getMetodePembayaran());
        if(configPembayaran != null && configPembayaran.getPembayaran().equals(JenisPembayaran.COD.name())){
            if(!configPembayaran.getConfig().isEmpty()){
                var vtipeBiaya = configPembayaran.getConfig().get("tipeBiaya");
                String tipeBiaya = vtipeBiaya == null?null:vtipeBiaya.toString();
                var vminimalBiaya = configPembayaran.getConfig().get("minimalBiaya");
                Integer minimalBiaya = vminimalBiaya == null?null:Integer.valueOf(vminimalBiaya.toString());
                var vmaksimalBiaya = configPembayaran.getConfig().get("maksimalBiaya");
                Integer maksimalBiaya = vmaksimalBiaya == null?null:Integer.valueOf(vmaksimalBiaya.toString());
                var vpersentaseBiaya = configPembayaran.getConfig().get("persentaseBiaya");
                Double persentaseBiaya = vpersentaseBiaya == null?null:Double.valueOf(vpersentaseBiaya.toString());

                if(tipeBiaya != null  && minimalBiaya != null && maksimalBiaya != null && persentaseBiaya == null){
                    if(tipeBiaya.equalsIgnoreCase("hanya-produk")){
                        Double penambahanBiaya = (order.getHarga() - order.getDiskon()) * persentaseBiaya / 100;
                        if(penambahanBiaya < minimalBiaya){
                            penambahanBiaya = minimalBiaya.doubleValue();
                        }else if(penambahanBiaya > maksimalBiaya){
                            penambahanBiaya = maksimalBiaya.doubleValue();
                        }

                        order.setOngkosKirim(order.getOngkosKirim() + penambahanBiaya.longValue());
                    }else if(tipeBiaya.equalsIgnoreCase("produk-pengiriman")){
                        Double penambahanBiaya = (order.getHarga() - order.getDiskon() + order.getOngkosKirim()) * persentaseBiaya / 100;
                        if(penambahanBiaya < minimalBiaya){
                            penambahanBiaya = minimalBiaya.doubleValue();
                        }else if(penambahanBiaya > maksimalBiaya){
                            penambahanBiaya = maksimalBiaya.doubleValue();
                        }

                        order.setOngkosKirim(order.getOngkosKirim() + penambahanBiaya.longValue());
                    }
                }

            }
        }
        order.setPembayaran(configPembayaran.getPembayaran());
        order.setConfigPembayaran(configPembayaran.getConfig());

        var attributProduk = attributProdukRepository.findById(data.getIdAtributProduk());
        order.setIdAtributProduk(attributProduk.get().getId());
        order.setBerat(attributProduk.get().getBerat());
        order.setHarga(attributProduk.get().getHarga());
        order.setDeskripsiProduk(attributProduk.get().getDeskripsi());

        order.setUpdatedAt(Instant.now());

        var savedOrder = orderRepository.save(order);

        String logs = String.format("Pesanan diubah oleh %s", actor);
        createLogs(savedOrder, logs);
        if(!recentOrderStatus.equals(savedOrder.getStatus())){
            if(savedOrder.getStatus().equals("PAID")){
                increaseProdukSoldCount(savedOrder.getIdProduk());
            } else if (recentOrderStatus.equals("PAID")) {
                savedOrder.setPaidAt(null);
                savedOrder.setLastHandleBy(null);
                decreaseProdukCount(savedOrder.getIdProduk());
            }

            logs = String.format("Status pesanan diubah dari %s ke %s oleh %s", recentOrderStatus, savedOrder.getStatus(), actor);
            createLogs(savedOrder, logs);
        }

        return savedOrder;


    }

    @Transactional
    public void updateOrderStatus(List<BulkUpdateStatus> listData, String actor){
        var handledBy = accountRepository.findByUsername(actor).get();
        listData.forEach(data -> {

            Order order = orderRepository.findOrderById(data.getId());
            if (!handledBy.getRole().name().equals(Role.OWNER.name())){
                if(order.getLastHandleBy() != null && order.getLastHandleBy() != handledBy.getId()){
                    throw new RuntimeException("Ada data pesanan yang sudah diproses oleh orang lain");
                }
            }
            String recentOrderStatus = order.getStatus();
            if (!recentOrderStatus.equals(data.getStatus().name())
                    && data.getStatus().equals(OrderStatus.PAID)) {

                // Status berubah menjadi PAID
                increaseProdukSoldCount(order.getIdProduk());
                order.setPaidAt(Instant.now());
                order.setLastHandleBy(handledBy.getId());

            } else if (recentOrderStatus.equals(OrderStatus.PAID.name())
                    && !data.getStatus().equals(OrderStatus.PAID)) {

                // Status berubah dari PAID ke status lain
                decreaseProdukCount(order.getIdProduk());
                order.setLastHandleBy(null);
                order.setPaidAt(null);
            }

            order.setStatus(data.getStatus().name());
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            String logs = String.format("Pesanan diubah oleh %s", actor);
            createLogs(order, logs);

            if(!recentOrderStatus.equals(order.getStatus())){
                logs = String.format("Status pesanan diubah dari %s ke %s oleh %s", recentOrderStatus, order.getStatus(), actor);
                createLogs(order, logs);
            }
        });
    }

    public Page<OrderListDto> getOrderList(Long idWorkspace, Integer page, Integer limit,
                                           Integer idProvinsi, Integer idKota, Integer idKecamatan,
                                           String status, String jenisPembayaran, Boolean statusEkspor,
                                           LocalDateTime tanggalAwalPaid, LocalDateTime tanggalAkhirPaid,
                                           LocalDateTime tanggalAwalOrder, LocalDateTime tanggalAkhirOrder, String search){

        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "created_at"));
        var tomorow = LocalDateTime.now().plus(1, ChronoUnit.DAYS);
        var sentinel = LocalDateTime.of(1970, 1,1,0,0,0);




        var listOrder = orderRepository.getOrderList(idWorkspace, idProvinsi, idKota, idKecamatan, status, jenisPembayaran, statusEkspor,
                tanggalAwalOrder ,
                tanggalAkhirOrder ,
                tanggalAwalPaid ,
                tanggalAkhirPaid,
                sentinel , tomorow, search,
                pageable);

        return listOrder;
    }

    public void saveAbandonedOrder(CreateOrderDto data){

        if (data.getIdProduk() == null || data.getNomorWhatsapp() == null){
            return;
        }
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


        order.setCreatedAt(Instant.now());

        abandonedOrderRepository.save(order);
    }

    private Contact getOrCreateContact(String phoneNumber, String namaKontak, Long idWorkspace){
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


    @Transactional
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

        if(order.getHandleByAccount() != null){
            detailOrder.setHandleBy(accountRepository.findById(order.getLastHandleBy()).get().getNama());
        }

        detailOrder.setNamaPenerima(order.getNamaPenerima());
        detailOrder.setNomorWhatsapp(order.getNomorWhatsapp());

        detailOrder.setMetodePembayaran(order.getPembayaran());
        detailOrder.setProvinsi(new ProvinceDto(order.getIdProvinsi(), provinceRepository.findById(order.getIdProvinsi()).get().getProvinceName()));
        detailOrder.setKota(new CityDto(order.getIdKota(), cityRepository.findById(order.getIdKota()).get().getCityName()));
        detailOrder.setKecamatan(new DistrictDto(order.getIdKecamatan(), districtRepository.findById(order.getIdKecamatan()).get().getDistrictName()));
        detailOrder.setAlamat(order.getAlamat());
        detailOrder.setStatus(order.getStatus());
        detailOrder.setNotes(order.getNotes());
        detailOrder.setTanggalOrder(order.getCreatedAt().atZone(ZoneId.of("Asia/Jakarta")).format(formatter));


        if(order.getHandleByAccount() != null){
            var account = accountRepository.findById(order.getLastHandleBy()).get();
            detailOrder.setHandleBy(account.getNama());
        }

        // Label berasal dari snapshot saat order dibuat, bukan dari konfigurasi produk
        // saat ini — order lama tetap menampilkan label yang dilihat pelanggan.
        detailOrder.setCustomFields(orderCustomFieldService.findByOrder(order.getId()));

        return detailOrder;
    }

    public List<OrderLogsDto> getOrderLogs(UUID orderId){
        return orderHistoryRepository.getOrderLogs(orderId);
    }

    public void createLogs(Order order, String logs){
        OrderHistory orderHistory = new OrderHistory();
        orderHistory.setIdOrder(order.getId());
        orderHistory.setLog(logs);
        orderHistory.setCreatedAt(Instant.now());

        orderHistoryRepository.save(orderHistory);
    }


    @Transactional
    public byte[] exportOrder(Long idWorkspace,
                              Integer idProvinsi, Integer idKota, Integer idKecamatan,
                              String status, String jenisPembayaran, Boolean statusEkspor,
                              LocalDateTime tanggalAwalPaid, LocalDateTime tanggalAkhirPaid,
                              LocalDateTime tanggalAwalOrder, LocalDateTime tanggalAkhirOrder, String search) throws IOException {

        var tomorow = LocalDateTime.now().plus(1, ChronoUnit.DAYS);
        var sentinel = LocalDateTime.of(1970, 1,1,0,0,0);




        var listOrder = orderRepository.exportOrder(idWorkspace, idProvinsi, idKota, idKecamatan, status, jenisPembayaran, statusEkspor,
                tanggalAwalOrder ,
                tanggalAkhirOrder ,
                tanggalAwalPaid ,
                tanggalAkhirPaid,
                sentinel, tomorow, search
                );

        List<UUID> ids = listOrder.stream().map(ExportOrderListDto::getId).toList();
        var file = generateUserExcel(listOrder);
        orderRepository.markAsExported(ids);

        return file;

    }

    public byte[] generateUserExcel(List<ExportOrderListDto> orders) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("List Order");

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
                row.createCell(11).setCellValue("0");
                row.createCell(12).setCellValue(order.getDiskon() != null ? order.getDiskon().toString() : "0");
                row.createCell(13).setCellValue("1");
                row.createCell(14).setCellValue(order.getNotes() != null ? order.getNotes() : "");
                row.createCell(15).setCellValue("NINJA - STANDARD");
                row.createCell(16).setCellValue(order.getOngkir().toString());
                var harga = order.getHarga() == null ? 0 : order.getHarga();
                var diskon = order.getDiskon() == null ? 0 : order.getDiskon();
                var ongkir = order.getOngkir() == null ? 0 : order.getOngkir();
                var grossRevenue = harga.longValue() - diskon.longValue() + ongkir.longValue();
                row.createCell(17).setCellValue(grossRevenue);
                var nettRevenue = harga.longValue() - diskon.longValue();
                row.createCell(18).setCellValue(nettRevenue);
                row.createCell(19).setCellValue(
                        order.getTanggalOrder() != null ? order.getTanggalOrder() : ""
                );
                row.createCell(20).setCellValue(
                    order.getPaidAt()!= null ? order.getPaidAt() : ""
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
        YearMonth currentMonth = YearMonth.now(ZoneId.of("Asia/Jakarta"));
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

        // YYMMXXXXX: 2-digit year + 2-digit month + min-5-digit per-month sequence (grows beyond 99,999)
        return String.format("%s%05d", yearMonthStr.substring(2), nextNumber);
    }



    @Transactional
    public void attachConversation(UUID orderId, UUID conversationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow();
        order.setIdConversation(conversationId);
    }

    public void deleteAbandonedOrder(List <DeleteAbandonedOrder> ids){
        ids.forEach(id -> abandonedOrderRepository.deleteById(id.getId()));
    }

    public List<TotalOrderReportView> getTotalOrderReport(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long idWorkspace
    ) {

        // Default handling
        if (endDate == null) {
            endDate = LocalDate.now().atTime(23, 59, 59);
        }

        if (startDate == null) {
            startDate = endDate.minusDays(30);
        }

        long days = ChronoUnit.DAYS.between(
                startDate.toLocalDate(),
                endDate.toLocalDate()
        );

        if (days <= 31) {
            return orderRepository.getDailyReportTotalOrder(startDate, endDate, idWorkspace);
        }

        if (days <= 140) {
            return orderRepository.getWeeklyReportTotalOrder(startDate, endDate, idWorkspace);
        }

        return orderRepository.getMonthlyReportTotalOrder(startDate, endDate, idWorkspace);
    }

    public List<TotalPendapatanReportView> getPendapatanReport(
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {

        // Default handling
        if (endDate == null) {
            endDate = LocalDate.now().atTime(23, 59, 59);
        }

        if (startDate == null) {
            startDate = endDate.minusDays(14);
        }

        long days = ChronoUnit.DAYS.between(
                startDate.toLocalDate(),
                endDate.toLocalDate()
        );

        if (days <= 15) {
            return orderRepository.getDailyReportPendapatan(startDate, endDate);
        }

        if (days <= 60) {
            return orderRepository.getWeeklyReportPendapatan(startDate, endDate);
        }

        return orderRepository.getMonthlyReportPendapatan(startDate, endDate);
    }

    public String constructExportOrderFileName() {
            return String.format("ListPesanan.xlsx", LocalDateTime.now().format(formatterSecond));

    }

    public Order getConfirmOrder(OrderChatInfo orderChatInfo, UUID idConversation){
//        Instant jakartaInstant = ZonedDateTime
//                .now(ZoneId.of("Asia/Jakarta"))
//                .toInstant();

        Instant jakartaInstant = Instant.now();

        Instant startOfDay = jakartaInstant.minus(20, ChronoUnit.MINUTES);
        Instant endOfDay = jakartaInstant.plus(1, ChronoUnit.MINUTES);

        var idWorkspace = orderRepository.getIdWorkspaceByConversationId(idConversation);

        var order = orderRepository.searchOrderForConfirmation(orderChatInfo.getCustomerName(), idWorkspace, orderChatInfo.getProductName(), startOfDay, endOfDay).get(0);

        return order;
    }

    public Order getOrderById(UUID id){
        return orderRepository.findOrderById(id);
    }

    public void increaseProdukSoldCount(UUID idProduk){
        var produk = produkRepository.findById(idProduk).get();
        produk.setSoldCount(produk.getSoldCount() + 1);
        produkRepository.save(produk);
    }

    public void decreaseProdukCount(UUID idProduk){
        var produk = produkRepository.findById(idProduk).get();
        produk.setSoldCount(produk.getSoldCount() - 1);
        produkRepository.save(produk);
    }



}
