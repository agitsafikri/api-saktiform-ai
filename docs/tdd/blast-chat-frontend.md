# TDD — Blast Chat (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | Modul **Campaign (Blast Chat)** — Saktiform Dashboard |
| Dokumen induk | [PRD Frontend](../prd/blast-chat-frontend.md) (Ready for Implementation), [PRD Backend](../prd/blast-chat.md), [TDD Backend](./blast-chat.md) |
| Stack | Vue 3 `<script setup>` + TypeScript, Vite, Pinia (Options API `defineStore`), Axios, SCSS utility classes |
| Referensi FE | [architecture.md](../frontend/architecture.md), [component-inventory.md](../frontend/component-inventory.md), [business-modules.md](../frontend/business-modules.md) |
| Status | Draft for Implementation |
| Last updated | 2026-07-01 |

> TDD ini menerjemahkan PRD Frontend menjadi desain teknis konkret **selaras konvensi dashboard existing**: modul `src/modules/campaign/`, halaman `src/pages/campaign/`, store Pinia Options-API, helper `apiConfig/method.ts`, komponen `common/`. Snippet TypeScript/Vue = acuan (skeleton), bukan kode final.

---

## Daftar Isi
1. [Konvensi yang Diwarisi](#1-konvensi-yang-diwarisi)
2. [Struktur File Modul](#2-struktur-file-modul)
3. [Routing & Sidebar](#3-routing--sidebar)
4. [TypeScript Types (`types.ts`)](#4-typescript-types-typests)
5. [Store (`campaignStore.ts`)](#5-store-campaignstorets)
6. [Pemetaan API (helper calls)](#6-pemetaan-api-helper-calls)
7. [Composable Polling Progress](#7-composable-polling-progress)
8. [Desain Komponen & Halaman](#8-desain-komponen--halaman)
9. [Utilitas Status → Warna](#9-utilitas-status--warna)
10. [Upload & Download (blob)](#10-upload--download-blob)
11. [Validasi Client](#11-validasi-client)
12. [Error, Loading, Empty State](#12-error-loading-empty-state)
13. [Lifecycle & Edge Case](#13-lifecycle--edge-case)
14. [Rencana Implementasi & Checklist QA](#14-rencana-implementasi--checklist-qa)

---

## 1. Konvensi yang Diwarisi

| Aspek | Konvensi (dipakai apa adanya) |
|---|---|
| Store | `defineStore('campaignStore', { state, actions })` (Options API). Factory `initDraft()` untuk reset. Auto-import (tanpa `import`). |
| API helper | `getData(type,url,params)`, `postData(type,url,data)`, `uploadData(type,url,formData)`, `downloadFile(type,url,params)` — arg pertama `type='api_url'`. `errorHelper(err)`. |
| Pola action | `try { const res = await getData('api_url', url, params); return res.data } catch(err){ alertStore.setAlert(msg,'danger'); return errorHelper(err) }`. Sukses aksi → `alertStore.setAlert(msg,'success')`. |
| Auth/scope | `useAuthStore().user.activeWorkspace.id` → query `workspaceId`. Token via interceptor otomatis. |
| Envelope | `{ success, message, data }`. List paginated = Spring `Page` di `data`: `content[]`, `totalElements`, `totalPages`, `number` (0-idx), `size`. **Kirim `page` 1-indexed** (BE `page-1`). |
| Route | `<route lang="yaml">` block: `name`, `meta.parent`, `meta.layout`, `meta.requiredAuth`, `meta.breadcrumb`. |
| Tabel | Komponen **`Table`** (baru; kolom slot + pagination bawaan; emits `setPage/setShow/search/setSort`). Ikuti pola `ListProduk`/`listPesanan`. |
| Status label | **`ChipCustom`** (type bebas: `success|warning|danger|info|…`) — bukan `Badge` (vocab terbatas). |
| Wizard | **`StepperCustom`** (`item:[{id,label}]`, `activeId`) — display-only; parent kontrol `activeId`. |
| Konfirmasi | **`ConfirmModal`** (`title/action/actionVerb/nextStatus/variable/visible/loading`, emit `closeModal`,`onAction`). |
| Format | `functions/moment.ts` (locale ID), `functions/delimiter.ts` (angka), `functions/formHelper.ts` (validateForm). |
| Toast | `useAlertStore().setAlert(message, 'success'|'danger')`. Breadcrumb via `useHeaderStore`. |

---

## 2. Struktur File Modul

```
src/pages/campaign/
├── index.vue        # /campaign — list (ganti placeholder existing)
├── buat.vue         # /campaign/buat — wizard
└── [id].vue         # /campaign/:id — detail + monitoring

src/modules/campaign/
├── store/campaignStore.ts
├── types.ts
├── utils/
│   ├── statusColor.ts          # map status → ChipCustom type + label ID
│   └── useCampaignProgress.ts  # composable polling progress
└── components/
    ├── CampaignList.vue
    ├── wizard/CampaignWizard.vue
    ├── wizard/StepUpload.vue
    ├── wizard/StepAnalisis.vue
    ├── wizard/StepTarget.vue
    ├── wizard/StepPesan.vue
    ├── wizard/StepReview.vue
    ├── detail/CampaignSummary.vue     # progress bar + kartu breakdown
    ├── detail/CampaignRecipients.vue  # tabel recipient + filter status
    └── detail/CampaignActions.vue     # Start/Pause/Resume/Cancel/Retry/Report + ConfirmModal
```
Komponen modul otomatis ter-register (`unplugin-vue-components` men-scan `src/modules/**/components/`). Store & utils auto-import.

---

## 3. Routing & Sidebar

Submenu **Campaign** (grup Chat) sudah ada → `pages/campaign/index.vue` tinggal diisi. Tambah 2 halaman baru dengan `<route>` block:

```yaml
# pages/campaign/index.vue
name: campaign
meta:
  parent: chat
  layout: dashboardLayout
  requiredAuth: 1
  breadcrumb:
    - { name: Chat, route: null }
    - { name: Campaign, route: campaign }
```
```yaml
# pages/campaign/buat.vue
name: campaign-buat
meta: { parent: chat, layout: dashboardLayout, requiredAuth: 1,
        breadcrumb: [ {name: Campaign, route: campaign}, {name: Buat Campaign, route: null} ] }
```
```yaml
# pages/campaign/[id].vue  → path /campaign/:id
name: campaign-detail
meta: { parent: chat, layout: dashboardLayout, requiredAuth: 1,
        breadcrumb: [ {name: Campaign, route: campaign}, {name: Detail Campaign, route: null} ] }
```
- **Tanpa `rolePermission`** (semua role, OQ-1). `parent: chat` agar menu Chat ter-highlight.
- Set judul via `useHeaderStore` di `onMounted` tiap halaman bila perlu.

---

## 4. TypeScript Types (`types.ts`)

```ts
export type CampaignStatus = 'DRAFT'|'QUEUED'|'RUNNING'|'PAUSED'|'FINISHED'|'CANCELLED'|'FAILED'
export type MessageStatus  = 'WAITING'|'SENDING'|'SENT'|'DELIVERED'|'READ'|'REPLIED'|'FAILED'|'SKIPPED'
export type TargetType     = 'ALL_VALID'|'EXISTING_ONLY'|'NEW_ONLY'
export type MessageSource  = 'TEMPLATE'|'CUSTOM'
export type ImportStatus   = 'UPLOADED'|'ANALYZING'|'ANALYZED'|'CONSUMED'|'FAILED'
export type ContactCategory= 'EXISTING'|'NEW'|'INVALID'|'DUPLICATE'

export interface ImportSummary {
  totalUpload: number; totalValid: number; totalInvalid: number;
  totalDuplicate: number; existingContact: number; newContact: number;
}
export interface ImportResponse {
  importId: number; fileName: string; status: ImportStatus;
  totalUpload: number; summary: ImportSummary | null;
}
export interface CampaignListItem {
  id: string; name: string; status: CampaignStatus; totalRecipient: number;
  countSent: number; countFailed: number; countReplied: number; createdAt: string;
}
export interface CampaignProgress {
  status: CampaignStatus; total: number; waiting: number; sending: number;
  success: number; failed: number; replied: number; skipped: number; percentage: number;
}
export interface CampaignDetail {
  id: string; name: string; status: CampaignStatus; messageSource: MessageSource;
  targetType: TargetType; mediaLink: string | null; totalRecipient: number;
  createdAt: string; startedAt: string | null; finishedAt: string | null;
  progress: CampaignProgress;
}
export interface RecipientItem {
  id: number; phone: string; name: string; status: MessageStatus; retryCount: number;
  lastError: string | null; sentAt: string | null; repliedAt: string | null; firstReplyMessage: string | null;
}
export interface CreateCampaignPayload {
  importId: number; name: string; targetType: TargetType; messageSource: MessageSource;
  templateId?: string | null; content?: string | null; mediaLink?: string | null;
  deviceId?: string | null; config?: null; // config disembunyikan (OQ-7) → selalu null
}
export interface WizardDraft {
  importId: number | null; fileName: string; totalUpload: number;
  summary: ImportSummary | null; name: string; targetType: TargetType;
  messageSource: MessageSource; templateId: string | null;
  content: string; mediaLink: string | null;
}
export interface Paged<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }
```

---

## 5. Store (`campaignStore.ts`)

Options-API `defineStore`. Menyimpan list, draft wizard, detail + progress + recipient, dan flag loading. Semua action mengikuti pola try/catch + `alertStore`.

```ts
export const initDraft = (): WizardDraft => ({
  importId: null, fileName: '', totalUpload: 0, summary: null,
  name: '', targetType: 'ALL_VALID', messageSource: 'CUSTOM',
  templateId: null, content: '', mediaLink: null,
})

export const useCampaignStore = defineStore('campaignStore', {
  state: () => ({
    // list
    list: [] as CampaignListItem[],
    listReq: { page: 1, limit: 10, search: '', status: '' as CampaignStatus | '' },
    listMeta: { totalElements: 0, totalPages: 0 },
    loadingList: false,
    // wizard
    draft: initDraft(),
    importStatus: '' as ImportStatus | '',
    analyzing: false,
    templateOptions: [] as { name: string; value: string }[],
    // detail
    detail: null as CampaignDetail | null,
    progress: null as CampaignProgress | null,
    loadingDetail: false,
    actionLoading: false,
    // recipients
    recipients: [] as RecipientItem[],
    recipientReq: { page: 1, limit: 20, status: '' as MessageStatus | '' },
    recipientMeta: { totalElements: 0, totalPages: 0 },
    loadingRecipients: false,
  }),

  getters: {
    workspaceId: () => useAuthStore().user.activeWorkspace.id,
  },

  actions: {
    // ---- list ----
    async fetchList() {
      const alert = useAlertStore(); this.loadingList = true
      try {
        const res = await getData('api_url', '/blast/campaign', {
          workspaceId: this.workspaceId, page: this.listReq.page, limit: this.listReq.limit,
          search: this.listReq.search || undefined, status: this.listReq.status || undefined,
        })
        this.list = res.data.data.content
        this.listMeta = { totalElements: res.data.data.totalElements, totalPages: res.data.data.totalPages }
        return res.data
      } catch (err) { alert.setAlert(errMsg(err), 'danger'); return errorHelper(err) }
      finally { this.loadingList = false }
    },

    // ---- import (wizard step 1-2) ----
    async uploadImport(file: File) {
      const alert = useAlertStore()
      try {
        const fd = new FormData(); fd.append('file', file)
        const res = await uploadData('api_url', `/blast/import?workspaceId=${this.workspaceId}`, fd)
        const d = res.data.data as ImportResponse
        this.draft.importId = d.importId; this.draft.fileName = d.fileName; this.draft.totalUpload = d.totalUpload
        this.importStatus = d.status
        return res.data
      } catch (err) { alert.setAlert(errMsg(err), 'danger'); return errorHelper(err) }
    },
    async pollImport() {                           // dipakai composable/interval di StepAnalisis
      if (!this.draft.importId) return
      const res = await getData('api_url', `/blast/import/${this.draft.importId}`, { workspaceId: this.workspaceId })
      const d = res.data.data as ImportResponse
      this.importStatus = d.status; this.draft.summary = d.summary
      return d
    },
    async analyzeImport() {                         // re-run manual (opsional)
      if (!this.draft.importId) return
      await postData('api_url', `/blast/import/${this.draft.importId}/analyze?workspaceId=${this.workspaceId}`, {})
      return this.pollImport()
    },
    async fetchImportContacts(importId: number, category: string, page = 1, limit = 20) {
      // pratinjau calon recipient dari staging (dipakai StepAnalisis "Lihat invalid" & recipient DRAFT)
      const res = await getData('api_url', `/blast/import/${importId}/contacts`, {
        workspaceId: this.workspaceId, category, page, limit,
      })
      return res.data.data // Paged<ImportContactRow>
    },
    async fetchTemplateOptions() {
      const res = await getData('api_url', '/template', { workspaceId: this.workspaceId, page: 1, limit: 100 })
      this.templateOptions = (res.data.data.content ?? res.data.data).map((t: any) => ({ name: t.namaTemplate, value: t.id }))
      return this.templateOptions
    },

    // ---- create + start ----
    async createCampaign(): Promise<string | null> {
      const alert = useAlertStore(); this.actionLoading = true
      try {
        const payload: CreateCampaignPayload = {
          importId: this.draft.importId!, name: this.draft.name, targetType: this.draft.targetType,
          messageSource: this.draft.messageSource,
          templateId: this.draft.messageSource === 'TEMPLATE' ? this.draft.templateId : null,
          content: this.draft.messageSource === 'CUSTOM' ? this.draft.content : null,
          mediaLink: this.draft.mediaLink, deviceId: null, config: null,
        }
        const res = await postData('api_url', `/blast/campaign?workspaceId=${this.workspaceId}`, payload)
        alert.setAlert('Campaign dibuat', 'success')
        return res.data.data.campaignId as string
      } catch (err) { alert.setAlert(errMsg(err), 'danger'); return null }
      finally { this.actionLoading = false }
    },
    async startCampaign(id: string)  { return this.simpleAction(id, 'start',  'Campaign dijalankan') },
    async pauseCampaign(id: string)  { return this.simpleAction(id, 'pause',  'Campaign dijeda') },
    async resumeCampaign(id: string) { return this.simpleAction(id, 'resume', 'Campaign dilanjutkan') },
    async cancelCampaign(id: string) { return this.simpleAction(id, 'cancel', 'Campaign dibatalkan') },
    async retryAllFailed(id: string) {
      const alert = useAlertStore(); this.actionLoading = true
      try {
        const res = await postData('api_url', `/blast/campaign/${id}/retry?workspaceId=${this.workspaceId}`, {})
        alert.setAlert(`${res.data.data.retried} pesan di-retry`, 'success'); return res.data
      } catch (err) { alert.setAlert(errMsg(err), 'danger'); return errorHelper(err) }
      finally { this.actionLoading = false }
    },
    async simpleAction(id: string, verb: string, okMsg: string) {
      const alert = useAlertStore(); this.actionLoading = true
      try {
        const res = await postData('api_url', `/blast/campaign/${id}/${verb}?workspaceId=${this.workspaceId}`, {})
        alert.setAlert(okMsg, 'success'); return res.data
      } catch (err) { alert.setAlert(errMsg(err), 'danger'); return errorHelper(err) }
      finally { this.actionLoading = false }
    },

    // ---- detail / monitoring ----
    async fetchDetail(id: string) {
      this.loadingDetail = true
      try {
        const res = await getData('api_url', `/blast/campaign/${id}`, { workspaceId: this.workspaceId })
        this.detail = res.data.data; this.progress = res.data.data.progress; return res.data
      } catch (err) { useAlertStore().setAlert(errMsg(err), 'danger'); return errorHelper(err) }
      finally { this.loadingDetail = false }
    },
    async fetchProgress(id: string) {              // dipanggil polling
      const res = await getData('api_url', `/blast/campaign/${id}/progress`, { workspaceId: this.workspaceId })
      this.progress = res.data.data
      if (this.detail) this.detail.status = this.progress!.status
      return this.progress
    },
    async fetchRecipients(id: string) {
      this.loadingRecipients = true
      try {
        const res = await getData('api_url', `/blast/campaign/${id}/messages`, {
          workspaceId: this.workspaceId, status: this.recipientReq.status || undefined,
          page: this.recipientReq.page, limit: this.recipientReq.limit,
        })
        this.recipients = res.data.data.content
        this.recipientMeta = { totalElements: res.data.data.totalElements, totalPages: res.data.data.totalPages }
        return res.data
      } catch (err) { useAlertStore().setAlert(errMsg(err), 'danger'); return errorHelper(err) }
      finally { this.loadingRecipients = false }
    },

    // ---- downloads (blob) ----
    async downloadReport(id: string, campaignName: string) {
      try {
        const res = await downloadFile('api_url', `/blast/campaign/${id}/report`, { workspaceId: this.workspaceId })
        saveBlob(res.data, reportFileName(campaignName))
      } catch (err) { useAlertStore().setAlert('Gagal mengunduh report', 'danger') }
    },
    async downloadTemplate() {
      try {
        const res = await downloadFile('api_url', '/blast/import/template-file', {})
        saveBlob(res.data, 'blast_template.xlsx')
      } catch (err) { useAlertStore().setAlert('Gagal mengunduh template', 'danger') }
    },

    resetDraft() { this.draft = initDraft(); this.importStatus = ''; this.analyzing = false },
  },
})
```
`errMsg(err)` = `err.response?.data?.message || 'Jaringan Bermasalah'`. `saveBlob`/`reportFileName` di §10.

---

## 6. Pemetaan API (helper calls)

| Store action | Helper | URL |
|---|---|---|
| `fetchList` | `getData` | `/blast/campaign?workspaceId&page&limit&search&status` |
| `uploadImport` | `uploadData` | `/blast/import?workspaceId` (FormData `file`) |
| `pollImport` | `getData` | `/blast/import/{importId}?workspaceId` |
| `analyzeImport` | `postData` | `/blast/import/{importId}/analyze?workspaceId` |
| `fetchImportContacts` (opsional) | `getData` | `/blast/import/{importId}/contacts?workspaceId&category&page&limit` |
| `fetchTemplateOptions` | `getData` | `/template?workspaceId&page&limit` |
| `createCampaign` | `postData` | `/blast/campaign?workspaceId` (body `CreateCampaignPayload`) |
| `startCampaign`/`pause`/`resume`/`cancel` | `postData` | `/blast/campaign/{id}/{verb}?workspaceId` |
| `retryAllFailed` | `postData` | `/blast/campaign/{id}/retry?workspaceId` (body `{}`) |
| `fetchDetail` | `getData` | `/blast/campaign/{id}?workspaceId` |
| `fetchProgress` | `getData` | `/blast/campaign/{id}/progress?workspaceId` |
| `fetchRecipients` | `getData` | `/blast/campaign/{id}/messages?workspaceId&status&page&limit` |
| `downloadReport` | `downloadFile` | `/blast/campaign/{id}/report?workspaceId` (blob) |
| `downloadTemplate` | `downloadFile` | `/blast/import/template-file` (blob) |

---

## 7. Composable Polling Progress

`utils/useCampaignProgress.ts` — poll `fetchProgress` tiap 3–5 dtk (OQ-6) saat status non-terminal; auto-stop saat terminal/unmount.

```ts
export function useCampaignProgress(campaignId: string, intervalMs = 4000) {
  const store = useCampaignStore()
  let timer: number | undefined
  const TERMINAL = new Set(['FINISHED', 'CANCELLED', 'FAILED'])

  const tick = async () => {
    const p = await store.fetchProgress(campaignId)
    if (!p || TERMINAL.has(p.status)) stop()
  }
  const start = () => {
    stop()
    if (store.progress && TERMINAL.has(store.progress.status)) return // sudah selesai
    timer = window.setInterval(tick, intervalMs)
  }
  const stop = () => { if (timer) { clearInterval(timer); timer = undefined } }

  onBeforeUnmount(stop)
  return { start, stop }
}
```
Halaman detail memanggil `start()` setelah `fetchDetail` bila status ∈ {QUEUED, RUNNING, PAUSED}. (PAUSED tetap poll agar update saat Resume dari device lain; opsional.)

---

## 8. Desain Komponen & Halaman

### 8.1 `pages/campaign/index.vue` + `CampaignList.vue`
- Halaman tipis: render `<DashboardContent>` + `<CampaignList />`.
- `CampaignList` (pakai store langsung, pola `ListProduk`):
  - `onMounted`: `store.fetchList()`.
  - `Table` dengan kolom (slot): Nama, Status (`ChipCustom`), Total, Terkirim, Gagal, Dibalas, Dibuat (`formatDateTime`), Aksi (tombol "Detail" → `router.push('/campaign/'+id)`).
  - Search (`InputCustom` debounce → set `listReq.search`, page=1, `fetchList`), filter status (`SelectCustom` → `listReq.status`).
  - Pagination: `Table` emits `setPage`/`setShow` → update `listReq` → `fetchList`.
  - Tombol "Buat Campaign" (`ButtonCustom`) → `router.push('/campaign/buat')`.
- **Emits:** —. **Props:** —.

### 8.2 `pages/campaign/buat.vue` + `CampaignWizard.vue` + steps
`CampaignWizard` memegang `activeId` (1..5) dan membaca/menulis `store.draft`.
```ts
const steps = [
  { id: 1, label: 'Upload' }, { id: 2, label: 'Analisis' }, { id: 3, label: 'Target' },
  { id: 4, label: 'Pesan' }, { id: 5, label: 'Review' },
]
```
Render `<StepperCustom :item="steps" :active-id="activeId" />` + komponen step aktif + tombol Kembali/Lanjut (Lanjut disable bila step belum valid).

**`StepUpload.vue`** — props: —; emits: `uploaded`, `next`.
- `FileUpload` (atau `ButtonFile`) accept `.xlsx,.xls`; validasi client ekstensi + ≤2MB (tampilkan `error`/`message`).
- Tombol "Unduh template" → `store.downloadTemplate()`.
- On upload → `store.uploadImport(file)`; jika `success` → `emit('uploaded')` lalu `emit('next')` (pindah ke Step 2 & mulai poll).

**`StepAnalisis.vue`** — props: —; emits: `next`.
- `onMounted`: mulai interval poll `store.pollImport()` tiap 1,5s sampai `importStatus === 'ANALYZED'` atau `'FAILED'`; `onBeforeUnmount` clear.
- Loading state "Menganalisis kontak…". Saat `ANALYZED` → tampilkan `BaseHighlightCard`: Total Upload, Valid, Existing, New, Invalid, Duplicate (dari `store.draft.summary`).
- Tombol "Analisis ulang" → `store.analyzeImport()`. Guard Lanjut: `summary.totalValid > 0`.
- (Opsional) tombol "Lihat invalid" → modal tabel `fetchImportContacts('INVALID')`.

**`StepTarget.vue`** — v-model `targetType` (ke `store.draft.targetType`); emits `next`.
- `RadioButton` group: `ALL_VALID` (Valid = existing+new), `EXISTING_ONLY`, `NEW_ONLY` — tampilkan angka proyeksi dari `summary`.
- Guard: proyeksi terpilih > 0.

**`StepPesan.vue`** — menulis `store.draft` (name, messageSource, templateId, content, mediaLink); emits `next`.
- `InputCustom` Nama Campaign (wajib).
- `SwitchButton`/`RadioButton` sumber: Template vs Custom.
  - Template: `SelectCustom :list="templateOptions"` (`fetchTemplateOptions` onMounted); preview konten template (read-only) bila perlu (ambil detail via `/template/{id}` opsional).
  - Custom: `TextAreaCustom` content (wajib); chip hint "Sisipkan `{{name}}` / `{{phone}}`" (klik menyisipkan token ke textarea); opsional media `FileUpload` → upload ke `POST /master/saktiform-media` → set `draft.mediaLink`.
- **Config lanjutan disembunyikan** (OQ-7).
- Guard: name terisi; jika Template → templateId; jika Custom → content non-kosong.

**`StepReview.vue`** — props: `draft` (atau baca store); emits `submit` payload `{ startNow: boolean }`.
- **Preview & estimasi dihitung client** (OQ-2): preview = `renderSample(draft.content/templateContent, { name: sampleName, phone: samplePhone })` (regex ganti `{{name}}`/`{{phone}}`); estimasi = `draft.totalRecipientTerpilih × 1500ms` → format ("± 25 menit").
- Tampilkan Nama, Jumlah Recipient (proyeksi target), Preview, Estimasi.
- Dua tombol:
  - **"Buat & Start"** → `const id = await store.createCampaign(); if (id) { await store.startCampaign(id); store.resetDraft(); router.push('/campaign/'+id) }`.
  - **"Simpan sebagai Draft"** (OQ-3) → `const id = await store.createCampaign(); if (id) { store.resetDraft(); router.push('/campaign/'+id) }`.
- Bila `createCampaign` gagal (mis. import `CONSUMED`) → tetap di step, toast sudah muncul dari store.

### 8.3 `pages/campaign/[id].vue`
- `onMounted`: `id = route.params.id`; `await store.fetchDetail(id)`; `await store.fetchRecipients(id)`; `const {start} = useCampaignProgress(id); start()`.
- Layout: header (Nama + `ChipCustom` status + `<CampaignActions>`), lalu `TabsWrapper` tabs `[{title:'Ringkasan',value:'summary'},{title:'Recipient',value:'recipients'}]`.
  - Panel summary → `<CampaignSummary :progress="store.progress" :detail="store.detail" />`.
  - Panel recipients → `<CampaignRecipients :campaign-id="id" :status="store.detail?.status" />` (status dipakai untuk empty state / pratinjau staging saat DRAFT).

**`CampaignSummary.vue`** — props `{ progress: CampaignProgress; detail: CampaignDetail }`; presentational.
- Progress bar (`percentage`) + kartu breakdown (`BaseHighlightCard`): Total, Waiting, Sending, Success, Failed, Replied, Skipped.
- Info: sumber pesan, target, media (link/preview), Dibuat/Dimulai/Selesai (`formatDateTime`).

**`CampaignRecipients.vue`** — props `{ campaignId: string; status: CampaignStatus }`.
- `SelectCustom` filter status pesan (`MessageStatus`) → set `recipientReq.status`, page=1, `fetchRecipients`.
- `Table` kolom: Phone, Nama, Status (`ChipCustom`), Retry (`retryCount`), Error (`lastError`), Terkirim (`sentAt`), Balasan (`firstReplyMessage` + `repliedAt`). Pagination via `recipientReq`.
- **Campaign `DRAFT`:** recipient (`blast_message`) baru terbentuk saat **Start**, jadi `GET /messages` untuk DRAFT mengembalikan **page kosong** (bukan error). Tampilkan empty state khusus, mis. *"Recipient tersedia setelah campaign dijalankan."* Untuk pratinjau calon recipient sebelum Start → panggil `fetchImportContacts(importId, category)` (endpoint staging), bukan `fetchRecipients`. `detail.totalRecipient` pada DRAFT = proyeksi.
- Tombol Refresh manual.

**`CampaignActions.vue`** — props `{ campaign: CampaignDetail }`; emits `changed`.
- Render tombol sesuai `campaign.status` (lihat matriks §9 PRD): Start(DRAFT), Pause(RUNNING), Resume(PAUSED), Cancel(DRAFT/QUEUED/RUNNING/PAUSED), Retry(bila `progress.failed>0`), Unduh Report(selalu).
- Cancel & Retry pakai `ConfirmModal` (state `visible`+`loading`). onAction → panggil store action → `emit('changed')` (page re-`fetchDetail`+`fetchProgress`+restart polling).
- Tombol di-disable saat `store.actionLoading` (cegah double-submit / double-start).

---

## 9. Utilitas Status → Warna

`utils/statusColor.ts`:
```ts
export const campaignChip = (s: CampaignStatus): { type: string; label: string } => ({
  DRAFT:     { type: 'info',    label: 'Draft' },
  QUEUED:    { type: 'info',    label: 'Antre' },
  RUNNING:   { type: 'warning', label: 'Berjalan' },
  PAUSED:    { type: 'warning', label: 'Dijeda' },
  FINISHED:  { type: 'success', label: 'Selesai' },
  CANCELLED: { type: 'danger',  label: 'Dibatalkan' },
  FAILED:    { type: 'danger',  label: 'Gagal' },
}[s])

export const messageChip = (s: MessageStatus): { type: string; label: string } => ({
  WAITING:{type:'info',label:'Menunggu'}, SENDING:{type:'info',label:'Mengirim'},
  SENT:{type:'success',label:'Terkirim'}, DELIVERED:{type:'success',label:'Sampai'},
  READ:{type:'success',label:'Dibaca'},  REPLIED:{type:'success',label:'Dibalas'},
  FAILED:{type:'danger',label:'Gagal'},  SKIPPED:{type:'warning',label:'Dilewati'},
}[s])
```
`ChipCustom :text="messageChip(s).label" :type="messageChip(s).type"`. (Jika desain minta `Badge`, tambah varian baru di `Badge.vue` — tapi `ChipCustom` lebih tepat karena vocab bebas.)

---

## 10. Upload & Download (blob)

**Upload Excel:** `FormData` + `uploadData` (helper memaksa `multipart/form-data`). Validasi client sebelum kirim:
```ts
const okExt = /\.(xlsx|xls)$/i.test(file.name)
const okSize = file.size <= 2 * 1024 * 1024
```
`ButtonFile` sudah punya validasi 2MB internal; tetap validasi ekstensi manual.

**Download (report/template):** `downloadFile` mengembalikan blob (`responseType:'blob'`). Simpan:
```ts
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = url; a.download = filename
  document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url)
}
export function reportFileName(name: string) {
  const safe = (name || 'campaign').trim().replace(/[^a-zA-Z0-9-_]+/g, '_')
  return `${safe}_messages_${formatDateSystem(new Date())}.xlsx` // yyyy-MM-dd
}
```
(Nama file dihitung client dari nama campaign + tanggal, selaras backend; header `Content-Disposition` tidak wajib dibaca.)

---

## 11. Validasi Client

Pakai `functions/formHelper.ts` (`validateForm`) atau guard inline per step:
- **Upload:** ekstensi `.xlsx/.xls`, ukuran ≤2MB → blokir Lanjut + pesan.
- **Analisis:** `summary.totalValid > 0`.
- **Target:** proyeksi terpilih > 0.
- **Pesan:** `name` required; Template → `templateId` required; Custom → `content` non-blank.
- **Retry:** hanya aktif bila `progress.failed > 0`.
Tombol Lanjut/aksi `:disabled` mengikuti hasil validasi + `actionLoading`.

---

## 12. Error, Loading, Empty State
- **Error server** → toast `alertStore` (pesan dari `data.message`). Kasus khas: 400 "Import sudah dipakai" (BR-22), 400 transisi state → toast + `fetchDetail` ulang.
- **Loading:** `loadingList`/`loadingDetail`/`loadingRecipients`/`actionLoading` → skeleton/spinner + disable tombol. `Table :loading`.
- **Empty:** list kosong → empty state + CTA Buat; recipient kosong per filter → "Tidak ada data".
- **401** → interceptor existing hapus token (redirect saat navigasi berikutnya) — perilaku global, tidak ditangani modul.

---

## 13. Lifecycle & Edge Case
- **Polling:** hanya saat detail aktif & status non-terminal; `clearInterval` di `onBeforeUnmount` dan saat status jadi terminal. Hindari interval ganda (guard di composable).
- **Wizard poll analisis:** juga di-clear on unmount / saat `ANALYZED`.
- **Draft:** reset setelah Create sukses; jika user tinggalkan wizard, draft tetap di store sampai reset manual (opsional: reset saat masuk `/campaign/buat`).
- **Double-submit:** `actionLoading` menonaktifkan tombol; setelah Start sukses langsung redirect (tidak ada tombol Start kedua).
- **Pagination:** server-side untuk list & recipient (recipient bisa besar) — jangan load semua.
- **Import CONSUMED:** jika user reload wizard dengan importId yang sudah dipakai, Create gagal → arahkan upload baru.

---

## 14. Rencana Implementasi & Checklist QA

**Urutan implementasi:**
1. `types.ts` + `statusColor.ts` + `campaignStore.ts` (list + detail + progress dulu).
2. `pages/campaign/index.vue` + `CampaignList` (list, search, filter, navigate).
3. `pages/campaign/[id].vue` + `CampaignSummary` + `CampaignRecipients` + `useCampaignProgress` (monitoring — bisa diuji dengan campaign yang dibuat via API/Swagger).
4. `CampaignActions` (Start/Pause/Resume/Cancel/Retry/Report).
5. Wizard: `StepUpload` → `StepAnalisis` → `StepTarget` → `StepPesan` → `StepReview` + `CampaignWizard`.
6. Upload media custom + unduh template/report (blob).
7. Poles: empty/loading/error, label ID, responsive dasar.

**Checklist QA (manual — tidak ada test infra):**
- [ ] List: pagination/search/filter server-side benar; klik → detail.
- [ ] Upload menolak non-xlsx / >2MB (client) dengan pesan.
- [ ] Step Analisis poll s/d ANALYZED lalu tampil summary benar; "Analisis ulang" bekerja.
- [ ] Target menampilkan proyeksi; tidak bisa lanjut bila 0.
- [ ] Template list muncul; Custom mendukung sisip `{{name}}`/`{{phone}}` + media.
- [ ] Review preview & estimasi tampil; "Buat & Start" membuat+start lalu redirect; "Simpan Draft" → DRAFT.
- [ ] Import CONSUMED → error jelas.
- [ ] Detail: progress polling jalan saat RUNNING/QUEUED, berhenti saat terminal/unmount (cek tidak ada interval bocor).
- [ ] Recipient: filter status + pagination; balasan tampil pada REPLIED.
- [ ] Pause/Resume/Cancel/Retry tampil sesuai status, konfirmasi untuk Cancel/Retry, UI ter-refresh.
- [ ] Unduh Report `.xlsx` (nama file benar) & unduh Template bekerja.
- [ ] Saat campaign berjalan, Inbox `/chat` menampilkan percakapan blast (verifikasi manual, tanpa perubahan FE).

---

*TDD ini turunan langsung dari PRD Frontend. Seluruh endpoint sudah tersedia di backend (Fase 0–7). Snippet = acuan; nama simbol final mengikuti review kode & lint dashboard.*
