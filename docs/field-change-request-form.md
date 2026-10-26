# Field Change Request Form

Fill this out and send it back with the file/screen you're referring to. The more precise the "Field Details" section, the faster the change can be made — vague requests ("change the name field") need follow-up questions before anything can start.

---

## 1. Request Info

| | |
|---|---|
| Requested by | |
| Date | |
| Module | e.g. Client |
| Screen(s) affected | ☐ List page &nbsp; ☐ Create form &nbsp; ☐ Edit form &nbsp; ☐ View/detail page |
| Priority | ☐ Low &nbsp; ☐ Normal &nbsp; ☐ Urgent |

## 2. Type of Change

☐ **Add a new field**
☐ **Remove an existing field**
☐ **Rename a field** (label only — data stays the same)
☐ **Change a field's rules** (make required/optional, change dropdown options, change what kind of value it accepts)
☐ **Other change** — describe: __________

## 3. Field Details

> Tip: the "Field label" is the text the user sees on screen (e.g. "Date of Birth"). If you don't know the technical name, leave it blank — just attach a screenshot with the field circled/highlighted.

| | Value |
|---|---|
| Field label shown on screen (current) | |
| Exact new label (if renaming) | |
| Field label for new field (if adding) | |
| Data type (if adding/changing) | ☐ Text &nbsp; ☐ Number &nbsp; ☐ Date &nbsp; ☐ Dropdown/List &nbsp; ☐ Yes/No &nbsp; ☐ Other: ____ |
| If dropdown: list the options | |
| If dropdown: should the list of options come from a fixed list you provide, or from something already set up elsewhere in the system (e.g. an existing list of offices, currencies, staff)? | ☐ Fixed list I'm providing &nbsp; ☐ Pull from an existing list — which one: ____ &nbsp; ☐ Not sure |
| Required or optional? | ☐ Required &nbsp; ☐ Optional |
| Default value (if any) | |
| Any limit on what can be typed? (e.g. numbers only, max 20 characters, must look like a phone number) | |
| Example of a valid entry | |
| Short instruction text to show under/inside the field (if helpful) | |
| If the entry is invalid, what message should the user see? | |
| Should this field only appear when another field has a certain value? (e.g. "Show 'Reason' only when Status = Rejected") | ☐ No, always show it &nbsp; ☐ Yes — describe: ____ |
| Where should it appear relative to other fields? | e.g. "right after Phone Number" |
| If adding more than one field, what order should they appear in relative to each other? | |
| Should this field also show as a column on the list page? | ☐ Yes &nbsp; ☐ No &nbsp; ☐ N/A |
| If yes: should users be able to search/filter/sort by it? | ☐ Yes &nbsp; ☐ No &nbsp; ☐ Not sure |
| Should everyone see/edit this field, or only certain staff roles? | ☐ Everyone &nbsp; ☐ Only certain roles — which: ____ |

## 4. Reason for the Change

_Why is this needed? (business reason, compliance, report requirement, etc.)_

```


```

## 5. If Removing or Renaming a Field

| | |
|---|---|
| Is existing data in this field still needed (e.g. for reports/history)? | ☐ Yes, keep it &nbsp; ☐ No, safe to discard |
| Is this field used in any report, export, or printed form? | ☐ Yes — which one: ____ &nbsp; ☐ No &nbsp; ☐ Not sure |

## 6. Attachments

☐ Screenshot of the current screen with the field marked
☐ Mockup / example of how it should look (optional)

---

### Example — filled in

| | |
|---|---|
| Module | Client |
| Screen(s) affected | Create form, Edit form, View page |
| Type of change | Rename a field |
| Field label shown on screen (current) | "Mobile No" |
| Exact new label (if renaming) | "Contact Number" |
| Reason | Field also holds landline numbers now, "Mobile" is misleading |

---

*For internal/dev use only — do not fill in:*

| | |
|---|---|
| Technical field name (e.g. API/database name, if known) | |
| Files affected | |
| Backend change needed? | ☐ Yes ☐ No |
| Liquibase migration needed? | ☐ Yes ☐ No |
| Ticket / commit ref | |
