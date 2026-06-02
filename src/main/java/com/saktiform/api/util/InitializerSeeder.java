package com.saktiform.api.util;

import com.saktiform.api.entity.Account;
import com.saktiform.api.entity.AppConfig;
import com.saktiform.api.model.account.Role;
import com.saktiform.api.repository.AccountRepository;
import com.saktiform.api.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class InitializerSeeder implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppConfigRepository appConfigRepository;

    @Value("${app.superadmin.username}")
    private String username;

    @Value("${app.superadmin.password}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (!accountRepository.existsByUsername(username)){
            Account user = new Account();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setNama("Admin Saktiform");
            user.setRole(Role.OWNER);
            accountRepository.save(user);
        }





        if(!appConfigRepository.existsByConfigName("ai.system.prompt")) {
            AppConfig appConfig = new AppConfig();
            appConfig.setConfigName("ai.system.prompt");
            appConfig.setCreatedAt(Instant.now());

            appConfig.setConfig("""
                    Anda adalah AI Customer Service resmi toko kami.
                    
                    TUGAS:
                    Jawab pertanyaan terakhir user berdasarkan konteks percakapan dan data sistem yang diberikan.
                    
                    ATURAN PERILAKU:
                    - Gunakan Bahasa Indonesia yang sopan, ramah, dan natural.
                    - Jawaban maksimal 3 paragraf pendek.
                    - Fokus hanya pada pertanyaan terakhir user.
                    - Jangan menambahkan informasi yang tidak relevan.
                    
                    ATURAN DATA:
                    - Gunakan hanya informasi yang tersedia pada bagian DATA SISTEM (misalnya DATA ORDER atau DATA PRODUK).
                    - Dilarang mengarang harga, status, stok, resi, atau detail lainnya.
                    - Jika informasi tidak tersedia dalam DATA SISTEM, jawab: NULL
                    - Jika terdapat perbedaan antara asumsi user dan DATA SISTEM, prioritaskan DATA SISTEM.
                    
                    OUTPUT:
                    - Jika dapat dijawab → berikan jawaban customer service secara natural.
                    - Jika tidak dapat dijawab berdasarkan DATA SISTEM → jawab tepat: NULL
                    - Jangan menambahkan teks lain selain jawaban.
                    """);
            appConfig.setUpdatedAt(Instant.now());
            appConfig.setDeskripsi("Prompt utama yang mengatur perilaku AI saat menghasilkan jawaban customer service");
            appConfigRepository.save(appConfig);
        }

        if(!appConfigRepository.existsByConfigName("ai.guardrail.prompt")) {
            AppConfig appConfig = new AppConfig();
            appConfig.setConfigName("ai.guardrail.prompt");
            appConfig.setCreatedAt(Instant.now());

            appConfig.setConfig("""
                Anda adalah AI validator.

                Tugas Anda hanya mengklasifikasikan pesan user.
            
                Jika pesan:
                - marah
                - komplain agresif
                - menggunakan kata kasar
                - di luar konteks toko (produk, order, pembayaran, pengiriman, kebijakan)
                - spam / nonsense
                - testing AI
            
                Maka jawab:
                BLOCK
            
                Jika pesan normal dan relevan dengan toko, jawab:
                ALLOW
            
                Jawab hanya satu kata: ALLOW atau BLOCK.
                """);
            appConfig.setUpdatedAt(Instant.now());
            appConfig.setDeskripsi("Prompt khusus untuk memvalidasi apakah pesan user boleh dijawab oleh AI atau harus diblokir");
            appConfigRepository.save(appConfig);
        }

        if(!appConfigRepository.existsByConfigName("bot.default.quota")) {
            AppConfig appConfig = new AppConfig();
            appConfig.setConfigName("bot.default.quota");
            appConfig.setCreatedAt(Instant.now());

            appConfig.setConfig("3");
            appConfig.setUpdatedAt(Instant.now());
            appConfig.setDeskripsi("Batas jumlah penggunaan bot AI per conversation atau per user");
            appConfigRepository.save(appConfig);
        }

        if(!appConfigRepository.existsByConfigName("ai.temperature")) {
            AppConfig appConfig = new AppConfig();
            appConfig.setConfigName("ai.temperature");
            appConfig.setCreatedAt(Instant.now());

            appConfig.setConfig("0.3");
            appConfig.setUpdatedAt(Instant.now());
            appConfig.setDeskripsi("Tingkat kreativitas dan variasi jawaban AI, range: 0.0 – 1.0, Semakin tinggi → semakin kreatif ");
            appConfigRepository.save(appConfig);
        }

        if(!appConfigRepository.existsByConfigName("openai.model")) {
            AppConfig appConfig = new AppConfig();
            appConfig.setConfigName("openai.model");
            appConfig.setCreatedAt(Instant.now());

            appConfig.setConfig("gpt-4o-mini");
            appConfig.setUpdatedAt(Instant.now());
            appConfig.setDeskripsi("Model OpenAI yang digunakan untuk menghasilkan jawaban AI");
            appConfigRepository.save(appConfig);
        }

        if(!appConfigRepository.existsByConfigName("ai.max.tokens")) {
            AppConfig appConfig = new AppConfig();
            appConfig.setConfigName("ai.max.tokens");
            appConfig.setCreatedAt(Instant.now());

            appConfig.setConfig("250");
            appConfig.setUpdatedAt(Instant.now());
            appConfig.setDeskripsi("Batas maksimal token yang dapat dihasilkan oleh AI");
            appConfigRepository.save(appConfig);
        }

        if(!appConfigRepository.existsByConfigName("bot.reply.delay")) {
            AppConfig appConfig = new AppConfig();
            appConfig.setConfigName("bot.reply.delay");
            appConfig.setCreatedAt(Instant.now());

            appConfig.setConfig("3");
            appConfig.setUpdatedAt(Instant.now());
            appConfig.setDeskripsi("Waktu tunggu (dalam detik) sebelum bot membalas pesan user untuk menggabungkan beberapa pesan menjadi satu respons.");
            appConfigRepository.save(appConfig);
        }


    }
}

