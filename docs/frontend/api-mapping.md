# API Mapping — Saktiform Dashboard

All requests go through `src/apiConfig/client.ts`, which attaches `Authorization: Bearer <token>` on every call. All responses follow the shape `{ success: boolean, message: string, data: any }` unless noted. Errors are normalised via `errorHelper(err)` → `{ success: false, message: string }`.

---

## Auth

### POST `/account/login`
| Field | Value |
|---|---|
| **Module** | Auth |
| **Store** | `useAuthStore` → `login()` |
| **Used in** | `src/pages/login.vue` |

**Request**
```json
{ "username": "string", "password": "string" }
```

**Response `data`**
```json
{
  "nama": "string",
  "username": "string",
  "role": "OWNER | ADMIN | CUSTOMER_SERVICE",
  "workspaces": [{ "id": "any", "namaWorkspace": "string" }],
  "token": "string"
}
```

---

## Dashboard

### GET `/workspace/{workspaceId}/dashboard-matrix`
| Field | Value |
|---|---|
| **Module** | Dashboard |
| **Store** | `useWorkspaceStore` → `onGetDashboardMatrix()` |
| **Used in** | `src/pages/index.vue` |

**Request (query params)**
```
startDate: string (YYYY-MM-DD HH:mm:ss)
endDate:   string (YYYY-MM-DD HH:mm:ss)
```

**Response `data`**
```json
{
  "totalOrder": "number",
  "totalBayar": "number",
  "rasioBayar": "string",
  "unpaidOrder": "number"
}
```

---

### GET `/workspace/{workspaceId}/dashboard-order`
| Field | Value |
|---|---|
| **Module** | Dashboard |
| **Store** | `useWorkspaceStore` → `onGetDashboardOrder()` |
| **Used in** | `src/pages/index.vue` |

**Request (query params)**
```
startDate: string (YYYY-MM-DD HH:mm:ss)
endDate:   string (YYYY-MM-DD HH:mm:ss)
```

**Response `data`** — array of daily entries
```json
[{ "date": "string", "jumlahOrder": "number", "jumlahBayar": "number" }]
```

---

## Chat

### GET `/conversation/unassigned`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `fetchUnassignedChats()` |
| **Used in** | `src/pages/chat/index.vue` |

**Request (query params)**
```
workspaceId:  any
page:         number (default 1)
limit:        number (default 10)
agent?:       string
keyword?:     string
statusOrder?: string
statusPesan?: string
startDate?:   string
endDate?:     string
isUnread?:    boolean
```

**Response `data`** — Spring Page object
```json
{
  "content": [{ "id": "any", "contactName": "string", "contactPhone": "string",
    "lastMessage": "string", "lastMessageTime": "string", "lastMessageType": "string",
    "status": "string", "chatStatus": "string", "unreadMessageCount": "number" }],
  "pageable": { "pageNumber": "number", "pageSize": "number" },
  "totalElements": "number",
  "totalPages": "number"
}
```

---

### GET `/conversation/assigned`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `fetchAssignedChats()` |
| **Used in** | `src/pages/chat/index.vue` |

**Request (query params)** — same shape as `/conversation/unassigned`

**Response `data`** — same shape as `/conversation/unassigned`

---

### GET `/conversation/message`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `fetchChatMessages()` |
| **Used in** | `src/pages/chat/index.vue` (on chat select and message search) |

**Request (query params)**
```
conversationId: string
page:           number
limit:          number
keyword?:       string
```

**Response `data`** — Spring Page object
```json
{
  "content": [{
    "id": "any", "text": "string", "message": "string",
    "type": "TEXT | IMAGE | AUDIO | VIDEO | DOCUMENT",
    "mediaLink": "string", "pengirim": "CUSTOMER | string",
    "namaPengirim": "string", "tanggal": "string (YYYY-MM-DD HH:mm:ss)",
    "repliedMessage": { "pengirim": "string", "namaPengirim": "string",
      "type": "string", "text": "string", "message": "string" }
  }],
  "totalPages": "number"
}
```

---

### GET `/conversation/detail`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `fetchConversationDetail()` |
| **Used in** | `src/pages/chat/index.vue` (on chat select) |

**Request (query params)**
```
conversationId: string
```

**Response `data`**
```json
{
  "namaKontak": "string", "phoneNumber": "string",
  "handledBy": "string", "status": "string",
  "selectedOrder": "string | null"
}
```

---

### GET `/conversation/order`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `fetchConversationOrders()` |
| **Used in** | `src/pages/chat/index.vue` (on chat select) |

**Request (query params)**
```
conversationId: string
```

**Response `data`** — array
```json
[{
  "id": "string", "status": "string", "tanggalOrder": "string",
  "namaProduk": "string", "variasiProduk": "string", "isSelected": "boolean"
}]
```

---

### POST `/send-message`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `sendMessage()` |
| **Used in** | `src/pages/chat/index.vue` (via `ChatDetail` send handler) |

**Request body**
```json
{
  "conversationId": "string",
  "messageType": "TEXT | IMAGE | AUDIO | VIDEO | DOCUMENT",
  "mediaLink": "string | null",
  "message": "string",
  "repliedMessageId": "string | null"
}
```

**Response `data`** — not used by the frontend; new message arrives via WebSocket

---

### POST `/master/upload-file`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `uploadChatFile()` |
| **Used in** | `src/pages/chat/index.vue` (file attachment before send) |

**Request** — `multipart/form-data`
```
file: File
```

**Response `data`**
```
string  (the uploaded file URL / media link)
```

---

### GET `/conversation/takeover`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `takeoverChat()` |
| **Used in** | `src/pages/chat/index.vue` (Takeover button) |

**Request (query params)**
```
conversationId: string
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/conversation/select-order`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `selectOrder()` |
| **Used in** | `src/modules/chat/components/ChatDetail.vue` (order dropdown) |

**Request body**
```json
{ "conversationId": "string", "orderId": "string" }
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/template`
| Field | Value |
|---|---|
| **Module** | Chat / Workspace Management |
| **Store** | `useChatStore` → `quickChat()` and `useTemplateChatStore` → `onIndex()` |
| **Used in** | `src/modules/chat/components/ChatDetail.vue` (quick-chat modal), `src/modules/pengaturan/workspace-management/template-chat/components/ListWorkspaceTemplateChat.vue` |

**Request (query params)**
```
workspaceId?: any     (used by quickChat)
page:         number
limit:        number
idWorkspace?: any     (used by template management)
```

**Response `data`** — Spring Page object
```json
{
  "content": [{ "id": "any", "namaTemplate": "string", "content": "string", "kategori": "string" }],
  "totalPages": "number"
}
```

---

### POST `/conversation/quick-chat`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `generateQuickChatMessage()` |
| **Used in** | `src/modules/chat/components/ChatDetail.vue` (template selection) |

**Request body**
```json
{ "conversationId": "string", "templateId": "string" }
```

**Response `data`**
```json
{ "message": "string" }
```

---

### GET `/agent`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useChatStore` → `fetchAgents()` |
| **Used in** | `src/modules/chat/components/ChatList.vue` (role filter dropdown) |

**Request (query params)**
```
workspaceId: string
```

**Response `data`** — array of agent objects (exact shape consumed as-is for filter dropdown)

---

### POST `/conversation/add-order`
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useOrderStore` → `createOrder()` |
| **Used in** | `src/pages/chat/tambah-pesanan/index.vue` |

**Request body**
```json
{
  "idProduk": "string",
  "idAtributProduk": "string",
  "namaLengkap": "string",
  "nomorWhatsapp": "string",
  "alamat": "string",
  "idProvinsi": "number",
  "idKota": "number",
  "idKecamatan": "number",
  "metodePembayaran": "string",
  "status": "PAID | UNPAID | CANCELLED",
  "notes": "string",
  "diskon": "number",
  "source": "chat"
}
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/order/{id}` *(Chat context)*
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useOrderStore` → `fetchOrderDetail()` |
| **Used in** | `src/pages/chat/[id].vue` (order detail view), `src/pages/chat/edit-pesanan/index.vue` (pre-fill edit form) |

**Request** — path param `id`

**Response `data`**
```json
{
  "id": "any", "namaPenerima": "string", "nomorWhatsapp": "string",
  "alamat": "string", "ongkir": "number", "diskon": "number",
  "status": "string", "metodePembayaran": "string", "notes": "string",
  "tanggalOrder": "string", "handlyBy": "string",
  "namaProduk": "string",
  "idProduk": "string",
  "atributProduk": { "id": "string", "deskripsi": "string", "harga": "number" },
  "provinsi": { "id": "any", "provinceName": "string" },
  "kota": { "id": "any", "cityName": "string" },
  "kecamatan": { "id": "any", "districtName": "string" }
}
```

---

### GET `/order/{id}/logs` *(Chat context)*
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useOrderStore` → `fetchOrderLogs()` |
| **Used in** | `src/pages/chat/[id].vue` (history log section) |

**Request** — path param `id`

**Response `data`** — array
```json
[{ "time": "string", "log": "string" }]
```

---

### POST `/order/update` *(Chat context)*
| Field | Value |
|---|---|
| **Module** | Chat |
| **Store** | `useOrderStore` → `updateOrder()` |
| **Used in** | `src/pages/chat/edit-pesanan/index.vue` |

**Request body**
```json
{
  "id": "any",
  "idProduk": "string", "idAtributProduk": "string",
  "namaLengkap": "string", "nomorWhatsapp": "string",
  "alamat": "string",
  "idProvinsi": "number", "idKota": "number", "idKecamatan": "number",
  "metodePembayaran": "string", "status": "string",
  "notes": "string", "diskon": "number",
  "source": "chat"
}
```

**Response** — `{ success: boolean, message: string }`

---

## Location (shared — Chat & Pesanan)

### GET `/location/province`
| Field | Value |
|---|---|
| **Module** | Chat, Pesanan, Pengaturan (Gudang) |
| **Store** | `useLocationStore` (chat) → `fetchProvinces()` / `useLocationStore` (gudang) → `onIndexProvince()` / `useResponsiveStore` in `listPesanan` |
| **Used in** | `src/pages/chat/tambah-pesanan/index.vue`, `src/pages/chat/edit-pesanan/index.vue`, `src/modules/pesanan/components/listPesanan.vue`, `src/modules/pesanan/components/ModalEditPesanan.vue`, `src/modules/pengaturan/workspace-management/gudang/components/ModalFormGudang.vue` |

**Request** — none

**Response `data`** — array
```json
[{ "id": "any", "provinceName": "string" }]
```

---

### GET `/location/city`
| Field | Value |
|---|---|
| **Module** | Chat, Pesanan, Pengaturan (Gudang) |
| **Store** | `useLocationStore` (chat) → `fetchCities()` / `useLocationStore` (gudang) → `onIndexCity()` |
| **Used in** | Same pages as `/location/province` — triggered when a province is selected |

**Request (query params)**
```
provinceId: any
```

**Response `data`** — array
```json
[{ "id": "any", "cityName": "string" }]
```

---

### GET `/location/district`
| Field | Value |
|---|---|
| **Module** | Chat, Pesanan, Pengaturan (Gudang) |
| **Store** | `useLocationStore` (chat) → `fetchDistricts()` / `useLocationStore` (gudang) → `onIndexDistrict()` |
| **Used in** | Same pages as `/location/province` — triggered when a city is selected |

**Request (query params)**
```
cityId: any
```

**Response `data`** — array
```json
[{ "id": "any", "districtName": "string" }]
```

---

## Pesanan (Orders)

### GET `/order`
| Field | Value |
|---|---|
| **Module** | Pesanan |
| **Store** | `usePesananStore` → `onIndex()` |
| **Used in** | `src/modules/pesanan/components/listPesanan.vue` |

**Request (query params)**
```
page:              number
limit:             number
workspaceId:       any
search?:           string
searchBy?:         string (orderCode | namaProduk)
idProvinsi?:       any
idKota?:           any
idKecamatan?:      any
status?:           PAID | UNPAID | CANCELLED
jenisPembayaran?:  BANK_TRANSFER | COD
statusEkspor?:     boolean
tanggalAwalPaid?:  string
tanggalAkhirPaid?: string
tanggalAwalOrder?: string
tanggalAkhirOrder?: string
```

**Response `data`** — Spring Page object
```json
{
  "content": [{
    "id": "any", "orderCode": "string", "namaCustomer": "string",
    "namaProduk": "string", "provinsi": "string", "status": "string",
    "notes": "string", "tanggalOrder": "string", "paidAt": "string",
    "jenisPembayaran": "string", "statusEkspor": "boolean"
  }],
  "pageable": { "pageNumber": "number", "pageSize": "number" },
  "totalPages": "number"
}
```

---

### GET `/order/{id}` *(Pesanan context)*
| Field | Value |
|---|---|
| **Module** | Pesanan |
| **Store** | `usePesananStore` → `onDetail()` |
| **Used in** | `src/modules/pesanan/components/ModalDetailPesanan.vue`, `src/modules/pesanan/components/ModalEditPesanan.vue` |

**Request** — path param `id`

**Response `data`** — same shape as Chat's `/order/{id}` response

---

### POST `/order/update` *(Pesanan context)*
| Field | Value |
|---|---|
| **Module** | Pesanan |
| **Store** | `usePesananStore` → `onUpdate()` |
| **Used in** | `src/modules/pesanan/components/ModalEditPesanan.vue` |

**Request body**
```json
{
  "id": "any",
  "idProduk": "string", "idAtributProduk": "string",
  "namaLengkap": "string", "nomorWhatsapp": "string",
  "alamat": "string",
  "idProvinsi": "any", "idKota": "any", "idKecamatan": "any",
  "metodePembayaran": "string",
  "notes": "string",
  "diskon": "number",
  "status": "PAID | UNPAID | CANCELLED"
}
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/order/update-bulk`
| Field | Value |
|---|---|
| **Module** | Pesanan |
| **Store** | `usePesananStore` → `onUpdateBulk()` |
| **Used in** | `src/modules/pesanan/components/listPesanan.vue` (bulk status change) |

**Request body** — array
```json
[{ "id": "any", "status": "PAID | UNPAID | CANCELLED" }]
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/order/status`
| Field | Value |
|---|---|
| **Module** | Pesanan, Chat |
| **Store** | `usePesananStore` → `onGetStatus()` / `useChatStore` → `fetchOrderStatus()` |
| **Used in** | `src/modules/pesanan/components/listPesanan.vue`, `src/modules/pesanan/components/ModalEditPesanan.vue` |

**Request** — none

**Response `data`** — array of status strings
```json
["PAID", "UNPAID", "CANCELLED"]
```
Mapped client-side to `[{ name: string, value: string }]`

---

### GET `/order/export`
| Field | Value |
|---|---|
| **Module** | Pesanan |
| **Store** | `usePesananStore` → `onExport()` |
| **Used in** | `src/modules/pesanan/components/listPesanan.vue` (Export button) |

**Request (query params)**
```
workspaceId:       any
idProvinsi?:       any
idKota?:           any
idKecamatan?:      any
status?:           string
jenisPembayaran?:  string
statusEkspor?:     boolean
tanggalAwalPaid?:  string
tanggalAkhirPaid?: string
tanggalAwalOrder?: string
tanggalAkhirOrder?: string
```

**Response** — binary blob (`application/xlsx`). Downloaded client-side as `Pesanan.xlsx`

---

### GET `/order/{id}/logs` *(Pesanan context)*
| Field | Value |
|---|---|
| **Module** | Pesanan |
| **Store** | `usePesananStore` → `onGetLogs()` |
| **Used in** | `src/modules/pesanan/components/ModalDetailPesanan.vue` |

**Request** — path param `id`

**Response `data`** — same shape as Chat's `/order/{id}/logs`

---

### GET `/order/abandoned`
| Field | Value |
|---|---|
| **Module** | Pesanan |
| **Store** | `usePesananStore` → `onIndexAbandoned()` |
| **Used in** | `src/modules/pesanan/components/ListAbandonedCart.vue` |

**Request (query params)**
```
page:        number
limit:       number
workspaceId: any
search?:     string
```

**Response `data`** — Spring Page object
```json
{
  "content": [{ "id": "any", "namaCustomer": "string", "namaProduk": "string", "alamat": "string" }],
  "pageable": { "pageNumber": "number", "pageSize": "number" },
  "totalPages": "number"
}
```

---

### GET `/order/abandoned/{id}`
| Field | Value |
|---|---|
| **Module** | Pesanan |
| **Store** | `usePesananStore` → `onDetailAbandoned()` |
| **Used in** | `src/modules/pesanan/components/ModalEditAbandonedCart.vue` |

**Request** — path param `id`

**Response `data`** — abandoned cart record (shape consumed as-is)

---

### POST `/order/abandoned/delete`
| Field | Value |
|---|---|
| **Module** | Pesanan |
| **Store** | `usePesananStore` → `onDeleteAbandoned()` |
| **Used in** | `src/modules/pesanan/components/ListAbandonedCart.vue` (delete action) |

**Request body** — array
```json
[{ "id": "any" }]
```

**Response** — `{ success: boolean, message: string }`

---

## Produk (Products)

### GET `/produk`
| Field | Value |
|---|---|
| **Module** | Produk |
| **Store** | `useProdukStore` → `onIndex()` |
| **Used in** | `src/modules/produk/components/ListProduk.vue`, `src/pages/chat/tambah-pesanan/index.vue`, `src/pages/chat/edit-pesanan/index.vue` |

**Request (query params)**
```
page:        number
limit:       number
workspaceId: any
search?:     string
```

**Response `data`** — Spring Page object
```json
{
  "content": [{
    "id": "any", "namaProduk": "string", "harga": "number",
    "totalOrder": "number", "totalDibayar": "number",
    "rasioDibayar": "string", "totalTerjual": "number"
  }],
  "pageable": { "pageNumber": "number", "pageSize": "number" },
  "totalPages": "number"
}
```

---

### GET `/produk/list-dropdown`
| Field | Value |
|---|---|
| **Module** | Produk |
| **Store** | `useProdukStore` → `onList()` |
| **Used in** | `src/modules/pesanan/components/ModalEditPesanan.vue` |

**Request (query params)**
```
workspaceId: string
```

**Response `data`** — array
```json
[{ "id": "any", "namaProduk": "string" }]
```
Mapped to `[{ name, value }]`

---

### GET `/produk/{id}`
| Field | Value |
|---|---|
| **Module** | Produk |
| **Store** | `useProdukStore` → `onDetail()` |
| **Used in** | `src/modules/produk/components/FormProduk.vue` (edit mode) |

**Request** — path param `id`

**Response `data`** — full product object matching the `initProduk()` shape (name, images, features, variants, payments, warehouse, form config, testimonials, tracking IDs, etc.)

---

### POST `/produk`
| Field | Value |
|---|---|
| **Module** | Produk |
| **Store** | `useProdukStore` → `onStore()` |
| **Used in** | `src/modules/produk/components/FormProduk.vue` (create) |

**Request body** — full product payload matching `initProduk()` shape:
```json
{
  "idWorkspace": "any",
  "namaProduk": "string", "urlCheckout": "string", "narasiTombol": "string",
  "gambarProduk": ["string"],
  "poinFitur": ["string"],
  "atributProduk": [{ "deskripsi": "string", "harga": "string", "berat": "number" }],
  "pembayaran": ["any"],
  "idGudang": "any",
  "formConfig": ["any"],
  "ekstra": ["any"],
  "testimoni": [{ "nama": "string", "pesan": "string", "urlGambar": "string" }],
  "facebookPixelId": "string",
  "googleGtmId": "string",
  "embededCheckoutScript": "string",
  "embededPurchaseScript": "string"
}
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/produk/delete`
| Field | Value |
|---|---|
| **Module** | Produk |
| **Store** | `useProdukStore` → `onDelete()` |
| **Used in** | `src/modules/produk/components/ListProduk.vue` |

**Request body** — array of IDs
```json
["any"]
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/produk/copy`
| Field | Value |
|---|---|
| **Module** | Produk |
| **Store** | `useProdukStore` → `onCopyData()` |
| **Used in** | `src/modules/produk/components/ListProduk.vue` (copy action) |

**Request (query params)**
```
id: any  (product to copy)
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/produk/uploadFile`
| Field | Value |
|---|---|
| **Module** | Produk |
| **Store** | `useProdukStore` → `onUploadProductFile()` |
| **Used in** | `src/modules/produk/components/FormProduk.vue` (image upload) |

**Request** — `multipart/form-data`
```
file: File
```

**Response `data`** — uploaded file URL string

---

### GET `/produk/{id}/attribut`
| Field | Value |
|---|---|
| **Module** | Produk, Chat, Pesanan |
| **Store** | `useProdukStore` → `onGetProductAttribute()` |
| **Used in** | `src/pages/chat/tambah-pesanan/index.vue`, `src/pages/chat/edit-pesanan/index.vue`, `src/modules/pesanan/components/ModalEditPesanan.vue` |

**Request** — path param `id`

**Response `data`** — array
```json
[{ "id": "any", "deskripsi": "string", "harga": "number", "berat": "number" }]
```
Mapped to `[{ value, name, harga }]`

---

### GET `/produk/{id}/pembayaran`
| Field | Value |
|---|---|
| **Module** | Produk, Chat, Pesanan |
| **Store** | `useProdukStore` → `onGetProductPayment()` |
| **Used in** | `src/pages/chat/tambah-pesanan/index.vue`, `src/pages/chat/edit-pesanan/index.vue`, `src/modules/pesanan/components/ModalEditPesanan.vue` |

**Request** — path param `id`

**Response `data`** — array of payment method strings
```json
["BANK_TRANSFER", "COD"]
```

---

## Pengaturan — User Management

### GET `/account`
| Field | Value |
|---|---|
| **Module** | Pengaturan / User Management |
| **Store** | `useUserStore` → `onIndex()` |
| **Used in** | `src/modules/pengaturan/user-management/components/ListUserManagement.vue` |

**Request (query params)**
```
page:  number
limit: number
```

**Response `data`** — Spring Page object
```json
{
  "content": [{ "id": "any", "nama": "string", "username": "string", "role": "string" }],
  "pageable": { "pageNumber": "number", "pageSize": "number" },
  "totalPages": "number"
}
```

---

### GET `/account/{id}`
| Field | Value |
|---|---|
| **Module** | Pengaturan / User Management |
| **Store** | `useUserStore` → `onDetail()` |
| **Used in** | `src/modules/pengaturan/user-management/components/ListUserManagement.vue` (edit flow) |

**Request** — path param `id`

**Response `data`** — user object matching `initUser()` shape

---

### POST `/account`
| Field | Value |
|---|---|
| **Module** | Pengaturan / User Management |
| **Store** | `useUserStore` → `onStore()` |
| **Used in** | `src/modules/pengaturan/user-management/components/ListUserManagement.vue` |

**Request body**
```json
{
  "nama": "string", "username": "string",
  "password": "string", "confirmPassword": "string",
  "role": "OWNER | ADMIN | CUSTOMER_SERVICE",
  "idWorkspaces": ["any"]
}
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/account/delete`
| Field | Value |
|---|---|
| **Module** | Pengaturan / User Management |
| **Store** | `useUserStore` → `onDelete()` |
| **Used in** | `src/modules/pengaturan/user-management/components/ListUserManagement.vue` |

**Request body**
```json
{ "id": "any" }
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/account/delete-bulk`
| Field | Value |
|---|---|
| **Module** | Pengaturan / User Management |
| **Store** | `useUserStore` → `onDeleteBulk()` |
| **Used in** | `src/modules/pengaturan/user-management/components/ListUserManagement.vue` |

**Request body** — array of IDs
```json
["any"]
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/account/reset-password`
| Field | Value |
|---|---|
| **Module** | Pengaturan / User Management |
| **Store** | `useUserStore` → `onResetPassword()` |
| **Used in** | `src/modules/pengaturan/user-management/components/ListUserManagement.vue` |

**Request body**
```json
{ "id": "any", "password": "string", "confirmPassword": "string" }
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/account/role`
| Field | Value |
|---|---|
| **Module** | Pengaturan / User Management |
| **Store** | `useUserStore` → `getRoles()` |
| **Used in** | `src/modules/pengaturan/user-management/components/ListUserManagement.vue` (role select on create) |

**Request** — none

**Response `data`** — array of role strings
```json
["OWNER", "ADMIN", "CUSTOMER_SERVICE"]
```
Mapped to `[{ value: string, name: string }]` using `convertSentenceCase`

---

### GET `/account/list`
| Field | Value |
|---|---|
| **Module** | Pengaturan / User Management |
| **Store** | `useUserStore` → `getAvailableUsers()` |
| **Used in** | `src/modules/pengaturan/workspace-management/user/components/ModalAddWorkspaceUser.vue` |

**Request** — none

**Response `data`** — array
```json
[{ "id": "any", "name": "string" }]
```
Mapped to `[{ name, value }]`

---

## Pengaturan — Workspace Management

### GET `/workspace`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace |
| **Store** | `useWorkspaceStore` → `onIndex()` |
| **Used in** | `src/modules/pengaturan/workspace-management/components/ListWorkspaceManagement.vue` |

**Request (query params)**
```
page:  number
limit: number
```

**Response `data`** — Spring Page object with workspace list

---

### GET `/workspace/list`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace |
| **Store** | `useWorkspaceStore` → `onList()` |
| **Used in** | `src/modules/pengaturan/workspace-management/components/ListWorkspaceManagement.vue` (workspace switcher dropdown) |

**Request** — none

**Response `data`** — array
```json
[{ "id": "any", "namaWorkspace": "string" }]
```

---

### GET `/workspace/{id}`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace, Dashboard |
| **Store** | `useWorkspaceStore` → `onDetail()` |
| **Used in** | `src/modules/pengaturan/workspace-management/components/DetailWorkspace.vue` |

**Request** — path param `id`

**Response `data`** — workspace object matching `initWorkspace()` shape:
```json
{
  "namaWorkspace": "string", "wabaId": "string",
  "idUsers": ["any"],
  "gudang": { "namaGudang": "string", "alamat": "string",
    "idProvinsi": "any", "idKota": "any", "idKecamatan": "any" }
}
```

---

### POST `/workspace`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace |
| **Store** | `useWorkspaceStore` → `onStore()` |
| **Used in** | `src/modules/pengaturan/workspace-management/components/ListWorkspaceManagement.vue` |

**Request body** — workspace object matching `initWorkspace()` shape

**Response** — `{ success: boolean, message: string }`

---

### POST `/workspace/update`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace |
| **Store** | `useWorkspaceStore` → `onUpdate()` |
| **Used in** | `src/modules/pengaturan/workspace-management/components/DetailWorkspace.vue` |

**Request body** — workspace object (without `gudang` and `users` keys, stripped before send)
```json
{ "id": "any", "namaWorkspace": "string", "wabaId": "string", "idUsers": ["any"] }
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/workspace/{id}/account`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace |
| **Store** | `useWorkspaceStore` → `onGetWorkspaceAccount()` |
| **Used in** | `src/modules/pengaturan/workspace-management/user/components/ListWorkspaceUser.vue` |

**Request (query params)**
```
workspaceId: any
page:        number
limit:       number
```

**Response `data`** — Spring Page object
```json
{ "content": [{ "id": "any", "nama": "string", "username": "string", "role": "string" }] }
```

---

### POST `/workspace/{id}/account`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace |
| **Store** | `useWorkspaceStore` → `onStoreWorkspaceAccount()` |
| **Used in** | `src/modules/pengaturan/workspace-management/user/components/ModalAddWorkspaceUser.vue` |

**Request body**
```json
{ "idAccount": "any" }
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/workspace/{id}/account/remove`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace |
| **Store** | `useWorkspaceStore` → `onDeleteWorkspaceAccount()` |
| **Used in** | `src/modules/pengaturan/workspace-management/user/components/ListWorkspaceUser.vue` |

**Request (query params)**
```
idAccount: any
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/workspace/{id}/domain`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace |
| **Store** | `useWorkspaceStore` → `onGetWorkspaceDomain()` |
| **Used in** | `src/modules/produk/components/ListProduk.vue` (to show linked domain) |

**Request** — path param `id`

**Response `data`**
```json
{ "id": "any", "domain": "string" }
```

---

### POST `/workspace/domain`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace |
| **Store** | `useWorkspaceStore` → `onSetWorkspaceDomain()` |
| **Used in** | `src/modules/produk/components/ListProduk.vue` (domain selector) |

**Request body**
```json
{ "workspaceId": "any", "domainId": "any" }
```

**Response** — `{ success: boolean, message: string }`

---

## Pengaturan — Warehouse (Gudang)

### GET `/gudang`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Gudang |
| **Store** | `useGudangStore` → `onIndex()` |
| **Used in** | `src/modules/pengaturan/workspace-management/gudang/components/ListWorkspaceGudang.vue` |

**Request (query params)**
```
page:        number
limit:       number
workspaceId: any
```

**Response `data`** — Spring Page object with warehouse list

---

### GET `/gudang/{id}`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Gudang |
| **Store** | `useGudangStore` → `onDetail()` |
| **Used in** | `src/modules/pengaturan/workspace-management/gudang/components/ModalFormGudang.vue` (edit mode) |

**Request** — path param `id`

**Response `data`** — warehouse object matching `initGudang()` shape

---

### POST `/gudang`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Gudang |
| **Store** | `useGudangStore` → `onStore()` |
| **Used in** | `src/modules/pengaturan/workspace-management/gudang/components/ModalFormGudang.vue` |

**Request body**
```json
{
  "namaGudang": "string", "alamat": "string",
  "idProvinsi": "any", "idKota": "any", "idKecamatan": "any"
}
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/gudang/delete`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Gudang |
| **Store** | `useGudangStore` → `onDelete()` |
| **Used in** | `src/modules/pengaturan/workspace-management/gudang/components/ListWorkspaceGudang.vue` |

**Request (query params)**
```
id: any
```

**Response** — `{ success: boolean, message: string }`

---

## Pengaturan — Domain

### GET `/domain/list`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Domain |
| **Store** | `useDomainStore` → `onIndex()` |
| **Used in** | `src/modules/pengaturan/workspace-management/domain/components/ListWorkspaceDomain.vue` |

**Request (query params)**
```
page:        number
limit:       number
workspaceId: any
```

**Response `data`** — Spring Page object with domain list

---

### POST `/domain/upsert`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Domain |
| **Store** | `useDomainStore` → `onStore()` |
| **Used in** | `src/modules/pengaturan/workspace-management/domain/components/ModalFormDomain.vue` |

**Request body**
```json
{ "id": "any | null", "domain": "string", "workspaceId": "any" }
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/domain/delete`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Domain |
| **Store** | `useDomainStore` → `onDelete()` |
| **Used in** | `src/modules/pengaturan/workspace-management/domain/components/ListWorkspaceDomain.vue` |

**Request body**
```json
{ "id": "any" }
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/domain/dropdown`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Domain, Produk |
| **Store** | `useDomainStore` → `onGetAvailableDomain()` |
| **Used in** | `src/modules/produk/components/ListProduk.vue` (domain picker dropdown) |

**Request (query params)**
```
workspaceId: any
```

**Response `data`** — array
```json
[{ "id": "any", "domain": "string" }]
```
Mapped to `[{ name, value }]`

---

## Pengaturan — Chat Templates

### GET `/template/{id}`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Template |
| **Store** | `useTemplateChatStore` → `onDetail()` |
| **Used in** | `src/modules/pengaturan/workspace-management/template-chat/components/ModalFormTemplateChat.vue` (edit mode) |

**Request** — path param `id`

**Response `data`** — template object matching `initTemplateChat()` shape:
```json
{ "id": "any", "idWorkspace": "any", "namaTemplate": "string", "content": "string", "mediaLink": "string | null" }
```

---

### POST `/template`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Template |
| **Store** | `useTemplateChatStore` → `onStore()` |
| **Used in** | `src/modules/pengaturan/workspace-management/template-chat/components/ModalFormTemplateChat.vue` |

**Request body**
```json
{ "id": "any | null", "idWorkspace": "any", "namaTemplate": "string", "content": "string", "mediaLink": "string | null" }
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/template/delete`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Template |
| **Store** | `useTemplateChatStore` → `onDelete()` |
| **Used in** | `src/modules/pengaturan/workspace-management/template-chat/components/ListWorkspaceTemplateChat.vue` |

**Request (query params)**
```
id: any
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/template/variable`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Workspace / Template |
| **Store** | `useTemplateChatStore` → `onGetTemplateVariable()` |
| **Used in** | `src/modules/pengaturan/workspace-management/template-chat/components/ModalFormTemplateChat.vue` (variable picker) |

**Request** — none

**Response `data`** — array
```json
[{ "field": "string", "placeholder": "string" }]
```
Mapped to `[{ name: field, value: placeholder }]`

---

### POST `/master/saktiform-media`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Template Chat |
| **Store** | `useTemplateChatStore` → `onUploadMedia()` |
| **Used in** | `src/modules/pengaturan/workspace-management/template-chat/components/ModalFormTemplateChat.vue` (media upload) |

**Request** — `multipart/form-data`
```
file: File
```

**Response `data`**
```
string  (the uploaded media URL)
```

---

## Pengaturan — Province Exception

### GET `/master/province/blocked`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Disable Provinsi |
| **Store** | `useProvinceExceptionStore` → `onGetBlocked()` |
| **Used in** | `src/modules/pengaturan/province-exception/components/ListProvinceException.vue` |

**Request** — none

**Response `data`** — array or Spring Page object (frontend handles both shapes)
```json
[{ "id": "any", "provinceName": "string" }]
```

---

### POST `/master/province/block`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Disable Provinsi |
| **Store** | `useProvinceExceptionStore` → `onBlock()` |
| **Used in** | `src/modules/pengaturan/province-exception/components/ListProvinceException.vue` (bulk add modal) |

**Request body** — array of province IDs
```json
[number]
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/master/province/unblock`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Disable Provinsi |
| **Store** | `useProvinceExceptionStore` → `onUnblock()` |
| **Used in** | `src/modules/pengaturan/province-exception/components/ListProvinceException.vue` (unblock action) |

**Request body** — array with a single province ID
```json
[number]
```

**Response** — `{ success: boolean, message: string }`

---

## Pengaturan — WhatsApp Business

### GET `/whatsapp`
| Field | Value |
|---|---|
| **Module** | Pengaturan / WhatsApp Business |
| **Store** | `useWhatsappStore` → `onIndex()` |
| **Used in** | `src/modules/pengaturan/whatsapp-business/components/ListWhatsappBusiness.vue` |

**Request (query params)**
```
page:  number
limit: number
```

**Response `data`** — Spring Page object with WhatsApp account list

---

### GET `/whatsapp/{id}`
| Field | Value |
|---|---|
| **Module** | Pengaturan / WhatsApp Business |
| **Store** | `useWhatsappStore` → `onDetail()` |
| **Used in** | `src/modules/pengaturan/whatsapp-business/components/ListWhatsappBusiness.vue` (edit modal) |

**Request** — path param `id`

**Response `data`** — WhatsApp account object:
```json
{ "id": "string | null", "nomorWhatsapp": "string" }
```

---

### POST `/whatsapp`
| Field | Value |
|---|---|
| **Module** | Pengaturan / WhatsApp Business |
| **Store** | `useWhatsappStore` → `onStore()` |
| **Used in** | `src/modules/pengaturan/whatsapp-business/components/ListWhatsappBusiness.vue` |

**Request body**
```json
{ "nomorWhatsapp": "string" }
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/whatsapp/delete`
| Field | Value |
|---|---|
| **Module** | Pengaturan / WhatsApp Business |
| **Store** | `useWhatsappStore` → `onDelete()` |
| **Used in** | `src/modules/pengaturan/whatsapp-business/components/ListWhatsappBusiness.vue` |

**Request body**
```json
{ "id": "string" }
```

**Response** — `{ success: boolean, message: string }`

---

### POST `/whatsapp/connect`
| Field | Value |
|---|---|
| **Module** | Pengaturan / WhatsApp Business |
| **Store** | `useWhatsappStore` → `onConnect()` |
| **Used in** | `src/modules/pengaturan/whatsapp-business/components/ListWhatsappBusiness.vue` |

**Request body**
```json
{ "wabaId": "string" }
```

**Response** — `{ success: boolean, message: string }`

---

### GET `/whatsapp/available`
| Field | Value |
|---|---|
| **Module** | Pengaturan / WhatsApp Business, Workspace |
| **Store** | `useWhatsappStore` → `onGetAvailableWhatsapp()` |
| **Used in** | `src/modules/pengaturan/workspace-management/components/DetailWorkspace.vue` (WABA selector) |

**Request** — none

**Response `data`** — array
```json
[{ "id": "any", "phoneNumber": "string" }]
```
Mapped to `[{ name: formatPhoneNumber(phoneNumber), value: id }]`

---

## Pengaturan — Model AI

### GET `/master/ai-key`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Model AI |
| **Store** | `useMasterStore` → `onGetAI()` |
| **Used in** | `src/pages/pengaturan/model-ai/index.vue` |

**Request** — none

**Response `data`** — string (the API key value)

---

### POST `/master/ai-key`
| Field | Value |
|---|---|
| **Module** | Pengaturan / Model AI |
| **Store** | `useMasterStore` → `onStoreAI()` |
| **Used in** | `src/pages/pengaturan/model-ai/index.vue` |

**Request body**
```json
{ "key": "string" }
```

**Response** — `{ success: boolean, message: string }`

---

## Notes

- **Endpoints that are DELETE-via-GET**: `/gudang/delete` and `/template/delete` use `GET` with an `id` query param instead of `DELETE` — this is how the backend has implemented them.
- **`/master/facebook-pixel` and `/master/google-gtm`**: Actions exist in `useMasterStore` but are not called from any page in the current implementation.
- **`/workspace/{id}/dashboard-pendapatan`**: Action exists in `useWorkspaceStore` (`onGetDashboardPendapatan`) but is not called from any page in the current implementation.
- **`/chat/status`**: `fetchChatStatus()` exists in `useChatStore` but is not called from any component in the current implementation.
- **`/master/province/blocked`, `/master/province/block`, `/master/province/unblock`**: Province Exception endpoints. The frontend uses defensive mapping for the blocked list: `Array.isArray(data) ? data : (data?.content ?? [])` to handle both flat array and Spring Page responses.
