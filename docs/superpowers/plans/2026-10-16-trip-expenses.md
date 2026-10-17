# Trip Expense Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the family group transactions into named trips and see each trip's spending, without changing categories or the monthly balance.

**Architecture:** A new `Trip` entity and a nullable `Transaction.trip` link. A `TripService`/`TripController` pair handles trip CRUD, bulk tagging, candidate suggestions and a summary. `TransactionService` stays the only place that builds `TransactionDto`s. The React side gets a Trips view and detail page as new components, plus a trip picker and bulk assign in `TransactionsModal`.

**Tech Stack:** Spring Boot 3 / Java 17 / Lombok / Spring Data JPA / Flyway (MySQL); React + TypeScript + Tailwind + lucide-react.

**Spec:** `docs/superpowers/specs/2026-10-16-trip-expenses-design.md`

## Global Constraints

- Schema changes only via a new Flyway migration (`V12`); `ddl-auto=none`.
- **No unit tests are added** (user decision). Verification is compile/build/lint plus the manual checks in each task.
- Trip spending stays in the monthly shared-expense balance; no changes to `SettlementService`, `Category`, `Account`, or the `shared`/`owner` logic.
- Amounts are CAD; no currency handling.
- A transaction's value in a trip summary is `Transaction.balanceContribution()` (spending positive, refunds negative), and only transactions where `countsInBalance()` is true are summed.
- Tagging is never applied silently: candidates are only suggested, and the user confirms.
- Deleting a trip must untag its transactions, never delete them.
- Frontend: new UI goes in new components (not `App.tsx`); every backend call goes through `services/api.ts`.
- Existing 9- and 11-argument `TransactionDto` constructors must keep working (tests use them).
- Do not commit unless asked. The working tree already holds unrelated uncommitted changes.

## Review Focus

Checked manually in the owning task, since no unit tests are written:

1. Trip with `endDate` before `startDate` is rejected with 400 (Task 2).
2. Refund inside a trip lowers the total instead of adding to it (Task 3).
3. Unshared or uncategorized trip transactions are listed on the trip but excluded from the total (Task 3).
4. Trip with no budget returns `budgetRemaining = null`, not an error (Task 3).
5. Assigning a transaction that already belongs to another trip moves it; unknown trip or transaction ids return 404 (Task 2).

## File Structure

Backend (`backend/src/main/java/ca/homefinance/`):
- Create `entity/Trip.java`, `repository/TripRepository.java`
- Create `dto/TripDto.java`, `dto/TripSummaryDto.java`
- Create `service/TripService.java`, `controller/TripController.java`
- Modify `entity/Transaction.java`, `dto/TransactionDto.java`, `repository/TransactionRepository.java`, `service/TransactionService.java`, `controller/TransactionController.java`
- Create `resources/db/migration/V12__add_trip.sql`

Frontend (`frontend/src/`):
- Modify `services/api.ts`, `App.tsx` (nav entry and view switch only), `components/TransactionsModal.tsx`
- Create `components/TripsView.tsx` (list and create/edit form), `components/TripDetail.tsx`, `components/TripPicker.tsx`, `components/useTrips.ts`

---

### Task 1: Data model and transaction-side plumbing

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__add_trip.sql`, `entity/Trip.java`, `repository/TripRepository.java`
- Modify: `entity/Transaction.java`, `dto/TransactionDto.java`, `repository/TransactionRepository.java`, `service/TransactionService.java`, `controller/TransactionController.java`

**Interfaces:**
- Produces:
  - `Trip` entity: `Integer tripId` (`trip_id`), `String name`, `LocalDate startDate`, `LocalDate endDate`, `BigDecimal budget` (nullable). Lombok `@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor`, `@Table(name = "trip")`.
  - `Transaction.trip`: nullable `@ManyToOne Trip`, column `trip_id`.
  - `TransactionDto` gains `Integer tripId` and `String tripName` (appended after `owner`).
  - `TransactionRepository`: `List<Transaction> findByTrip_TripId(Integer tripId)` and `List<Transaction> findByTripIsNullAndDateBetween(LocalDate start, LocalDate end)`.
  - `TransactionService`: `TransactionDto setTrip(Integer transactionId, Integer tripId)` (null clears; 404 if either id is unknown), `List<TransactionDto> getTransactionDtosByTrip(Integer tripId)`, `List<TransactionDto> getTripCandidates(LocalDate start, LocalDate end)` (untagged transactions in the range).
  - `PUT /api/v1/transaction/{transactionId}/trip` with body `{"tripId": 3}` or `{"tripId": null}`, returns `TransactionDto`.
  - `TripRepository extends JpaRepository<Trip, Integer>`.

- [ ] **Step 1: Write `V12__add_trip.sql`.** Table `trip` (singular, like `transaction` and `settlement`): `trip_id INT AUTO_INCREMENT PRIMARY KEY`, `name VARCHAR(255) NOT NULL`, `start_date DATE NOT NULL`, `end_date DATE NOT NULL`, `budget DECIMAL(10, 2) NULL`, `CONSTRAINT chk_trip_dates CHECK (end_date >= start_date)`. Then `ALTER TABLE transaction ADD COLUMN trip_id INT NULL, ADD CONSTRAINT fk_transaction_trip FOREIGN KEY (trip_id) REFERENCES trip(trip_id) ON DELETE SET NULL;`. Add a leading comment in the style of V8.
- [ ] **Step 2: Create `Trip`, `TripRepository`, and add `Transaction.trip`** (field with a one-line Javadoc, `@JoinColumn(name = "trip_id", referencedColumnName = "trip_id")`).
- [ ] **Step 3: Extend `TransactionDto`.** Append `tripId` and `tripName` to the fields. Update the two existing delegating constructors to pass `null, null`, and add an explicit 12-argument constructor (the current all-args shape) that delegates with `null, null`, so existing callers still compile.
- [ ] **Step 4: Update `TransactionService.toDto`** to pass `tx.getTrip() != null ? tx.getTrip().getTripId() : null` and the trip's name (or null).
- [ ] **Step 5: Add the repository finders and the three `TransactionService` methods** listed above. `setTrip` mirrors `setOwner` (404 via `ResponseStatusException` when the transaction is missing).
- [ ] **Step 6: Add `PUT /{transactionId}/trip` to `TransactionController`**, with `public record TripRequest(Integer tripId) {}`, delegating to `transactionService.setTrip(transactionId, request.tripId())`. Inject `TripRepository` into `TransactionService` (a new `private final` field) so the service resolves the trip and returns 404 for an unknown id.
- [ ] **Step 7: Verify.** Run `./mvnw -q -DskipTests compile` from `backend/`. Expected: success. Then run `./mvnw test` to confirm the existing tests still compile and pass.

---

### Task 2: Trip CRUD, bulk assign and candidates

**Files:**
- Create: `dto/TripDto.java`, `service/TripService.java`, `controller/TripController.java`

**Interfaces:**
- Consumes: Task 1's `Trip`, `TripRepository`, `TransactionRepository`, `TransactionService.getTransactionDtosByTrip/getTripCandidates/setTrip`.
- Produces `TripDto` (`@Data @AllArgsConstructor @NoArgsConstructor`): `Integer tripId; String name; LocalDate startDate; LocalDate endDate; BigDecimal budget;`. Endpoints under `/api/v1/trip`:
  - `GET /` returns `List<TripDto>`, newest `startDate` first.
  - `POST /` creates a trip from a `TripDto` body (ignores `tripId`) and returns the saved `TripDto`.
  - `PUT /{tripId}` updates and returns it.
  - `DELETE /{tripId}` returns 204. Its transactions become untagged.
  - `GET /{tripId}/transactions` returns `List<TransactionDto>`.
  - `GET /{tripId}/candidates` returns untagged `TransactionDto`s dated within the trip's range.
  - `PUT /{tripId}/transactions` with body `{"transactionIds": [1, 2]}` assigns them and returns `List<TransactionDto>`.

- [ ] **Step 1: Create `TripDto` and `TripService`.** Methods: `List<TripDto> getTrips()`, `TripDto createTrip(TripDto)`, `TripDto updateTrip(Integer, TripDto)`, `void deleteTrip(Integer)`, `List<TransactionDto> assignTransactions(Integer tripId, List<Integer> transactionIds)`. Validation, with a 400 via `ResponseStatusException`: name is required, both dates are required, and `endDate` is not before `startDate`. A negative budget is also rejected. A missing trip is a 404. `assignTransactions` calls `TransactionService.setTrip` per id, so a transaction already in another trip moves, and an unknown transaction id gives a 404.
- [ ] **Step 2: Create `TripController`** with the endpoints above, using `@RequiredArgsConstructor` and `ResponseEntity` like `SettlementController`. Declare `public record AssignRequest(List<Integer> transactionIds) {}`. `GET /{tripId}/candidates` looks the trip up and delegates to `getTripCandidates(startDate, endDate)`.
- [ ] **Step 3: Verify.** Compile with `./mvnw -q -DskipTests compile`. Then start the stack (`docker-compose up --build`) and check with curl against `http://localhost:8585/api/v1/trip`:
  - create a trip → 200 with an id;
  - create with `endDate` before `startDate` → 400;
  - assign a transaction that already belongs to another trip → it moves;
  - assign id `999999` → 404;
  - delete the trip → 204, and its transactions still exist with `tripId` null.

---

### Task 3: Trip summary

**Files:**
- Create: `dto/TripSummaryDto.java`
- Modify: `service/TripService.java`, `controller/TripController.java`

**Interfaces:**
- Consumes: `TransactionRepository.findByTrip_TripId`, `Transaction.countsInBalance()`, `Transaction.balanceContribution()`.
- Produces `TripSummaryDto` (`@Data @AllArgsConstructor`): `TripDto trip; BigDecimal total; Map<String, BigDecimal> byCategory; Map<LocalDate, BigDecimal> byDay; Map<String, BigDecimal> byPayer; BigDecimal budgetRemaining; int uncountedTransactions;`. Endpoint `GET /api/v1/trip/{tripId}/summary`. `TripService.getSummary(Integer tripId)` returns it.

- [ ] **Step 1: Implement `getSummary`.** Only transactions with `countsInBalance()` are summed, using `balanceContribution()`. `byCategory` is keyed by category name, `byPayer` by `account.getPayer().getCode()`, and `byDay` by transaction date, sorted ascending (`TreeMap`). `byCategory` and `byPayer` are ordered by amount descending (`LinkedHashMap`). `budgetRemaining` is `budget - total`, or null when the trip has no budget. `uncountedTransactions` counts tagged transactions that don't count in the balance (unshared or uncategorized), so the UI can say they were left out.
- [ ] **Step 2: Add the endpoint** and 404 for an unknown trip.
- [ ] **Step 3: Verify.** Compile, then curl `GET /trip/{id}/summary` for a trip with a known mix and confirm:
  - the total equals the sum of `-amount` over counted rows;
  - a refund lowers the total;
  - an unshared transaction raises `uncountedTransactions` and not the total;
  - a trip with no budget gives `budgetRemaining: null`.

---

### Task 4: Frontend API client

**Files:**
- Modify: `frontend/src/services/api.ts`

**Interfaces:**
- Produces, exported from `api.ts`:
  - `Trip { tripId?: number; name: string; startDate: string; endDate: string; budget?: number | null }`
  - `TripSummary { trip: Trip; total: number; byCategory: Record<string, number>; byDay: Record<string, number>; byPayer: Record<string, number>; budgetRemaining: number | null; uncountedTransactions: number }`
  - `TransactionDto` and `Transaction` gain `tripId?: number | null` and `tripName?: string | null`.
  - `ApiService` methods: `getTrips(): Promise<Trip[]>`, `saveTrip(trip: Trip): Promise<Trip>` (POST when no `tripId`, else PUT), `deleteTrip(tripId: number): Promise<void>`, `getTripTransactions(tripId): Promise<TransactionDto[]>`, `getTripCandidates(tripId): Promise<TransactionDto[]>`, `getTripSummary(tripId): Promise<TripSummary>`, `assignTransactionsToTrip(tripId: number, transactionIds: number[]): Promise<TransactionDto[]>`, `setTransactionTrip(transactionId: number, tripId: number | null): Promise<TransactionDto>`.

- [ ] **Step 1: Add the types and methods** following the existing `makeRequest` + `log.info` style (`/trip/...` and `/transaction/{id}/trip`).
- [ ] **Step 2: Verify.** Run `npm run build` from `frontend/`. Expected: success.

---

### Task 5: Trips list, form and navigation

**Files:**
- Create: `components/useTrips.ts`, `components/TripsView.tsx`
- Modify: `App.tsx`

**Interfaces:**
- Consumes: Task 4's `apiService` trip methods and `Trip` type.
- Produces: `useTrips(): { trips: Trip[]; reload: () => Promise<void>; loading: boolean }`, and `export default function TripsView()`. `TripsView` shows the list and the create/edit form and, when a trip is selected, renders `<TripDetail trip={...} onBack={...} onChanged={reload} />` from Task 6.

- [ ] **Step 1: Create `useTrips`.** It loads once on mount and exposes `reload`.
- [ ] **Step 2: Create `TripsView`.** It has a card list (name, date range, budget if set) newest first, a "New trip" button, an inline create/edit form (name, start, end, optional budget) with the 400 message shown on failure, and a delete action that goes through the existing `ConfirmDialog`. Match the Tailwind styling of `MonthlyBalanceChecker` and `OwnerSelect`.
- [ ] **Step 3: Wire navigation in `App.tsx`.** Add `'Trips'` to the `View` union, a sidebar item `{ id: 9, name: 'Trips', icon: Plane }` (import `Plane` from `lucide-react`) and `case 'Trips': return <TripsView />;`. Nothing else changes in `App.tsx`.
- [ ] **Step 4: Verify.** Run `npm run build` and `npm run lint`. Then run `npm run dev` and check in the browser that you can create, edit and delete a trip. Task 6 is needed before selecting one. Until then, stub the detail with a placeholder import that Task 6 replaces.

---

### Task 6: Trip detail page

**Files:**
- Create: `components/TripDetail.tsx`

**Interfaces:**
- Consumes: `getTripSummary`, `getTripTransactions`, `getTripCandidates`, `assignTransactionsToTrip`, `setTransactionTrip`.
- Produces: `export function TripDetail({ trip, onBack, onChanged }: { trip: Trip; onBack: () => void; onChanged: () => void })`.

- [ ] **Step 1: Summary section.** Show the total, budget and remaining (a red "over budget" state when `budgetRemaining < 0`), the spend by category and by payer as simple sorted lists with amounts, and the spend per day. If `uncountedTransactions > 0`, show a note such as "N transactions are tagged but not counted (unshared or uncategorized)". Plain lists only; charts are out of scope.
- [ ] **Step 2: Transactions section.** List the trip's transactions (date, entity, account, category, amount) with a "Remove" action calling `setTransactionTrip(id, null)`, then reload.
- [ ] **Step 3: Suggested section.** From `getTripCandidates`, show untagged transactions in the trip's date range with checkboxes, "Select all", and an "Add to trip" button that calls `assignTransactionsToTrip` and reloads. Nothing is added without this confirmation.
- [ ] **Step 4: Verify.** Run `npm run build` and `npm run lint`. In the browser, open a trip with in-range untagged transactions: candidates appear, adding them moves them to the trip list, the summary total updates, and removing one reverses it.

---

### Task 7: Trip picker and bulk assign in the transactions modal

**Files:**
- Create: `components/TripPicker.tsx`
- Modify: `components/TransactionsModal.tsx`

**Interfaces:**
- Consumes: `useTrips`, `apiService.setTransactionTrip`, `apiService.assignTransactionsToTrip`.
- Produces: `export function TripPicker({ transactionId, tripId, trips, onChanged }: { transactionId?: number; tripId?: number | null; trips: Trip[]; onChanged?: () => void })`. It is a small `<select>` styled like `OwnerSelect` (optimistic update, revert and error tint on failure), with "No trip" as the empty option.

- [ ] **Step 1: Create `TripPicker`**, following `OwnerSelect.tsx` closely.
- [ ] **Step 2: Extend `TransactionsModal`.** Add `tripId` and `tripName` to `Row` and `toRow`. Call `useTrips()` once in the modal. Add a "Trip" column using `TripPicker` (hidden when there are no trips). Add a leading checkbox column with a header select-all for the visible rows. When any rows are selected, show a bar above the table with a trip `<select>` and an "Assign to trip" button that calls `assignTransactionsToTrip`, clears the selection and calls `onCategoryChanged?.()` so the parent refreshes.
- [ ] **Step 3: Verify.** Run `npm run build` and `npm run lint`. In the browser, open the transactions modal from the dashboard or the transactions view: change one row's trip and confirm it appears on that trip's detail page. Select several rows and bulk-assign them, and confirm they all appear there.

---

### Final check

- [ ] Run `./mvnw test` from `backend/` and `npm run build && npm run lint` from `frontend/`. Run the full stack once and walk through: create a trip, assign transactions by candidates, by picker and in bulk, view the summary, delete the trip and confirm its transactions survive untagged. Report any failure with its output. Do not commit; ask the user first.
