package com.saktiform.api.controller;

import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.model.RestResponse;
import com.saktiform.api.repository.AccountRepository;
import com.saktiform.api.service.blast.BlastAnalysisService;
import com.saktiform.api.service.blast.BlastImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

@RestController
@RequestMapping("/blast/import")
public class BlastImportController {

    private final BlastImportService importService;
    private final BlastAnalysisService analysisService;
    private final JwtManager jwtManager;
    private final AccountRepository accountRepository;

    public BlastImportController(BlastImportService importService,
                                 BlastAnalysisService analysisService,
                                 JwtManager jwtManager,
                                 AccountRepository accountRepository) {
        this.importService = importService;
        this.analysisService = analysisService;
        this.jwtManager = jwtManager;
        this.accountRepository = accountRepository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam Long workspaceId,
                                    HttpServletRequest request) {
        RestResponse rest = new RestResponse();
        try {
            Long accountId = resolveAccountId(request);
            var data = importService.handleUpload(file, workspaceId, accountId);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @PostMapping("/{importId}/analyze")
    public ResponseEntity<?> analyze(@PathVariable Long importId, @RequestParam Long workspaceId) {
        RestResponse rest = new RestResponse();
        try {
            var data = analysisService.analyze(importId, workspaceId);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/{importId}")
    public ResponseEntity<?> detail(@PathVariable Long importId, @RequestParam Long workspaceId) {
        RestResponse rest = new RestResponse();
        try {
            var data = importService.getImport(importId, workspaceId);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/{importId}/contacts")
    public ResponseEntity<?> contacts(@PathVariable Long importId,
                                      @RequestParam Long workspaceId,
                                      @RequestParam String category,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "10") Integer limit) {
        RestResponse rest = new RestResponse();
        try {
            var data = importService.getContacts(importId, workspaceId, category, page, limit);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/template-file")
    public ResponseEntity<?> templateFile() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Contacts");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("phone_number");
            header.createCell(1).setCellValue("name");
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("6281234567890");
            sample.createCell(1).setCellValue("Nama Customer");
            wb.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"blast_template.xlsx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (Exception e) {
            RestResponse rest = new RestResponse();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(rest);
        }
    }

    private Long resolveAccountId(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String username = jwtManager.getUsernameByToken(auth.substring(7));
            return accountRepository.findByUsername(username).map(a -> a.getId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
