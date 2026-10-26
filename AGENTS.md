# AGENTS.md — Apache Fineract Backend (Accounting Module Family)

This file gives Codex persistent project context. It encodes the architecture, conventions, and known gotchas for this codebase so new modules can be generated consistently without re-explaining the pattern each time.

## Project Overview

This is an Apache Fineract-based backend (Java, Spring, JAX-RS/Jersey, JPA/Hibernate, JdbcTemplate for reads). The primary domain covered so far is the **accounting** package (`org.apache.fineract.accounting.*`), following the General Ledger Account (`glaccount`) module as the canonical reference implementation.

When asked to build a new module, follow the exact layered architecture below, mirroring the same package structure, naming conventions, and patterns already used in `glaccount` and `dailyspotrate`.

---

## Layered Architecture

Every module lives under `org.apache.fineract.accounting.<modulename>` and is split into these sub-packages:

```
<modulename>/
├── api/             REST resource (JAX-RS), JsonInputParams enum
├── command/          Immutable command object + validation
├── serialization/     FromApiJsonDeserializer (JSON -> Command)
├── domain/           JPA Entity, Repository, RepositoryWrapper
├── data/             Immutable DTO returned to API layer
├── service/          Read (JdbcTemplate) + Write (JPA) platform services
├── handler/          CommandType handlers (Create/Update/Delete)
└── exception/        NotFoundException, DuplicateException, domain rule exceptions
```

### 1. `api/` — REST Resource
- Annotated `@Path("/<resource-plural>")`, `@Component`, `@Scope("singleton")`, `@Tag(...)`, `@RequiredArgsConstructor`
- Constructor-injects: `PlatformSecurityContext`, `<Module>ReadPlatformService`, `DefaultToApiJsonSerializer<...Data>`, `ApiRequestParameterHelper`, `PortfolioCommandSourceWritePlatformService`
- `resourceNameForPermission` is a `private final String` matching the permission entity name (UPPERCASE, no underscores unless the original entity uses them)
- `RESPONSE_DATA_PARAMETERS` is a static `Set<String>` of field names allowed in the serialized response
- Standard endpoints: `GET` (list), `GET {id}` (single), `POST` (create), `PUT {id}` (update). Only add `DELETE` if explicitly requested.
- Every write endpoint builds a `CommandWrapper` via `CommandWrapperBuilder` and delegates to `commandsSourceWritePlatformService.logCommandSource(...)`
- Swagger annotations (`@Operation`, `@ApiResponses`, `@RequestBody`) are included on every endpoint

**CRITICAL GOTCHA — Jersey `@QueryParam` date binding:**
Jersey has no built-in `ParamConverter` for `java.time.LocalDate`. Declaring a `@QueryParam` as `LocalDate` directly throws:
```
org.glassfish.jersey.server.model.ModelValidationException: No injection source found for parameter of type java.time.LocalDate
```
**Fix:** Always declare date query params as `String`, then parse manually:
```java
@QueryParam("fromDate") final String fromDateStr
...
final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
final LocalDate fromDate = fromDateStr != null ? LocalDate.parse(fromDateStr, formatter) : null;
```
Never inject `LocalDate`/`LocalDateTime` directly as a JAX-RS param type.

### 2. `JsonInputParams` enum (in `api/`)
- One enum per module listing every JSON field accepted on create/update
- Each constant maps to a `String` value (the camelCase JSON key)
- Static `getAllValues()` returns a `Set<String>` used by the deserializer to reject unsupported parameters
- `toString()` overridden to replace `_` with space (for readable validation messages)

### 3. `command/` — Command Object
- Immutable POJO (`@RequiredArgsConstructor @Getter` from Lombok), one field per JSON input param
- `validateForCreate()` — all mandatory fields validated via `DataValidatorBuilder`, throws `PlatformApiDataValidationException` if errors exist
- `validateForUpdate()` — same fields but `.ignoreIfNull()`, plus `anyOfNotNull(...)` check to ensure at least one field changed
- Validation always goes through `new DataValidatorBuilder(dataValidationErrors).resource("<EntityName>")`

### 4. `serialization/` — JSON Deserializer
- `@Component @RequiredArgsConstructor final class <Module>CommandFromApiJsonDeserializer extends AbstractFromApiJsonDeserializer<<Module>Command>`
- Steps: check blank JSON → `checkForUnsupportedParameters` against the JsonInputParams enum → `fromApiJsonHelper.parse()` → extract each field via `extractLongNamed`, `extractStringNamed`, `extractLocalDateNamed`, `extractBigDecimalWithLocaleNamed`, etc. → construct the Command object

### 5. `domain/` — Entity, Repository, Wrapper
- Entity extends `AbstractPersistableCustom` (provides `id`, audit fields)
- `@Entity @Table(name = "acc_<module_snake_case>", uniqueConstraints = {...})`
- Private no-arg constructor for JPA (`protected EntityName() {}`)
- Private parameterized constructor + static factory `fromJson(...)` 
- `update(JsonCommand command)` returns `Map<String, Object> actualChanges` — only mutates fields that actually changed (checked via `command.isChangeIn*ParameterNamed(...)`)
- `Repository extends JpaRepository<Entity, Long>, JpaSpecificationExecutor<Entity>` — add custom finder methods only if needed (e.g. `findByX...AndY...`)
- `RepositoryWrapper` is a thin `@Service` wrapping the repository, exposing `findOneWithNotFoundDetection(Long id)` that throws the module's `NotFoundException`

### 6. `data/` — DTO
- Immutable, `Serializable`, no getters/setters needed beyond what callers use (Gson serializes fields directly, but expose getters used elsewhere in the codebase)
- Multiple constructors allowed for different contexts (minimal/lookup vs full vs template-enriched), following the `GLAccountData` pattern when templates/dropdowns are involved

**CRITICAL GOTCHA — Adding fields to an existing DTO (e.g. `ClientData`):**
Large DTOs like `ClientData` have a single private all-args constructor and multiple static factory methods (`instance`, `lookup`, `template`, `templateOnTop`, `setParentGroups`, etc.) that each delegate to it. The read service (`*ReadPlatformServiceImpl`) also contains **multiple inner `RowMapper` classes** — each with its own `mapRow()` that calls one of these factory methods.

When you add a new field to the DTO constructor you **must** update every call site:
1. Every static factory method in the DTO class itself.
2. Every `mapRow()` in every inner mapper class in every `*ReadPlatformServiceImpl` that references the DTO.

Mappers that don't join the new table should pass `null` for the new parameters — do **not** skip them or the compiler will fail with "no suitable method found."

Before generating or modifying a DTO, grep for all callers:
```
ClientData.instance(   ClientData.lookup(   ClientData.template(
```
and update every occurrence in one pass.

### 7. `service/` — Read & Write Platform Services
**Read service:**
- Interface: `<Module>ReadPlatformService`
- Impl: `<Module>ReadPlatformServiceImpl`, uses `JdbcTemplate` directly (NOT JPA) for all read queries
- Inner `private static final class <Module>Mapper implements RowMapper<<Module>Data>` with a `schema()` method building the SQL projection + joins
- Filtering: build SQL dynamically with a `List<Object> params`, appending `WHERE`/`AND` conditionally
- Always `// NOSONAR` comment on raw `jdbcTemplate.query(...)` calls with dynamic SQL
- Not-found single-record reads: wrap `queryForObject` in try/catch `EmptyResultDataAccessException` → throw module's `NotFoundException`

**Write service:**
- Interface: `<Module>WritePlatformService` with `create...`, `update...` (and `delete...` only if requested)
- Impl: `<Module>WritePlatformServiceJpaRepositoryImpl`, uses JPA repository + repository wrapper
- Pattern per method: deserialize JSON → command → `validateForCreate()`/`validateForUpdate()` → resolve FK relationships (e.g. office, currency) via their respective `RepositoryWrapper.findOneWithNotFoundDetection(...)` → build/update entity → `saveAndFlush(...)` → return `CommandProcessingResultBuilder()...build()`
- Wrap in try/catch `JpaSystemException | DataIntegrityViolationException` → call a private `handleDataIntegrityIssues(...)` that inspects `getMostSpecificCause().getMessage()` for known constraint names and throws a specific `PlatformDataIntegrityException` or domain exception; otherwise logs and throws a generic data integrity exception

### 8. `handler/` — Command Handlers
- One handler class per action: `Create<Module>CommandHandler`, `Update<Module>CommandHandler` (+ `Delete...` if needed)
- `@Service @CommandType(entity = "<ENTITY_NAME_UPPERCASE>", action = "CREATE"|"UPDATE"|"DELETE") @RequiredArgsConstructor`
- Implements `NewCommandSourceHandler`, single `@Transactional processCommand(JsonCommand command)` method delegating straight to the write service
- `entity` name in `@CommandType` must exactly match the `entityName` used in the corresponding `CommandWrapperBuilder` method and the `m_permission.entity_name` column

### 9. `exception/`
- `<Module>NotFoundException extends AbstractPlatformResourceNotFoundException`
- `<Module>DuplicateException extends AbstractPlatformDomainRuleException` (for unique constraint violations with a clean user-facing message)
- Add other domain-rule exceptions as needed (e.g. `InvalidParentException`, `InvalidDeleteException` with reason enums), following the `glaccount` exception patterns

---

## Cross-Cutting Conventions

### `CommandWrapperBuilder`
Every new module requires two (or three) new builder methods added to the existing `CommandWrapperBuilder` class:
```java
public CommandWrapperBuilder create<Module>() {
    this.actionName = "CREATE";
    this.entityName = "<ENTITY_NAME_UPPERCASE>";
    this.href = "/<resource-plural>";
    return this;
}

public CommandWrapperBuilder update<Module>(final Long <module>Id) {
    this.actionName = "UPDATE";
    this.entityName = "<ENTITY_NAME_UPPERCASE>";
    this.entityId = <module>Id;
    this.href = "/<resource-plural>/" + <module>Id;
    return this;
}
```
**Always provide these alongside the rest of the module** — do not just describe them, write the literal methods to be pasted into the existing builder class.

### Permission SQL
Every module requires permission rows inserted into `m_permission`:
```sql
INSERT INTO `m_permission` (`grouping`, `code`, `entity_name`, `action_name`, `can_maker_checker`)
VALUES
('accounting', 'CREATE_<MODULE>', '<MODULE_ENTITY_NAME>', 'CREATE', 0),
('accounting', 'UPDATE_<MODULE>', '<MODULE_ENTITY_NAME>', 'UPDATE', 0),
('accounting', 'READ_<MODULE>',   '<MODULE_ENTITY_NAME>', 'READ',   0);
```
Grouping is `accounting` for anything under `org.apache.fineract.accounting.*`. Adjust if the module lives elsewhere (e.g. `organisation`, `portfolio`).
**Always provide this SQL alongside the rest of the module.**

### Database / Migration
- Table prefix: `acc_` for accounting-domain tables
- Liquibase changelog format (`--liquibase formatted sql`, `--changeset fineract:<description>`)
- Always declare `id`, `created_by`, `created_on_utc`, `last_modified_by`, `last_modified_on_utc` audit columns (matches `AbstractPersistableCustom`)
- Unique constraints named descriptively: `uq_<table>_<columns>`
- Foreign keys named `fk_<table>_<referenced_table>`

### Lombok Usage
- `@RequiredArgsConstructor` is the standard for dependency injection across API resources, services, handlers, deserializers
- `@Getter` on command objects only (DTOs typically keep explicit getters to match existing `GLAccountData`-style codebase conventions)

### Validation
- All field-level validation goes through `DataValidatorBuilder` — never hand-roll validation logic in the command object or entity
- Money/decimal fields: `.positiveAmount()`
- IDs: `.longGreaterThanZero()` / `.integerGreaterThanZero()`
- Strings: `.notBlank().notExceedingLengthOf(n)`
- Optional fields on update: always `.ignoreIfNull()`

### Package & Naming Conventions
- Module package: `org.apache.fineract.accounting.<modulename>` (lowercase, no underscores)
- Class prefix: PascalCase module name (e.g. `DailySpotRate`, `GLAccount`)
- Table name: `acc_<snake_case_module_name>`
- Entity name in `@CommandType` / permissions: `UPPERCASE_NO_SPACES` (e.g. `DAILYSPOTRATE`, `GLACCOUNT`, `BUSINESS_DATE` — match whatever convention the specific entity family already uses; check sibling modules for consistency before inventing a new one)

---

## Reference Modules
- **`glaccount`** — canonical full-featured reference (includes templates, dropdowns, bulk import/export, tag associations). Use this as the gold-standard pattern for anything involving header/detail hierarchies or chart-of-accounts-style structures.
- **`dailyspotrate`** — simpler reference module (FX spot/buying/selling rate by office + currency + date, create/update only, unique constraint enforced at both DB and service level). Use this as the pattern for straightforward CRUD-style modules without hierarchy or bulk import.

---

## Workflow Expectations
When asked to build a new module:
1. Confirm entity fields, constraints (uniqueness, mandatory/optional), and supported actions (create/update/delete) before generating code if not already specified.
2. Generate all layers listed above, following naming and structural conventions exactly.
3. Always include the `CommandWrapperBuilder` methods to be pasted in.
4. Always include the permission SQL insert statements.
5. Always include a Liquibase migration script for the new table.
6. Apply the Jersey `LocalDate` `@QueryParam` gotcha — never inject `LocalDate` directly.
7. Flag any manual wiring steps still needed (e.g. registering the module in any central config, adding sidebar/menu entries if frontend is involved).
