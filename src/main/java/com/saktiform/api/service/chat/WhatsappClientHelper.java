package com.saktiform.api.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.saktiform.api.model.chat.GoWaSendMessageRequest;
import com.saktiform.api.model.chat.SendResults;
import com.saktiform.api.model.whatsapp.AddNewDeviceRequest;
import com.saktiform.api.model.whatsapp.WhatsappResponse;
import com.saktiform.api.model.whatsapp.envelopev2.LoginPairCodeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.net.MalformedURLException;

@Component
public class WhatsappClientHelper {
    @Value("${whatsapp.api.username}")
    private String username;

    @Value("${whatsapp.api.password}")
    private String password;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${whatsapp.api.url}")
    private String apiUrl;

    @Value("${whatsapp.multidevice.api.url}")
    private String apiMultiDeviceUrl;

//    public WhatsappResponse<SendResults> sendMessage(Integer port, GoWaSendMessageRequest req) {
//        String url = apiUrl +":"+ port + "/send/message";
//        return post(url, req, new ParameterizedTypeReference<WhatsappResponse<SendResults>>(){});
//    }
    public WhatsappResponse<SendResults> sendMessage(String deviceId, GoWaSendMessageRequest req) {
        String url = apiMultiDeviceUrl + "/send/message";
        return post(deviceId, url, req, new ParameterizedTypeReference<WhatsappResponse<SendResults>>(){});
    }


    public WhatsappResponse<SendResults> sendImage(int port, String phone, String caption, String imageUrl) {

        String url = apiUrl +":"+ port + "/send/image";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("phone", phone);
        body.add("caption", caption);
        body.add("image_url", imageUrl);

        return postMultipart(url, body,new ParameterizedTypeReference<WhatsappResponse<SendResults>>(){});
    }

    public WhatsappResponse<SendResults> sendAudio(int port, String phone,
                            String audioUrl) {

        String url = apiUrl +":"+ port + "/send/audio";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("phone", phone);
        body.add("audio_url", audioUrl);

        return postMultipart(url, body,new ParameterizedTypeReference<WhatsappResponse<SendResults>>(){});
    }

    public WhatsappResponse<SendResults> sendFile(int port, String phone, String caption,
                           String urlFile) throws MalformedURLException {

        String url = apiUrl +":"+ port + "/send/file";
        Resource resource = new UrlResource(urlFile);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("phone", phone);
        body.add("caption", caption);
        body.add("file", resource);

        return postMultipart(url, body,new ParameterizedTypeReference<WhatsappResponse<SendResults>>(){});
    }

    public WhatsappResponse<SendResults> sendVideo(int port, String phone, String caption,
                            String videoUrl) {

        String url = apiUrl +":"+ port + "/send/video";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("phone", phone);
        body.add("caption", caption);
        body.add("video_url", videoUrl);

        return postMultipart(url, body,new ParameterizedTypeReference<WhatsappResponse<SendResults>>(){});
    }

    public WhatsappResponse<JsonNode> connect(int port, String phoneNumber) {
        String url = apiUrl +":"+ port + "/app/login-with-code?phone="+phoneNumber;
        return get(url, WhatsappResponse.class);
    }



    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return headers;
    }

    private HttpHeaders buildHeaders(String deviceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Device-Id", deviceId);
        return headers;
    }

    public <T> T get(String url, Class<T> responseType) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        return restTemplate.exchange(url, HttpMethod.GET, entity, responseType).getBody();
    }

    public <T> T post(String deviceId, String url, Object body, ParameterizedTypeReference<T> responseType) {
        HttpEntity<Object> entity = new HttpEntity<>(body, buildHeaders(deviceId));
        return restTemplate.exchange(url, HttpMethod.POST, entity, responseType).getBody();
    }

    public <T> T delete(String url, Class<T> responseType) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        return restTemplate.exchange(url, HttpMethod.DELETE, entity, responseType).getBody();
    }

    public  <T> T postMultipart(String url, MultiValueMap<String, Object> body, ParameterizedTypeReference<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

        return restTemplate.exchange(url, HttpMethod.POST, requestEntity, responseType).getBody();
    }

    //=======================================
    public WhatsappResponse<Object> addNewDevice(AddNewDeviceRequest payload) {
        String url =apiMultiDeviceUrl + "/devices";
        return post(
                "",
                url,
                payload,
                new ParameterizedTypeReference<WhatsappResponse<Object>>() {}
        );
    }

    public WhatsappResponse<LoginPairCodeResponse> connectMultiDevice(String phoneNumber, String deviceId) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(deviceId));
        String url = apiMultiDeviceUrl + "/app/login-with-code?phone="+phoneNumber;

        return restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<WhatsappResponse<LoginPairCodeResponse>>(){}).getBody();
    }



    public WhatsappResponse<Object> reconnect(int port, String deviceId) {
        String url = apiUrl + ":" + port + "/devices/" + deviceId + "/reconnect";

        return post(
                "",
                url,
                null,
                new ParameterizedTypeReference<WhatsappResponse<Object>>() {}
        );
    }





}
