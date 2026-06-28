# PRD — Chat Template `mediaLink`

| Field | Value |
|---|---|
| Feature name | Chat Template Media Link |
| Component | `ChatTemplate` entity, `AddChatTemplateDto`, `MessageTemplateService` |
| Status | Reviewed — decisions captured |
| Scope | Per-workspace (chat templates are workspace-scoped) |
| Author | — |
| Last updated | 2026-06-29 |

> This PRD proposes adding a single `mediaLink` field to chat templates. The open questions raised during drafting have been answered and are recorded under **Resolved Decisions**; the sections below reflect those answers.

---

## 1. Background

Chat templates (`ChatTemplate`, table `chat_template`) are reusable WhatsApp message templates scoped to a workspace. They are created/updated through:

```
POST /template  →  ChatTemplateController.saveTemplate(AddChatTemplateDto)
                →  MessageTemplateService.upsertMessageTemplate(...)
```

The current `AddChatTemplateDto` carries only: `id`, `idWorkspace`, `namaTemplate`, `content`. The `ChatTemplate` entity has no media attachment.

The platform already handles media on chat messages using a consistent **store-path / return-URL** pattern via `StorageService`:

- **On write** (e.g. `ChatService` line 102): the incoming public URL is converted to a storage path before persisting —
  `chat.setMedia(data.getMediaLink() != null ? storageService.extractPathFromPublicUrl(data.getMediaLink()) : null)`.
- **On read** (e.g. `ConversationService` line 95): the stored path is converted back to a public URL —
  `data.setMediaLink(storageService.getPublicUrl(data.getMediaLink()))`.

`StorageService.extractPathFromPublicUrl(publicUrl)` behavior (current):
- Returns `null` if input is `null` or blank.
- Strips the public base URL + bucket prefix and returns the relative storage path.
- **Throws `IllegalArgumentException("URL tidak valid untuk bucket ini")`** if the URL does not start with a recognized bucket prefix.

`StorageService.getPublicUrl(path)` rebuilds `publicBaseUrl + "/" + bucket + "/" + path`.

---

## 2. Problem Statement

A chat template needs to be able to carry an associated media attachment (image/file) so templated messages can include media. The frontend submits the media as a **public URL**, but — consistent with the rest of the system — the database must store the **relative storage path**, not the full public URL. Today there is no field to hold this, and no extraction step in the template save flow.

---

## 3. Goals

- Add a `mediaLink` field to the chat template so a template can reference a stored media object.
- On add/update, the incoming `mediaLink` (public URL) MUST be converted to a storage path via `StorageService.extractPathFromPublicUrl()` before persistence.
- Reuse the existing store-path / return-URL convention already used for chat media.
- Confine the change to the chat-template create/update path; do not alter unrelated template behavior.

- On read, the stored path MUST always be converted back to a public URL using `StorageService.getProdukPublicUrl()` (template media lives in the product bucket).

### Non-Goals
- Uploading the media file itself (upload remains handled by the existing upload endpoint; this feature only stores a reference).
- Adding media support to any entity other than `ChatTemplate`.
- Changing how templates are rendered/sent (`getFollowUpText`, message construction).

---

## 4. Scope

### Included
- New `mediaLink` field on `AddChatTemplateDto` (incoming public URL).
- New `mediaLink` persisted attribute on `ChatTemplate` (stored as a relative storage path).
- Extraction step in `MessageTemplateService.upsertMessageTemplate()`:
  `chatTemplate.setMediaLink(data.getMediaLink() != null ? storageService.extractPathFromPublicUrl(data.getMediaLink()) : null)`.
- Read-side conversion in the template read paths (`getDetail` and any list/detail response that exposes media): always convert the stored path back to a public URL with `storageService.getProdukPublicUrl(...)`, adding `mediaLink` to the response DTOs.
- Injecting `StorageService` into `MessageTemplateService` (not currently a dependency).

### Not Included
- File upload / storage of bytes.
- Media on other entities.

---

## 5. Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | `AddChatTemplateDto` MUST accept an optional `mediaLink` (string, a public URL). |
| FR-2 | On create/update, if `mediaLink` is non-null, the service MUST convert it via `StorageService.extractPathFromPublicUrl()` and persist the resulting storage path. |
| FR-3 | If `mediaLink` is null (or blank), the persisted media value MUST be `null` (no media). |
| FR-4 | The `ChatTemplate` entity MUST persist the extracted storage path (not the original public URL). |
| FR-5 | The extraction MUST occur for both create (new template) and update (existing `id`) flows, consistent with the existing `upsertMessageTemplate` behavior. |
| FR-6 | Existing templates without a media link MUST remain valid (the field is nullable). |
| FR-7 | On update, a null/absent `mediaLink` MUST overwrite the stored value with `null` (clear the existing media). |
| FR-8 | If `mediaLink` is not a valid bucket URL, the operation MUST fail (extraction throws) and return **400 Bad Request** with the `StorageService` error message. |
| FR-9 | On read, when a template has a stored media path, the response MUST expose `mediaLink` as a public URL produced by `StorageService.getProdukPublicUrl()`; when there is no path, `mediaLink` is `null`. |

---

## 6. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | The change follows the existing `StorageService` store-path / return-URL convention used for chat media. |
| NFR-2 | The save endpoint continues to return the standard `RestResponse<T>` envelope; existing error handling (catch → `badRequest` with message) is preserved. |
| NFR-3 | No regression to existing template create/update/list/delete/render behavior. |
| NFR-4 | Backward compatible: the new field is optional/nullable; existing API clients that omit `mediaLink` keep working. |

---

## 7. Affected Components

| Layer | Element | Change |
|---|---|---|
| Model | `AddChatTemplateDto` | Add `String mediaLink`. |
| Entity | `ChatTemplate` | Add `mediaLink` attribute → column `media_link` (nullable). |
| Service | `MessageTemplateService` | Inject `StorageService`; extract path in `upsertMessageTemplate()`. |
| Read DTOs | `DetailTemplate` (and any list/detail DTO that should expose media) | Add `mediaLink`; convert stored path → public URL via `storageService.getProdukPublicUrl()` on read. |

---

## 8. Data Model Changes (conceptual)

- Add a nullable text column **`media_link`** to table `chat_template`, storing the relative storage path (mirrors how `chat.media` stores a path).
- Schema is managed by Hibernate `ddl-auto=update`, which will add the column automatically from the entity mapping. Optionally, a plain SQL file `db/migration/V2__add_media_link_to_chat_template.sql` can be added for parity with the existing `V1` migration (it is a manual artifact, not auto-run — see Open Questions #3).
- No change to any other table.

---

## 9. API Requirements (high level)

- `POST /template` request body (`AddChatTemplateDto`) gains an optional `mediaLink` (public URL string). No path/route changes. Response envelope unchanged.
- Example request (conceptual):
  ```json
  {
    "idWorkspace": 1,
    "namaTemplate": "Promo Juni",
    "content": "Halo {nama_customer} ...",
    "mediaLink": "https://<public-base>/<bucket>/2026/6/<uuid>.jpg"
  }
  ```

---

## 10. Acceptance Criteria

- Given a create request with a valid `mediaLink` public URL, when the template is saved, then `chat_template.media_link` stores the extracted **path** (not the full URL).
- Given a create request with `mediaLink = null`, when the template is saved, then `media_link` is `null`.
- Given an update request (existing `id`) with a `mediaLink`, when the template is saved, then the stored path reflects the new extracted value.
- Given an update request with `mediaLink = null`, when the template is saved, then the existing `media_link` is cleared to `null` (overwrite-null).
- Given a request whose `mediaLink` is not a valid bucket URL, when the template is saved, then the operation fails with the `StorageService` error message (`URL tidak valid untuk bucket ini`) and returns **400 Bad Request**.
- Given a stored template with a media path, when it is read, then the response `mediaLink` is a full public URL produced by `getProdukPublicUrl()`.
- Given an existing template created before this change, when it is read or updated, then it remains valid with `media_link = null` (and read `mediaLink = null`).

---

## 11. Edge Cases

- **Blank string `mediaLink`** — `extractPathFromPublicUrl` returns `null` for blank input, so a blank value persists as `null`. (Mirror the `!= null` guard used in `ProdukService`/`ChatService`.)
- **Invalid / wrong-bucket URL** — `extractPathFromPublicUrl` throws `IllegalArgumentException("URL tidak valid untuk bucket ini")`; surfaces as a `badRequest` via the existing controller catch block.
- **Update clearing media** — a null `mediaLink` on update clears existing media (overwrite-null, confirmed).
- **Round-trip on edit** — the read side returns a public URL (via `getProdukPublicUrl()`); when the frontend resubmits it on edit, `extractPathFromPublicUrl` re-derives the same path. Consistent round-trip.
- **Bucket** — template media lives in the product bucket; the read uses `getProdukPublicUrl()` and the submitted URL must be a product-bucket URL so `extractPathFromPublicUrl` resolves the correct prefix.

---

## 12. Resolved Decisions

Answers provided by the product owner, reflected in the sections above.

1. **Read-side exposure** — Answer: **Always** convert the stored media path → public URL on read, using `storageService.getProdukPublicUrl()`. Read DTOs expose `mediaLink`.
2. **Update semantics** — Answer: **Overwrite-null**. A null/absent `mediaLink` on update clears the existing media.
3. **Validation** — Answer: an invalid media URL **throws** (extraction `IllegalArgumentException`) and the endpoint returns **400 Bad Request**.
4. **Bucket** — Resolved by #1: template media lives in the **product bucket** (read via `getProdukPublicUrl()`); submitted URLs must be product-bucket URLs.
5. **Migration artifact** — Not separately confirmed; default is to rely on Hibernate `ddl-auto=update` to add `media_link`. (Raise if a manual `V2` SQL file is wanted for parity with `V1`.)
6. **Column type/length** — `media_link` is a nullable text column, consistent with other `chat_template` text columns (`length = Integer.MAX_VALUE`).
