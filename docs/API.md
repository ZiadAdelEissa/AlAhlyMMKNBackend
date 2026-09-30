# Screen 1 — Service dictionary

Base URL: `http://localhost:8080`

## GET /api/v1/services

No authentication, parameters, or request body. Returns `200 OK`, `application/json`.
The root object is the dictionary itself: category keys map directly to service arrays.
All names and keys are English. IDs are stable identifiers, independent of display names.

```json
{
  "electricity": [
    { "id": 1, "name": "Cairo Electricity", "available": true, "iconUrl": "/icons/electricity.svg" },
    { "id": 2, "name": "Alexandria Electricity", "available": false, "iconUrl": "/icons/electricity.svg" },
    { "id": 3, "name": "North Delta Electricity", "available": true, "iconUrl": "/icons/electricity.svg" }
  ],
  "water": [
    { "id": 4, "name": "Greater Cairo Water", "available": true, "iconUrl": "/icons/water.svg" },
    { "id": 5, "name": "Alexandria Water", "available": true, "iconUrl": "/icons/water.svg" }
  ],
  "gas": [
    { "id": 6, "name": "Town Gas", "available": true, "iconUrl": "/icons/gas.svg" },
    { "id": 7, "name": "Natgas", "available": true, "iconUrl": "/icons/gas.svg" }
  ]
}
```

## Frontend behavior

- Render groups in `electricity`, `water`, `gas` order; do not depend on JSON object key order.
- Use the category key for the section title and `iconUrl` for the image. Use integer `id` as the selected service identifier.
- Show `UNAVAILABLE` and disable selection when `available` is false. Keep unavailable items visible in search results.
- Search the downloaded dictionary locally using English text, ignoring case and surrounding whitespace. There are 7 demo services; derive the count from the response rather than hardcoding the screenshot's 24.
- Save the full response and a client `savedAt` timestamp only after a successful fetch. On network failure, use that saved response and show its age. If there is no saved response, show a retry/error state. Offline status is determined by the client, not this endpoint.
- There is no search endpoint or payment in this screen. Inquiry checks availability again on the server; disabling a button alone is not server validation.
- For browser development, proxy `/api` to `http://localhost:8080` through the frontend dev server. This project does not enable unrestricted cross-origin requests.

Example local search preserving the dictionary shape:

```javascript
function searchServices(dictionary, query) {
  const term = query.trim().toLowerCase();
  return Object.fromEntries(
    Object.entries(dictionary)
      .map(([category, services]) => [
        category,
        services.filter(service =>
          category.includes(term) || service.name.toLowerCase().includes(term)
        )
      ])
      .filter(([, services]) => services.length > 0)
  );
}
```

`electricity` returns three services, including unavailable Alexandria Electricity. `  CAIRO  ` returns Cairo Electricity and Greater Cairo Water. Blank input returns all services, and an unknown query returns `{}`.

## Checks

The integration test starts the server and verifies anonymous HTTP access, JSON content type, dictionary categories, nonempty arrays, unique service IDs, English names, boolean availability, and unavailable Alexandria Electricity. This GET endpoint has no user input to validate.

# Screen 2 — Fees inquiry

Live demo preview: `http://localhost:8080/`. Select a service to open the inquiry screen. A successful inquiry opens Bill details automatically using the response body.

## POST /api/v1/inquiries

No authentication. Header: `Content-Type: application/json`.

```json
{
  "serviceId": 1,
  "subscriberNumber": "1024750891"
}
```

serviceId must be a positive JSON integer (int32), not a string or decimal. subscriberNumber must be a string. The subscriber number must contain exactly 10 ASCII digits, with no spaces. The preview removes display whitespace before sending it. This 10-digit rule applies to all providers in this demo only; actual provider-specific rules are not implemented.

Response: `200 OK`, `Cache-Control: no-store`.

```json
{
  "quoteId": "a-generated-uuid",
  "serviceId": 1,
  "serviceName": "Cairo Electricity",
  "subscriberNumber": "1024750891",
  "customerName": "Mina A.",
  "billMonth": "2026-08",
  "currency": "EGP",
  "amountDuePiastres": 24750,
  "serviceFeePiastres": 500,
  "vatPiastres": 70,
  "totalPiastres": 25320,
  "createdAt": "2026-09-29T16:00:00Z",
  "expiresAt": "2026-09-29T16:05:00Z"
}
```

Amounts are integer piastres. The server adds the fixed demo fee (500 piastres) and 14% VAT on that fee (70 piastres). The frontend formats these amounts only. Client-supplied amounts are ignored. Timestamps use UTC and the quote lifetime is 300 seconds. Each inquiry generates a new quote ID. Quotes and successful payments are now stored in process memory, and payment validates expiry on the server. All records reset when the server restarts. No real provider or payment gateway is called.

Demo fixtures for every available service:

| Subscriber | Result |
| --- | --- |
| `1024750891` | Bill found: electricity 24750, water 12500, gas 8500 piastres before fees |
| `1024750892` | No outstanding bill |
| Any other valid 10-digit number | Subscriber not found |

Errors have `{ "code": "...", "message": "English explanation" }`.

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | INVALID_REQUEST | Malformed JSON, missing fields, non-integer serviceId, or non-string subscriberNumber |
| 400 | INVALID_SERVICE_ID | Non-positive service ID |
| 400 | INVALID_SUBSCRIBER_NUMBER | Subscriber format is invalid |
| 404 | SERVICE_NOT_FOUND | Unknown service ID |
| 409 | SERVICE_UNAVAILABLE | Service is currently disabled |
| 404 | SUBSCRIBER_NOT_FOUND | No matching demo subscriber |
| 404 | NO_OUTSTANDING_BILL | Subscriber has no unpaid demo bill |

Preview transport is local HTTP. The mockup's device-encryption statement is deliberately omitted because application-level encryption is not implemented. Use HTTPS when deploying beyond localhost.

# Screen 3 — Bill details integration

There is no additional API request to render this screen. Use the complete successful response from `POST /api/v1/inquiries`.

## Check my bill button

1. Validate the input and remove display spaces; keep the number as a string.
2. Disable the button while the request is pending and send the inquiry.
3. On `200`, store the response in the frontend's temporary state, then navigate to Bill details and render it. The API returns JSON, not an HTTP redirect or a page URL.
4. On any error, remain on Fees inquiry and display the returned `message`. Do not show an old quote or navigate on `404`/`409`.
5. Re-enable the button after the request finishes. On network failure, show a retry message.

Framework-neutral example (`setQuote`, `navigate`, `showError`, and `setLoading` are functions implemented by your frontend, not backend endpoints):

```javascript
async function checkBill(serviceId, subscriberNumber) {
  setLoading(true);
  try {
    const response = await fetch('/api/v1/inquiries', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ serviceId, subscriberNumber })
    });
    const body = await response.json();
    if (!response.ok) {
      showError(body.message);
      return;
    }
    setQuote(body);
    navigate('/bill-details'); // Your frontend route, NOT a backend API URL.
  } catch {
    showError('Unable to connect. Please try again.');
  } finally {
    setLoading(false);
  }
}
```

Our vanilla-JavaScript preview implements this with `showBillDetails(result)`: it hides the inquiry section and shows the details section at the same URL `/`. It does not implement a `/bill-details` route. A React, Angular, Flutter, or other frontend can use its own routing mechanism instead.

## Field mapping

| UI field | Response field | Display example |
| --- | --- | --- |
| Service | serviceName | Cairo Electricity |
| Subscriber | subscriberNumber | 1024750891 |
| Customer | customerName | Mina A. |
| Bill month | billMonth | 2026-08 → August 2026 |
| Amount due | amountDuePiastres | 24750 → 247.50 |
| Service fee | serviceFeePiastres | 500 → 5.00 |
| VAT | vatPiastres | 70 → 0.70 |
| Total | totalPiastres | 25320 → 253.20 |
| Currency | currency | EGP |
| Countdown | expiresAt | Remaining time, formatted mm:ss |

Divide piastres by 100 for display with two decimal places only; never recompute the fee, VAT, or total. Preserve `quoteId` with the quote for the payment request. Do not send a client-computed amount for payment.

## Expiry, back, and reload

- Recompute the display countdown from `expiresAt` and the current time; do not reset it to five minutes when opening the screen or returning from a background tab. The display depends on the device clock; the payment API independently enforces expiry using the server clock.
- At zero, show `Quote expired` and a `Check bill again` button. The preview returns to the input screen with the previous number preserved; the user presses `Check my bill` to request a fresh quote.
- The in-page back button also returns to inquiry and preserves the input. The next inquiry creates a new quote. Timers stop when leaving the details screen.
- Preview state is memory-only: a browser refresh opens the service list, and a new inquiry is required. A frontend route opened without a quote should return to inquiry. No quote retrieval endpoint exists yet.
- The Pay now · Demo button sends the saved quoteId to the payment API. Only a successful payment response opens the receipt. Finding a bill is not payment success.

## Postman

Import `docs/MMKN.postman_collection.json`. Its `baseUrl` defaults to `http://localhost:8080`. Start the backend, then use Collection Runner to run all requests. Requests include status/body assertions for catalog, successful quotes and error cases. The collection includes payment, replay, altered amounts, and history assertions. Restart the demo server before a complete collection run so all bills are unpaid.

# Payment and receipt

Service IDs are integers: 1 Cairo Electricity, 2 Alexandria Electricity (unavailable), 3 North Delta Electricity, 4 Greater Cairo Water, 5 Alexandria Water, 6 Town Gas, 7 Natgas. Category keys remain strings. quoteId and paymentId are UUID strings; subscriberNumber stays a string to preserve leading zeros.

## POST /api/v1/payments

Headers: Content-Type: application/json and Idempotency-Key: a unique 8–128 character key containing letters, digits, hyphens or underscores. Generate one key per payment attempt and **reuse it after a timeout or lost response**.

Request body (no other fields accepted):

```json
{"quoteId":"a-canonical-uuid-from-the-inquiry-response"}
```

Returns 200 for a new payment and for a replay, with Cache-Control: no-store:

```json
{
  "paymentId":"a-generated-uuid",
  "quoteId":"the-original-paid-quote-uuid",
  "reference":"MP-20260929-a-generated-uuid",
  "status":"SUCCESS",
  "serviceId":1,
  "serviceName":"Cairo Electricity",
  "subscriberNumber":"1024750891",
  "totalPiastres":25320,
  "currency":"EGP",
  "paidAt":"2026-09-29T16:00:00Z"
}
```

The backend uses the saved quote amount, checks provider availability, and rejects an unpaid quote at or after expiresAt. A completed payment replay succeeds even after expiry. Requests for the same bill (serviceId + subscriberNumber + billMonth), including requests using another pre-existing quote or another key, return the original receipt without creating a second payment. The returned quoteId is the one originally paid. Reusing a successful key for a different requested quote returns 409 IDEMPOTENCY_CONFLICT. Failed attempts do not reserve a key. Concurrent requests are serialized within this demo process.

Frontend: disable Pay while pending, send quoteId plus the same key on retries, and open the receipt only on 200. Render serviceName, subscriberNumber, totalPiastres, currency, paidAt and reference from the response. The preview formats paidAt in Africa/Cairo time. Copy the reference with the clipboard API; provide a manual-copy fallback.

On an unknown network outcome, keep the quote and key and offer a safe retry even if the display timer has reached zero. A server error keeps the user on Bill details. QUOTE_EXPIRED requires a fresh inquiry. A fresh inquiry after payment returns 409 BILL_ALREADY_PAID.

## GET /api/v1/payments

Returns 200 and an array of receipts, newest first, with Cache-Control: no-store. This is the shared demo history, not a per-user history (there is no Auth). A browser refresh loses preview state but history remains until the server restarts.

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | INVALID_REQUEST | Invalid quoteId, non-string quoteId, or extra payment fields |
| 400 | INVALID_IDEMPOTENCY_KEY | Missing or invalid key |
| 404 | QUOTE_NOT_FOUND | Unknown quote, expired quote cleaned up, or server restarted |
| 409 | QUOTE_EXPIRED | Unpaid quote expired |
| 409 | IDEMPOTENCY_CONFLICT | Successful key reused for a different quote |
| 409 | SERVICE_UNAVAILABLE | Provider unavailable at payment time |
| 409 | BILL_ALREADY_PAID | Inquiry for a bill already paid |
| 503 | DEMO_CAPACITY_REACHED | Demo store limit reached; restart to reset |

## VAT policy

VAT is authoritative on the backend; the frontend may display an estimate but never supplies the payable VAT or total. The calculation comment in InquiryService explains the change point. If VAT is removed, update that policy, UI labels, examples and tests together. In this demo the fee is 500 piastres and VAT is exactly 70; changing the fee requires an explicit rounding policy.

## Demo limits

No real money is charged. Quotes, idempotency records and receipts are in memory, capped at 10,000 records per store. Duplicate protection lasts only for this server process; restart resets all demo bills. This is not durable or multi-instance payment storage. Production would need persistent transactions, unique bill/payment constraints and provider reconciliation.

# Icons: URL, not inline image data

Every service has a required `iconUrl` string. It is a root-relative URL on the backend, not Base64, an SVG markup string, or a `data:` URL. All icons are the same drawings and colors used in the preview.

| Category | iconUrl | Content-Type |
| --- | --- | --- |
| electricity | /icons/electricity.svg | image/svg+xml |
| water | /icons/water.svg | image/svg+xml |
| gas | /icons/gas.svg | image/svg+xml |

`GET` the URL to retrieve the actual SVG image. No authentication is needed. Resolve it against the **backend origin**, particularly if the frontend runs on a different port or domain:

```javascript
const backendOrigin = 'http://localhost:8080'; // Set this per environment.
imageElement.src = new URL(service.iconUrl, backendOrigin).href;
imageElement.alt = ''; // Decorative when the adjacent service name is visible.
```

The example resolves to `http://localhost:8080/icons/electricity.svg`. The preview uses `<img>`; use your platform's SVG-capable image component in a mobile app. Use `serviceId` to find the service's icon in the downloaded dictionary for Bill details; quotes and receipts do not duplicate `iconUrl`.

The drawings have built-in category colors. Card backgrounds and the unavailable grayscale treatment are frontend styling. If an image fails, keep the service name and action usable. Saving the JSON alone does not guarantee offline images; the frontend must cache the SVG files separately if guaranteed offline icons are required.

# Data type reference

JSON has a `number` type; here `integer/int32` means a whole number mapped to Java `int`, and `integer/int64` maps to Java `long`. All amounts are whole piastres. Dates are ISO-8601 **strings** in UTC, not numeric timestamps or JSON Date objects. Response fields below are present and non-null. All listed request fields are required.

## ServiceDictionary — GET /api/v1/services

Root: `object` / Java `Map<String, List<ServiceItem>>`. Keys `electricity`, `water`, `gas` each contain an `array<ServiceItem>`. The root is not an array and has no `data` envelope.

## ServiceItem

| Field | JSON / Java type | Example |
| --- | --- | --- |
| id | integer int32 / int | 1 |
| name | string / String | Cairo Electricity |
| available | boolean / boolean | true |
| iconUrl | string / String (relative image URL) | /icons/electricity.svg |

## InquiryRequest — POST /api/v1/inquiries

| Field | JSON type | Rule |
| --- | --- | --- |
| serviceId | integer int32 | Positive; send `1`, not `"1"` |
| subscriberNumber | string | Exactly 10 ASCII digits |

Extra inquiry fields are ignored and cannot override pricing. Payment requests, in contrast, reject extra fields.

## BillQuote — inquiry response

| Field | JSON / Java type | Example or meaning |
| --- | --- | --- |
| quoteId | string / String (UUID) | Opaque quote identifier |
| serviceId | integer int32 / int | 1 |
| serviceName | string / String | Cairo Electricity |
| subscriberNumber | string / String | 1024750891 |
| customerName | string / String | Mina A. |
| billMonth | string / String (YYYY-MM) | 2026-08 |
| currency | string / String | EGP |
| amountDuePiastres | integer int64 / long | 24750 |
| serviceFeePiastres | integer int64 / long | 500 |
| vatPiastres | integer int64 / long | 70 |
| totalPiastres | integer int64 / long | 25320 |
| createdAt | string date-time / Instant | UTC ISO-8601, may include fractional seconds |
| expiresAt | string date-time / Instant | createdAt + 300 seconds |

## PaymentRequest — POST /api/v1/payments

| Field | JSON type | Rule |
| --- | --- | --- |
| quoteId | string UUID | Canonical lowercase UUID returned by inquiry |

`Idempotency-Key` is a required **string HTTP header**, not a body field. Generate a key once per attempt (a UUID works) and reuse it for retries. `Content-Type` is the string header `application/json`.

## PaymentReceipt — payment response and history item

| Field | JSON / Java type | Example or meaning |
| --- | --- | --- |
| paymentId | string / String (UUID) | Opaque successful payment identifier |
| quoteId | string / String (UUID) | Originally paid quote |
| reference | string / String | MP-YYYYMMDD-UUID; copy the full value |
| status | string / String | SUCCESS |
| serviceId | integer int32 / int | 1 |
| serviceName | string / String | Cairo Electricity |
| subscriberNumber | string / String | 1024750891 |
| totalPiastres | integer int64 / long | 25320 |
| currency | string / String | EGP |
| paidAt | string date-time / Instant | Actual server payment time in UTC |

## PaymentHistory — GET /api/v1/payments

Root: `array<PaymentReceipt>` / Java `List<PaymentReceipt>`. Empty history is `[]`; there is no `data` envelope.

## ApiError — documented business/validation failures

| Field | JSON / Java type | Example |
| --- | --- | --- |
| code | string / String | QUOTE_EXPIRED |
| message | string / String | This quote has expired. Check your bill again. |

HTTP status (400/404/409/503) is in the HTTP response, not another field inside this error object. Unknown routes and unsupported HTTP methods/media types may use Spring's default error response; branch on HTTP status and tolerate a missing business `code`.
