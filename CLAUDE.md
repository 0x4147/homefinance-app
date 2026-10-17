# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

A personal finance app with a Spring Boot backend (`backend/`, Java 17) and a React + TypeScript frontend (`frontend/`), backed by MySQL. Core purpose: import transactions from bank exports (CIBC, AMEX), categorize them (rule-based + learning), and surface spending insights.

## Commands

### Backend (Maven, run from `backend/`)
- Run locally: `./mvnw spring-boot:run` (requires `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_PROFILES_ACTIVE` env vars — see docker-compose.yml for dev values)
- Build: `./mvnw clean package`
- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=TransactionServiceTest`
- Run a single test method: `./mvnw test -Dtest=TransactionServiceTest#methodName`
- Tests use H2 in-memory DB (`src/test/resources/application-test.properties`), not MySQL.

### Frontend (npm, run from `frontend/`)
- Install: `npm install`
- Dev server: `npm run dev` (expects backend at `http://localhost:8080` by default — see `src/services/api.ts`; docker-compose actually exposes the backend on 8585, so adjust the API base URL when running against a local/dockerized backend)
- Build: `npm run build` (runs `tsc -b` then `vite build`)
- Lint: `npm run lint`

### Full stack via Docker
- `docker-compose up --build` from repo root starts MySQL (3306), backend (8585, debug 5005), and frontend (3001, via Nginx).
- Flyway migrations run automatically on backend startup (`backend/src/main/resources/db/migration`); `spring.jpa.hibernate.ddl-auto=none` — schema changes must go through a new Flyway migration, never Hibernate auto-DDL.

## Architecture

### Backend package layout (`ca.homefinance`)
- `controller/` — REST endpoints (`TransactionController`, `CategorizationController`, `FileUploadController`, `ReceiptController`, `AthenaController`).
- `service/` — business logic (`TransactionService`, `TransactionCategorizationService`, `ReceiptService`, `AthenaService`).
- `batch/` — Spring Batch job for CSV import (`BatchConfig`, `CsvTransactionReader`/`JsonTransactionReader`, `TransactionWriter`).
- `mapper/` — bank-specific field mappers (`CIBCTransactionFieldMapper`, `AMEXTransactionFieldMapper`) plus `TransactionMapper` (entity/DTO) and `CategorizeLearner`.
- `entity/` / `repository/` — JPA entities and Spring Data repositories.
- `dto/`, `helper/`, `constant/`, `config/` — supporting types, shared logic, constants (e.g. `ALLOWED_SOURCES`), and bean config (`RestClientConfig`).

### Transaction import pipeline
File upload (`FileUploadController`) → temp file written to disk → Spring Batch job (`transactionJob` in `BatchConfig`) launched with `filePath` and `sourceType` (`amex` or `cibc`) as job parameters → `FlatFileItemReader` tokenizes CSV columns differently per source type → the matching `TransactionFieldMapper` builds a `Transaction` → `TransactionWriter` persists it. Adding a new bank source means: add the source to `ALLOWED_SOURCES`, add a field mapper, and add a tokenizer branch in `BatchConfig.csvFileReader`.

### Transaction model
- `Transaction.amount` is a signed cash flow for every source: negative = money out, positive = money in. Importers translate their file format into this (CIBC: out/in columns; AMEX: sign flipped; shared-bill CSVs: unsigned, direction from the category's kind, negative reverses it).
- The **category** decides how a transaction behaves: `Category.kind` (`EXPENSE`/`INCOME`/`TRANSFER`) and `Category.inBalance` (counts toward the monthly shared-expense balance). There is no stored transaction type; `Transaction.getType()` derives a display label (EXPENSE/REFUND/INCOME/TRANSFER) from kind + sign.
- A transaction counts in the balance if `shared`, categorized, and `category.inBalance`; its value is `-amount`, attributed to `account.payer` (CIBC/AMEX are settled by Divya). Uncategorized rows don't count until reviewed.
- `Account` (CIBC, AMEX, ASANKA, DIVYA) is a table, not an enum; upload sources and the UI refer to accounts by `code`.
- Rental income is just categories: `Rental Bill Income` / `Rental Rent Income` (kind INCOME, in balance).

### Categorization
Two categorization mechanisms coexist:
- `TransactionCategorizer` (`helper/`) — simple substring lookup against `category_mappings.json` (merchant name → category), loaded once at startup via `@PostConstruct`.
- `TransactionCategorizationService` (`service/`) — the newer, multi-layer approach: exact merchant mapping, fuzzy keyword matching, historical pattern learning, and a manual review queue (`UncategorizedTransaction` entity) for transactions the system can't confidently categorize. User corrections made via `CategorizationController`'s `/learn` and `/review` endpoints feed back into future categorization. See `IMPROVED_CATEGORIZATION.md` for the full design rationale.

### Athena (AI insights)
`AthenaService` tries a local AI endpoint first (Ollama-compatible `generate` API, configured via `athena.local.*`), then falls back to an external provider (OpenAI chat completions, `athena.external.*`). Transactions are serialized to a CSV-like text block as prompt context (`buildTransactionsContext`). API keys are read from Spring config (`athena.external.openai.apiKey`), not hardcoded — keep it that way when adding providers.

### Frontend
Single-page app; almost all UI lives in `App.tsx` (dashboard, transaction views, bulk upload, spending insights, monthly balance checker), with `CategorizationReview.tsx` as the one extracted component and `services/api.ts` as the single typed API client for every backend call. When adding a feature, prefer extracting a new component rather than growing `App.tsx` further, and add new backend calls to `api.ts` rather than calling `fetch` directly from components.

## Notes
- `application.properties` contains dev-only credentials/config (DB, Athena keys) sourced from env vars where sensitive — don't hardcode real secrets when editing it.
- Swagger/OpenAPI UI is available via `springdoc-openapi-starter-webmvc-ui` (default path `/swagger-ui.html`).

Don't add Unit tests unless explicitly asked.