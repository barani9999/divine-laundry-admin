# WhatsApp Cloud API setup

The backend supports three official WhatsApp Cloud API delivery flows:

- Invoice image delivery with invoice details, amounts, balance and optional UPI QR.
- Invoice PDF delivery as a document message.
- Payment receipt PDF delivery as a document message.

Each flow uploads media first, then sends the returned media ID in an approved template message.

## Automatic triggers

- Finalising an invoice sends the invoice image automatically after the database transaction commits.
- Invoice PDF delivery is available through the authenticated admin workflow.
- Recording a payment sends a payment receipt PDF through the payment-number deduplicated flow.
- Invoice and payment-number deduplication prevents duplicate successful sends.
- A `FAILED` message can be retried manually by an administrator. There is no automatic retry worker or scheduler.

When provider configuration is incomplete or disabled, the message remains `WAITING_FOR_PROVIDER`. Provider failures are stored as `FAILED` with a safe classification so an administrator can retry manually.

## Meta account requirements

1. Create or use the client's Meta Business account.
2. Add WhatsApp to a Meta app and register the business sending number.
3. Obtain the WhatsApp phone-number ID and a production system-user access token.
4. Select a currently supported Graph API version from the Meta dashboard/documentation.
5. Obtain customer consent/opt-in to receive transactional WhatsApp messages.
6. Create and obtain approval for the image and document utility templates used by this application.

Never paste the access token into chat, frontend JavaScript or Git. Store it only as a server environment variable.

## Required templates

Configure these independently:

- `WHATSAPP_TEMPLATE_NAME` and `WHATSAPP_TEMPLATE_LANGUAGE` for invoice images.
- `WHATSAPP_DOCUMENT_TEMPLATE_NAME` and `WHATSAPP_DOCUMENT_TEMPLATE_LANGUAGE` for invoice PDFs and payment receipt PDFs.

Both templates must be approved by Meta before production use.

### Invoice image template

- Category: **Utility**
- Header: **Image**
- Language: **English** (`en`) or the exact language code approved in Meta
- The body must contain six text parameters in this order: customer name, invoice or receipt number, order number, total amount, paid amount, balance amount.
- Suggested body:

```text
Hello {{1}}, your Divine Laundry invoice {{2}} for order {{3}} is ready.
Total: ₹{{4}}
Paid: ₹{{5}}
Balance: ₹{{6}}
The invoice and payment details are shown in the attached image.
```

### Invoice and receipt document template

- Category: **Utility**
- Header: **Document**
- Language: the exact language code approved in Meta
- The body must contain the same six text parameters and order shown above.

The configured names, languages, header types, parameter count and parameter order must exactly match the approved Meta templates. The application does not verify template approval against Meta.

## Server environment

Copy `backend/.env.example` to a private deployment environment and set:

```text
UPI_ID=verified-business-upi-id
UPI_PAYEE_NAME=Divine Laundry Trichy
WHATSAPP_ENABLED=true
WHATSAPP_GRAPH_BASE_URL=https://graph.facebook.com
WHATSAPP_GRAPH_API_VERSION=current-supported-version
WHATSAPP_PHONE_NUMBER_ID=meta-phone-number-id
WHATSAPP_ACCESS_TOKEN=production-system-user-token
WHATSAPP_APP_SECRET=meta-app-secret
WHATSAPP_WEBHOOK_VERIFY_TOKEN=private-webhook-verification-token
WHATSAPP_TEMPLATE_NAME=laundry_invoice_payment
WHATSAPP_TEMPLATE_LANGUAGE=en
WHATSAPP_DOCUMENT_TEMPLATE_NAME=approved-document-template
WHATSAPP_DOCUMENT_TEMPLATE_LANGUAGE=en
WHATSAPP_PENDING_TIMEOUT=PT15M
```

Keep `WHATSAPP_ENABLED=false` until the phone number, access token, image template, document template, customer opt-in, and UPI configuration have all been verified.

The access token must be stored only in the backend environment or a secret manager. Never place it in source control, frontend code, logs, templates, or screenshots.

## Payment safety and testing

- Confirm that the displayed payee name and UPI ID belong to the laundry business.
- First test the generated QR with a ₹1 test invoice and an approved test recipient.
- Confirm the amount and order reference shown by more than one common UPI app.
- A UPI QR starts a payment but does not prove that payment succeeded. This version still requires the admin to record the received payment. Automatic reconciliation requires a payment-gateway order and signed webhook integration.
- Do not mark an order paid from a QR scan alone.

## Production verification

Use a Meta-approved test recipient and verify both media types:

WhatsApp delivery uses a database-backed claim with a 15-minute stale-`PENDING` recovery window. If the provider accepts a message and the application crashes before saving `SENT`, a later manual retry may send a duplicate after that window. This phase does not implement an outbox or provider reconciliation workflow, so this crash window remains an accepted limitation.

1. Finalise a test invoice and confirm the invoice image reaches the test recipient.
2. Send the invoice PDF and confirm the document template accepts the PDF.
3. Record a payment and confirm the payment receipt PDF reaches the test recipient.
4. Confirm the provider message ID is stored and the message state becomes `SENT`.
5. Confirm `SENT` means Meta accepted the request; it does not prove customer delivery.
6. Confirm failed messages can be retried manually and successful messages are not sent again.

## Public delivery-status webhook

Configure Meta's webhook callback URL as `https://<public-host>/api/webhooks/whatsapp` and subscribe to WhatsApp message status updates. The GET verification challenge uses `WHATSAPP_WEBHOOK_VERIFY_TOKEN`; POST notifications require a valid `X-Hub-Signature-256` signature using `WHATSAPP_APP_SECRET`.

The local app at `http://localhost:8080` is not reachable by Meta. To test real callbacks while developing locally, expose the app through a public HTTPS tunnel and configure that public URL in Meta, or deploy to a public HTTPS host. Until Meta can reach and verify that callback, local HTTP 200 send results only confirm provider acceptance; they do not test delivered, read, or failed status callbacks.

Status events are matched to the stored provider message ID and map to `SENT`, `DELIVERED`, `READ`, or `FAILED`. The application logs a masked provider ID, event status and timestamp, and the mapped internal message/order reference. It does not log the callback body, customer phone, access token, or provider credentials.

`WHATSAPP_PENDING_TIMEOUT` must be a positive ISO-8601 duration. Its default is `PT15M`.

Delivery and read tracking depend on Meta reaching the public HTTPS webhook described above. `DELIVERED` must not be inferred from the send API's HTTP 200 response.

## Delivery claim limitation

## Official Meta references

- [WhatsApp Business Platform get started](https://developers.facebook.com/documentation/business-messaging/whatsapp/get-started)
- [Media upload and management](https://developers.facebook.com/documentation/business-messaging/whatsapp/business-phone-numbers/media)
- [Template fundamentals](https://developers.facebook.com/documentation/business-messaging/whatsapp/templates/overview)
- [Meta WhatsApp API example repository](https://github.com/fbsamples/whatsapp-api-examples)
