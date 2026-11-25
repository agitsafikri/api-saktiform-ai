# TDD — Chat Template Multiple Image (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature name | Chat Template Multiple Image — Frontend |
| Dokumen induk | [PRD FE](../prd/chat-template-multiple-image-frontend.md) · [PRD Backend](../prd/chat-template-multiple-image.md) · [TDD Backend](chat-template-multiple-image.md) |
| Bergantung pada | [TDD FE Chat Multiple Image](chat-multiple-image-frontend.md) (composer multi lampiran) — TDD ini mendefinisikan **delta** atasnya |
| Repo | `FE/dashboard`, branch `dev` (**tarik versi terbaru** — snapshot lokal `e7825d2` usang) |
| Status | **Ready for Implementation** — keputusan PRD FE §11 resolved (default, kecuali Q3: tanpa pengurutan gambar) |
| Last updated | 2026-10-15 |

> Snippet adalah **acuan desain**. Desain mengikuti keputusan PRD FE §11: rekomendasi default Q1, Q2, Q4–Q7; Q3 = **tanpa mekanisme pengurutan gambar**. Nama variabel/file di `dev` terbaru dapat berbeda dari snapshot; sesuaikan tanpa mengubah perilaku.

---

## Daftar Isi

1. [Baseline & Kontrak API](#1-baseline--kontrak-api)
2. [Struktur File](#2-struktur-file)
3. [Bagian A — Form Template](#3-bagian-a--form-template)
4. [Bagian B — Indikator Jumlah Gambar](#4-bagian-b--indikator-jumlah-gambar)
5. [Bagian C — Quick Chat → Composer](#5-bagian-c--quick-chat--composer)
6. [Traceability PRD → Implementasi](#6-traceability-prd--implementasi)
7. [Testing & QA](#7-testing--qa)
8. [Rencana Implementasi & Rilis](#8-rencana-implementasi--rilis)

---

## 1. Baseline & Kontrak API

### 1.1 Kode existing (dari snapshot + dokumentasi FE)

| File | Kondisi |
|---|---|
| `pengaturan/workspace-management/template-chat/stores/templateChatStore.ts` | `initTemplateChat()` → `{id, idWorkspace, namaTemplate, content}`; versi terbaru (menurut `docs/frontend`) ditambah `mediaLink` + action `onUploadMedia(file)` → `POST /master/saktiform-media`. `onDetail` menimpa `this.item` dengan `response.data.data`. |
| `.../components/ModalFormTemplateChat.vue` | Form nama + konten + variabel; versi terbaru: `FileUpload` tunggal, upload saat dipilih. `submitData` mengirim `{...templateChatStore.item, idWorkspace}`. |
| `.../components/ListWorkspaceTemplateChat.vue` | `Table` dengan kolom config + slot per `fieldName` (`#action="props"` → `props.row`). `openModal('edit', row)` menyalin row ke `item`. |
| `components/common/MultipleFileUpload.vue` | Props `files, size, acceptedFiles, maxFiles, width, height, ...`; emit `onUpload(list)` setiap tambah/hapus. Item: `{url, name, size, file, type}`. Hanya validasi ukuran; tidak ada urutan. Dipakai `FormProduk` (`maxFiles=5`). |
| `modules/chat/components/ChatDetail.vue` | `handleSelectTemplate` → `chatStore.generateQuickChatMessage()` → `messageText = res.data.message`. Modal quick chat menampilkan `namaTemplate` + `kategori`. |
| `FormProduk.vue` (`mappingPayload`) | Pola acuan: item tanpa `file` = URL lama; item ber-`file` di-upload saat submit, hasil URL dimasukkan berurutan. |

### 1.2 Kontrak backend (sudah live di backend)

| Endpoint | Perubahan |
|---|---|
| `POST /template` | Body + `mediaLinks?: string[]` (maks 10, diprioritaskan). `mediaLink` dipakai hanya bila `mediaLinks` tidak dikirim. Update = replace. |
| `GET /template` (item), `GET /template/{id}` | + `mediaLinks: string[]` (selalu array) ; `mediaLink` = elemen pertama / `null`. |
| `POST /conversation/quick-chat` | `data: { message: string; mediaLinks: string[] }`. |
| `POST /send-message` | `mediaLink: string[]` boleh berisi URL bucket produk (gambar template); backend menyalinnya ke bucket chat. |
| `POST /master/saktiform-media` | Hanya gambar; raster → WebP; response `data` = public URL bucket produk. |

---

## 2. Struktur File

```
src/
├── components/common/MultipleFileUpload.vue                      (tidak diubah — dipakai apa adanya)
└── modules/
    ├── pengaturan/workspace-management/template-chat/
    │   ├── types.ts                                              (baru)
    │   ├── stores/templateChatStore.ts                           (ubah)
    │   └── components/
    │       ├── ModalFormTemplateChat.vue                         (ubah)
    │       └── ListWorkspaceTemplateChat.vue                     (ubah: kolom gambar)
    └── chat/
        ├── types.ts                                              (ubah: ChatAttachment union, QuickChatResult)
        ├── components/ChatDetail.vue                             (ubah: apply gambar template)
        └── store/chatStore.ts                                    (ubah: tipe generateQuickChatMessage)
src/pages/chat/index.vue                                          (ubah: kirim campuran lokal + template)
```

---

## 3. Bagian A — Form Template

### 3.1 Tipe (`template-chat/types.ts`)

```ts
/** Bentuk item MultipleFileUpload. file=null → gambar tersimpan (URL). */
export interface TemplateMediaItem {
  url: string;
  file: File | null;
  type: string;
  name?: string;
  size?: number;
}

export const TEMPLATE_MAX_IMAGES = 10;                       // selaras backend chat.template.max-images
export const TEMPLATE_MAX_FILE_BYTES = 5_000_000;            // PRD FE Q2 (konvensi MultipleFileUpload: 1 MB = 1_000_000)
export const TEMPLATE_ACCEPT = '.jpg, .jpeg, .png, .webp';
export const TEMPLATE_MIME = ['image/jpeg', 'image/png', 'image/webp'];
```

### 3.2 Store (`templateChatStore.ts`)

```ts
export const initTemplateChat = () => ({
  id: null as string | null,
  idWorkspace: null,
  namaTemplate: '',
  content: '',
  mediaLink: null as string | null,
  mediaLinks: [] as string[],
  fileMedia: [] as TemplateMediaItem[],      // state form saja, tidak dikirim
});

export const initInfoTemplateChat = () => ({
  namaTemplate: { type: 'info', message: '' },
  content: { type: 'info', message: '' },
  fileMedia: { type: 'info', message: '' },
});

/** Response lama (tanpa mediaLinks) tetap didukung — NFR-3. */
export const toMediaLinks = (data: { mediaLinks?: string[] | null; mediaLink?: string | null }) =>
  data.mediaLinks?.length ? data.mediaLinks : data.mediaLink ? [data.mediaLink] : [];
```

`onDetail`:

```ts
const data = response.data.data;
this.item = {
  ...initTemplateChat(),
  ...data,
  fileMedia: toMediaLinks(data).map((url) => ({ url, file: null, type: 'image/*' })),
};
```

`onUploadMedia(file)` existing dipakai ulang (tanpa toast sukses per file agar tidak spam; error tetap via `errorHelper`). Bila action belum ada di `dev`, tambahkan:

```ts
async onUploadMedia(file: File): Promise<any> {
  try {
    const form = new FormData();
    form.append('file', file);
    const response: any = await postData('api_url', '/master/saktiform-media', form);
    return response.data;                  // { success, data: publicUrl }
  } catch (err: any) {
    return errorHelper(err);
  }
},
```

### 3.3 `MultipleFileUpload.vue` — tidak diubah

PRD FE §11 Q3: **tidak ada mekanisme pengurutan**. Komponen dipakai apa adanya (sama seperti `FormProduk`), sehingga form produk tidak terdampak (NFR-1).

Perilaku urutan yang dihasilkan (FR-4):
- Mode edit: gambar tersimpan dimuat sesuai `mediaLinks`.
- Tombol "+" menambah slot di **akhir** → gambar baru selalu di belakang.
- Hapus memakai `splice` → gambar sesudahnya bergeser maju; urutan relatif tetap.
- Untuk mengubah urutan, admin menghapus lalu menambah ulang gambar.

Catatan: komponen me-`ref(props.files)` sehingga memutasi array prop dan memakai slot kosong `{}` (PRD R-3) → parent selalu menyimpan hasil `onUpload` ke store (pola `FormProduk`) dan memfilter item tanpa `url` saat menyusun payload.

### 3.4 `ModalFormTemplateChat.vue`

Ganti `FileUpload` tunggal dengan:

```vue
<MultipleFileUpload
  class="m-8-top"
  label="Gambar Template"
  :files="templateChatStore.item.fileMedia"
  :size="TEMPLATE_MAX_FILE_BYTES"
  :accepted-files="TEMPLATE_ACCEPT"
  :max-files="TEMPLATE_MAX_IMAGES"
  :width="100"
  :height="100"
  :disabled="loading === 'submit-data'"
  :message-type="templateChatStore.info.fileMedia.type"
  :message="templateChatStore.info.fileMedia.message"
  @on-upload="onMediaChange" />
<p class="fz-px-12 neutral-500 m-4-top">
  {{ filledMedia.length }}/{{ TEMPLATE_MAX_IMAGES }} gambar · gambar dikirim sesuai urutan yang tampil
</p>
```

```ts
const filledMedia = computed(() =>
  templateChatStore.item.fileMedia.filter((m) => m && m.url)          // buang slot kosong {} (R-3)
);

/** FR-2: MultipleFileUpload hanya cek ukuran; tipe dicek di sini. */
const onMediaChange = (list: TemplateMediaItem[]) => {
  const invalid = list.some((m) => m?.file && !TEMPLATE_MIME.includes(m.file.type));
  if (invalid) alertStore.setAlert('Format gambar harus JPG, PNG, atau WEBP', 'danger');
  templateChatStore.item.fileMedia = list.filter((m) => !m?.file || TEMPLATE_MIME.includes(m.file.type));
};

/** FR-6, FR-7: upload berurutan; satu gagal → batal, null. */
const resolveMediaLinks = async (): Promise<string[] | null> => {
  const urls: string[] = [];
  for (const item of filledMedia.value) {
    if (!item.file) { urls.push(item.url); continue; }
    const res = await templateChatStore.onUploadMedia(item.file);
    if (!res?.success || !res.data) return null;
    urls.push(res.data as string);
  }
  return urls;
};
```

`submitData` (setelah `validateData()` lolos):

```ts
const mediaLinks = await resolveMediaLinks();
if (mediaLinks === null) {
  alertStore.setAlert('Gagal mengunggah gambar. Template belum disimpan, silahkan coba lagi.', 'danger');
  loading.value = null;
  return;                                                    // form & pilihan gambar tetap (FR-7)
}

// Payload eksplisit — jangan spread item (fileMedia/kategori tidak dikirim). FR-8
const payload = {
  id: templateChatStore.item.id,
  idWorkspace: idWorkspace,
  namaTemplate: templateChatStore.item.namaTemplate,
  content: templateChatStore.item.content,
  mediaLinks,
  mediaLink: mediaLinks[0] ?? null,                          // G-4: backend lama tetap simpan gambar pertama
};
const response = await templateChatStore.onStore(payload);
```

- Gambar yang sudah ter-upload lalu simpan gagal: saat retry, item masih ber-`file` sehingga di-upload ulang (file yatim di storage — diterima, sama dengan pola produk). Opsional: setelah upload sukses, ganti item menjadi `{ url, file: null }` agar retry tidak upload ulang.
- `idWorkspace` mengikuti sumber existing di `dev` terbaru (prop `workspaceId` dengan fallback `route.params.id`).
- Hapus handler upload-saat-pilih (`FileUpload` lama) beserta state terkait.

---

## 4. Bagian B — Indikator Jumlah Gambar

### 4.1 `ListWorkspaceTemplateChat.vue` (FR-12)

Tambah kolom sebelum `action`:

```ts
{ label: 'Gambar', fieldName: 'mediaLinks', isSortable: false, class: 'text-align-center' },
```

```vue
<template #mediaLinks="props">
  <span v-if="toMediaLinks(props.row).length" class="d-inline-flex align-items-center gap-4">
    <image-outline class="primary" :size="16" /> {{ toMediaLinks(props.row).length }}
  </span>
  <span v-else class="neutral-500">-</span>
</template>
```

### 4.2 Modal quick chat di `ChatDetail.vue` (FR-13)

Di item template (di bawah `kategori`):

```vue
<div v-if="item.mediaLinks?.length" class="fz-px-12 primary d-flex align-items-center gap-4 m-4-top">
  <image-outline :size="14" /> {{ item.mediaLinks.length }} gambar
</div>
```

---

## 5. Bagian C — Quick Chat → Composer

### 5.1 Delta tipe (`modules/chat/types.ts`)

Ganti `ChatAttachment` dan `SendDoneResult` dari TDD FE Chat Multiple Image §3:

```ts
interface AttachmentBase { id: string; isImage: boolean; previewUrl: string | null; }

/** Dipilih dari perangkat → di-upload saat kirim. */
export interface LocalAttachment extends AttachmentBase { source: 'local'; file: File; }

/** Dari template → URL sudah publik, tidak di-upload ulang (FR-19). */
export interface TemplateAttachment extends AttachmentBase { source: 'template'; url: string; isImage: true; previewUrl: string; }

export type ChatAttachment = LocalAttachment | TemplateAttachment;

export interface SendDoneResult {
  ok: boolean;
  /** id attachment yang belum terkirim (gagal sebagian). Pakai id, bukan File/URL — URL template bisa duplikat. */
  remainingIds?: string[];
  keepText?: boolean;
}

export interface QuickChatResult { message: string; mediaLinks?: string[]; }
```

Emit `ChatDetail`: `(e: 'send-message', text: string, attachments: ChatAttachment[], done: SendDoneCallback)`.

Penyesuaian kode TDD FE Chat Multiple Image:

| Lokasi | Sebelum | Sesudah |
|---|---|---|
| `handleFileSelect` push | `{ id, file, isImage, previewUrl }` | + `source: 'local'` |
| `revoke` | `if (a.previewUrl) revoke` | `if (a.source === 'local' && a.previewUrl) revoke` (URL template bukan object URL) |
| `keepOnly(files: File[])` | cocokkan `File` | `keepOnly(ids: string[])` cocokkan `a.id` |
| Emit kirim | `attachments.map((a) => a.file)` | `[...attachments.value]` |
| Markup dokumen di strip | `item.file.name` | hanya untuk `source === 'local'` (template selalu gambar) |

### 5.2 `chatStore.generateQuickChatMessage`

Tidak ada perubahan logika; ketik `data` sebagai `QuickChatResult`.

### 5.3 `ChatDetail.vue` — memasukkan gambar template (FR-14…FR-18)

```ts
const newId = () => `${Date.now()}-${Math.random().toString(36).slice(2)}`;

const applyTemplateImages = (urls: string[]) => {
  // FR-16: buang lampiran template sebelumnya, pertahankan lampiran lokal
  const locals = attachments.value.filter((a) => a.source === 'local');
  attachments.value = locals;
  if (!urls.length) return;

  // FR-18: dokumen tidak bisa digabung dengan gambar
  if (locals.some((a) => !a.isImage)) {
    alertStore.setAlert('Hapus dokumen terlebih dahulu untuk menambahkan gambar template', 'danger');
    return;
  }

  // FR-17: kuota
  const quota = Math.max(0, MAX_IMAGES - locals.length);
  const taken = urls.slice(0, quota);
  if (taken.length < urls.length) {
    alertStore.setAlert(`Hanya ${taken.length} gambar template yang ditambahkan (maksimal ${MAX_IMAGES} gambar)`, 'danger');
  }

  // PRD FE Q6: gambar template di depan lampiran lokal
  attachments.value = [
    ...taken.map<TemplateAttachment>((url) => ({ id: newId(), source: 'template', url, isImage: true, previewUrl: url })),
    ...locals,
  ];
};
```

`handleSelectTemplate` — di cabang `res.success`:

```ts
messageText.value = res.data.message;
applyTemplateImages(res.data.mediaLinks ?? []);
showFilterModal.value = false;
```

- Guard `isSending`: jangan izinkan memilih template saat mengirim (`if (isSending.value) return` di awal `handleSelectTemplate`).
- Watcher `props.chat?.id` sudah memanggil `clearAttachments()` → lampiran template ikut bersih (FR-21).
- Counter `imageCount` sudah menghitung semua `isImage` → template ikut terhitung (FR-15).

### 5.4 `pages/chat/index.vue` — `handleSendMessage` (FR-19, FR-20)

Ganti cabang upload + kirim dari TDD FE Chat Multiple Image §7:

```ts
const handleSendMessage = async (text: string, items: ChatAttachment[], done: SendDoneCallback) => {
  // ...cabang TEXT existing (items.length === 0) tidak berubah

  // Upload hanya lampiran lokal; template sudah berupa URL
  const locals = items.filter((a): a is LocalAttachment => a.source === 'local');
  const urlById = new Map<string, string>();
  if (locals.length) {
    const { urls, failedCount } = await chatStore.uploadChatFiles(locals.map((a) => a.file));
    if (failedCount > 0) {
      alertStore.setAlert(`${failedCount} file gagal diunggah. Silahkan coba lagi.`, 'danger');
      return done({ ok: false });
    }
    locals.forEach((a, i) => urlById.set(a.id, urls[i] as string));
  }

  // Urutan = urutan strip
  const mediaLink = items.map((a) => (a.source === 'template' ? a.url : urlById.get(a.id)!));
  const first = items[0];
  const messageType = first.source === 'template' ? 'IMAGE' : resolveMessageType(first.file);

  const res = await chatStore.sendMessage({
    conversationId, messageType, mediaLink, message: caption, repliedMessageId: null,
  });

  if (res.success) return done({ ok: true });

  alertStore.setAlert(res.message || 'Jaringan Bermasalah', 'danger');
  const sent = (res.data as SendMessageResult | undefined)?.sent ?? 0;
  if (sent > 0) return done({ ok: false, remainingIds: items.slice(sent).map((a) => a.id), keepText: false });
  return done({ ok: false });
};
```

- Backend menjamin urutan kirim = urutan `mediaLink`, dan `sent` = jumlah dari depan → `items.slice(sent)` tepat.
- Error salin bucket (`Gagal menyalin media template ke penyimpanan chat`) datang sebagai 400 sebelum ada yang terkirim → cabang `done({ ok: false })`, composer utuh.
- Bila retry setelah gagal, lampiran lokal di-upload ulang (sama dengan perilaku TDD FE Chat Multiple Image).

---

## 6. Traceability PRD → Implementasi

| PRD FE | Implementasi |
|---|---|
| FR-1, FR-3 | `MultipleFileUpload` (`maxFiles=10`) di §3.4 |
| FR-2 | `TEMPLATE_ACCEPT`, `TEMPLATE_MAX_FILE_BYTES`, `onMediaChange` |
| FR-4 | Urutan = urutan slot `MultipleFileUpload` (§3.3), tanpa fitur pengurutan |
| FR-5 | `onDetail` + `toMediaLinks` (§3.2) |
| FR-6, FR-7, FR-10 | `resolveMediaLinks` + `submitData` (§3.4) |
| FR-8, FR-9 | Payload eksplisit `mediaLinks` + `mediaLink` |
| FR-11 | Toast existing `onStore` (`err.response.data.message`) |
| FR-12, FR-13 | §4.1, §4.2 |
| FR-14…FR-18 | `applyTemplateImages` (§5.3) |
| FR-19, FR-20 | `handleSendMessage` (§5.4) + `remainingIds` |
| FR-21 | Watcher conversation existing → `clearAttachments` |
| NFR-1 | `MultipleFileUpload` tidak diubah |
| NFR-3 | `toMediaLinks` fallback |

---

## 7. Testing & QA

### 7.1 Otomatis

Repo tidak punya test runner; gate = `npm run build` (`vue-tsc -b && vite build`) tanpa error tipe baru. Bila Vitest ditambahkan kemudian, kandidat unit test: `toMediaLinks`, `applyTemplateImages` (dipindah ke util murni), penyusunan `mediaLink` di §5.4.

### 7.2 QA manual (DevTools Network terbuka)

| # | Skenario | Ekspektasi |
|---|---|---|
| 1 | Tambah template, 3 gambar, simpan | 3× `POST /master/saktiform-media` berurutan, lalu `POST /template` dengan `mediaLinks` 3 + `mediaLink` = pertama |
| 2 | Edit template tsb | 3 preview sesuai urutan; `GET /template/{id}` berisi `mediaLinks` |
| 3 | Edit template 2 gambar, tambah 1 gambar, simpan | 1 upload; gambar baru di urutan ke-3 `mediaLinks` |
| 4 | Hapus 1 gambar lama, tambah 1 baru, simpan | 1 upload; `mediaLinks` = 2 lama + 1 baru |
| 5 | Hapus semua gambar, simpan | `mediaLinks: []`, `mediaLink: null` |
| 6 | Pilih file `.mp4` / `.gif` | Ditolak, toast, tidak ada upload |
| 7 | Simulasi upload gagal (offline / block request) saat simpan | `POST /template` tidak terkirim, toast, modal tetap terbuka |
| 8 | Template lama 1 gambar (dibuat sebelum rilis) | Edit menampilkan 1 gambar |
| 9 | Form produk (regresi `MultipleFileUpload`) | Perilaku sama seperti sebelum (komponen tidak diubah) |
| 10 | Daftar template & modal quick chat | Indikator jumlah gambar tampil untuk template bergambar |
| 11 | Chat: pilih template 3 gambar, kirim | Strip 3/10; 1 `POST /send-message` dengan 3 URL bucket produk; **tanpa** upload; gambar tampil di riwayat (URL bucket chat setelah refresh) |
| 12 | Chat: 2 foto lokal + template 3 gambar | Strip 5 (template di depan); 2 upload; `mediaLink` 5 sesuai urutan strip |
| 13 | Pilih template A lalu B | Gambar A diganti gambar B; foto lokal tetap |
| 14 | 9 foto lokal + template 3 gambar | 1 gambar template ditambahkan + toast kuota |
| 15 | PDF di composer + template bergambar | Teks terisi, gambar tidak ditambahkan, toast |
| 16 | Hapus 1 gambar template dari strip lalu kirim | `mediaLink` tanpa gambar tsb |
| 17 | Gagal sebagian (mis. putus koneksi device WA di tengah) | Composer menyisakan gambar yang belum terkirim, teks kosong |
| 18 | Pindah conversation setelah memilih template | Strip kosong di conversation baru |
| 19 | Window 24 jam habis → "Pilih Template untuk Membuka Chat" dengan template bergambar | Teks + gambar masuk composer, kirim berhasil |

---

## 8. Rencana Implementasi & Rilis

| Fase | Pekerjaan | Prasyarat |
|---|---|---|
| 0 | `git pull` `dev`; cocokkan kode terbaru `ModalFormTemplateChat`/`templateChatStore` dengan §1.1 | — |
| 1 | Bagian A (types, store, form) + QA #1–9 | Backend template multi image ter-deploy |
| 2 | Bagian B (indikator) + QA #10 | Fase 1 |
| 3 | Bagian C (delta composer, `applyTemplateImages`, `handleSendMessage`) + QA #11–19 | FE Chat Multiple Image selesai (atau dikerjakan bersamaan) |
| 4 | `npm run build` hijau; update `docs/frontend/ui-flow.md`, `business-modules.md` | — |

Fase 1–2 dapat dirilis sendiri; fase 3 dirilis bersama/sesudah FE Chat Multiple Image.
