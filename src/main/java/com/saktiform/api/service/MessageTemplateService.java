package com.saktiform.api.service;

import com.saktiform.api.entity.ChatTemplate;
import com.saktiform.api.model.chat.TemplateVariable;
import com.saktiform.api.model.template.AddChatTemplateDto;
import com.saktiform.api.model.template.ChatTemplateDetailDto;
import com.saktiform.api.model.template.ChatTemplateListDto;
import com.saktiform.api.model.template.DetailTemplate;
import com.saktiform.api.repository.ChatTemplateRepository;
import com.saktiform.api.service.chat.MessageConstructorHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MessageTemplateService {
    private final MessageConstructorHelper messageConstructorHelper;
    private final ChatTemplateRepository chatTemplateRepository;
    private final StorageService storageService;
    public MessageTemplateService(ChatTemplateRepository chatTemplateRepository, MessageConstructorHelper messageConstructorHelper, StorageService storageService) {
        this.chatTemplateRepository = chatTemplateRepository;
        this.messageConstructorHelper = messageConstructorHelper;
        this.storageService = storageService;
    }

    public Page<ChatTemplateListDto> getListTemplate(Long idWorkspace, Integer limit, Integer page){
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = chatTemplateRepository.getListChatTemplate(idWorkspace, pageable);
        result.getContent().forEach(dto ->
                dto.setMediaLink(dto.getMediaLink() != null ? storageService.getProdukPublicUrl(dto.getMediaLink()) : null));
        return result;
    }

    public DetailTemplate getDetail(UUID id){
        var template = chatTemplateRepository.findById(id).get();
        var detailTemplate = new DetailTemplate();
        detailTemplate.setId(template.getId());
        detailTemplate.setNamaTemplate(template.getNamaTemplate());
        detailTemplate.setContent(template.getContent());
        detailTemplate.setMediaLink(template.getMediaLink() != null ? storageService.getProdukPublicUrl(template.getMediaLink()) : null);
        return detailTemplate;

    }

    public void upsertMessageTemplate(AddChatTemplateDto data){
        ChatTemplate chatTemplate;
        if (data.getId() != null) {
            chatTemplate = chatTemplateRepository.findById(data.getId()).get();
        }else{
            chatTemplate = new ChatTemplate();
            chatTemplate.setCategory("QUICK_REPLY");
        }

        chatTemplate.setContent(data.getContent());
        chatTemplate.setNamaTemplate(data.getNamaTemplate());
        chatTemplate.setIdWorkspace(data.getIdWorkspace());
        chatTemplate.setMediaLink(data.getMediaLink() != null ? storageService.extractPathFromPublicUrl(data.getMediaLink()) : null);

        chatTemplateRepository.save(chatTemplate);
    }

    @Transactional
    public void deleteMessageTemplateById(UUID id){
        var chatTemplate = chatTemplateRepository.findById(id).get();
        if (chatTemplate.getCategory().contains("FOLLOWUP") || chatTemplate.getCategory().contains("CONFIRMATION")){
            throw new RuntimeException("Kategori template ini tidak bisa dihapus");
        }
        chatTemplateRepository.deleteChatTemplateById(id);
    }

    public ChatTemplateDetailDto getTemplateDetail(UUID idTemplate){
        var chatTemplate = chatTemplateRepository.findById(idTemplate).get();
        return new ChatTemplateDetailDto(chatTemplate.getId(), chatTemplate.getNamaTemplate(), chatTemplate.getContent());
    }

    public List<TemplateVariable> getTemplateVariables() {
        var templateVariables = new ArrayList<TemplateVariable>();
        templateVariables.add(new TemplateVariable("Nama Customer", "{nama_customer}"));
        templateVariables.add(new TemplateVariable("Telepon Customer", "{telepon_customer}"));
        templateVariables.add(new TemplateVariable("Nama Produk", "{nama_produk}"));
        templateVariables.add(new TemplateVariable("Atribut Produk", "{atribut_produk}"));
        templateVariables.add(new TemplateVariable("Harga Produk", "{harga_produk}"));
        templateVariables.add(new TemplateVariable("Berat Produk", "{berat_produk}"));
        templateVariables.add(new TemplateVariable("Kode Order", "{kode_order}"));
        templateVariables.add(new TemplateVariable("Status Order", "{status_order}"));
        templateVariables.add(new TemplateVariable("Tanggal Order", "{tanggal_order}"));
        templateVariables.add(new TemplateVariable("Tanggal Paid", "{tanggal_paid}"));
        templateVariables.add(new TemplateVariable("Ongkir", "{ongkir}"));
        templateVariables.add(new TemplateVariable("Diskon", "{diskon}"));
        templateVariables.add(new TemplateVariable("Total", "{total}"));
        templateVariables.add(new TemplateVariable("Alamat Customer", "{alamat_customer}"));
        templateVariables.add(new TemplateVariable("Provinsi Customer", "{provinsi_customer}"));
        templateVariables.add(new TemplateVariable("Kota Customer", "{kota_customer}"));
        templateVariables.add(new TemplateVariable("Kecamatan Customer", "{kecamatan_customer}"));
        templateVariables.add(new TemplateVariable("Metode Pembayaran", "{metode_pembayaran}"));
        templateVariables.add(new TemplateVariable("Deskripsi Transfer Bank", "{deskripsi_transfer_bank}"));
        templateVariables.add(new TemplateVariable("Alamat Gudang", "{alamat_gudang}"));
        templateVariables.add(new TemplateVariable("Provinsi Gudang", "{provinsi_gudang}"));
        templateVariables.add(new TemplateVariable("Kota Gudang", "{kota_gudang}"));
        templateVariables.add(new TemplateVariable("Kecamatan Gudang", "{kecamatan_gudang}"));


        return templateVariables;
    }

    public String getFollowUpText(Long idWorkspace, UUID idOrder, String type){
        var template = chatTemplateRepository.getByCategoryAndIdWorkspace(type, idWorkspace);
        var templateParam = messageConstructorHelper.buildOrderParams(idOrder);

        return messageConstructorHelper.fillTemplate(template.getContent(), templateParam);
    }
}
