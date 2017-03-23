# API Specification
## POST /v1/tasks/expired-charges-sweep

This starts a task to expire the charges with a default window of 1 Hr. The default value can be overridden by setting an environment variable CHARGE_EXPIRY_WINDOW_SECONDS in seconds. Response of the call will tell you how many charges were successfully expired and how many of them failed for some reason.

### Request example

```
POST /v1/tasks/expired-charges-sweep
```

### Response example

```
200 OK
Content-Type: application/json
{
"expiry-success": 0
"expiry-failed": 0
}
```
-----------------------------------------------------------------------------------------------------------

## POST /v1/api/accounts

This endpoint creates a new account in this connector.

### Request example

```
POST /v1/api/accounts
Content-Type: application/json

{
    "payment_provider": "sandbox",
    "description": "This is an account for the GOV.UK Pay team",
    "analytics_id": "PAY-GA-123",
    "type: "test"
}
```

#### Request body description

| Field                    | required | Description                                                      | Supported Values     |
| ------------------------ |:--------:| ---------------------------------------------------------------- |----------------------|
| `payment_provider`       | X        | The payment provider for which this account is created.          | sandbox, worldpay, smartpay |
| `type`                   |          | Account type for this provider.                                  | test, live (defaults to test if missing) |
| `description`            |          | Some useful non-ambiguiuos description about the gateway account | |
| `analytics_id`           |          | Google Analytics (GA) unique ID for the GOV.UK Pay platform      | |

### Response example

```
201 OK
Content-Type: application/json
Location: http://connector.service/v1/api/accounts/1

{
    "gateway_account_id": "1",
    "type": "live",
    "description": "This is an account for the GOV.UK Pay team",
    "analytics_id": "PAY-GA-123",
    "links": [{
        "href": "http://connector.service/v1/api/accounts/1",
        "rel" : "self",
        "method" : "GET"
    }]
}
```

#### Response field description

| Field                    | always present | Description                                   |
| ------------------------ |:--------------:| --------------------------------------------- |
| `gateway_account_id`     | X              | The account Id created by the connector       |
| `type`                   | X              | Account type for this provider (test/live)    |
| `description`            | X              | Some useful non-ambiguiuos description about the gateway account |
| `analytics_id`           | X              | Google Analytics (GA) unique ID for the GOV.UK Pay platform      |
| `links`                  | X              | HTTP self link containing resource reference to the account.     |

-----------------------------------------------------------------------------------------------------------

## GET /v1/api/accounts/{accountsId}

Retrieves an existing account without the provider credentials.

### Request example

```
GET /v1/api/accounts/1
```


### Response example

```
200 OK
Content-Type: application/json
{
    "payment_provider": "sandbox",
    "gateway_account_id": "1",
    "type": "live",
    "description": "Sample Service",
    "analytics_id": "some identifier",
    "service_name": "service name"
}
```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `gateway_account_id`      | X | The account Id        |
| `type`                    | X | Account type for this provider (test/live)|
| `payment_provider`        | X | The payment provider for which this account is created.       |
| `description`             | X | An internal description to identify the gateway account. The default value is `null`.      |
| `analytics_id`            | X | An identifier used to identify the service in Google Analytics. The default value is `null`.      |
| `service_name`            |   | The service name that is saved for this account, present if not empty.      |

---------------------------------------------------------------------------------------------------------------
## GET /v1/api/accounts

Retrieves a collection of all the accounts

### Request example

```
GET /v1/api/accounts
```

### Response example

```
200 OK
Content-Type: application/json
{
  "accounts": [
    {
      "type": "test",
      "description": "a description",
      "gateway_account_id": 100,
      "payment_provider": "sandbox",
      "service_name": "service_name",
      "analytics_id": "an analytics id",
      "_links": {
        "self": {
          "href": "http://connector.service/v1/api/accounts/100"
        }
      }
    },
    {
      "type": "live",
      "description": "a description",
      "gateway_account_id": 200,
      "payment_provider": "sandbox",
      "service_name": "service_name",
      "analytics_id": "an analytics id",
      "_links": {
        "self": {
          "href": "http://connector.service/v1/api/accounts/200"
        }
      }
    },
    {
      "type": "test",
      "description": "a description",
      "gateway_account_id": 400,
      "payment_provider": "worldpay",
      "analytics_id": "an analytics id",
      "_links": {
        "self": {
          "href": "http://connector.service/v1/api/accounts/400"
        }
      }
    }
  ]
}
```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `accounts`                | X | The collection of accounts        |
| `gateway_account_id`      | X | The account Id        |
| `type`                    | X | Account type for this provider (test/live)|
| `payment_provider`        | X | The payment provider for which this account is created.       |
| `description`             | X | An internal description to identify the gateway account. The default value is `null`.      |
| `analytics_id`            | X | An identifier used to identify the service in Google Analytics. The default value is `null`.      |
| `service_name`            |   | The service name that is saved for this account, present if not empty.      |
| `_links.self`             | X | A self link to get this account resource by account-id.      |
------------------------------------------------------------------------------------------------

## GET /v1/api/accounts/{accountId}/charges/{chargeId}

Find a charge by ID for a given account. It does a check to see if the charge belongs to the given account. This endpoint is very similar to [```/v1/frontend/charges/{chargeId}```](#get-v1frontendchargeschargeid)
except it translates the status of the charge to an external representation (see [Payment States](https://sites.google.com/a/digital.cabinet-office.gov.uk/payments-platform/payment-states---evolving-diagram)).

### Request example

```
GET /v1/api/accounts/2131/charges/1
```

### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{
    "charge_id": "1",
    "description": "Breathing licence",
    "reference": "Ref-1234",
    "amount": 5000,
    "payment_provider": "sandbox",
    "gateway_account_id": "10",
    "gateway_transaction_id": "DFG98-FG8J-R78HJ-8JUG9",
    "state": {
      "status": "created",
      "finished": false
    },
    "card_brand": "Visa",
    "card_details": {
            "billing_address": {
                "city": "TEST",
                "country": "GB",
                "line1": "TEST",
                "line2": "TEST - DO NOT PROCESS",
                "postcode": "SE1 3UZ"
            },
            "card_brand": "Visa",
            "cardholder_name": "TEST",
            "expiry_date": "12/19",
            "last_digits_card_number": "4242"
    },
    "return_url": "http://example.service/return_from_payments",
    "refund_summary": {
            "amount_available": 5000,
            "amount_submitted": 0,
            "status": "available"
    },
    "settlement_summary": {
            "capture_submit_time": null,
            "captured_date": null
    },
    "links": [
        {
            "rel": "self",
            "method": "GET",
            "href": "http://connector.service/v1/api/charges/1"
        },
        {
            "rel": "next_url",
            "method": "GET",
            "href": "http://frontend/charges/1?chargeTokenId=82347"
        },
        {
            "href": "https://connector.service/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds",
            "method": "GET",
            "rel": "refunds"
        }
    ],
}
```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `charge_id`                 | X | The unique identifier for this charge       |
| `amount`                 | X | The amount of this charge       |
| `description`            | X | The payment description       
| `reference`              | X | There reference issued by the government service for this payment       |
| `gateway_account_id`     | X | The ID of the gateway account to use with this charge       |
| `gateway_transaction_id` | X | The gateway transaction reference associated to this charge       |
| `status`                 | X | The current external status of the charge       |
| `card_brand`             |   | The brand label of the card                 |
| `card_details.card_brand`      |           | The card brand used for this payment                    |
| `card_details.cardholder_name` |           | The card card holder name of this payment               |
| `card_details.expiry_date`     |           | The expiry date of this card                            |
| `card_details.last_digits_card_number`  |  | The last 4 digits of this card                          |
| `card_details.billing_address.line1`    |  | The line 1 of the billing address                       |
| `card_details.billing_address.line2`    |  | The line 2 of the billing address                       |
| `card_details.billing_address.postcode` |  | The postcode of the billing address                     |
| `card_details.billing_address.city`     |  | The city of the billing address                         |
| `card_details.billing_address.country`  |  | The country of the billing address                      |
| `payment_provider`       | X | The gateway provider used by this transaction                         |
| `return_url`             | X | The url to return the user to after the payment process has completed.|
| `links`                  | X | Array of relevant resource references related to this charge|
| `refund_summary`         | X | Provides a refund summary of the total refund amount still available and how much has already been refunded, plus a refund status|
| `settlement_summary`         | X | Provides a settlement summary of the charge containing date and time of capture, if present.|

-----------------------------------------------------------------------------------------------------------

## POST /v1/api/accounts/{accountId}/charges

This endpoint creates a new charge for the given account.

### Request example

```
POST /v1/api/accounts/3121/charges
Content-Type: application/json

{
    "amount": 5000,
    "gateway_account_id": "10",
    "return_url": "http://example.service/return_from_payments"
}
```

#### Request body description

| Field                    | required | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `amount`                 | X | The amount (in minor units) of the charge       |
| `description`            | X | The payment description       |
| `reference`              | X | There reference issued by the government service for this payment       |
| `gateway_account_id`     | X | The gateway account to use for this charge |
| `return_url`             | X | The url to return the user to after the payment process has completed.|

### Response example

```
201 Created
Content-Type: application/json
Location: http://connector.service/v1/api/charges/1

{
    "charge_id": "1",
    "description": "Breathing licence",
    "reference": "Ref-1234",
    "links": [{
            "href": "http://connector.service/v1/api/charges/1",
            "rel" : "self",
            "method" : "GET"
        }, 
        {
            "href": "http://frontend/charges/1",
            "rel" : "next_url",
            "method" : "GET"
        }
      ]
}
```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `charge_id`                 | X | The unique identifier for this charge       |

-----------------------------------------------------------------------------------------------------------

## GET /v1/api/accounts/{accountId}/charges

This endpoint searches for transactions for the given account id and specified filters in query params and responds with JSON or CSV according to the Accept header

### Request example for JSON response

```
GET /v1/api/accounts/3121/charges
Accept application/json

```

#### Query Parameters description

| Field                    | required | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `email`                  | X        | The end-user email used in the charge.    |
| `reference`              | X        | There (partial or full) reference issued by the government service for this payment. |
| `status`                 | X        | The transaction status |
| `from_date`              | X        | The initial date to search transactions |
| `to_date`                | X        | The end date we should search transactions|

### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{
    "results": [{     
        "charge_id": "1",
        "description": "Breathing licence",
        "reference": "Ref-1234",
        "amount": 5000,
        "gateway_account_id": "10",
        "gateway_transaction_id": "DFG98-FG8J-R78HJ-8JUG9",
        "status": "CAPTURED",
        "card_brand": "Visa",
        "card_details": {
            "billing_address": {
                "city": "TEST",
                "country": "GB",
                "line1": "TEST",
                "line2": "TEST - DO NOT PROCESS",
                "postcode": "SE1 3UZ"
            },
            "card_brand": "Visa",
            "cardholder_name": "TEST",
            "expiry_date": "12/19",
            "last_digits_card_number": "4242"
        },
        "payment_provider": "sandbox",
        "return_url": "http://example.service/return_from_payments",
        "links": [
            {
                "href": "https://connector.pymnt.localdomain/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn",
                "method": "GET",
                "rel": "self"
            },
            {
                "href": "https://connector.pymnt.localdomain/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds",
                "method": "GET",
                "rel": "refunds"
            }
        ],
        "refund_summary": {
            "amount_available": 5000,
            "amount_submitted": 0,
            "status": "available"
        },
        "settlement_summary": {
                "capture_submit_time": "2016-01-02T15:02:00Z",
                "captured_date": "2016-01-02"
        }
     }]
}
```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `results`                | X | List of payments       |
| `charge_id`              | X | The unique identifier for this charge       |
| `amount`                 | X | The amount of this charge       |
| `description`            | X | The payment description       
| `reference`              | X | There reference issued by the government service for this payment       |
| `gateway_account_id`     | X | The ID of the gateway account to use with this charge       |
| `gateway_transaction_id` | X | The gateway transaction reference associated to this charge       |
| `status`                 | X | The current external status of the charge       |
| `card_brand`             |   | The brand label of the card                 |
| `card_details.card_brand`      |           | The card brand used for this payment                    |
| `card_details.cardholder_name` |           | The card card holder name of this payment               |
| `card_details.expiry_date`     |           | The expiry date of this card                            |
| `card_details.last_digits_card_number`  |  | The last 4 digits of this card                          |
| `card_details.billing_address.line1`    |  | The line 1 of the billing address                       |
| `card_details.billing_address.line2`    |  | The line 2 of the billing address                       |
| `card_details.billing_address.postcode` |  | The postcode of the billing address                     |
| `card_details.billing_address.city`     |  | The city of the billing address                         |
| `card_details.billing_address.country`  |  | The country of the billing address                      |
| `payment_provider`       | X | The gateway provider used by this transaction                         |
| `return_url`             | X | The url to return the user to after the payment process has completed.|
| `refund_summary`         | X | Provides a refund summary of the total refund amount still available and how much has already been refunded, plus a refund status|
| `settlement_summary`         | X | Provides a settlement summary of the charge containing date and time of capture, if present.|

-----------------------------------------------------------------------------------------------------------


### Request example for CSV response

```
GET /v1/api/accounts/3121/charges
Accept: text/csv

```

#### Query Parameters description

| Field                    | required | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `reference`              | X | There (partial or full) reference issued by the government service for this payment. |
| `status`                 | X | The transaction status |
| `from_date`               | X | The initial date to search transactions |
| `to_date`                 | X | The end date we should search transactions|

### Response example

```
HTTP/1.1 200 OK
Content-Type: text/csv

Service Payment Reference,Amount,Status,Gateway Transaction ID,GOV.UK Pay ID,Date Created
ref2,500.00,IN PROGRESS,DFG98-FG8J-R78HJ-8JUG9,1,05/02/2016 14:17:00

```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `GOV.UK Pay ID`              | X | The unique identifier for this charge       |
| `amount`                 | X | The amount of this charge       |
| `Service Payment Reference`              | X | There reference issued by the government service for this payment       |
| `Service Payment Reference` | X | The gateway transaction reference associated to this charge       |
| `status`                 | X | The current external status of the charge       |
| `Date Created`                 | X | Date the charge was created       |

-----------------------------------------------------------------------------------------------------------

## GET /v1/api/accounts/{accountId}/charges/{chargeId}/events

This endpoint retrieves the transaction history for a given `chargeId` associated to account `accountId`

### Request example 

```
GET /v1/api/accounts/123/charges/4321/events

```

### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{
 "charge_id": 4321,
  "events":[
    {
        "state": {
          "status": "created",
          "finished": false
        }
        "updated": "23-12-2015 13:21:05"
    },
     {
        "state": {
          "status": "cancelled",
          "finished": true,
          "message": "Payment was cancelled by service",
          "code": "P0040"
        }
        "updated": "23-12-2015 13:23:12"
     }
  ]
}
```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `charge_id`                 | X | The unique identifier for this charge       |
| `events`                    | X | An array of events associated to this charge          |
|  `events[0].status`         | X | The externally visible status of this event          |
|  `events[0].updated`        | X | The date and time of the event                      |


----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

## GET /v1/api/accounts/{accountId}/email-notification

This endpoint retrieves an email notification template body for account `accountId`

### Request example 

```
GET /v1/api/accounts/123/email-notification

```

### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{
   "id":1,
   "version":1,
   "template_body":"bla bla"
}
```

#### Response field description

| Field                    | always present | Description                         |
| ------------------------ |:--------:| ----------------------------------------- |
| `template_body`          | X        | The service template body paragraph       |


-----------------------------------------------------------------------------------------------------------

## POST /v1/api/accounts/{accountId}/email-notification

Updates an email notification template body for account `accountId`

### Request example

```
POST /v1/api/accounts/123/email-notification
Content-Type: application/json
{
    "custom-email-text": "lorem ipsum"
}
```

### Response when update is successful

```
200 {}
```

### Response when update unsuccessful due to invalid account

```
HTTP/1.1 404 Not Found
Content-Type: application/json
Content-Length: 72

{
    "message": "The gateway account id '111' does not exist"
}
```

### Response if mandatory fields are missing

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "message": "The following fields are missing: [custom-email-text]"
}
```
-----------------------------------------------------------------------------------------------------------

## PATCH /v1/api/accounts/{accountId}/email-notification

Enables/disables email notifications for account `accountId`

### Request example

```
PATCH /v1/api/accounts/123/email-notification
Content-Type: application/json
{
    "op":"replace", "path":"enabled", "value": true
}
```

```
PATCH /v1/api/accounts/123/email-notification
Content-Type: application/json
{
    "op":"replace", "path":"enabled", "value": false
}
```

### Response when update is successful

```
200 {}
```

### Response when update unsuccessful due to invalid account

```
HTTP/1.1 404 Not Found
Content-Type: application/json
Content-Length: 72

{
    "message": "The gateway account id '111' does not exist"
}
```

### Response if mandatory fields are missing

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "message": "The following fields are missing: [enabled]"
}
```
-----------------------------------------------------------------------------------------------------------

## PATCH /v1/api/accounts/{accountId}/description-analytics-id

Allows editing description and/or analyticsId for the given account `accountId`

### Request example

```
PATCH /v1/api/accounts/123/description-analytics-id
Content-Type: application/json
{
    "description":"desc", "analytics_id":"id"
}
```

### Response when update is successful

```
200 {}
```

### Response if all fields are missing

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "message": "The following fields are missing: [description, analytics_id]"
}
```
-----------------------------------------------------------------------------------------------------------

## PUT /v1/frontend/charges/{chargeId}/status

This endpoint updates the status of the charge through this connector.

### Request example

```
PUT /v1/frontend/charges/{chargeId}/status
Content-Type: application/json

{
    "new_status": "CREATED"
}
```

### Response example

```
204 Done
```
-----------------------------------------------------------------------------------------------------------

## POST /v1/api/accounts/{accountId}/charges/{chargeId}/cancel

This endpoint cancels a charge.

### Request example

```
POST /v1/api/accounts/111222333/charges/123456/cancel
```

### Response when cancellation successful

```
204 No Content
```

### Response when cancellation unsuccessful due to invalid state

```
HTTP/1.1 400 Bad Request
Content-Type: application/json
Content-Length: 72

{
    "message": "Cannot cancel a charge with status AUTHORIZATION REJECTED."
}
```

### Response when the matching payment does not exist for the given account (or even if account does not exist)

```
HTTP/1.1 404 Not Found
Content-Type: application/json
Content-Length: 52

{
    "message": "Charge with id [123456] not found."
}
```

-----------------------------------------------------------------------------------------------------------

## GET /v1/frontend/accounts/{accountId}

Retrieves an existing account together with the provider credentials.

### Request example

```
GET /v1/frontend/accounts/111222333
```

### Response example

```
200 OK
Content-Type: application/json
{
    "payment_provider": "sandbox",
    "gateway_account_id": "111222333",
    "description": "Sample Service",
    "analytics_id": "some identifier",
    "credentials: {
      "username:" "Username"
    }
}
```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `gateway_account_id`     | X | The account Id        |
| `payment_provider`       | X | The payment provider for which this account is created.       |
| `credentials`            | X | The payment provider credentials. Password is not returned. The default value is the empty JSON document {}      |
| `description`            | X | An internal description to identify the gateway account. The default value is `null`.      |
| `analytics_id`           | X | An identifier used to identify the service in Google Analytics. The default value is `null`.      |
-----------------------------------------------------------------------------------------------------------

## PUT /v1/frontend/accounts/{accountId}
   

Update gateway credentials associated with this account

### Request example

```
PUT /v1/frontend/accounts/111222333
Content-Type: application/json

{
    "username": "a-user-name",
    "password": "a-password",
    "merchant_id": "a-merchant-id"
}
```

#### Request body description

| Field                    | Description                               |
| ------------------------ | ----------------------------------------- |
| `username`               | The payment provider's username for this gateway account    |
| `password`               | The payment provider's password for this gateway account    |
| `merchant_id`            | The payment provider's merchant id for this gateway account (if applicable)    |

Note: The fields in the JSON document vary depending on the payment provider assigned to the given account. For instance Worldpay requires username, password and merchant_id, whereas Smartpay only requires username and password.

### Response for a successful update

```
200 OK
```

### Response when account id is not found

```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
    "message": "The gateway account id '111222333' does not exist"
}
```

### Response if mandatory fields are missing

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "message": "The following fields are missing: [username]"
}
```

-----------------------------------------------------------------------------------------------------------

## GET /v1/frontend/charges/{chargeId}

Find a charge by ID.

### Request example

```
GET /v1/frontend/charges/1

```

### Response example

```
200 OK
Content-Type: application/json

{
    "amount": 5000,
    "status": "CREATED",
    "card_brand": "Visa",
    "links": [{
                "href": "http://connector.service/v1/frontend/charges/1",
                "rel" : "self",
                "method" : "GET"
            },
            {
                "rel": "cardAuth",
                "method": "POST",
                "href": "http://connector.service/v1/frontend/charges/1/cards"
            },
            {
                "rel": "cardCapture",
                "method": "POST",
                "href": "http://connector.service/v1/frontend/charges/1/capture"
            }],
}
```

#### Response field description

| Field                    | always present | Description                         |
| ------------------------ |:--------:| ----------------------------------------- |
| `amount`                 | X | The amount (in minor units) of the charge       |
| `status`                 | X | The current (internal) status of the charge |
| `card_brand`              |   | The brand label of the card                 |

-----------------------------------------------------------------------------------------------------------

## POST /v1/frontend/charges/{chargeId}/cards

This endpoint takes card details and authorises them for the specified charge.

### Request example

```
POST /v1/frontend/charges/1/cards
Content-Type: application/json

{
    "card_number": "4242424242424242",
    "card_brand": "visa",
    "cvc": "123",
    "expiry_date": "11/17"
    "address" : 
    {
        "line1": "The street",
        "city": "The city",
        "postcode": "W1 2CP",
        "country": "GB",
    }
}
```

#### Request body description

| Field                    | required | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `card_number`                 | X | The card number (16 digits)       |
| `card_brand`                 | X | The card brand                     |
| `cvc`     | X | The cvc of the card (3 digits) |
| `expiry_date`     | X | The expiry date (no validation other than format being mm/yy) |
| `address`     | X | The billing address associated to this charge. Mandatory Address fields are `line1, city, postcode, country`. Optional Address fields are `line2` only  |

### Valid card numbers (inspired from Stripe)

| Card Number                          |  Status | Message                        |
| ----------------------------- | ----------------- | ---------------------------------- |
|```4242424242424242```|Auth success|-|
|```5105105105105100```|Auth success|-|
|```4000000000000002```|Auth rejected|This transaction was declined.|
|```4000000000000069```|Auth rejected|The card is expired.|
|```4000000000000127```|Auth rejected|The CVC code is incorrect.|
|```4000000000000119```|System error|This transaction could be not be processed.|

### Response example

#### Authorisation success

```
204 No content
Content-Type: application/json
```
#### Error
```
400 Bad Request
Content-Type: application/json

{
    "message": "This transaction was declined."
}
```
-----------------------------------------------------------------------------------------------------------

## POST /v1/frontend/charges/{chargeId}/capture

This endpoint proceeds to the capture of the card for the specified charge. The charge needs to have been previously authorised for this call to succeed.

### Request example

```
POST /v1/frontend/charges/1/capture
Content-Type: application/json
```

#### The request body is empty


### Response example

#### Authorisation success

```
204 No content
Content-Type: application/json
```
#### Error
```
400 Bad Request
Content-Type: application/json

{
    "message": "Cannot capture a charge with status AUTHORISATION REJECTED."
}
```
-----------------------------------------------------------------------------------------------------------
## GET /v1/frontend/charges?gatewayAccountId={gatewayAccountId}

List all the transactions for a given gateway account sorted by ChargeID

### Request example

```
GET /v1/frontend/charges?gatewayAccountId=1223445
```

### Response for the success path

```
HTTP/1.1 200 OK
Content-Type: application/json

{
    "results": [
        {
            "amount": 500,            
            "charge_id": "10002",
            "gateway_transaction_id": null,
            "status": "AUTHORISATION REJECTED"
        },
        {
            "amount": 100,            
            "charge_id": "10001",
            "gateway_transaction_id": "transaction-id-1",
            "status": "AUTHORISATION SUCCESS"
        }
    ]
}
```

### Response for the failure path

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "message": "invalid gateway account reference f7h4f7hg4"
}
```

#### Request query param description
| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `gatewayAccountId`               | X | Gateway Account Id of which the transactions must be received.    |


#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `result`               | X | JSON Array of which each element represents a transaction row.       |
|                        |   | Element structure:                         |
|                        |   | `amount`: Transaction amount in pence      |
|                        |   | `charge_id`: GDS charge reference          |
|                        |   | `gateway_transaction_id`: payment gateway reference for this charge          |
|                        |   | `status`: Current status of the charge          |

-----------------------------------------------------------------------------------------------------------
## GET /v1/frontend/tokens/{chargeTokenId}/charge

Find a secure redirect token. This is used by the Frontend to determine that the chargeId in the frontend redirect url
is a genuine one and was generated by the Connector.

### Request example

```
GET /v1/frontend/tokens/2344/charge
```

### Response for the success path

```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 1,
  "version": 1,
  "externalId": "noiamn0qr0i6q1o1iab3ott2ne",
  "amount": 2000,
  "status": "CREATED",
  "gatewayTransactionId": null,
  "returnUrl": "https://service-name.gov.uk/transactions/12345",
  "gatewayAccount": {
    "version": 0,
    "credentials": {},
    "gateway_account_id": 111,
    "payment_provider": "sandbox"
  },
  "card_details": {
      "billing_address": {
          "city": "TEST",
          "country": "GB",
          "line1": "TEST",
          "line2": "TEST - DO NOT PROCESS",
          "postcode": "SE1 3UZ"
      },
      "card_brand": "Visa",
      "cardholder_name": "TEST",
      "expiry_date": "12/19",
      "last_digits_card_number": "4242"
  },
  "events": [
    {
      "id": 1,
      "version": 1,
      "status": "CREATED",
      "updated": {
        "monthValue": 4,
        "hour": 14,
        "minute": 23,
        "second": 2,
        "nano": 997000000,
        "year": 2016,
        "month": "APRIL",
        "dayOfMonth": 11,
        "dayOfWeek": "MONDAY",
        "dayOfYear": 102,
        "chronology": {
          "calendarType": "iso8601",
          "id": "ISO"
        }
      }
    }
  ],
  "description": "New passport application",
  "reference": "12345",
  "createdDate": {
    "offset": {
      "totalSeconds": 0,
      "id": "Z",
      "rules": {
        "fixedOffset": true,
        "transitions": [],
        "transitionRules": []
      }
    },
    "zone": {
      "id": "UTC",
      "rules": {
        "fixedOffset": true,
        "transitions": [],
        "transitionRules": []
      }
    },
    "monthValue": 4,
    "hour": 14,
    "minute": 23,
    "second": 2,
    "nano": 997000000,
    "year": 2016,
    "month": "APRIL",
    "dayOfMonth": 11,
    "dayOfWeek": "MONDAY",
    "dayOfYear": 102,
    "chronology": {
      "calendarType": "iso8601",
      "id": "ISO"
    }
  }
}
```

### Response for the failure path

```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
    "message": "Token has expired!"
}
```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `chargeId`               | X | The chargeId corresponding to the secure redirect token       |
| `message`               | X | 'Token has expired!' to indicate that the token is no longer valid       |

-----------------------------------------------------------------------------------------------------------
## DELETE /v1/frontend/tokens/{chargeTokenId}

Delete a secure redirect token. This is done so that the token will expire on its first use.

### Request example

```
GET /v1/frontend/tokens/32344
```

### Response example

```
HTTP/1.1 204 No Content
```
-----------------------------------------------------------------------------------------------------------

## POST /v1/api/notifications/worldpay

This endpoint handles a notification from worldpays Order Notification mechanism as described in the [Order Notifications - Reporting Payment Statuses Guide](http://support.worldpay.com/support/kb/gg/ordernotifications/on0000.html)

### Request example

```
POST /v1/api/notifications/worldpay
Content-Type: text/xml

```
See [src/test/resources/templates/worldpay/notification.xml](src/test/resources/templates/worldpay/notification.xml) for an example notification.

#### Request body description

See [Interpreting Order Notifications > General Structure XML Order Notifications](http://support.worldpay.com/support/kb/gg/ordernotifications/on0000.html)

### Response example

```
200 OK
Content-Type: text/plain

[OK]
```

## POST /v1/api/notifications/smartpay

This endpoint handles a notification from Barclays Smartpay's Notification mechanism as descrbied in the [Barclaycard SmartPay Notifications Guide](http://www.barclaycard.co.uk/business/files/SmartPay_Notifications_Guide.pdf)

Smartpay notification credentials are specific to the accounts. The credentials can be chanegd through selfservice. For the test accounts these credentails are configured when
the test accounts are setup.

### Request example

```
POST /v1/api/notifications/smartpay
Content-Type: application/json
Authorization: Basic YWRtaW46cGFzc3dvcmQ=

See [src/test/resources/templates/smartpay/notification-authorisation.json](src/test/resources/templates/smartpay/notification-authorisation.json) for an example notification.
```

### Response example

```
200 OK
Content-Type: text/plain

[accepted]
```

## POST /v1/api/notifications/sandbox

This endpoint handles a notification from the sandbox.

It is currently complete insecure.

### Request example

```
POST /v1/api/notifications/smartpay
Content-Type: application/json

{
  "transaction_id": "transaction-id-1",
  "status": "AUTHORISATION SUCCESS"
}
```

### Response example

```
200 OK
Content-Type: text/plain

OK
```


## Securing notifications
We try and validate the source of a notification in three ways:
1. Shared provider specific credentials.
    For smartpay, this takes the form of the service setting a set of basic auth credentials in their management console, and sharing them with the connector.
2. Verifying the origin of the notification request.
    This takes place externally to the connector, at the boundary of the system that it sits in, to avoid unverified requests reaching the connector at all.
3. All notification requests into the platform must be https.

The connector only deals with the first consideration.

------------------------------------------------------------------------------------------------
## POST /v1/api/accounts/{accountId}/charges/{chargeId}/refunds

Submits a refund for a given `accountId` and `chargeId`

### Request example

```
POST /v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds
{
    "amount": 25000,
    "refund_amount_available": 30000
}
```

#### Request description


| Field                    | required | Description                                             |
| ------------------------ |:--------:| ------------------------------------------------------- |
| `amount`                 | Yes      | Amount to refund in pence                               |
| `refund_amount_available`| Yes      | Total amount still available before issuing the refund  |

### Refund created response

```
HTTP/1.1 202 Accepted
Content-Type: application/json

{
    "amount":3444,
    "created_date":"2016-10-05T14:15:34.096Z",
    "refund_id":"vijjk08adovg10gfqc46joem2l",
    "status":"success",
    "_links":{
        "self":{"href":"https://connector.pymnt.localdomain/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds/vijjk08adovg10gfqc46joem2l"},
        "payment":{"href":"https://connector.pymnt.localdomain/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn"}
    }
}
```

#### Response fields description

| Field                  | Description                               |
| ---------------------- | ----------------------------------------- |
| `refund_id`            | The ID of the refund created             |
| `amount`               | Amount of refund in pence                |
| `status`               | Current status of the refund             |
| `created_date`         | The creation date for this refund        |
| `_links.self`          | Link to this refund                      |
| `_links.payment`       | Link to the payment this refund relates to|

### POST Charge Refunds response errors

#### Refund not available for charge

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "message": "Charge with id [123123qwe123] not available for refund."
}
```

#### Validation error for amount

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "message": "Validation error for amount. Minimum amount for a refund is 1."
}
```

#### No sufficient amount available for refund

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "message": "Not sufficient amount available for refund"
}
```

#### Refund amount available mismatch

```
HTTP/1.1 412 Precondition Failed
Content-Type: application/json

{
    "message": "Refund Amount Available Mismatch"
}
```

#### Something went wrong error

```
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
    "message": "something went wrong during refund of charge 123123qwe123"
}
```


#### Unknown error

```
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
    "message": "unknown error"
}
```



------------------------------------------------------------------------------------------------
## GET /v1/api/accounts/{accountId}/charges/{chargeId}/refunds

Returns all the refunds associated with a charge.

### Request example

```
GET /v1/api/accounts/1/charges/asdwa32wd23442rwe24/refunds
```


### Charge refunds response

```
HTTP/1.1 200 OK
Content-Type: application/json
{
    "_embedded": {
        "refunds": [
            {
                "_links": {
                    "payment": {
                        "href": "https://connector.pymnt.localdomain/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn"
                    },
                    "self": {
                        "href": "https://connector.pymnt.localdomain/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds/vijjk08adovg10gfqc46joem2l"
                    }
                },
                "amount": 3444,
                "created_date": "2016-10-05T14:15:34.096Z",
                "refund_id": "vijjk08adovg10gfqc46joem2l",
                "status": "success"
            }
        ]
    },
    "_links": {
        "payment": {
            "href": "https://connector.pymnt.localdomain/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn"
        },
        "self": {
            "href": "https://connector.pymnt.localdomain/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds"
        }
    },
    "payment_id": "uqu4s24383qkod35rsb06gv3cn"
}
```

#### Response field description

| Field                  | Description                               |
| ---------------------- | ----------------------------------------- |
| `payment_id`           | The ID of the created payment             |
| `_embedded.refunds.payment_id`               | The ID of this refund                    |
| `_embedded.refunds.amount`          | The amount of refund                       |
| `_embedded.refunds.status`               | Current status of the refund (submitted/success)             |
| `_embedded.refunds.created_date`           | Date when the refund was created    |
| `_links.self`          | Link to this refund                      |
| `_links.payment`       | Link to the payment this refund relates to|

### GET Charge Refunds response errors

#### Payment not found

```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
    "code" : "P0800"
    "description": "Not found"
}
```                                            
------------------------------------------------------------------------------------------------
## GET /v1/api/accounts/{accountId}/charges/{chargeId}/refunds/{refundId}

Retrieves a refund for a given `accountId` and `chargeId`.

### Request example

```
GET /v1/api/accounts/1/charges/asdwa32wd23442rwe24/refunds/123
```


### Refund by Id response

```
HTTP/1.1 200 OK
Content-Type: application/json
{
    "_links": {
            "payment": {
                "href": "https://connector.pymnt.localdomain/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn"
            },
            "self": {
                "href": "https://connector.pymnt.localdomain/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn/refunds/vijjk08adovg10gfqc46joem2l"
            }
        },
    "amount": 3444,
    "created_date": "2016-10-05T14:15:34.096Z",
    "refund_id": "vijjk08adovg10gfqc46joem2l",
    "status": "success"
}
```

#### Response field description

| Field                  | Description                               |
| ---------------------- | ----------------------------------------- |
| `refund_id`           | The ID of the created payment             |
| `amount`          | The amount of refund                       |
| `status`               | Current status of the refund (submitted/success)             |
| `created_date`           | Date when the refund was created    |
| `_links.self`          | Link to this refund                      |
| `_links.payment`       | Link to the payment this refund relates to|

### GET Refund response errors

#### Charge not found

```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
    "message" : "Charge with id [1] not found."
}
```   

#### Refund not found

```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
    "message" : "Refund with id [123] not found."
}
```
