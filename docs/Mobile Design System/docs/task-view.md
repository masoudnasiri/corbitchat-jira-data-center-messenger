# Task View (Issue Detail)

Mobile task view modeled on Jira issue layout with CorbitChat styling. Supports RTL Persian and LTR English.

## Screen Structure

```
┌─────────────────────────────────────┐
│ [←]  TEC-34                    [⋮]  │  optional compact top bar
├─────────────────────────────────────┤
│ [icon] Technical / TEC-34           │  breadcrumb
│ آیا RTL درست است؟                   │  summary title
├─────────────────────────────────────┤
│ [Edit] [Comment] [Assign] [More] [▼Status] │  action toolbar
├─────────────────────────────────────┤
│ ▼ Details                           │
│   Type · Priority · Labels            │
│   Resolution + custom fields          │
├─────────────────────────────────────┤
│ ▼ People                            │
│   Assignee · Reporter                 │
├─────────────────────────────────────┤
│ ▼ Description                       │
├─────────────────────────────────────┤
│ ▼ Attachments                       │
├─────────────────────────────────────┤
│ ▼ Comments                          │
└─────────────────────────────────────┘
```

## Standard Fields (Details section)

| Field ID | Label (fa) | Label (en) | Render type |
|----------|------------|------------|-------------|
| `issuetype` | نوع | Type | Icon + name |
| `priority` | اولویت | Priority | Icon bars + name |
| `labels` | برچسب‌ها | Labels | Tag list or "None" |
| `resolution` | Resolution | Resolution | Plain text |
| `customfield_*` | (from API) | (from API) | Per field rule |

## Custom Fields (from screenshots)

| Field | Example value | Render |
|-------|---------------|--------|
| کارفرما | سازمان بنادر و دریانوردی بوشهر | Plain text |
| سطح پیگیری | عادی / بالا | Plain text |
| سایر کارفرماها | وزارت ارتباطات | Plain text |
| **نوع تسک (طبق دستورالعمل)** (`customfield_10903`) | بحرانی, توسعه‌ای, … | Colored pill + dot |

Field rows use `.cc-field-row` with label at **start** (right in RTL) and value at **end**.

## نوع تسک (طبق دستورالعمل) — `customfield_10903`

This Jira custom field must always appear in the UI with the label **نوع تسک (طبق دستورالعمل)** (Persian) or **Task Type (per instructions)** (English). The field ID `customfield_10903` is for API/config only.

Configuration: `tokens/customfield_10903.json`

| CSS Class | fa | en | Text | Background |
|-----------|----|----|------|------------|
| `critical` | بحرانی | Critical | `#DE350B` | `#FFEBE6` |
| `urgent` | فوری | Urgent | `#FF8B00` | `#FFF0B3` |
| `committed` | تعهدی | Committed | `#FFAB00` | `#FFF7D6` |
| `current` | جاری | Current | `#0052CC` | `#DEEBFF` |
| `project` | پروژه‌ای | Project-Based | `#6554C0` | `#EAE6FF` |
| `development` | توسعه‌ای | Development | `#00875A` | `#E3FCEF` |
| `strategic` | راهبردی | Strategic | `#172B4D` | `#DFE1E6` |

### HTML

```html
<span class="cc-field-pill cc-field-pill--critical">
  <span class="cc-field-pill__dot"></span>
  بحرانی
</span>
```

### Flutter

```dart
CorbitChatTheme.taskTypePill('بحرانی'); // returns (foreground, background, cssClass)
```

### Lookup logic

1. Match API value against `labelFa`, `aliasesFa`, `labelEn`, or `cssClass`
2. Apply pill class `cc-field-pill--{cssClass}`
3. Server field-display-rules may override colors in future — local config is fallback

## People Section

| Field | Component |
|-------|-----------|
| Assignee | `.cc-user-field` — avatar + name + info icon |
| Reporter | Same as assignee |
| Watchers | Avatar stack (Phase 2) |

Tap avatar → profile / start chat. Tap info icon → user details sheet.

## Action Toolbar

| Action | Permission | Behavior |
|--------|------------|----------|
| Edit | EDIT_ISSUES | Open edit sheet |
| Add comment | COMMENT | Scroll to composer |
| Assign | ASSIGN_ISSUES | User picker |
| More | — | Overflow menu (watch, share, link) |
| Status | TRANSITION | Workflow dropdown (blue button) |

Status button uses Jira workflow color when provided by API; default `#0052CC`.

## Collapsible Sections

All sections default **expanded**. Class `.cc-task-section--collapsed` hides body and rotates chevron.

Sections: Details, People, Description, Attachments, Comments, Activity (optional).

## CSS Files

- `css/task-view.css` — task header, toolbar, fields, pills, attachments
- `css/components.css` — shared buttons, avatars, badges

## API Mapping

From `GET /mobile/issues/{key}`:

```json
{
  "key": "TEC-34",
  "summary": "آیا RTL درست است؟",
  "project": { "key": "TEC", "name": "Technical" },
  "fields": [
    { "id": "issuetype", "label": "Type", "value": "Task", "render": "issueType" },
    { "id": "customfield_10903", "label": "نوع تسک (طبق دستورالعمل)", "value": "بحرانی", "render": "pill", "cssClass": "critical" }
  ],
  "people": {
    "assignee": { "displayName": "Masoud Nasirinezhad", "avatarUrl": "..." },
    "reporter": { "displayName": "Masoud Nasirinezhad", "avatarUrl": "..." }
  }
}
```

The Mobile API should resolve `cssClass` for `customfield_10903` server-side using the same mapping table.
