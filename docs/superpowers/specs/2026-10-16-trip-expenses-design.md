# Trip expense tracking — design

Date: 2026-10-16

## Goal

Let the family track the expenses of each trip (e.g. "Japan 2026") without disturbing the existing category insights or the monthly shared-expense balance.

## Decisions

- A trip is a **label on top of** a transaction, not a category. Category, `shared`, `owner`, `Account.payer` and the balance logic are unchanged.
- Trip spending stays in the monthly shared-expense balance. There is no separate trip settlement.
- Trips are mainly paid on CIBC/AMEX but may be paid on a personal card (ASANKA/DIVYA). Whether such a transaction is shared or not is controlled by the existing `Transaction.shared` flag.
- Amounts are CAD. Foreign currency is out of scope.
- No unit tests are to be added for this feature (user decision).

## 1. Data model

New Flyway migration `V12__add_trips.sql` (schema changes go through Flyway only; `ddl-auto=none`):

- Table `trips`: `id` (PK), `name` (not null), `start_date` (not null), `end_date` (not null), `budget` (nullable decimal).
- Column `transactions.trip_id` (nullable FK to `trips.id`). Existing rows stay untagged.

New `Trip` entity and `TripRepository` in `ca.homefinance.entity` / `repository`, following existing entity patterns. `Transaction` gets a nullable `@ManyToOne Trip trip`.

## 2. Backend API

New `TripController` and `TripService`.

- CRUD for trips. Deleting a trip sets `trip_id = null` on its transactions; it never deletes transactions.
- Tag/untag transactions, singly or in bulk (e.g. `PUT /trips/{id}/transactions` with a list of transaction ids; untag via `trip_id = null`).
- `GET /trips/{id}/summary`: total spend, spend by category, spend per day, spend by payer (`Account.payer`), and spend vs. budget. Uses the same rules as the main dashboard: signed amount (value is `-amount`), refunds net off, only transactions that count in the balance (shared, categorized, `category.inBalance`) are included.
- Auto-suggest: for a transaction, return the trip whose `[start_date, end_date]` contains the transaction date (exposed as a field on the transaction DTO or a small suggest endpoint). It is only a suggestion; nothing is tagged without confirmation.

## 3. Frontend

- New components (not added to `App.tsx`): trips list with create/edit form, and trip detail page showing the summary and the tagged transactions with add/remove.
- Trip picker on the transaction edit/review UI, showing the suggested trip when one matches, and a bulk "assign to trip" action on a selection of transactions.
- All new backend calls added to `services/api.ts`.

## 4. Out of scope

Separate trip settlement, currency conversion, per-person trip budgets, automatic (unconfirmed) tagging.
