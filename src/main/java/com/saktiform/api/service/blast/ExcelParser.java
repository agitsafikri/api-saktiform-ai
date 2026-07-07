package com.saktiform.api.service.blast;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parser Excel (POI) untuk upload Blast. Header kanonik: phone_number, name (+ alias, lihat Appendix A PRD).
 */
@Component
public class ExcelParser {

    public record ParsedRow(int rowNumber, String rawName, String rawPhone) {}

    private static final Set<String> NAME_HEADERS = Set.of(
            "name", "nama", "nama kontak", "nama customer");
    private static final Set<String> PHONE_HEADERS = Set.of(
            "phone_number", "nomor hp", "no hp", "phone", "telepon", "nomor", "wa", "whatsapp");

    public List<ParsedRow> parse(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook wb = WorkbookFactory.create(is)) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("File Excel tidak memiliki sheet");
            }
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new IllegalArgumentException("Header kolom tidak ditemukan pada baris pertama");
            }

            DataFormatter fmt = new DataFormatter();
            int nameCol = -1;
            int phoneCol = -1;
            for (Cell c : header) {
                String h = fmt.formatCellValue(c).trim().toLowerCase();
                if (nameCol == -1 && NAME_HEADERS.contains(h)) nameCol = c.getColumnIndex();
                if (phoneCol == -1 && PHONE_HEADERS.contains(h)) phoneCol = c.getColumnIndex();
            }
            if (phoneCol == -1) {
                throw new IllegalArgumentException("Kolom nomor wajib ada (mis. header 'phone_number' / 'Nomor HP')");
            }

            List<ParsedRow> rows = new ArrayList<>();
            int firstData = sheet.getFirstRowNum() + 1;
            int last = sheet.getLastRowNum();
            for (int r = firstData; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String name = nameCol >= 0 ? cellString(row.getCell(nameCol), fmt) : null;
                String phone = cellString(row.getCell(phoneCol), fmt);
                if ((name == null || name.isBlank()) && (phone == null || phone.isBlank())) {
                    continue; // baris kosong → lewati
                }
                rows.add(new ParsedRow(r + 1, name, phone)); // r 0-based → +1 = nomor baris manusiawi
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Gagal membaca file Excel: " + e.getMessage(), e);
        }
    }

    private String cellString(Cell cell, DataFormatter fmt) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (!Double.isInfinite(d) && d == Math.rint(d)) {
                // integer (mis. nomor telepon) → hindari notasi ilmiah
                return new BigDecimal(cell.getNumericCellValue()).toBigInteger().toString();
            }
            return fmt.formatCellValue(cell).trim();
        }
        String v = fmt.formatCellValue(cell);
        return v == null ? null : v.trim();
    }
}
