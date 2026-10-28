# GMoney — E-Wallet Monolith API

GMoney is a monolithic Spring Boot REST API for an e-wallet. Customers register, log in with a JWT, open **peso and forex wallets**, send money between wallets (converting currency automatically when the wallets differ), and read an immutable transfer ledger. Administrators manage accounts, customers and the exchange-rate table.

| | |
|---|---|
| Stack | Java 17, Spring Boot 4.0.6, Spring Web MVC, Spring Data JPA (Hibernate), MySQL, Spring Security + JWT (jjwt 0.11.5), Bean Validation, springdoc-openapi 3.0.3, JUnit 5 + Mockito + MockMvc, H2 (tests) |
| Live rates | [Frankfurter](https://frankfurter.dev) open API (European Central Bank reference rates, no API key) |
| Base URL | `http://localhost:8080` |
| API docs | Swagger UI at `http://localhost:8080/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs` |

---

## 1. Set up the database

1. Install and start **MySQL Community Server 8.x**.
2. GMoney uses the database `gmoney_db`. It is **created automatically** on first run (`createDatabaseIfNotExist=true`). To create it yourself instead:
   ```sql
   CREATE DATABASE gmoney_db;
   ```
3. Give the application your MySQL credentials (the app also needs a JWT key, see [section 2](#2-run-the-application)). Choose one option:
   - **Environment variables** (recommended):
     ```bash
     export DB_USERNAME=root
     export DB_PASSWORD=your_password
     ```
   - **Or** edit the defaults in `src/main/resources/application.properties`:
     ```properties
     spring.datasource.username=${DB_USERNAME:root}
     spring.datasource.password=${DB_PASSWORD:your_password}
     ```
4. Tables are created and updated by Hibernate (`spring.jpa.hibernate.ddl-auto=update`): `users`, `customers`, `accounts`, `transfer_records`, `exchange_rates`.

### Seed data
On startup (`gmoney.seed.enabled=true`) GMoney adds the following, but only if they are missing:

- An administrator: **`admin` / `admin123`**. Change it with `gmoney.seed.admin.username` and `gmoney.seed.admin.password`.
- Starter exchange rates. These are illustrative demo values, not live market rates. They are the **offline fallback**: as soon as the app is up, the live sync (see [Live exchange rates](#live-exchange-rates)) replaces them, and an admin can still edit them:

| Base → Quote | Rate |
|---|---|
| USD → PHP | 58.00 |
| EUR → PHP | 63.00 |
| GBP → PHP | 74.00 |
| SGD → PHP | 43.50 |

Reverse pairs (PHP → USD) and cross pairs (USD → EUR, which goes through PHP) are derived automatically.

### Live exchange rates
GMoney pulls the latest `USD/EUR/GBP/SGD → PHP` rates from the free [Frankfurter](https://frankfurter.dev) API (European Central Bank reference rates, published once per working day around 16:00 CET). No API key is needed.

- **When:** once at startup, then every weekday at 16:30 Frankfurt time. An admin can also sync on demand with `POST /api/v1/forex/rates/sync`.
- **If the API is down:** the stored rates (seeded or admin-edited) are kept and transfers keep working. The startup and scheduled syncs only log a warning, and the manual endpoint returns `503`. If only some currencies fail, the rest are still updated and the failed ones are listed in `failedCurrencies`.
- **Settings** (`application.properties`):
  ```properties
  gmoney.forex.sync.enabled=true                 # false = never call the API (tests use false)
  gmoney.forex.sync.cron=0 30 16 * * MON-FRI     # Spring cron, Europe/Berlin time zone
  gmoney.forex.frankfurter.base-url=https://api.frankfurter.dev/v1
  ```
- An admin edit through `PUT /api/v1/forex/rates/{id}` lasts until the next sync. Set `gmoney.forex.sync.enabled=false` to manage the rates by hand only.

## 2. Run the application

### The JWT signing key
GMoney signs every login token with a secret key from the **`JWT_SECRET`** environment variable:

```properties
gmoney.jwt.secret=${JWT_SECRET:gmoney-local-dev-secret-change-me-0123456789abcdef}
```

- **Not set:** the app starts with the built-in local development key after the colon. That's fine on your own machine, but the key is public in this repository, so anyone could forge tokens with it. **Always set your own key anywhere other than local development.**
- **Your own key** must be at least 32 bytes (32 ASCII characters). A shorter one stops startup with `JWT_SECRET must contain at least 32 UTF-8 bytes`. Generate a random one:
  ```bash
  openssl rand -base64 48
  ```
- Keep using the **same key** between restarts. A new key invalidates every token issued with the old one, so users have to log in again.

Tests don't need it: `src/test/resources/application.properties` has its own test-only key.

### Start the app
**Terminal** (macOS / Linux). Set the variables in the same terminal session, then start:
```bash
export JWT_SECRET="$(openssl rand -base64 48)"   # or paste a key you saved, to keep tokens valid across restarts
export DB_USERNAME=root
export DB_PASSWORD=your_password
./mvnw spring-boot:run                           # run `chmod +x mvnw` once if needed
```
**Windows** (Command Prompt): `set JWT_SECRET=<your key>`, then `mvnw.cmd spring-boot:run`.

**IntelliJ IDEA**
1. Open the green ▶ next to `GMoneyApplication` → **Modify Run Configuration…**
2. In **Environment variables**, enter (separated by `;`):
   ```text
   JWT_SECRET=<your generated key>;DB_USERNAME=root;DB_PASSWORD=your_password
   ```
   If the field is hidden, choose **Modify options → Environment variables**.
3. Click **OK**, then run `GMoneyApplication`.

To check that it is up, open `GET http://localhost:8080/actuator/health`. It should return `{"status":"UP"}`.

### Try the API in Swagger UI
1. Open `http://localhost:8080/swagger-ui.html`.
2. Run `POST /api/v1/auth/login` (for example `{"username":"admin","password":"admin123"}`) and copy the token from `data`.
3. Click **Authorize**, paste the token (without `Bearer `) and confirm. Every request after that sends `Authorization: Bearer <token>`, and the token is kept if you reload the page.

## 3. Run the tests

```bash
./mvnw test
```
Tests use an in-memory **H2** database (`src/test/resources/application.properties`), so MySQL does not need to be running.

---

## 4. Features

| Area | What it does |
|---|---|
| Auth | Register (customer), register admin (admin only), login → JWT (1 hour) |
| Customers | Own profile; owner/admin read and update; admin list/create/delete with wallet and login safeguards |
| Wallets | Open a wallet in **PHP, USD, EUR, GBP or SGD** (at most **one per currency** per customer); list *my* wallets; admin list/filter/paginate/adjust/close |
| Transfers | Atomic transfer (`@Transactional`): debit → credit → ledger row. Cross-currency transfers are converted at the current rate. Only the owner can send. |
| Ledger | `GET /history/{accountId}` returns the owner's sent and received entries, newest first, with currency, rate and converted amount |
| Forex | Supported currencies, exchange-rate CRUD (admin writes), conversion quotes, live ECB rates from Frankfurter (startup, daily schedule, admin on-demand sync) |
| API docs | OpenAPI 3 spec + Swagger UI with JWT bearer **Authorize** |
| Errors | One JSON shape for every error: `{timestamp, status, errorType, message}` |

### Forex wallet rules
- A wallet holds exactly one currency. PHP is the default, and any other supported currency makes it a forex wallet.
- A customer can hold **at most one wallet per currency** (one PHP, one USD, and so on). Opening a second one returns `409 Conflict`. `AccountService` checks this before saving, and a unique key on `accounts (customer_id, currency)` backs it up for two simultaneous requests. Wallets without an owner are not limited. An admin can delete only a zero-balance wallet with no transfer history. After that deletion, the customer can open that currency again. Historical wallets remain readable.
- The amount in `POST /api/v1/transfers` is always in the **source** wallet's currency. The destination receives `amount × rate`, rounded HALF_EVEN to 2 decimals.
- Rate lookup order: same currency gives 1, then a direct rate, then the inverse of the reverse rate, then a cross rate through PHP. If no rate is found the response is `404`, and because this happens **inside** the transaction, the debit is rolled back.
- "Buying dollars" means transferring from your own PHP wallet to your own USD wallet.

## 5. API reference

All endpoints except `/api/v1/auth/register`, `/api/v1/auth/login`, `/actuator/health` and the API docs (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`) need `Authorization: Bearer <token>`.

| Method | Path | Who | Success | Errors |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | public | 201 | 400, 409 |
| POST | `/api/v1/auth/register/admin` | ADMIN | 201 | 400, 401, 403, 409 |
| POST | `/api/v1/auth/login` | public | 200 (token in `data`) | 400, 401 |
| GET | `/api/v1/customers/me` | logged in | 200 | 401, 404 |
| POST | `/api/v1/customers` | ADMIN | 201 | 400, 403 |
| GET | `/api/v1/customers` | ADMIN | 200 | 401, 403 |
| GET | `/api/v1/customers/{id}` | ADMIN or profile owner | 200 | 401, 403, 404 |
| PUT | `/api/v1/customers/{id}` | ADMIN or profile owner | 200 | 400, 401, 403, 404 |
| DELETE | `/api/v1/customers/{id}` | ADMIN | 200 | 401, 403, 404, 409 |
| POST | `/api/v1/accounts` | ADMIN or owner of `customerId` | 201 | 400, 403, 404, 409 |
| GET | `/api/v1/accounts/mine` | logged in | 200 | 401 |
| GET | `/api/v1/accounts/{id}` | ADMIN or owner | 200 | 403, 404 |
| GET | `/api/v1/accounts[?status=]` | ADMIN | 200 | 403 |
| GET | `/api/v1/accounts/page?page=&size=` | ADMIN | 200 | 403 |
| PUT | `/api/v1/accounts/{id}` | ADMIN | 200 | 400, 403, 404, 409 |
| DELETE | `/api/v1/accounts/{id}` | ADMIN | 200 | 403, 404, 409 |
| POST | `/api/v1/transfers` | owner of `fromId` | 200 `"Transfer successful"` | 400, 401, 403, 404 |
| GET | `/api/v1/transfers/history/{accountId}` | owner | 200 | 401, 403, 404 |
| GET | `/api/v1/forex/currencies` | logged in | 200 | 401 |
| GET | `/api/v1/forex/rates`, `/rates/{id}` | logged in | 200 | 401, 404 |
| POST | `/api/v1/forex/rates` | ADMIN | 201 | 400, 403 |
| PUT / DELETE | `/api/v1/forex/rates/{id}` | ADMIN | 200 | 400, 403, 404 |
| POST | `/api/v1/forex/rates/sync` | ADMIN | 200 (updated rates, failed currencies, ECB date) | 401, 403, 503 |
| GET | `/api/v1/forex/quote?from=&to=&amount=` | logged in | 200 | 400, 404 |

### Example: open a USD wallet, then fund it from pesos
_The amounts below use the seeded USD → PHP rate of 58.00. With live rates on, the converted amount follows that day's ECB rate._

```http
POST /api/v1/accounts
{ "customerId": 3, "accountType": "SAVINGS", "balance": 0.00, "status": "ACTIVE", "currency": "USD" }

POST /api/v1/transfers
{ "fromId": 1, "toId": 2, "amount": 1000.00 }
→ 200 { "message": "Transfer successful",
        "data": "Successfully transferred 1000.00 from Account #1 to Account #2 (1000.00 PHP converted to 17.24 USD at rate 0.017241)" }
```

### Error status mapping
| Status | When |
|---|---|
| 400 | Bean-validation failure, malformed JSON, bad URL value, business-rule violation (unsupported currency, same-account transfer, more than 2 decimals, duplicate rate) |
| 401 | Missing, invalid or expired token; wrong username or password |
| 403 | Logged in but not the owner, or not an admin |
| 404 | Account, customer or rate not found; **insufficient funds** (kept as 404 to match the Week 7 lab contract) |
| 409 | Duplicate username/wallet; unsafe deletion; stale concurrent account update; database constraint |
| 503 | Live-rate sync could not reach Frankfurter for any currency (stored rates are kept) |

---

### Updates, deletion and concurrent transfers

- Balance adjustments accept only `{"balance":25.00}` with a required nonnegative value, at most 17 integer digits and 2 decimal places. Validation also runs in the service.
- A customer update replaces name, email and phone; it never changes the linked username or wallet ownership. Customers can update only their own profile; admins can update any profile.
- A customer can be deleted only after all their wallets have been removed. Deleting an eligible profile also deletes its linked login; tokens for that login then fail user lookup. Funded or historical wallets must remain, so their customer cannot be deleted.
- A wallet can be deleted only when its balance is zero and it has no transfer records. This preserves both money and access to history.
- `Account.version` provides optimistic locking. Conflicting balance writes roll back the entire transaction and return 409. Refresh balances before deciding whether to retry; the API does not automatically replay transfers.
- Hibernate adds the account version column with a default of zero for existing rows. Back up persistent databases before applying schema changes. Automated integration checks cover H2; verify the migration separately on your MySQL database.
- Postman folder **2b. CRUD and safeguards** exercises disposable customer/wallet CRUD and rejected inputs without changing the main transfer balances.

## 6. Project structure

```
src/main/java/com/fintech/walletapi
├── GMoneyApplication.java
├── client/       FrankfurterClient (live ECB rates over HTTP)
├── config/       DataSeeder (admin + starter rates), ForexRateSyncScheduler, OpenApiConfig (Swagger + JWT)
├── controller/   Auth, Customer, Account, Transfer, Forex
├── dto/          Request/response DTOs + ApiResponse envelope + BalanceUpdateDTO
├── entity/       User, Customer, Account, TransferRecord, ExchangeRate, SupportedCurrency
├── exception/    GlobalExceptionHandler, ErrorResponseDTO, InvalidTransactionException
├── repository/   Spring Data JPA repositories
├── security/     SecurityConfig, JWT filter/util, JSON 401/403 writers, ownership checks
└── service/      AccountService, CustomerService, TransferService, ExchangeRateService, ForexRateSyncService
```
The package name `com.fintech.walletapi` is kept from the course labs, so the lab contracts and autograder paths still match.

## 7. Design notes
- **Layering:** Controller → Service → Repository. Controllers stay thin, services hold all business rules, and entities never leave the service layer (DTOs only).
- **Constructor injection** is used everywhere.
- **Atomicity:** `TransferService.transferMoney` is `@Transactional` (Spring's). A failure after the first write, such as a missing destination or missing rate, rolls everything back. This is proven by `TransferIntegrationTest`.
- **Security:** stateless JWT. The username always comes from the token and never from the request body, which prevents BOLA. Method security uses `@PreAuthorize` and `@PostAuthorize`.
- **Money:** `BigDecimal` everywhere. Rates are kept to 6 decimals and money to 2, both rounded HALF_EVEN.

### Ready for microservices (MCO2)
The monolith already separates into five bounded contexts, one per team member:

| Microservice | Owns |
|---|---|
| Identity / Auth | `User`, login, JWT issuing |
| Customer | `Customer` profiles |
| Wallet | `Account` balances and wallet lifecycle |
| Transfer / Ledger | `TransferRecord`, transfer orchestration (would become a saga across services) |
| Forex | `ExchangeRate`, quotes and conversion |
