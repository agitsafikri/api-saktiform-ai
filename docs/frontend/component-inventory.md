# Component Inventory — Saktiform Dashboard

This document covers all reusable components registered globally via `unplugin-vue-components`. All components in `src/components/common/` and `src/components/dashboard/` are auto-imported — no manual import is needed anywhere in the project.

Module-level components under `src/modules/**/components/` are feature-specific and used only within their own module; they are excluded from this inventory.

---

## Common Components (`src/components/common/`)

### Alert

**File:** [src/components/common/Alert.vue](../src/components/common/Alert.vue)

**Purpose:** Global toast/alert notification. Reads from `useAlertStore` and auto-hides after 3 seconds. Mounted once in `App.vue`; no props needed.

**Props:** None (store-driven)

**Emits:** None

**Used in:** `src/App.vue` (root — single global instance)

**Reusability notes:** Singleton pattern. Should not be placed more than once. All pages trigger it indirectly through `alertStore.setAlert()`.

---

### Badge

**File:** [src/components/common/Badge.vue](../src/components/common/Badge.vue)

**Purpose:** Inline status label with color-coded variants for conversation and order states.

**Props:**
| Prop | Type | Required | Default | Notes |
|---|---|---|---|---|
| `variant` | `'open' \| 'pending' \| 'closed' \| 'paid' \| 'unpaid'` | No | `'open'` | Controls color |
| `label` | `string` | No | `''` | Display text |

**Emits:** None

**Used in:** Chat list items (conversation status), order tables (order status)

**Reusability notes:** Covers both chat and order status vocabulary. Adding a new status requires extending the variant type and adding a corresponding CSS class.

---

### BaseHighlightCard

**File:** [src/components/common/BaseHighlightCard.vue](../src/components/common/BaseHighlightCard.vue)

**Purpose:** KPI card displaying a title, a large value, and an optional trend indicator (up/down arrow + percentage).

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `title` | `string` | Yes | — |
| `value` | `string \| number` | Yes | — |
| `icon` | `string` | No | — |
| `trend` | `{ value: number, direction: 'up' \| 'down' }` | No | — |
| `valueColor` | `string` | No | `'primary'` |

**Emits:** None

**Used in:** `src/pages/index.vue` (dashboard KPI row)

**Reusability notes:** Self-contained display component; no store dependencies. Can be dropped anywhere a highlight metric is needed.

---

### ButtonCustom

**File:** [src/components/common/ButtonCustom.vue](../src/components/common/ButtonCustom.vue)

**Purpose:** Primary action button with loading spinner and disabled state. Uses a `<slot>` for button text.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `type` | `string` | No | `''` |
| `color` | `string` | No | `'primary'` |
| `loading` | `boolean` | No | `false` |
| `disabled` | `boolean` | No | `false` |
| `minWidth` | `any` | No | `'fit-content'` |
| `withPadding` | `any` | No | `true` |

**Emits:** None (click handled via native `@click`)

**Used in:** Throughout all pages and modals for form submit, confirm, and action buttons.

**Reusability notes:** Most widely used interactive component in the project. The `color` prop maps to CSS utility classes (e.g., `btn-primary`, `btn-danger`).

---

### ButtonFile

**File:** [src/components/common/ButtonFile.vue](../src/components/common/ButtonFile.vue)

**Purpose:** Single-file upload trigger button with preview support. Validates file size (max 2 MB) and shows error state.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `name` | `string` | Yes | — |
| `showImagePreview` | `boolean` | Yes | — |
| `showFileInfo` | `boolean` | Yes | — |
| `hasFile` | `boolean` | Yes | — |
| `label` | `string` | No | — |
| `error` | `boolean` | No | `false` |
| `errorMsg` | `string` | No | `'Tidak boleh kosong'` |
| `withError` | `boolean` | No | `true` |
| `height` | `any` | No | `0` |
| `type` | `string` | No | `'ext'` |
| `url` | `string` | No | `''` |
| `disabled` | `boolean` | No | `false` |
| `loading` | `boolean` | No | `false` |
| `onUpdated` | `boolean` | No | `false` |

**Emits:**
| Event | Payload |
|---|---|
| `onUpload` | `File` object |
| `onReset` | `''` (empty string) |

**Used in:** Product form (image upload fields)

**Reusability notes:** Lighter alternative to `FileUpload` — no dimension validation, simpler preview. Use when only a URL string preview is needed.

---

### CarouselCustom

**File:** [src/components/common/CarouselCustom.vue](../src/components/common/CarouselCustom.vue)

**Purpose:** Image carousel with left/right navigation arrows and dot pagination.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `images` | `string[]` | No | `[]` |

**Emits:** None

**Used in:** Product detail/preview sections

**Reusability notes:** Display-only component. Accepts an array of image URLs.

---

### ChartComparison

**File:** [src/components/common/ChartComparison.vue](../src/components/common/ChartComparison.vue)

**Purpose:** Area chart for overlaying two data series on a shared time axis. Built on `vue3-apexcharts`.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `series` | `any[]` | Yes | — |
| `categories` | `string[]` | Yes | — |
| `height` | `number` | No | `350` |
| `colors` | `string[]` | No | `['#008FFB', '#00E396']` |
| `showDataLabels` | `boolean` | No | `false` |
| `isDualYAxis` | `boolean` | No | `true` |

**Emits:** None

**Used in:** `src/pages/index.vue` (order vs paid comparison chart)

**Reusability notes:** `isDualYAxis: true` renders independent Y-axis scales per series — useful when the two series have different value magnitudes.

---

### ChipCustom

**File:** [src/components/common/ChipCustom.vue](../src/components/common/ChipCustom.vue)

**Purpose:** Small pill/chip label with optional left and right icon slots. The `type` prop maps to a CSS color class (`success`, `warning`, `danger`, etc.).

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `text` | `string` | No | `''` |
| `type` | `string` | No | `'success'` |
| `hasIcon` | `boolean` | No | — |
| `hasRightIcon` | `boolean` | No | — |

**Emits:** None

**Used in:** Filter tags, status indicators across modules

**Reusability notes:** Similar to `Badge` but more generic — does not have a fixed vocabulary. Use `Badge` for known status values; use `ChipCustom` for free-form labels.

---

### ConfirmModal

**File:** [src/components/common/ConfirmModal.vue](../src/components/common/ConfirmModal.vue)

**Purpose:** Generic confirmation dialog used before destructive or important actions (delete, status change, save). Shows title, a description sentence built from props, and confirm/cancel buttons.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `title` | `string` | Yes | — |
| `action` | `string` | Yes | — |
| `actionVerb` | `string` | Yes | — |
| `nextStatus` | `string` | Yes | — |
| `variable` | `string` | Yes | — |
| `visible` | `boolean` | No | `false` |
| `loading` | `boolean` | No | `false` |
| `showTooltip` | `boolean` | No | `false` |

**Emits:**
| Event | Payload |
|---|---|
| `closeModal` | — |
| `onAction` | — |

**Used in:** All delete and update confirmation flows across every module (products, orders, users, workspaces, warehouses, domains, templates, WhatsApp accounts)

**Reusability notes:** Most heavily reused modal in the project. The description sentence is built by composing `action`, `actionVerb`, `nextStatus`, and `variable` props.

---

### DatePicker

**File:** [src/components/common/DatePicker.vue](../src/components/common/DatePicker.vue)

**Purpose:** Single date/datetime picker backed by `@vuepic/vue-datepicker`. Supports `date` or `dateTime` mode.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `modelValue` | `any` | Yes | — |
| `label` | `string` | No | `''` |
| `placeholder` | `string` | No | `''` |
| `error` | `boolean` | No | `false` |
| `disabled` | `boolean` | No | `false` |
| `readonly` | `boolean` | No | `false` |
| `withError` | `boolean` | No | `true` |
| `minDate` | `any` | No | `'current'` |
| `maxDate` | `any` | No | — |
| `message` | `string` | No | — |
| `withMessage` | `boolean` | No | `true` |
| `messageType` | `'error' \| 'info'` | No | — |
| `required` | `boolean` | No | `false` |
| `customClass` | `string` | No | `''` |
| `fieldId` | `string` | No | `''` |
| `fieldName` | `string` | No | `''` |
| `loading` | `boolean` | No | `false` |
| `calendarMode` | `'date' \| 'dateTime'` | No | `'date'` |
| `type` | `string` | No | `'text'` |

**Emits:** `input`, `change`, `blur`, `click`, `onFocus`, `update:modelValue`

**v-model:** `modelValue`

**Used in:** Date fields across order forms, filter panels

**Reusability notes:** Pair with `DateRangePicker` for range selection. The `minDate: 'current'` default prevents selecting past dates — override explicitly when editing historical records.

---

### DateRangePicker

**File:** [src/components/common/DateRangePicker.vue](../src/components/common/DateRangePicker.vue)

**Purpose:** Date range picker that returns a `{ start, end }` object.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `modelValue` | `{ start: string, end: string } \| null` | Yes | — |
| `label` | `string` | No | `''` |
| `placeholder` | `string` | No | `'Pilih Periode'` |
| `disabled` | `boolean` | No | `false` |
| `required` | `boolean` | No | `false` |
| `customClass` | `string` | No | `''` |
| `calendarMode` | `'date' \| 'dateTime'` | No | `'date'` |

**Emits:** `update:modelValue`

**v-model:** `modelValue`

**Used in:** Dashboard KPI filter, order list date range filter, chat list date range filter

**Reusability notes:** Returns `null` when the user clears the selection.

---

### DropdownCustom

**File:** [src/components/common/DropdownCustom.vue](../src/components/common/DropdownCustom.vue)

**Purpose:** Kebab-menu / action dropdown. Each item in `menus` is rendered as a clickable row; the click handler is defined on the item object itself.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `menus` | `any[]` | No | `[]` |
| `alignMenuRight` | `boolean` | Yes | — |
| `disabled` | `boolean` | Yes | — |

**Emits:** None (actions invoked via menu item callbacks)

**Used in:** Table row action menus throughout all list views

**Reusability notes:** Menu item shape must include at minimum `{ label: string, action: () => void }`. The `alignMenuRight` prop prevents overflow clipping in right-edge columns.

---

### FileUpload

**File:** [src/components/common/FileUpload.vue](../src/components/common/FileUpload.vue)

**Purpose:** Full-featured file upload field with image/document preview, dimension constraints, and download support.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `file` | `any` | Yes | — |
| `size` | `number` | Yes | — |
| `acceptedFiles` | `string` | Yes | — |
| `width` | `number` | Yes | — |
| `height` | `number` | Yes | — |
| `label` | `string` | No | — |
| `error` | `boolean` | No | `false` |
| `message` | `string` | No | — |
| `placeholder` | `string` | No | — |
| `disabled` | `boolean` | No | `false` |
| `loading` | `boolean` | No | `false` |
| `withMessage` | `boolean` | No | `true` |
| `messageType` | `'error' \| 'info'` | No | — |
| `required` | `boolean` | No | `false` |
| `fieldId` | `string` | No | `''` |
| `fieldName` | `string` | No | `''` |
| `minWidth` | `number` | No | — |
| `minHeight` | `number` | No | — |
| `maxWidth` | `number` | No | — |
| `maxHeight` | `number` | No | — |

**Emits:** `onUpload`, `onReset`, `onPreview`, `onDownload`

**Used in:** Product form (single image slot), workspace/user forms with document uploads

**Reusability notes:** More capable than `ButtonFile` — use when dimension validation or download support is needed.

---

### InputCustom

**File:** [src/components/common/InputCustom.vue](../src/components/common/InputCustom.vue)

**Purpose:** Standard text/number/password input field with optional maska-based input masking, label, error state, and info/error message.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `modelValue` | `string \| number` | No | — |
| `type` | `string` | No | `'text'` |
| `label` | `string` | No | — |
| `error` | `boolean` | No | `false` |
| `message` | `string` | No | — |
| `placeholder` | `string` | No | — |
| `disabled` | `boolean` | No | `false` |
| `readonly` | `boolean` | No | `false` |
| `loading` | `boolean` | No | `false` |
| `maxLength` | `string` | No | — |
| `min` | `number` | No | `0` |
| `max` | `number` | No | `0` |
| `withMessage` | `boolean` | No | `true` |
| `messageType` | `'error' \| 'info'` | No | — |
| `required` | `boolean` | No | `false` |
| `customClass` | `string` | No | `''` |
| `fieldId` | `string` | No | `''` |
| `fieldName` | `string` | No | `''` |
| `mask` | `string \| object` | No | `''` |
| `value` | `string` | No | — |

**Emits:** `update:modelValue`, `input`, `change`, `blur`, `keypress`, `keydown`, `keyup`, `click`, `onClickField`, `onFocus`, `onKeyupEnter`, `onKeypressEnter`, `onKeyupTab`, `onKeydownTab`

**v-model:** `modelValue`

**Used in:** Every form in the project — login, order creation/edit, product form, user management, workspace settings, etc.

**Reusability notes:** The most widely used input component. The `mask` prop accepts a maska pattern string or object for phone numbers, IDs, and other formatted inputs.

---

### Modal

**File:** [src/components/common/Modal.vue](../src/components/common/Modal.vue)

**Purpose:** Base modal dialog container. Uses a `<slot>` for all content. Controls visibility, size, position, and overflow behaviour.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `visible` | `boolean` | Yes | — |
| `size` | `'small' \| 'medium' \| 'large' \| 'extra-large' \| 'full'` | No | `'small'` |
| `position` | `string` | No | `'top'` |
| `overflow` | `'auto' \| 'hidden' \| 'visible' \| 'scroll' \| 'clip'` | No | `'auto'` |

**Emits:** None

**Used in:** All feature modals (create/edit/delete/detail) across every module

**Reusability notes:** Pure container — no close button or header. Parent is responsible for rendering those inside the slot and toggling `visible`. `ConfirmModal` wraps this internally.

---

### MultipleFileUpload

**File:** [src/components/common/MultipleFileUpload.vue](../src/components/common/MultipleFileUpload.vue)

**Purpose:** Multi-file upload field with individual previews, per-file remove, and a configurable maximum file count.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `files` | `any` | Yes | — |
| `size` | `number` | Yes | — |
| `acceptedFiles` | `string` | Yes | — |
| `width` | `number` | Yes | — |
| `height` | `number` | Yes | — |
| `maxFiles` | `number` | No | `5` |
| `label` | `string` | No | — |
| `error` | `boolean` | No | `false` |
| `message` | `string` | No | — |
| `placeholder` | `string` | No | — |
| `disabled` | `boolean` | No | `false` |
| `loading` | `boolean` | No | `false` |
| `withMessage` | `boolean` | No | `true` |
| `messageType` | `'error' \| 'info'` | No | — |
| `required` | `boolean` | No | `false` |
| `fieldId` | `string` | No | `''` |
| `fieldName` | `string` | No | `''` |
| `minWidth` | `number` | No | — |
| `minHeight` | `number` | No | — |
| `maxWidth` | `number` | No | — |
| `maxHeight` | `number` | No | — |

**Emits:** `onUpload`, `onReset`, `onPreview`, `onDownload`

**Used in:** Product form (product image gallery — `gambarProduk` array)

**Reusability notes:** Manages its own internal list state. The parent receives individual file events; it is responsible for maintaining the final array.

---

### MultipleSelectCustom

**File:** [src/components/common/MultipleSelectCustom.vue](../src/components/common/MultipleSelectCustom.vue)

**Purpose:** Multi-select dropdown with optional search. Selected items render as removable chips inside the input.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `list` | `{ name: string, value: string \| number }[]` | No | `[]` |
| `selected` | `any[]` | No | `[]` |
| `label` | `string` | No | `''` |
| `placeholder` | `string` | No | `''` |
| `isSearch` | `boolean` | No | `false` |
| `error` | `boolean` | No | `false` |
| `message` | `string` | No | — |
| `disabled` | `boolean` | No | `false` |
| `readonly` | `boolean` | No | `false` |
| `loading` | `boolean` | No | `false` |
| `withClearData` | `boolean` | No | `false` |
| `withMessage` | `boolean` | No | `true` |
| `messageType` | `'error' \| 'info'` | No | — |
| `customClass` | `string` | No | `''` |
| `fieldId` | `string` | No | `''` |
| `fieldName` | `string` | No | `''` |
| `roValue` | `string` | No | `''` |
| `required` | `boolean` | No | `false` |
| `isChild` | `any` | No | `false` |

**Emits:** `focus`, `onClick`, `close`, `select`, `mouseleave`, `blur`, `input`, `change`, `update:modelValue`, `addData`, `onRemove`

**Used in:** User management (workspace assignment field), add workspace user modal

**Reusability notes:** Pass selected values as an array of the same value type used in `list[n].value`. The `onRemove` emit provides the item being de-selected.

---

### OtpInputCustom

**File:** [src/components/common/OtpInputCustom.vue](../src/components/common/OtpInputCustom.vue)

**Purpose:** Multi-box OTP input that orchestrates N `SingleOtpInput` boxes. Handles keyboard navigation, paste, and auto-focus.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `modelValue` | `string` | Yes | — |
| `numInputs` | `number` | No | `6` |
| `inputType` | `string` | No | `'text'` |
| `inputmode` | `string` | No | `'text'` |
| `separator` | `string` | No | `'-'` |
| `focus` | `boolean` | No | — |
| `conditionalClass` | `any` | No | `[]` |
| `shouldAutoFocus` | `boolean` | No | `false` |
| `isLastChild` | `boolean` | No | — |
| `placeholder` | `string` | No | — |
| `isDisabled` | `boolean` | No | `false` |
| `inputClasses` | `string` | No | — |

**Emits:** `on-change`, `on-keydown`, `on-paste`, `on-focus`, `on-blur`, `update:modelValue`, `onComplete`

**v-model:** `modelValue`

**Used in:** Login page (forget-password OTP verification flow)

**Reusability notes:** The `onComplete` emit fires when all boxes are filled. Composes `SingleOtpInput` internally; `SingleOtpInput` should not be used directly.

---

### RadioButton

**File:** [src/components/common/RadioButton.vue](../src/components/common/RadioButton.vue)

**Purpose:** Single radio button input with label and error state.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `modelValue` | `string` | No | — |
| `value` | `string` | No | — |
| `label` | `string` | No | — |
| `error` | `boolean` | No | `false` |
| `errorMsg` | `string` | No | `'Tidak boleh kosong'` |
| `disabled` | `boolean` | No | — |
| `readonly` | `boolean` | No | — |
| `withError` | `boolean` | No | `true` |
| `type` | `string` | No | `'text'` |
| `rightIcon` | `boolean` | No | — |
| `placeholder` | `string` | No | — |

**Emits:** `update:modelValue`

**v-model:** `modelValue`

**Used in:** Order status selection (PAID / UNPAID / CANCELLED), payment method selection in order forms

**Reusability notes:** Typically rendered in a `v-for` loop over an options array. Each instance represents one choice; bind the same `v-model` across all instances in a group.

---

### SelectCustom

**File:** [src/components/common/SelectCustom.vue](../src/components/common/SelectCustom.vue)

**Purpose:** Single-select dropdown with optional search, combobox mode (free-text entry), external search trigger, and "add new" option.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `list` | `{ name: string, value: string \| number \| boolean }[]` | No | `[]` |
| `selected` | `any` | No | `''` |
| `label` | `string` | No | `''` |
| `placeholder` | `string` | No | `''` |
| `isSearch` | `boolean` | No | `false` |
| `error` | `boolean` | No | `false` |
| `message` | `string` | No | — |
| `disabled` | `boolean` | No | `false` |
| `readonly` | `boolean` | No | `false` |
| `loading` | `boolean` | No | `false` |
| `withClearData` | `boolean` | No | `false` |
| `isCombobox` | `boolean` | No | `false` |
| `withMessage` | `boolean` | No | `true` |
| `messageType` | `'error' \| 'info'` | No | — |
| `customClass` | `string` | No | `''` |
| `fieldId` | `string` | No | `''` |
| `fieldName` | `string` | No | `''` |
| `isFindExternal` | `boolean` | No | `false` |
| `isAllowAdd` | `boolean` | No | `false` |
| `modelValue` | `string` | No | `''` |
| `roValue` | `string` | No | `''` |
| `required` | `boolean` | No | `false` |
| `isChild` | `any` | No | `false` |

**Emits:** `focus`, `onClick`, `close`, `select`, `mouseleave`, `blur`, `input`, `change`, `update:modelValue`, `addData`, `onRemove`, `findExternal`

**v-model:** `modelValue`

**Used in:** Product selection, variant selection, payment method, province/city/district cascades, role selection, workspace WABA selector — across chat, order, product, and settings pages.

**Reusability notes:** The most versatile select in the project. Use `isFindExternal: true` when the option list is server-searched (not fully loaded); the `findExternal` emit carries the search keyword. Use `isAllowAdd: true` when the user can create a new option inline.

---

### SingleOtpInput

**File:** [src/components/common/SingleOtpInput.vue](../src/components/common/SingleOtpInput.vue)

**Purpose:** One box of an OTP input sequence. Handles keydown navigation and paste events; forwards them to `OtpInputCustom`.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `value` | `string` | Yes | — |
| `inputType` | `string` | No | `'text'` |
| `inputmode` | `any` | No | `'text'` |
| `separator` | `string` | No | — |
| `focus` | `boolean` | No | — |
| `conditionalClass` | `string` | No | — |
| `shouldAutoFocus` | `boolean` | No | — |
| `isLastChild` | `boolean` | No | — |
| `placeholder` | `string` | No | — |
| `isDisabled` | `boolean` | No | — |
| `inputClasses` | `string` | No | — |

**Emits:** `onChange`, `onKeydown`, `onPaste`, `onFocus`, `onBlur`, `update:modelValue`

**Used in:** `OtpInputCustom` (internal, should not be used directly)

**Reusability notes:** Internal implementation detail of `OtpInputCustom`. Do not use this component standalone.

---

### StepperCustom

**File:** [src/components/common/StepperCustom.vue](../src/components/common/StepperCustom.vue)

**Purpose:** Horizontal step progress indicator. Highlights the active step and marks previous steps as completed.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `item` | `array` | Yes | — |
| `activeId` | `number` | No | `1` |
| `withBorders` | `boolean` | No | `true` |

**Emits:** None

**Used in:** Multi-step forms (product creation wizard)

**Reusability notes:** Display-only; the parent controls `activeId`. Step items shape: `[{ id: number, label: string }]`.

---

### SwitchButton

**File:** [src/components/common/SwitchButton.vue](../src/components/common/SwitchButton.vue)

**Purpose:** Toggle switch (on/off) with optional label.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `value` | `boolean` | No | `true` |
| `label` | `string` | No | `''` |

**Emits:** `input`

**Used in:** Product form configuration fields (form field visibility toggles)

**Reusability notes:** Uses `input` emit (not `update:modelValue`), so it is not used with `v-model` directly — parent listens with `@input`.

---

### Table

**File:** [src/components/common/Table.vue](../src/components/common/Table.vue)

**Purpose:** Advanced data table with sortable column headers, optional row checkboxes, search, search-by-category, per-page selector, and pagination. Column definitions include a `slot` name for custom cell rendering.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `columns` | `columnInterface[]` | No | `[]` |
| `listData` | `any[]` | No | `[]` |
| `pagination` | `paginationInterface` | No | `{ page: 1, show: 10, ... }` |
| `loading` | `boolean` | No | `false` |
| `search` | `string` | No | `''` |
| `isWithHeader` | `boolean` | No | `true` |
| `isWithFooter` | `boolean` | No | `true` |
| `isWithCheckbox` | `boolean` | No | `false` |
| `isWithTableAction` | `boolean` | No | `true` |
| `isOverflowX` | `boolean` | No | `false` |
| `isWithSearchCategory` | `boolean` | No | `false` |
| `searchCategory` | `{ name: string, value: string }` | No | `{ name: 'Semua', value: '' }` |
| `searchCategoryList` | `any[]` | No | `[]` |

**Emits:** `setShow`, `setPage`, `setSort`, `setSelectAll`, `setSelect`, `search`, `setSearchCategory`

**Used in:** Products list, orders list, abandoned cart list, user management list, workspace list, warehouse list, domain list, template list, WhatsApp list

**Reusability notes:** Cell customisation uses named slots matching `column.slot`. This is the primary list table used across all modules. `TableCustom` (below) is a simpler, older variant.

---

### TableCustom

**File:** [src/components/common/table/TableCustom.vue](../src/components/common/table/TableCustom.vue)

**Purpose:** Simpler table variant with integrated search, filter button, dropdown, and pagination. Intended for smaller datasets.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `columns` | `any` | No | `[]` |
| `listData` | `any` | No | `{}` |
| `customKey` | `string` | No | `'filled'` |
| `request` | `any` | No | `{}` |
| `loading` | `boolean` | No | `false` |
| `search` | `string` | No | `''` |
| `isSearch` | `boolean` | No | `true` |
| `searchPlaceholder` | `string` | No | `'Cari Data . . '` |
| `withFilter` | `boolean` | No | `false` |
| `withDropdown` | `boolean` | No | `false` |
| `withPagination` | `boolean` | Yes | `true` |
| `selects` | `any` | No | `{}` |
| `label` | `string` | No | `'Data'` |

**Emits:** `setShow`, `setPage`, `search`, `onFilter`, `searchBy`, `selectBy`

**Used in:** Older list views; being superseded by `Table`

**Reusability notes:** Less flexible than `Table` — no column sorting, no checkbox selection. Prefer `Table` for new list views.

---

### TabsWrapper

**File:** [src/components/common/TabsWrapper.vue](../src/components/common/TabsWrapper.vue)

**Purpose:** Horizontal tab bar. Tracks the active tab and emits a `tabChange` event when the user switches.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `tabs` | `{ title: string, value: string }[]` | No | `[]` |
| `preSelectedTitle` | `string` | No | `''` |

**Emits:**
| Event | Payload |
|---|---|
| `tabChange` | `{ title: string, value: string }` |

**Used in:** Chat page (Belum Ditangani / Ditangani tabs), orders page (active/abandoned tabs)

**Reusability notes:** Purely presentational — does not control any content panel. The parent listens to `tabChange` and renders the appropriate panel.

---

### TextAreaCustom

**File:** [src/components/common/TextAreaCustom.vue](../src/components/common/TextAreaCustom.vue)

**Purpose:** Multi-line text input with label, error state, and the same event surface as `InputCustom`.

**Props:** Same set as `InputCustom` minus `type`, `mask`, `min`, `max`, `maxLength` — plus `rightIcon` (boolean)

**Emits:** `update:modelValue`, `input`, `change`, `blur`, `keypress`, `keydown`, `keyup`, `click`, `onClickField`, `onFocus`, `onKeyupEnter`, `onKeypressEnter`, `onKeyupTab`, `onKeydownTab`

**v-model:** `modelValue`

**Used in:** Notes fields in order forms, product description fields, template content field

**Reusability notes:** Drop-in replacement for `InputCustom` when multi-line input is needed.

---

### TimePicker

**File:** [src/components/common/TimePicker.vue](../src/components/common/TimePicker.vue)

**Purpose:** Time-only picker backed by `@vuepic/vue-datepicker`. Same API shape as `DatePicker`.

**Props:** Same set as `DatePicker` plus `presetValue` (any, default `''`)

**Emits:** `input`, `change`, `blur`, `click`, `onFocus`, `update:modelValue`

**v-model:** `modelValue`

**Used in:** Scheduled delivery or time-slot fields (where applicable)

**Reusability notes:** Shares the same visual and prop API as `DatePicker`. Use `calendarMode: 'dateTime'` on `DatePicker` instead if a combined date-time picker is preferred.

---

### VerificationOtp

**File:** [src/components/common/VerificationOtp.vue](../src/components/common/VerificationOtp.vue)

**Purpose:** OTP verification input with a countdown timer display. Wraps the OTP input with a "Resend" / countdown UI.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `modelValue` | `string` | Yes | — |
| `countdown` | `string` | Yes | — |
| `error` | `boolean` | No | `false` |
| `errorMsg` | `string` | No | `'Tidak boleh kosong'` |
| `disabled` | `boolean` | No | — |
| `readonly` | `boolean` | No | — |
| `withError` | `boolean` | No | `true` |
| `type` | `string` | No | — |
| `label` | `string` | No | — |
| `rightIcon` | `boolean` | No | — |
| `placeholder` | `string` | No | — |

**Emits:** `update:modelValue`, `change`, `blur`, `keypress`, `keydown`, `keyup`, `click`, `onClickField`, `onFocus`, `onComplete`

**v-model:** `modelValue`

**Used in:** Login page forget-password modal (OTP verification step)

**Reusability notes:** The `countdown` prop is a formatted string (e.g. `"01:30"`) managed by the parent. The component does not manage the countdown timer itself.

---

### FieldCustom (Table)

**File:** [src/components/common/table/FieldCustom.vue](../src/components/common/table/FieldCustom.vue)

**Purpose:** Wrapper for a single table data cell (`<td>`). Applies consistent padding, alignment, and overflow handling.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `label` | `any` | No | `''` |
| `className` | `any` | No | `''` |

**Emits:** None

**Used in:** Inside `TableCustom` row templates

**Reusability notes:** Used within `TableCustom`; not needed when using `Table` (which uses slot-based columns).

---

### PaginationCustom (Table)

**File:** [src/components/common/table/PaginationCustom.vue](../src/components/common/table/PaginationCustom.vue)

**Purpose:** Pagination controls (previous/next page, page size selector) for `TableCustom`.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `data` | `any` | No | `{}` |
| `request` | `any` | No | `{}` |

**Emits:**
| Event | Payload |
|---|---|
| `setPage` | page number |
| `setShow` | items per page |

**Used in:** `TableCustom` (internal footer)

**Reusability notes:** Used internally by `TableCustom`. `Table` has its own built-in pagination — this component is not needed with the newer table.

---

### TheadCustom (Table)

**File:** [src/components/common/table/TheadCustom.vue](../src/components/common/table/TheadCustom.vue)

**Purpose:** Table header row (`<thead>`) with column labels for `TableCustom`.

**Props:**
| Prop | Type | Required | Default |
|---|---|---|---|
| `columns` | `any` | No | `'filled'` |
| `customKey` | `string` | No | `'filled'` |

**Emits:** None

**Used in:** `TableCustom` (internal header)

**Reusability notes:** Internal to `TableCustom`. Not used with the newer `Table` component.

---

## Dashboard Components (`src/components/dashboard/`)

### DashboardContent

**File:** [src/components/dashboard/DashboardContent.vue](../src/components/dashboard/DashboardContent.vue)

**Purpose:** Page content wrapper that renders the breadcrumb bar above the page content slot. Reads breadcrumb data from `useHeaderContentStore`.

**Props:** None (store-driven)

**Emits:** None

**Used in:** All pages that use `dashboardLayout` (the layout renders `DashboardContent` which wraps `<RouterView>` content via slot)

**Reusability notes:** Should not be placed manually inside pages — it is part of `dashboardLayout.vue` and wraps every authenticated page automatically.

---

### Sidebar

**File:** [src/components/dashboard/Sidebar.vue](../src/components/dashboard/Sidebar.vue)

**Purpose:** Main application sidebar containing the Saktiform logo, user profile display, workspace switcher dropdown, role-filtered navigation menus, and a sidebar toggle button. Reads from `useSidebarStore` and `useAuthStore`.

**Props:** None (store-driven)

**Emits:** None

**Used in:** `src/layouts/dashboardLayout.vue` (single instance)

**Reusability notes:** Singleton — rendered once in the authenticated layout. Workspace switching inside this component triggers `authStore.setActiveWorkspace()` and redirects to `beranda`.

---

### SidebarAction

**File:** [src/components/dashboard/SidebarAction.vue](../src/components/dashboard/SidebarAction.vue)

**Purpose:** Action panel at the bottom of the sidebar containing the logout button. Shows a `ConfirmModal` before calling `authStore.logout()`.

**Props:** None (store-driven)

**Emits:** None

**Used in:** `src/components/dashboard/Sidebar.vue` (embedded at the bottom)

**Reusability notes:** Not reused independently. Tightly coupled to `Sidebar`.

---

## Summary

| Category | Count |
|---|---|
| Common components (`src/components/common/`) | 32 |
| Dashboard components (`src/components/dashboard/`) | 3 |
| **Total globally registered** | **35** |

**Components that use `v-model`:** `InputCustom`, `TextAreaCustom`, `SelectCustom`, `MultipleSelectCustom`, `DatePicker`, `DateRangePicker`, `TimePicker`, `RadioButton`, `OtpInputCustom`, `SingleOtpInput`, `VerificationOtp`

**Components with no props (store-driven):** `Alert`, `DashboardContent`, `Sidebar`, `SidebarAction`

**Internal sub-components (not for direct use):** `SingleOtpInput`, `FieldCustom`, `PaginationCustom`, `TheadCustom`
