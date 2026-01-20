package com.saktiform.api.service;

import com.saktiform.api.entity.WhatsappBusinessApi;
import com.saktiform.api.model.ErrorResponse;
import com.saktiform.api.model.whatsapp.*;
import com.saktiform.api.model.whatsapp.envelopev2.LoginPairCodeResponse;
import com.saktiform.api.repository.WhatsappBusinessApiRepository;
import com.saktiform.api.service.chat.WhatsappClientHelper;
import com.saktiform.api.util.ErrorParser;
import com.saktiform.api.util.PhoneNumberUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class WhatsappInstanceService {
    WhatsappBusinessApiRepository whatsappBusinessApiRepository;
    WhatsappClientHelper client;

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int START_PORT = 3100;
    private static final int END_PORT = 3199;
    private static final String BASE_DIR = "/var/lib/whatsapp";
    @Value("${saktiform.api.url}")
    private String saktiformApiUrl;

    @Value("${whatsapp.multidevice.api.url}")
    private String whatsappMultiDeviceUrl;


    public WhatsappInstanceService(WhatsappBusinessApiRepository whatsappBusinessApiRepository, WhatsappClientHelper client) {
        this.whatsappBusinessApiRepository = whatsappBusinessApiRepository;
        this.client = client;
    }

    public Page<WabaListDto> getListWhatsapp (Integer page, Integer limit){
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return whatsappBusinessApiRepository.getListWaba(pageable);
    }

    public List<AvailableWhatsappResponse> getAvailableWhatsapp (){
        return whatsappBusinessApiRepository.getAvailableWhatsapp();
    }

    public  void registerWhatsapp(RegisterWhatsappDto data) throws InterruptedException {


        var formatedPhoneNumber = PhoneNumberUtil.normalizeToIndonesianFormat(data.getNomorWhatsapp()).replace("+62", "62");

        if (whatsappBusinessApiRepository.findByNomorWhatsapp(formatedPhoneNumber) != null) {
            throw new RuntimeException("Nomor whatsapp sudah terdaftar");
        }

        var port = allocatePort();
        deploy(formatedPhoneNumber, port);

        WhatsappBusinessApi whatsappBusinessApi = new WhatsappBusinessApi();
        whatsappBusinessApi.setNomorWhatsapp(formatedPhoneNumber);
        whatsappBusinessApi.setPort(port);
        whatsappBusinessApi.setStatus("ONLINE");

        whatsappBusinessApiRepository.save(whatsappBusinessApi);
    }

    public WhatsappResponse registerWhatsappMultiDevice(RegisterWhatsappDto data) throws InterruptedException {


        var formatedPhoneNumber = PhoneNumberUtil.normalizeToIndonesianFormat(data.getNomorWhatsapp()).replace("+62", "62");

        if (whatsappBusinessApiRepository.findByNomorWhatsapp(formatedPhoneNumber) != null) {
            throw new RuntimeException("Nomor whatsapp sudah terdaftar");
        }

        WhatsappBusinessApi whatsappBusinessApi = new WhatsappBusinessApi();
        whatsappBusinessApi.setNomorWhatsapp(formatedPhoneNumber);
        whatsappBusinessApi.setCreatedAt(Instant.now());
        whatsappBusinessApi.setStatus("DISCONNECTED");

        var waba = whatsappBusinessApiRepository.save(whatsappBusinessApi);
        var newDeviceRequest = new AddNewDeviceRequest(waba.getId());
        var response = client.addNewDevice(newDeviceRequest);

        WhatsappResponse<LoginPairCodeResponse> connectResponse;
        if ("SUCCESS".equals(response.getCode())){
            connectResponse = client.connectMultiDevice(waba.getNomorWhatsapp(), waba.getId().toString());
            return connectResponse;
        }

        return response;




    }

    public WhatsappResponse connect(UUID wabaId){
        try {
            var waba = whatsappBusinessApiRepository.findById(wabaId).get();
            return client.connect(waba.getPort(), waba.getNomorWhatsapp());
        }catch (HttpClientErrorException e){
            ErrorResponse error = ErrorParser.parseError(e.getResponseBodyAsString());
            if (error != null) {
                throw new RuntimeException(error.getMessage());
            }
            throw new RuntimeException(e.getResponseBodyAsString());
        }catch (ResourceAccessException e){
            throw new RuntimeException("Whatsapp Server not running");
        }

    }

    public WhatsappResponse connectMultiDevice(UUID wabaId){
        try {
            var waba = whatsappBusinessApiRepository.findById(wabaId).get();

            return client.connectMultiDevice(waba.getNomorWhatsapp(), waba.getId().toString());
        }catch (HttpClientErrorException e){
            ErrorResponse error = ErrorParser.parseError(e.getResponseBodyAsString());
            if (error != null) {
                if(error.getCode().equals("ALREADY_LOGGED_IN") ){
                    var waba = whatsappBusinessApiRepository.findById(wabaId).get();
                    waba.setStatus("CONNECTED");
                    whatsappBusinessApiRepository.save(waba);
                }
                throw new RuntimeException(error.getMessage());
            }
            throw new RuntimeException(e.getResponseBodyAsString());
        }catch (ResourceAccessException e){
            throw new RuntimeException("Whatsapp Server not running");
        }

    }

    private void deploy(String noHp, Integer port){
        try {
            // 2. Buat folder khusus
            Path dir = Paths.get(BASE_DIR, "whatsapp-"+port.toString());
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            // 3. Generate file docker-compose.yml
            String dockerComposeContent = generateDockerCompose(port);
            Path composeFile = dir.resolve("docker-compose.yml");
            Files.writeString(composeFile, dockerComposeContent);

            // 4. Jalankan docker compose
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "compose", "up", "-d"
            );
            // Set working directory ke folder nomor HP
            pb.directory(new File(dir.toAbsolutePath().toString()));

            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Container failed to start");
            }
        }catch (Exception e){
            ErrorResponse error = ErrorParser.parseError(e.fillInStackTrace().getMessage());
            if (error != null) {
                throw new RuntimeException(error.getMessage());
            }
            throw new RuntimeException(e.fillInStackTrace().getMessage());
        }
    }

    private String generateDockerCompose(int port) {
        return """
                services:
                  whatsapp-%s:
                    image: aldinokemal2104/go-whatsapp-web-multidevice:v7.9.0
                    container_name: whatsapp-%s
                    restart: always
                    ports:
                      - "%d:%d"
                    volumes:
                      - whatsapp-%s:/app/storages
                    command:
                      - rest
                      - --basic-auth=admin:admin
                      - --port=%d
                      - --debug=false
                      - --os=Chrome
                      - --account-validation=false
                      - --webhook="%s/whatsapp/%s/webhook"
                """.formatted(port, port, port, port, port, port, saktiformApiUrl, port);
    }

    public int allocatePort() {
        List<Integer> usedPorts = whatsappBusinessApiRepository.findAllUsedPorts();
        for (int port = START_PORT; port <= END_PORT; port++) {
            if (!usedPorts.contains(port)) {
                return port;
            }
        }
        throw new RuntimeException("Sudah mencapai batas maksimal mendaftarkan nomor whatsapp");
    }
}
