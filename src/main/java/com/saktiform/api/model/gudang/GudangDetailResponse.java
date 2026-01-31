package com.saktiform.api.model.gudang;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GudangDetailResponse {
    private Long id;
    private String namaGudang;
    private String alamat;

    private LokasiDto provinsi;
    private LokasiDto kota;
    private LokasiDto kecamatan;

    private Long idWorkspace;

    public GudangDetailResponse(Long id, String namaGudang, String alamat, Integer idProvinsi, String provinsi, Integer idKota, String kota, Integer idKecamatan, String kecamatan, Long idWorkspace) {
        this.id = id;
        this.namaGudang = namaGudang;
        this.alamat = alamat;
        this.provinsi = new LokasiDto(idProvinsi, provinsi);
        this.kota = new LokasiDto(idKota, kota);
        this.kecamatan = new LokasiDto(idKecamatan, kecamatan);
    }

    // ===== INNER CLASS =====
    public static class LokasiDto {
        private Integer id;
        private String nama;

        public LokasiDto() {}

        public LokasiDto(Integer id, String nama) {
            this.id = id;
            this.nama = nama;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getNama() {
            return nama;
        }

        public void setNama(String nama) {
            this.nama = nama;
        }
    }
}
