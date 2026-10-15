# TDD — Chat Multiple Image (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | Kirim multiple gambar dari Chat Room (ekstensi modul `chat`) |
| Dokumen induk | [PRD Frontend](../prd/chat-multiple-image-frontend.md), [PRD Backend](../prd/chat-multiple-image.md), [TDD Backend](./chat-multiple-image.md), [FE API Mapping](../frontend/api-mapping.md) `POST /send-message` |
| Stack | Vue 3 `<script setup>` + TypeScript, Vite, Pinia (Options API `defineStore`), Axios, SCSS utility classes |
| Baseline source | `FE/dashboard`, branch `dev`, commit `e7825d2` (2026-02-22) — **lihat §1.2** |
| Status | **Ready for Implementation** — keputusan PRD §11 resolved (default) |
| Last updated | 2026-10-15 |

> TDD ini menerjemahkan PRD Frontend menjadi desain teknis yang selaras dengan konvensi dashboard: store Pinia Options API, helper `apiConfig/method.ts`, `alertStore`, utility class existing. Snippet = acuan (skeleton), bukan kode final.

---

## Daftar Isi
1. [Baseline & Konvensi](#1-baseline--konvensi)
2. [Struktur File](#2-struktur-file)
3. [TypeScript Types](#3-typescript-types)
4. [Util Lampiran (`utils/attachment.ts`)](#4-util-lampiran-utilsattachmentts)
5. [Perubahan `chatStore.ts`](#5-perubahan-chatstorets)
6. [Perubahan `ChatDetail.vue`](#6-perubahan-chatdetailvue)
7. [Perubahan `pages/chat/index.vue`](#7-perubahan-pageschatindexvue)
8. [Kontrak Komponen ↔ Halaman (callback `done`)](#8-kontrak-komponen--halaman-callback-done)
9. [Traceability PRD → Implementasi](#9-traceability-prd--implementasi)
10. [Testing & QA](#10-testing--qa)
11. [Rencana Implementasi & Rilis](#11-rencana-implementasi--rilis)

---

## 1. Baseline & Konvensi

### 1.1 Alur existing

```
ChatDetail.vue
  selectedFile: File | null, previewUrl
  handleSendClick() → emit('send-message', text, file) → kosongkan teks & file (langsung)
        │
pages/chat/index.vue
  handleSendMessage(text, file)
    ├─ file? → chatStore.uploadChatFile(file) → url
    │          → chatStore.sendMessage({ messageType: dari MIME, mediaLink: url, message: '' })
    │          (upload gagal → alert() native, return)
    └─ text?  → chatStore.sendMessage({ messageType: 'TEXT', mediaLink: null, message: text, repliedMessageId: '' })
  (return value sendMessage tidak dicek)
        │
WebSocket /topic/chatroom/{id} — NEW_MESSAGE → push ke chat.history (de-dupe id)
```

### 1.2 Peringatan baseline

Snapshot lokal (`e7825d2`, Feb 2026) **belum** memuat fitur Conversation Label yang sudah didokumentasikan (`labelStore.ts`, `LabelChips`, perubahan header `ChatDetail`). Sebelum implementasi:

1. `git pull` branch `dev` terbaru.
2. Cocokkan ulang §6 & §7 dengan isi terbaru `ChatDetail.vue` & `pages/chat/index.vue`, khususnya: signature `emit('send-message')`, `handleSendMessage`, markup composer.

Area yang diubah TDD ini (composer bagian bawah & `handleSendMessage`) diperkirakan tidak bersinggungan dengan fitur Label (header).

### 1.3 Konvensi yang diwarisi

| Aspek | Konvensi |
|---|---|
| Store | `defineStore('chat', { state, actions })`; action `try { postData(...) ; return response.data } catch (err) { return errorHelper(err) }`. |
| `errorHelper` | Membaca `err.response.data` → `{ message, success }` (pesan backend 4xx sampai ke UI). |
| Toast | `useAlertStore().setAlert(message, 'success' \| 'danger')`, auto-hide 3 s. Auto-import dari `src/stores/`. |
| Auto-import | `ref`, `computed`, `watch`, `onUnmounted`, dll. via unplugin (lihat `ChatDetail` memakai `onUnmounted` tanpa import). |
| Styling | Utility class (`d-flex`, `gap-10`, `border-radius-4`, `neutral-500`, …) + inline style ukuran, sama dengan preview existing. |
| Test | Tidak ada test runner (tidak ada vitest/jest di `package.json`). Verifikasi = `vue-tsc -b && vite build` + QA manual. |

---

## 2. Struktur File

```
src/modules/chat/
├── types.ts                      # (ubah) tipe payload, result, attachment, callback
├── utils/
│   ├── useWebSocket.ts           # tidak berubah
│   └── attachment.ts             # (baru) konstanta & helper validasi/MIME
├── store/
│   └── chatStore.ts              # (ubah) tipe sendMessage, action uploadChatFiles
└── components/
    └── ChatDetail.vue            # (ubah) composer multi-lampiran

src/pages/chat/index.vue          # (ubah) handleSendMessage(text, files, done)
```

Tidak ada route, komponen `common/`, atau dependency baru.

---

## 3. TypeScript Types

Tambahan di `src/modules/chat/types.ts`:

```ts
export type ChatMessageType = 'TEXT' | 'IMAGE' | 'AUDIO' | 'VIDEO' | 'DOCUMENT';

export interface SendMessagePayload {
  conversationId: string;
  messageType: ChatMessageType;
  mediaLink: string | string[] | null;
  message: string;
  repliedMessageId?: string | null;
}

/** `data` dari POST /send-message */
export interface SendMessageResult {
  total: number;
  sent: number;
  failed: number;
  chatIds: string[];
}

/** Item lampiran di composer */
export interface ChatAttachment {
  id: string;            // key v-for
  file: File;
  isImage: boolean;
  previewUrl: string | null;  // object URL, hanya untuk gambar
}

export interface SendDoneResult {
  ok: boolean;
  /** File yang belum terkirim & harus tetap di composer (gagal sebagian). */
  remaining?: File[];
  /** false → teks dikosongkan walau ok=false (caption sudah terkirim). Default true bila ok=false. */
  keepText?: boolean;
}

export type SendDoneCallback = (result: SendDoneResult) => void;
```

`MessageItem.type` existing dapat memakai `ChatMessageType` (opsional, refactor kecil).

---

## 4. Util Lampiran (`utils/attachment.ts`)

```ts
import type { ChatMessageType } from '../types';

export const MAX_IMAGES = 10;                       // PRD Q2 — selaras backend chat.multi-image.max
export const MAX_FILE_BYTES = 10 * 1024 * 1024;     // PRD Q3 — selaras spring.servlet.multipart.max-file-size
export const UPLOAD_CONCURRENCY = 3;                // PRD Q5
export const ACCEPT = 'image/*,application/pdf,.doc,.docx,.xls,.xlsx';

const DOC_EXT = ['.pdf', '.doc', '.docx', '.xls', '.xlsx'];

export const isImageFile = (f: File) => f.type.startsWith('image/');

export const isAcceptedFile = (f: File) =>
  isImageFile(f) || DOC_EXT.some((ext) => f.name.toLowerCase().endsWith(ext));

/** Sama dengan logika existing di handleSendMessage. */
export const resolveMessageType = (f: File): ChatMessageType => {
  if (f.type.startsWith('image/')) return 'IMAGE';
  if (f.type.startsWith('video/')) return 'VIDEO';
  if (f.type.startsWith('audio/')) return 'AUDIO';
  return 'DOCUMENT';
};

export const formatSize = (bytes: number) => `${(bytes / 1024).toFixed(1)} KB`;
```

---

## 5. Perubahan `chatStore.ts`

### 5.1 `sendMessage` — tipe saja

```ts
async sendMessage(payload: SendMessagePayload) {
  try {
    const response = await postData('api_url', `/send-message`, payload);
    return response.data;          // { success, message, data: SendMessageResult | null }
  } catch (err: any) {
    return errorHelper(err);       // { success: false, message } — tanpa data
  }
},
```

Catatan: gagal sebagian dibalas HTTP 200 → masuk cabang `try` dengan `success: false` dan `data` terisi. 400 → cabang `catch`, tanpa `data`.

### 5.2 Action baru `uploadChatFiles`

Memakai `uploadChatFile` existing, worker-pool sederhana dengan konkurensi terbatas, urutan hasil = urutan input.

```ts
async uploadChatFiles(files: File[], concurrency = UPLOAD_CONCURRENCY) {
  const urls: (string | null)[] = new Array(files.length).fill(null);
  let cursor = 0;

  const worker = async () => {
    while (cursor < files.length) {
      const index = cursor++;
      const res = await this.uploadChatFile(files[index]);
      urls[index] = res?.success && res.data ? (res.data as string) : null;
    }
  };

  await Promise.all(
    Array.from({ length: Math.min(concurrency, files.length) }, worker)
  );

  const failedCount = urls.filter((u) => u === null).length;
  return { urls, failedCount };
},
```

`uploadChatFile` tidak diubah.

---

## 6. Perubahan `ChatDetail.vue`

### 6.1 Emit

```ts
const emit = defineEmits<{
  // ...existing
  (e: 'send-message', text: string, files: File[], done: SendDoneCallback): void;
}>();
```

### 6.2 State

Ganti `selectedFile` + `previewUrl` dengan:

```ts
const attachments = ref<ChatAttachment[]>([]);
const isSending = ref(false);

const imageCount = computed(() => attachments.value.filter((a) => a.isImage).length);
const hasDocument = computed(() => attachments.value.some((a) => !a.isImage));
```

### 6.3 Seleksi file (FR-1…FR-7)

```ts
const alertStore = useAlertStore();

const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement;
  const picked = Array.from(target.files ?? []);
  target.value = '';                                   // FR-7

  let quotaHit = false;
  for (const file of picked) {
    if (!isAcceptedFile(file)) { alertStore.setAlert('Tipe file tidak didukung', 'danger'); continue; }
    if (file.size > MAX_FILE_BYTES) { alertStore.setAlert(`${file.name} melebihi 10 MB`, 'danger'); continue; }

    const isImage = isImageFile(file);
    if (!isImage || hasDocument.value) {
      if (attachments.value.length > 0) {
        alertStore.setAlert('Dokumen tidak dapat digabung dengan lampiran lain', 'danger');
        continue;
      }
    }
    if (isImage && imageCount.value >= MAX_IMAGES) { quotaHit = true; continue; }

    attachments.value.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      file,
      isImage,
      previewUrl: isImage ? URL.createObjectURL(file) : null,
    });
  }
  if (quotaHit) alertStore.setAlert(`Maksimal ${MAX_IMAGES} gambar per pengiriman`, 'danger');
};
```

> Toast hanya satu yang tampil (state tunggal `alertStore`); pesan terakhir menang. Diterima — kasus campuran beberapa error dalam satu seleksi jarang.

### 6.4 Hapus & bersihkan (FR-9)

```ts
const revoke = (a: ChatAttachment) => { if (a.previewUrl) URL.revokeObjectURL(a.previewUrl); };

const removeAttachment = (id: string) => {
  const target = attachments.value.find((a) => a.id === id);
  if (target) revoke(target);
  attachments.value = attachments.value.filter((a) => a.id !== id);
};

const clearAttachments = () => {
  attachments.value.forEach(revoke);
  attachments.value = [];
};

/** Pertahankan hanya file yang belum terkirim (gagal sebagian). */
const keepOnly = (files: File[]) => {
  const keep = new Set(files);
  attachments.value.filter((a) => !keep.has(a.file)).forEach(revoke);
  attachments.value = attachments.value.filter((a) => keep.has(a.file));
};

onUnmounted(() => clearAttachments());   // digabung dengan onUnmounted existing (searchTimeout)
```

### 6.5 Kirim (FR-12, FR-13, FR-19…FR-21, FR-23)

```ts
const handleSendClick = () => {
  if (isSending.value) return;                                        // FR-13
  if (!messageText.value.trim() && attachments.value.length === 0) return;  // FR-12

  isSending.value = true;
  const chatIdAtSend = props.chat?.id;

  emit('send-message', messageText.value, attachments.value.map((a) => a.file), (result) => {
    if (props.chat?.id !== chatIdAtSend) return;     // FR-23: sudah pindah conversation
    isSending.value = false;

    if (result.ok) {                                  // FR-19
      messageText.value = '';
      clearAttachments();
      return;
    }
    if (result.remaining) keepOnly(result.remaining); // FR-20
    if (result.keepText === false) messageText.value = '';
    // FR-21: selain itu teks & lampiran tetap
  });
};
```

Watcher `props.chat?.id` existing ditambah:

```ts
clearAttachments();
isSending.value = false;
```

### 6.6 Template composer

Ganti blok preview `v-if="selectedFile"` dengan:

```vue
<div
  v-if="attachments.length"
  class="p-10 border-bottom-1-solid-neutral d-flex align-items-center gap-10">
  <div class="d-flex gap-10 overflow-x-auto flex-grow-1 p-8-top">
    <div
      v-for="item in attachments"
      :key="item.id"
      class="position-relative flex-shrink-0">
      <img
        v-if="item.previewUrl"
        :src="item.previewUrl"
        class="border-radius-4 object-fit-cover border-1-solid-neutral"
        style="width: 50px; height: 50px" />
      <div v-else class="d-flex align-items-center gap-10">
        <!-- ikon paperclip + nama + ukuran: markup existing, sumber dari item.file -->
      </div>
      <div
        v-if="!isSending"
        @click="removeAttachment(item.id)"
        class="position-absolute danger-bg white border-radius-50 d-flex align-items-center justify-content-center cursor-pointer shadow-custom"
        style="width: 20px; height: 20px; top: -8px; right: -8px; font-size: 14px">
        &times;
      </div>
    </div>
  </div>
  <span v-if="imageCount" class="fz-px-12 neutral-500 flex-shrink-0">
    {{ imageCount }}/{{ MAX_IMAGES }}
  </span>
</div>

<div v-if="isSending" class="p-4-y p-10-x fz-px-12 neutral-500">
  {{ imageCount > 1 ? `Mengirim ${imageCount} gambar...` : 'Mengirim...' }}
</div>
```

Input file:

```vue
<input type="file" ref="fileInputRef" class="d-none" multiple :accept="ACCEPT" @change="handleFileSelect" />
```

Paperclip & ikon kirim: tambahkan `:class="{ 'opacity-50 cursor-not-allowed': isSending }"` dan guard `if (isSending) return` di `triggerFileInput`. Placeholder input teks memakai `attachments.length` menggantikan `selectedFile`.

> Utility class `overflow-x-auto`, `flex-shrink-0`, `p-8-top` — cek ketersediaan di SCSS utility; bila tidak ada, pakai inline style (`overflow-x: auto`, `flex-shrink: 0`).

---

## 7. Perubahan `pages/chat/index.vue`

```ts
const alertStore = useAlertStore();

const handleSendMessage = async (text: string, files: File[], done: SendDoneCallback) => {
  if (!selectedChat.value) return done({ ok: false });
  const conversationId = String(selectedChat.value.id);
  const caption = text.trim();

  try {
    // TEXT saja (FR-18)
    if (files.length === 0) {
      if (!caption) return done({ ok: true });
      const res = await chatStore.sendMessage({
        conversationId, messageType: 'TEXT', mediaLink: null, message: caption, repliedMessageId: null,
      });
      if (!res.success) alertStore.setAlert(res.message || 'Jaringan Bermasalah', 'danger');
      return done({ ok: !!res.success });
    }

    // Upload (FR-14, FR-15)
    const { urls, failedCount } = await chatStore.uploadChatFiles(files);
    if (failedCount > 0) {
      alertStore.setAlert(`${failedCount} file gagal diunggah. Silahkan coba lagi.`, 'danger');
      return done({ ok: false });
    }

    // Kirim 1 request (FR-16, FR-17) — caption menempel (PRD Q1)
    const res = await chatStore.sendMessage({
      conversationId,
      messageType: resolveMessageType(files[0]),
      mediaLink: urls as string[],
      message: caption,
      repliedMessageId: null,
    });

    if (res.success) return done({ ok: true });

    alertStore.setAlert(res.message || 'Jaringan Bermasalah', 'danger');
    const sent = (res.data as SendMessageResult | undefined)?.sent ?? 0;
    if (sent > 0) return done({ ok: false, remaining: files.slice(sent), keepText: false }); // FR-20
    return done({ ok: false });                                                               // FR-21
  } catch (e) {
    console.error('Gagal mengirim pesan:', e);
    alertStore.setAlert('Jaringan Bermasalah', 'danger');
    return done({ ok: false });
  }
};
```

Perubahan perilaku vs existing:

| # | Sebelum | Sesudah | Alasan |
|---|---|---|---|
| 1 | Teks + lampiran = 2 pesan (media tanpa caption, lalu TEXT) | 1 request, teks jadi caption | PRD Q1 |
| 2 | `alert()` native saat upload gagal | Toast `alertStore` | FR-22 |
| 3 | Error `sendMessage` diabaikan | Toast + composer dipertahankan | FR-21 |
| 4 | `repliedMessageId: ''` untuk TEXT | `null` | Tipe UUID backend; `''` hanya kebetulan diterima |

> **Mode fallback Q1:** bila product owner memilih mempertahankan teks sebagai pesan TEXT terpisah, kirim media dengan `message: ''` lalu panggil cabang TEXT setelah `res.success` — perubahan lokal di fungsi ini saja.

---

## 8. Kontrak Komponen ↔ Halaman (callback `done`)

Kenapa callback, bukan `await emit(...)`: `emit` di Vue tidak mengembalikan Promise handler, sehingga `ChatDetail` tidak bisa menunggu hasil. Callback `done` menjaga state composer tetap di komponen (pola existing) sementara upload/kirim tetap di halaman (pola existing).

Aturan:

1. Halaman **wajib** memanggil `done` tepat sekali di setiap jalur (termasuk `catch`) — kalau tidak, composer terkunci di state sending.
2. `done` boleh dipanggil setelah agent pindah conversation; `ChatDetail` mengabaikannya (cek `chatIdAtSend`).
3. `remaining` berisi referensi `File` yang sama dengan yang di-emit (dicocokkan dengan `Set`, bukan nama file).

| Hasil backend | `done(...)` | Composer |
|---|---|---|
| `success: true` | `{ ok: true }` | Kosong |
| `success: false`, `data.sent = k > 0` | `{ ok: false, remaining: files.slice(k), keepText: false }` | Sisa gambar, teks kosong |
| `success: false`, tanpa `data` / upload gagal / exception | `{ ok: false }` | Tidak berubah |

---

## 9. Traceability PRD → Implementasi

| PRD | Implementasi |
|---|---|
| FR-1, FR-7 | §6.6 `<input multiple>`, §6.3 reset `target.value` |
| FR-2, FR-3, FR-4, FR-5, FR-6 | §6.3 |
| FR-8, FR-10, FR-11 | §6.6 |
| FR-9, NFR-3 | §6.4 |
| FR-12, FR-13 | §6.5, §6.6 |
| FR-14 | §5.2 |
| FR-15 | §7 cabang `failedCount` |
| FR-16, FR-17, FR-18 | §7 |
| FR-19, FR-20, FR-21 | §7 + §8 |
| FR-22 | §7 `alertStore` |
| FR-23 | §6.5 `chatIdAtSend` + watcher |
| FR-24 | Tidak ada perubahan render |
| NFR-1, NFR-2 | §1.3, §10 |
| NFR-4 | §6.6 `overflow-x-auto` |
| NFR-5 | §5.2 `UPLOAD_CONCURRENCY` |

---

## 10. Testing & QA

### 10.1 Otomatis
- `npm run build` (`vue-tsc -b && vite build`) — wajib lulus.
- Unit test tidak diwajibkan (belum ada runner). Bila tim menambah vitest di kemudian hari, kandidat pertama: `utils/attachment.ts` dan `uploadChatFiles` (urutan & `failedCount`).

### 10.2 QA manual (DevTools Network terbuka)

| # | Skenario | Expected |
|---|---|---|
| 1 | Pilih 3 gambar sekaligus | 3 thumbnail, `3/10` |
| 2 | Tambah 2 gambar lagi | 5 thumbnail |
| 3 | Punya 9, pilih 3 | +1, toast maksimal |
| 4 | Punya gambar, pilih PDF | ditolak, toast |
| 5 | Pilih file 12 MB bersama 1 gambar valid | file besar ditolak, gambar valid masuk |
| 6 | Hapus thumbnail ke-2 | urutan sisa benar; object URL di-revoke (cek Memory/Console tidak error) |
| 7 | Kirim 3 gambar + teks | 3 `upload-file` (≤ 3 paralel) → 1 `send-message` (`mediaLink` 3 URL berurutan, `message` = teks), tidak ada request TEXT; composer kosong; 3 bubble, caption di bubble pertama; di HP penerima urutan sama |
| 8 | Spam Enter saat kirim | tetap 1 `send-message` |
| 9 | Network throttling, blokir 1 upload (DevTools "Block request URL") | tidak ada `send-message`; toast; composer utuh |
| 10 | Backend partial (simulasi: URL ke-2 object MinIO dihapus, atau mock response) | toast pesan backend; composer berisi sisa; teks kosong |
| 11 | 11 gambar via request manual (mis. curl) | 400 `Maksimal 10 gambar per pengiriman` (validasi backend tetap jadi pengaman) |
| 12 | Kirim teks saja | sama seperti sebelumnya |
| 13 | Kirim 1 PDF + teks | 1 request `DOCUMENT` dengan `message` = teks |
| 14 | Pilih gambar lalu pindah conversation | composer conversation baru kosong |
| 15 | Kirim lalu pindah conversation sebelum selesai | conversation baru tidak terpengaruh; pesan tetap terkirim di conversation lama |
| 16 | Mobile ≤ 768px dengan 10 gambar | strip scroll horizontal, layout tidak melebar |

---

## 11. Rencana Implementasi & Rilis

| Fase | Pekerjaan | Selesai bila |
|---|---|---|
| 0 | Pull `dev` terbaru, cocokkan §6/§7 (lihat §1.2) | Tidak ada konflik desain |
| 1 | `types.ts`, `utils/attachment.ts` | build lulus |
| 2 | `chatStore.ts` (§5) | build lulus |
| 3 | `ChatDetail.vue` (§6) | build lulus |
| 4 | `pages/chat/index.vue` (§7) | build lulus |
| 5 | QA manual §10.2 di staging | semua skenario lulus |
| 6 | Update `docs/frontend/component-inventory.md` / `ui-flow.md` / `business-modules.md` (composer multi-lampiran) | — |

**Urutan rilis:** backend (multiple image) **harus** sudah live sebelum FE ini di-deploy — backend lama menolak `mediaLink` array. Sebaliknya backend baru tetap kompatibel dengan FE lama, jadi backend bisa dirilis lebih dulu tanpa risiko.
