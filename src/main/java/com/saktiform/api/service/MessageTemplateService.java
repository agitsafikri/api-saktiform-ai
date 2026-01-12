package com.saktiform.api.service;

import com.saktiform.api.entity.ChatTemplate;
import com.saktiform.api.model.chat.TemplateVariable;
import com.saktiform.api.model.template.AddChatTemplateDto;
import com.saktiform.api.model.template.ChatTemplateDetailDto;
import com.saktiform.api.model.template.ChatTemplateListDto;
import com.saktiform.api.repository.ChatTemplateRepository;
import com.saktiform.api.service.chat.MessageConstructorHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MessageTemplateService {
    private final MessageConstructorHelper messageConstructorHelper;
    private final ChatTemplateRepository chatTemplateRepository;
    public MessageTemplateService(ChatTemplateRepository chatTemplateRepository, MessageConstructorHelper messageConstructorHelper) {
        this.chatTemplateRepository = chatTemplateRepository;
        this.messageConstructorHelper = messageConstructorHelper;
    }

    public Page<ChatTemplateListDto> getListTemplate(Long idWorkspace, Integer limit, Integer page){
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return  chatTemplateRepository.getListChatTemplate(idWorkspace, pageable);
    }

    public void upsertMessageTemplate(AddChatTemplateDto data){
        ChatTemplate chatTemplate;
        if (data.getId() != null) {
            chatTemplate = chatTemplateRepository.findById(data.getId()).get();
        }else{
            chatTemplate = new ChatTemplate();
            chatTemplate.setCategory("QUICK_REPLY");
        }

        chatTemplate.setContent(data.getNamaTemplate());
        chatTemplate.setNamaTemplate(data.getContent());
        chatTemplate.setIdWorkspace(data.getIdWorkspace());

        chatTemplateRepository.save(chatTemplate);
    }

    public void deleteMessageTemplateById(UUID id){
        var chatTemplate = chatTemplateRepository.findById(id).get();
        if (chatTemplate.getCategory().equals("FOLLOWUP")){
            throw new RuntimeException("Template followup tidak bisa dihapus");
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
        templateVariables.add(new TemplateVariable("Alamat", "{alamat}"));
        templateVariables.add(new TemplateVariable("Provinsi", "{provinsi}"));
        templateVariables.add(new TemplateVariable("Kota", "{kota}"));
        templateVariables.add(new TemplateVariable("Kecamatan", "{kecamatan}"));
        templateVariables.add(new TemplateVariable("Metode Pembayaran", "{metode_pembayaran}"));


        return templateVariables;
    }

    public String getFollowUpText(Long idWorkspace, UUID idOrder){
        var template = chatTemplateRepository.getByCategoryAndIdWorkspace("FOLLOWUP", idWorkspace);
        var templateParam = messageConstructorHelper.buildOrderParams(idOrder);
        var followUpText = messageConstructorHelper.fillTemplate(template.getContent(), templateParam);

        return followUpText;
    }
}
