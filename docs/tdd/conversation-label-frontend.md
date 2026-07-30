# TDD — Conversation Label (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | **Conversation Label** — UI di Saktiform Dashboard (ekstensi modul `chat`) |
| Dokumen induk | [PRD Frontend](../prd/conversation-label-frontend.md) (Ready for Implementation), [PRD Backend](../prd/conversation-label.md), [TDD Backend](./conversation-label.md), [API Reference §6.13](../api-reference.md) |
| Stack | Vue 3 `<script setup>` + TypeScript, Vite, Pinia (Options API `defineStore`), Axios, SCSS utility classes |
| Referensi FE | [architecture.md](../frontend/architecture.md), [component-inventory.md](../frontend/component-inventory.md), [business-modules.md](../frontend/business-modules.md), [ui-flow.md](../frontend/ui-flow.md) |
| Status | Draft for Implementation |
| Last updated | 2026-07-18 |

> TDD ini menerjemahkan PRD Frontend menjadi desain teknis konkret **selaras konvensi dashboard existing**: ekstensi modul `src/modules/chat/`, store Pinia Options-API, helper `apiConfig/method.ts`, komponen `common/`. Snippet TypeScript/Vue = acuan (skeleton), bukan kode final; nama simbol final mengikuti review kode & lint dashboard.

---

## Daftar Isi
1. [Konvensi yang Diwarisi](#1-konvensi-yang-diwarisi)
2. [Struktur File (ekstensi modul chat)](#2-struktur-file-ekstensi-modul-chat)
3. [TypeScript Types](#3-typescript-types)
4. [`labelStore.ts` (baru)](#4-labelstorets-baru)
5. [Perubahan `chatStore.ts`](#5-perubahan-chatstorets)
6. [Serialisasi query `labelId` (repeatable)](#6-serialisasi-query-labelid-repeatable)
7. [Pemetaan API (helper calls)](#7-pemetaan-api-helper-calls)
8. [Util Warna (`labelColor.ts`)](#8-util-warna-labelcolorts)
9. [Desain Komponen](#9-desain-komponen)
10. [Integrasi ke `ChatList` & `ChatDetail`](#10-integrasi-ke-chatlist--chatdetail)
11. [Validasi Client](#11-validasi-client)
12. [Error, Loading, Empty State](#12-error-loading-empty-state)
13. [Lifecycle & Edge Case](#13-lifecycle--edge-case)
14. [Rencana Implementasi & Checklist QA](#14-rencana-implementasi--checklist-qa)

---

## 1. Konvensi yang Diwarisi

| Aspek | Konvensi (dipakai apa adanya) |
|---|---|
| Store | `defineStore('labelStore', { state, getters, actions })` (Options API). Auto-import (tanpa `import`). |
| API helper | `getData(type,url,params)`, `postData(type,url,data)`, `putData(type,url,data)`, `destroyData(type,url)` — arg pertama `type='api_url'`. `errorHelper(err)`. |
| Pola action | `try { const res = await getData('api_url', url, params); return res.data } catch(err){ alertStore.setAlert(msg,'danger'); return errorHelper(err) }`. Sukses aksi tulis → `alertStore.setAlert(msg,'success')`. |
| Auth/scope | `useAuthStore().user.activeWorkspace.id` → query `workspaceId`. Token via interceptor otomatis. |
| Envelope | `{ success, message, data }`. List paginated = Spring `Page` di `data` (`content[]`, `totalElements`, `totalPages`, `number` 0-idx). Master label = **array biasa** di `data` (bukan Page). **Kirim `page` 1-indexed** untuk list conversation. |
| Modal | `Modal` (`#content` + `#action` slot). `ConfirmModal` (`title/action/actionVerb/visible/loading`, emit `closeModal`,`onAction`). |
| Chip/tag | `ChipCustom` (basis) — dibungkus `LabelChips` untuk latar warna kustom + kontras. |
| Format | `functions/moment.ts`, `functions/formHelper.ts` (`validateForm`), `functions/formater.ts`. |
| Toast | `useAlertStore().setAlert(message, 'success'|'danger')`. |

`errMsg(err)` = `err.response?.data?.message || 'Jaringan Bermasalah'`.

---

## 2. Struktur File (ekstensi modul chat)

```
src/modules/chat/
├── store/
│   ├── chatStore.ts                 # (ubah) tambah labels di ChatItem; filter.labelIds; fetch* kirim labelId
│   └── labelStore.ts                # (baru)
├── types.ts                         # (ubah) tambah LabelDto, LabelRequest, AssignLabelRequest; ChatItem.labels
├── utils/
│   └── labelColor.ts                # (baru)
└── components/
    ├── ChatList.vue                 # (ubah) LabelChips per baris + filter label + tombol Kelola Label
    ├── ChatDetail.vue               # (ubah) LabelChips + tombol "+ Label" (LabelAssignDropdown) di header
    └── label/                       # (baru)
        ├── LabelChips.vue
        ├── LabelAssignDropdown.vue
        ├── LabelManagerModal.vue
        └── LabelColorPicker.vue
```
Tidak ada file di `src/pages/` yang berubah selain (mungkin) meneruskan props; seluruh UI hidup di komponen modul chat. Tidak ada route/`<route>` block baru.

---

## 3. TypeScript Types

Tambahan di `src/modules/chat/types.ts`:

```ts
export interface LabelDto {
  id: number
  name: string
  colorHex: string        // selalu #rrggbb dari backend
}

export interface LabelRequest {
  name: string
  colorHex: string        // #RRGGBB (dinormalisasi client → #rrggbb)
}

export interface AssignLabelRequest {
  labelIds: number[]
}

// ChatItem existing — tambah field labels
export interface ChatItem {
  id: string
  contactName: string
  lastMessage: string
  lastMessageType: string
  lastMessageTime: string
  status: string
  chatStatus: string
  unreadMessageCount: number
  labels: LabelDto[]       // (baru) default [] bila tidak ada
  // …field lain existing
}

// ConversationDetail existing — tambah field labels
export interface ConversationDetail {
  namaKontak: string
  phoneNumber: string
  handledBy: string
  status: string
  selectedOrder: string | null
  labels: LabelDto[]       // (baru)
}
```

> Backend sudah mengirim `labels` pada list & detail — cukup ditipekan; tidak perlu fetch tambahan untuk read.

---

## 4. `labelStore.ts` (baru)

Options-API `defineStore`. Menyimpan master label workspace + flag loading; action CRUD master + assign/unassign.

```ts
export const useLabelStore = defineStore('labelStore', {
  state: () => ({
    list: [] as LabelDto[],       // master label workspace (picker, filter, manager)
    loadingList: false,
    actionLoading: false,         // assign/unassign/create/update/delete in-flight
  }),

  getters: {
    workspaceId: () => useAuthStore().user.activeWorkspace.id,
  },

  actions: {
    // ---- master label ----
    async fetchList() {
      this.loadingList = true
      try {
        const res = await getData('api_url', '/chat/label', { workspaceId: this.workspaceId })
        this.list = res.data.data ?? []
        return res.data
      } catch (err) { useAlertStore().setAlert(errMsg(err), 'danger'); return errorHelper(err) }
      finally { this.loadingList = false }
    },

    async create(payload: LabelRequest) {
      const alert = useAlertStore(); this.actionLoading = true
      try {
        const res = await postData('api_url', `/chat/label?workspaceId=${this.workspaceId}`, payload)
        alert.setAlert('Label dibuat', 'success')
        await this.fetchList()
        return res.data.data as LabelDto
      } catch (err) { alert.setAlert(errMsg(err), 'danger'); return null }
      finally { this.actionLoading = false }
    },

    async update(id: number, payload: LabelRequest) {
      const alert = useAlertStore(); this.actionLoading = true
      try {
        const res = await putData('api_url', `/chat/label/${id}?workspaceId=${this.workspaceId}`, payload)
        alert.setAlert('Label diperbarui', 'success')
        await this.fetchList()
        return res.data.data as LabelDto
      } catch (err) { alert.setAlert(errMsg(err), 'danger'); return null }
      finally { this.actionLoading = false }
    },

    async remove(id: number) {
      const alert = useAlertStore(); this.actionLoading = true
      try {
        await destroyData('api_url', `/chat/label/${id}?workspaceId=${this.workspaceId}`)
        alert.setAlert('Label dihapus', 'success')
        await this.fetchList()
        return true
      } catch (err) { alert.setAlert(errMsg(err), 'danger'); return false }
      finally { this.actionLoading = false }
    },

    // ---- assignment ----
    async assign(conversationId: string, labelIds: number[]) {
      const alert = useAlertStore(); this.actionLoading = true
      try {
        const body: AssignLabelRequest = { labelIds }
        const res = await postData('api_url',
          `/chat/conversation/${conversationId}/label?workspaceId=${this.workspaceId}`, body)
        return res.data.data as LabelDto[]   // label terpasang setelah assign
      } catch (err) { alert.setAlert(errMsg(err), 'danger'); return null }
      finally { this.actionLoading = false }
    },

    async unassign(conversationId: string, labelId: number) {
      const alert = useAlertStore(); this.actionLoading = true
      try {
        await destroyData('api_url',
          `/chat/conversation/${conversationId}/label/${labelId}?workspaceId=${this.workspaceId}`)
        return true
      } catch (err) { alert.setAlert(errMsg(err), 'danger'); return false }
      finally { this.actionLoading = false }
    },

    async listForConversation(conversationId: string) {   // opsional (detail sudah bawa labels)
      const res = await getData('api_url',
        `/chat/conversation/${conversationId}/label`, { workspaceId: this.workspaceId })
      return res.data.data as LabelDto[]
    },

    reset() { this.list = []; this.loadingList = false; this.actionLoading = false },
  },
})
```

> Master label `GET /chat/label` mengembalikan **array**, bukan Page — akses `res.data.data` langsung.
> Panggil `reset()` + `fetchList()` saat workspace aktif berganti.

---

## 5. Perubahan `chatStore.ts`

Minimal-invasif:

```ts
// state.filter tambahkan:
labelIds: [] as number[],

// fetchAssignedChats / fetchUnassignedChats — sertakan labelId bila ada:
const params: any = {
  workspaceId: this.workspaceId, page, limit,
  // …param existing (agent, keyword, statusOrder, statusPesan, startDate, endDate, isUnread)
}
if (this.filter.labelIds.length) params.labelId = this.filter.labelIds   // array → repeatable (lihat §6)
const res = await getData('api_url', '/conversation/assigned', params)

// item hasil sudah punya field labels → map apa adanya ke ChatItem (default [] jika undefined)

// Helper patch label pada item tertentu (optimistic setelah assign/unassign):
patchChatLabels(conversationId: string, labels: LabelDto[]) {
  const patch = (arr: ChatItem[]) => {
    const it = arr.find(c => c.id === conversationId)
    if (it) it.labels = labels
  }
  patch(this.assignedChats); patch(this.unassignedChats)
}
```

> `patchChatLabels` dipanggil oleh `ChatDetail` setelah assign/unassign sukses agar chip di baris list ikut ter-update tanpa full refetch.

---

## 6. Serialisasi query `labelId` (repeatable)

Backend mem-bind `@RequestParam(required=false) List<Long> labelId` dari `?labelId=1&labelId=2`. **Axios v1 default** menserialisasi array sebagai `labelId[]=1&labelId[]=2` (dengan `[]`) yang **tidak** cocok. Solusi (pilih salah satu, konsisten satu tempat):

**Opsi A — paramsSerializer di instance (disarankan, global):** di `apiConfig/client.ts`, set pada instance Axios:
```ts
import qs from 'qs'
axios.create({ /* … */, paramsSerializer: (p) => qs.stringify(p, { arrayFormat: 'repeat' }) })
// menghasilkan labelId=1&labelId=2 ; param non-array tetap normal
```
atau tanpa `qs` (Axios v1): `paramsSerializer: { indexes: null }` → juga menghasilkan `labelId=1&labelId=2`.

**Opsi B — bangun query manual** hanya untuk labelId di action chatStore:
```ts
const qsLabel = this.filter.labelIds.map(id => `labelId=${id}`).join('&')
const url = `/conversation/assigned?${qsLabel}`   // sisanya via params
```

> **Opsi A `indexes: null`** paling bersih & tidak menambah dependensi. Verifikasi tidak memecah endpoint lain (yang tak pakai array param tak terpengaruh).

---

## 7. Pemetaan API (helper calls)

| Store action | Helper | URL |
|---|---|---|
| `labelStore.fetchList` | `getData` | `/chat/label?workspaceId` |
| `labelStore.create` | `postData` | `/chat/label?workspaceId` (body `{name,colorHex}`) |
| `labelStore.update` | `putData` | `/chat/label/{id}?workspaceId` (body `{name,colorHex}`) |
| `labelStore.remove` | `destroyData` | `/chat/label/{id}?workspaceId` |
| `labelStore.assign` | `postData` | `/chat/conversation/{convId}/label?workspaceId` (body `{labelIds}`) |
| `labelStore.unassign` | `destroyData` | `/chat/conversation/{convId}/label/{labelId}?workspaceId` |
| `labelStore.listForConversation` | `getData` | `/chat/conversation/{convId}/label?workspaceId` |
| `chatStore.fetchAssignedChats` | `getData` | `/conversation/assigned?workspaceId&…&labelId=` |
| `chatStore.fetchUnassignedChats` | `getData` | `/conversation/unassigned?workspaceId&…&labelId=` |
| `chatStore.fetchConversationDetail` | `getData` | `/conversation/detail?conversationId` (respons kini bawa `labels`) |

---

## 8. Util Warna (`labelColor.ts`)

```ts
const PRESET = [
  '#ef4444','#f97316','#f59e0b','#eab308','#22c55e','#10b981',
  '#3b82f6','#6366f1','#8b5cf6','#ec4899','#64748b','#111827',
] as const

const HEX6 = /^#?[0-9a-fA-F]{6}$/

export function isValidHex(raw: string): boolean {
  return !!raw && HEX6.test(raw.trim())
}

export function normalizeHex(raw: string): string {
  const t = (raw ?? '').trim()
  if (!HEX6.test(t)) throw new Error('Warna harus format #RRGGBB (6 digit heksadesimal)')
  return (t.startsWith('#') ? t : `#${t}`).toLowerCase()
}

/** Warna teks kontras (hitam/putih) untuk latar hex. */
export function textColorOn(hex: string): string {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  const lum = 0.299 * r + 0.587 * g + 0.114 * b
  return lum < 140 ? '#ffffff' : '#111827'
}

export const presetColors = PRESET
```

> `normalizeHex` konsisten dengan `HexColor.normalize` backend (6-digit, `#` opsional, output lowercase). Client menolak `#RGB`/`#RRGGBBAA` lebih awal.

---

## 9. Desain Komponen

### 9.1 `LabelChips.vue`
- **Props:** `{ labels: LabelDto[]; max?: number }` (default `max = 3`). **Emits:** —.
- Render chip untuk tiap label (hingga `max`), sisanya jadi chip `+N`.
- Tiap chip: `:style="{ backgroundColor: l.colorHex, color: textColorOn(l.colorHex) }"`, teks `l.name`, kelas kecil (padding/rounded). Non-interaktif (read-only).
- Bila `labels.length === 0` → render nihil.

```vue
<template>
  <span class="label-chips">
    <span v-for="l in shown" :key="l.id" class="label-chip"
          :style="{ backgroundColor: l.colorHex, color: textColorOn(l.colorHex) }">{{ l.name }}</span>
    <span v-if="hidden > 0" class="label-chip label-chip--more">+{{ hidden }}</span>
  </span>
</template>
```

### 9.2 `LabelAssignDropdown.vue`
- **Props:** `{ conversationId: string; assigned: LabelDto[] }`. **Emits:** `changed(labels: LabelDto[])`.
- Sumber daftar: `labelStore.list` (panggil `fetchList()` bila kosong). Tandai tercentang bila `assigned.some(a => a.id === l.id)`.
- Interaksi baris:
  - **Centang** (belum terpasang) → `await labelStore.assign(conversationId, [l.id])` → hasil = daftar label terkini → `emit('changed', labels)`.
  - **Uncentang** (terpasang) → `await labelStore.unassign(conversationId, l.id)` → hitung `labels = assigned.filter(x => x.id !== l.id)` → `emit('changed', labels)`.
- Field search (filter `list` by nama), link "Kelola Label" (emit/inject buka `LabelManagerModal`), empty state bila `list` kosong.
- Bungkus dalam `DropdownCustom`; disable interaksi saat `labelStore.actionLoading`.

### 9.3 `LabelManagerModal.vue`
- **Props:** `{ visible: boolean }`. **Emits:** `close`, `mutated` (agar pemanggil bisa refresh inbox setelah delete/rename).
- Reuse `Modal`. Dua sub-state: **list** & **form** (create/edit) — kelola via `mode = 'list' | 'form'` + `editing: LabelDto | null`.
- **List:** iterasi `labelStore.list` → baris (swatch + nama) + tombol "Ubah"/"Hapus". Tombol "Tambah Label" → `mode='form'`, `editing=null`.
- **Form:** `InputCustom` name + `<LabelColorPicker v-model="colorHex" />`. Simpan → validasi (`name` non-kosong, `isValidHex`) → `create` / `update(editing.id)` → `mode='list'`.
- **Hapus:** buka `ConfirmModal` (peringatan cascade) → `labelStore.remove(id)` → `emit('mutated')`.
- Semua tombol disable saat `labelStore.actionLoading`.

### 9.4 `LabelColorPicker.vue`
- **Props/`v-model`:** `modelValue: string` (hex). **Emits:** `update:modelValue`.
- Render grid `presetColors` sebagai swatch (klik → set + emit). Swatch terpilih diberi ring.
- Input hex opsional (`InputCustom`): on blur/enter → bila `isValidHex` → `normalizeHex` → emit; bila invalid → tampilkan pesan error kecil.
- Preview chip contoh (`backgroundColor` + `textColorOn`).

---

## 10. Integrasi ke `ChatList` & `ChatDetail`

### 10.1 `ChatList.vue` (ubah)
- **Per baris:** tambahkan `<LabelChips :labels="chat.labels" :max="3" />` di area meta baris (di bawah nama/last message).
- **Panel filter:** tambah kontrol "Label" → `MultipleSelectCustom` opsi dari `labelStore.list.map(l => ({ name: l.name, value: l.id }))`; `v-model` → array id → set `chatStore.filter.labelIds`, page=1, refetch tab aktif.
- **Header panel:** tombol `ButtonCustom` "Kelola Label" → set `showManager = true` (`<LabelManagerModal :visible="showManager" @close="showManager=false" @mutated="onLabelsMutated" />`).
- `onMounted` halaman `/chat`: panggil `labelStore.fetchList()` (untuk filter & picker) selain fetch chat existing.
- `onLabelsMutated()` → `labelStore.fetchList()` + refetch tab aktif (agar chip yang di-rename/hapus konsisten).

### 10.2 `ChatDetail.vue` (ubah)
- **Header:** render `<LabelChips :labels="detail.labels" />` + tombol "+ Label" → toggle `LabelAssignDropdown`:
  ```vue
  <LabelAssignDropdown v-if="showAssign" :conversation-id="detail.id" :assigned="detail.labels"
     @changed="onLabelsChanged" />
  ```
- `onLabelsChanged(labels)`:
  ```ts
  detail.labels = labels
  chatStore.patchChatLabels(detail.id, labels)   // sinkron chip di list
  ```
- Tombol/dropdown disable saat `labelStore.actionLoading`.

> **Catatan `conversationId` di detail:** respons `/conversation/detail` membawa `id`. Bila belum, gunakan id percakapan terpilih yang sudah dipegang `chatStore`/`ChatDetail`.

---

## 11. Validasi Client

Pakai `functions/formHelper.ts` atau guard inline:
- **Create/Update label:** `name` required (trim non-kosong); `colorHex` → `isValidHex` sebelum submit; normalisasi `normalizeHex` saat kirim. Tombol Simpan `:disabled` bila invalid atau `actionLoading`.
- **Assign:** minimal 1 `labelId` (dropdown hanya mengirim 1 id per toggle).
- **Duplikat nama** ditangani server (case-insensitive) → tampilkan `message` backend & fokus ulang field nama.

---

## 12. Error, Loading, Empty State
- **Error server** → toast `alertStore` (`data.message`). Kasus khas: 400 duplikat nama, 400 all-or-nothing assign, 400 hex invalid (backstop).
- **Loading:** `labelStore.loadingList` (spinner di manager/picker), `labelStore.actionLoading` (disable tombol assign/simpan/hapus). Chip list tidak butuh loading tersendiri (menyatu di list).
- **Empty:** picker/manager tanpa label → empty state + CTA "Buat label pertama"; filter tanpa hasil → empty state inbox existing.
- **401** → interceptor existing hapus token (redirect saat navigasi berikutnya).

---

## 13. Lifecycle & Edge Case
- **Ganti workspace:** `labelStore.reset()` + `fetchList()`; bersihkan `chatStore.filter.labelIds` agar tidak membawa id label workspace lain.
- **Optimistic + rollback:** bila `assign`/`unassign` gagal, kembalikan `detail.labels` & item list ke kondisi sebelum toggle (simpan snapshot sebelum request) + toast.
- **Rename/Hapus master saat percakapan terbuka:** setelah `mutated`, refetch list conversation & (bila detail terbuka) `fetchConversationDetail` agar chip mencerminkan nama baru / hilang.
- **Serialisasi labelId:** verifikasi query jadi `labelId=1&labelId=2` (bukan `labelId[]`) — lihat §6. Uji dengan 1 dan >1 label.
- **Idempotensi:** assign ulang label yang sudah ada / unassign yang tak terpasang → aman (backend idempotent); UI tetap konsisten.
- **Kontras warna:** untuk warna sangat terang (mis. kuning) pastikan `textColorOn` mengembalikan teks gelap (uji `#eab308`).

---

## 14. Rencana Implementasi & Checklist QA

**Urutan implementasi:**
1. `types.ts` (LabelDto/LabelRequest/AssignLabelRequest + `labels` di ChatItem/Detail) + `utils/labelColor.ts`.
2. `labelStore.ts` (fetchList + CRUD + assign/unassign).
3. Konfigurasi serializer `labelId` repeatable (§6) di `apiConfig/client.ts`.
4. `LabelChips.vue` → pasang di `ChatList` (read) & `ChatDetail` header.
5. `LabelColorPicker.vue` + `LabelManagerModal.vue` (CRUD master) + tombol "Kelola Label" di `ChatList`.
6. `LabelAssignDropdown.vue` + tombol "+ Label" di `ChatDetail` + `patchChatLabels`.
7. Filter label di panel filter `ChatList` (`chatStore.filter.labelIds`).
8. Poles: empty/loading/error, label ID (Bahasa), responsive, kontras warna.

**Checklist QA (manual — tidak ada test infra):**
- [ ] Chip label tampil di baris list & header detail; warna teks kontras; overflow "+N" bekerja.
- [ ] "Kelola Label": buat (nama+warna), ubah, hapus (konfirmasi cascade) — daftar/picker/filter ikut update.
- [ ] Duplikat nama (case-insensitive) ditolak dengan pesan jelas; hex invalid dicegah di client.
- [ ] "+ Label" menampilkan seluruh label workspace; terpasang tercentang; centang→assign, uncentang→unassign; chip list ikut update (patchChatLabels).
- [ ] Assign/unassign idempotent (ulang tak menduplikasi / tak error).
- [ ] Filter label (multi-select) menyaring inbox (OR); kosongkan → list penuh; query jadi `labelId=1&labelId=2`.
- [ ] Ganti workspace mereset label & filter; label workspace lain tak tampak.
- [ ] Semua aksi memunculkan toast; tombol disable saat in-flight; rollback saat gagal.
- [ ] Rename/hapus master mencerminkan ke percakapan setelah refetch.

---

*TDD ini turunan langsung dari [PRD Frontend](../prd/conversation-label-frontend.md). Seluruh endpoint tersedia di backend (Fase 1–6, terverifikasi — lihat [API Reference §6.13](../api-reference.md)). Snippet = acuan; nama simbol final mengikuti review kode & lint dashboard.*
