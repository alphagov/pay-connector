# API Specification
## POST /v1/tasks/expired-charges-sweep

This starts a task to expire the charges with a default window of 90 minutes. The default value can be overridden by setting an environment variable CHARGE_EXPIRY_WINDOW_SECONDS in seconds. Response of the call will tell you how many charges were successfully expired and how many of them failed for some reason.
This endpoint also expires charges in AWAITING_CAPTURE_REQUEST status. The default window is 120 hours. It can be overriden by setting an environment variable AWAITING_DELAY_CAPTURE_EXPIRY_WINDOW in seconds.

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

## POST /v1/tasks/emitted-events-sweep

During the state transition event connector puts an event in an in-memory queue (and database) which is then picked up by
the background process to emit the event to SQS.
If the process is interrupted there is a database record which indicates that the event has been
put in an in-memory queue, but not yet emitted to the SQS.

This task retrieves all the records that haven't been fully processed, for each event it invokes the backfill process and
marks the event as processed.

The default age of the non-emitted event is at least 30 minutes. This value can be controlled with
`NOT_EMITTED_EVENT_MAX_AGE_IN_SECONDS` environment variable. 

### Request example

POST `/v1/tasks/emitted-events-sweep`
```
curl -v -XPOST '127.0.0.1:9300/v1/tasks/emitted-events-sweep'
```

### Response example

```
*   Trying 127.0.0.1...
* TCP_NODELAY set
* Connected to 127.0.0.1 (127.0.0.1) port 9300 (#0)
> POST /v1/tasks/emitted-events-sweep HTTP/1.1
> Host: 127.0.0.1:9300
> User-Agent: curl/7.54.0
> Accept: */*
> 
< HTTP/1.1 200 OK
< Date: Wed, 25 Sep 2019 08:15:48 GMT
< Content-Length: 0
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
    "type": "test"
}
```

#### Request body description

| Field                    | required | Description                                                      | Supported Values     |
| ------------------------ |:--------:| ---------------------------------------------------------------- |----------------------|
| `payment_provider`       | X        | The payment provider for which this account is created.          | sandbox, worldpay, smartpay |
| `type`                   |          | Account type for this provider.                                  | test, live (defaults to test if missing) |
| `description`            |          | Some useful non-ambiguiuos description about the gateway account | |
| `analytics_id`           |          | Google Analytics (GA) unique ID for the GOV.UK Pay platform      | |
| `requires_3ds`           |          | Set to 'true' to enable 3DS for this account                     | true, false (default) |

### Response example

```
201 OK
Content-Type: application/json
Location: https://connector.example.com/v1/api/accounts/1

{
    "gateway_account_id": "1",
    "type": "live",
    "description": "This is an account for the GOV.UK Pay team",
    "analytics_id": "PAY-GA-123",
    "requires_3ds": "false"
    "links": [{
        "href": "https://connector.example.com/v1/api/accounts/1",
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
| `requires_3ds`           | X              | Indicates if 3DS is enabled or disabled for the account |
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
    "service_name": "service name",
    "corporate_credit_card_surcharge_amount": 250,
    "corporate_debit_card_surcharge_amount": 50
    "corporate_prepaid_credit_card_surcharge_amount": 250,
    "corporate_prepaid_debit_card_surcharge_amount": 50,
    "allow_apple_pay": false,
    "allow_google_pay": false,
    "allow_zero_amount": false,
    "email_collection_mode": "MANDATORY",
    "toggle_3ds": false,
    "email_notifications": {
        "REFUND_ISSUED": {
            "version": 1,
            "enabled": true,
            "template_body": null
        },
        "PAYMENT_CONFIRMED": {
            "version": 1,
            "enabled": true,
            "template_body": null
        }
    }
}
```

#### Response field description

| Field                                            | always present | Description                                                                                  |
|--------------------------------------------------|----------------|----------------------------------------------------------------------------------------------|
| `gateway_account_id`                             | X              | The account Id                                                                               |
| `type`                                           | X              | Account type for this provider (test/live)                                                   |
| `payment_provider`                               | X              | The payment provider for which this account is created.                                      |
| `description`                                    | X              | An internal description to identify the gateway account. The default value is `null`.        |
| `analytics_id`                                   | X              | An identifier used to identify the service in Google Analytics. The default value is `null`. |
| `service_name`                                   |                | The service name that is saved for this account, present if not empty.                       |
| `corporate_credit_card_surcharge_amount`         | X              | A corporate credit card surcharge amount in pence. The default value is `0`.                 |
| `corporate_debit_card_surcharge_amount`          | X              | A corporate debit card surcharge amount in pence. The default value is `0`.                  |
| `corporate_prepaid_credit_card_surcharge_amount` | X              | A corporate prepaid credit card surcharge amount in pence. The default value is `0`.         |
| `corporate_prepaid_debit_card_surcharge_amount`  | X              | A corporate prepaid debit card surcharge amount in pence. The default value is `0`.          |
| `allow_apple_pay`                                | X              | Whether apple pay is enabled. The default value is `false`.                                  |
| `allow_google_pay`                               | X              | Whether google pay is enabled. The default value is `false`.                                 |
| `allow_zero_amount`                              | X              | Whether the account supports charges with a zero amount. The default value is `false`.       |
| `email_collection_mode`                          | X              | Whether email address is required from paying users. Can be `MANDATORY`, `OPTIONAL` or `OFF` |
| `toggle_3ds`                                     | X              | Whether 3DS is enabled. The default value is `false`.                                        |
| `email_notifications`                            | X              | The settings for the different emails that are sent out                                      |

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
      "corporate_credit_card_surcharge_amount": 0,
      "corporate_debit_card_surcharge_amount": 0,
      "corporate_prepaid_credit_card_surcharge_amount": 0,
      "corporate_prepaid_debit_card_surcharge_amount": 0,
      "allow_apple_pay": false,
      "allow_google_pay": false,
      "allow_zero_amount": false,
      "email_collection_mode": "MANDATORY",
      "toggle_3ds": false,
      "email_notifications": {
          "REFUND_ISSUED": {
              "version": 1,
              "enabled": true,
              "template_body": null
          },
          "PAYMENT_CONFIRMED": {
              "version": 1,
              "enabled": true,
              "template_body": null
          }
      },
      "_links": {
        "self": {
          "href": "https://connector.example.com/v1/api/accounts/100"
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
      "corporate_credit_card_surcharge_amount": 250,
      "corporate_debit_card_surcharge_amount": 0,
      "corporate_prepaid_credit_card_surcharge_amount": 250,
      "corporate_prepaid_debit_card_surcharge_amount": 0,
      "allow_apple_pay": false,
      "allow_google_pay": false,
      "allow_zero_amount": false,
      "email_collection_mode": "MANDATORY",
      "toggle_3ds": false,
      "email_notifications": {
          "REFUND_ISSUED": {
              "version": 1,
              "enabled": true,
              "template_body": null
          },
          "PAYMENT_CONFIRMED": {
              "version": 1,
              "enabled": true,
              "template_body": null
          }
      },
      "_links": {
        "self": {
          "href": "https://connector.example.com/v1/api/accounts/200"
        }
      }
    },
    {
      "type": "test",
      "description": "a description",
      "gateway_account_id": 400,
      "payment_provider": "worldpay",
      "analytics_id": "an analytics id",
      "corporate_credit_card_surcharge_amount": 0,
      "corporate_debit_card_surcharge_amount": 0,
      "corporate_prepaid_credit_card_surcharge_amount": 0,
      "corporate_prepaid_debit_card_surcharge_amount": 0,
      "allow_apple_pay": false,
      "allow_google_pay": false,
      "allow_zero_amount": false,
      "email_collection_mode": "MANDATORY",
      "toggle_3ds": false,
      "email_notifications": {
          "REFUND_ISSUED": {
              "version": 1,
              "enabled": true,
              "template_body": null
          },
          "PAYMENT_CONFIRMED": {
              "version": 1,
              "enabled": true,
              "template_body": null
          }
      },
      "_links": {
        "self": {
          "href": "https://connector.example.com/v1/api/accounts/400"
        }
      }
    }
  ]
}
```

#### Response field description

| Field                                            | always present | Description                                                                                  |
|--------------------------------------------------|----------------|----------------------------------------------------------------------------------------------|
| `accounts`                                       | X              | The collection of accounts.                                                                  |
| `gateway_account_id`                             | X              | The account Id.                                                                              |
| `type`                                           | X              | Account type for this provider (test/live).                                                  |
| `payment_provider`                               | X              | The payment provider for which this account is created.                                      |
| `description`                                    | X              | An internal description to identify the gateway account. The default value is `null`.        |
| `analytics_id`                                   | X              | An identifier used to identify the service in Google Analytics. The default value is `null`. |
| `service_name`                                   |                | The service name that is saved for this account, present if not empty.                       |
| `corporate_credit_card_surcharge_amount`         | X              | A corporate credit card surcharge amount in pence. The default value is `0`.                 |
| `corporate_debit_card_surcharge_amount`          | X              | A corporate debit card surcharge amount in pence. The default value is `0`.                  |
| `corporate_prepaid_credit_card_surcharge_amount` | X              | A corporate prepaid credit card surcharge amount in pence. The default value is `0`.         |
| `corporate_prepaid_debit_card_surcharge_amount`  | X              | A corporate prepaid debit card surcharge amount in pence. The default value is `0`.          |
| `allow_apple_pay`                                | X              | Whether apple pay is enabled. The default value is `false`.                                  |
| `allow_google_pay`                               | X              | Whether google pay is enabled. The default value is `false`.                                 |
| `allow_zero_amount`                              | X              | Whether the account supports charges with a zero amount. The default value is `false`.       |
| `email_collection_mode`                          | X              | Whether email address is required from paying users. Can be `MANDATORY`, `OPTIONAL` or `OFF` |
| `toggle_3ds`                                     | X              | Whether 3DS is enabled. The default value is `false`.                                        |
| `email_notifications`                            | X              | The settings for the different emails that are sent out                                      |
| `_links.self`                                    | X              | A self link to get this account resource by account-id.                                      |


---------------------------------------------------------------------------------------------------------------
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
            "last_digits_card_number": "4242",
            "first_digits_card_number": "424242",
    },
    "return_url": "https://govservice.example.com/return_from_payments",
    "refund_summary": {
            "amount_available": 5000,
            "amount_submitted": 0,
            "status": "available"
    },
    "settlement_summary": {
            "capture_submit_time": null,
            "captured_date": null
    },
    "fee": 5,
    "links": [
        {
            "rel": "self",
            "method": "GET",
            "href": "https://connector.example.com/v1/api/charges/1"
        },
        {
            "rel": "next_url",
            "method": "GET",
            "href": "https://frontend.example.com/charges/1?chargeTokenId=82347"
        },
        {
            "href": "https://connector.example.com//v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds",
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
| `card_details.first_digits_card_number`  |  | The first 6 digits of this card                          |
| `card_details.billing_address`    | | Not present when no billing address is collected |
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
| `fee`                 |  | The fee charged by payment service provider, if available    |
-----------------------------------------------------------------------------------------------------------

## POST /v1/api/accounts/{accountId}/charges

This endpoint creates a new charge for the given account.

### Request example

```
POST /v1/api/accounts/3121/charges
Content-Type: application/json

{
    "amount": 5000,
    "description": "The payment description (shown to the user on the payment pages)",
    "reference": "The reference issued by the government service for this payment",
    "gateway_account_id": "10",
    "return_url": "https://govservice.example.com/return_from_payments"
}
```

#### Request body description

| Field                    | required | Description                                                                            |
| ------------------------ |:---------:| --------------------------------------------------------------------------------------|
| `amount`                 | X         | The amount (in minor units) of the charge                                             |
| `description`            | X         | The payment description (shown to the user on the payment pages)                      |
| `reference`              | X         | The reference issued by the government service for this payment                       |
| `gateway_account_id`     | X         | The gateway account to use for this charge                                            |
| `return_url`             | X         | The url to return the user to after the payment process has completed                 |
| `language`               |           | A supported ISO-639-1 language code e.g. `"cy"` — defaults to `"en"` if not specified |
| `delayed_capture`        |           | Whether the payment requires an explicit request to capture — defaults to false       |
| `prefilled_cardholder_details.cardholder_name` | | Cardholder name to be prefilled on frontend card details page |
| `prefilled_cardholder_details.billing_addess.line1` | | Line 1 of the billing address to be prefilled on frontend card details page |
| `prefilled_cardholder_details.billing_addess.line2` | | Line 2 of the billing address to be prefilled on frontend card details page |
| `prefilled_cardholder_details.billing_addess.postcode` | | Postcode of the billing address to be prefilled on frontend card details page |
| `prefilled_cardholder_details.billing_addess.city` | | City of the billing address to be prefilled on frontend card details page |
| `prefilled_cardholder_details.billing_addess.country` | | Country code of the billing address to be prefilled on frontend card details page |

### Response example

```
201 Created
Content-Type: application/json
Location: https://connector.example.com/v1/api/charges/1

{
    "amount": 5000,
    "state": {
        "finished": false,
        "status": "created"
    },
    "description": "The payment description (shown to the user on the payment pages)",
    "reference": "The reference issued by the government service for this payment",
    "language": "en",
    "links": [
        {
            "rel": "self",
            "method": "GET",
            "href": "https://connector.example.com/v1/api/accounts/1/charges/d1onfdh8qptnclbs8q7f5ldles"
        },
        {
            "rel": "refunds",
            "method": "GET",
            "href": "https://connector.example.com/v1/api/accounts/1/charges/d1onfdh8qptnclbs8q7f5ldles/refunds"
        },
        {
            "rel": "next_url",
            "method": "GET",
            "href": "https://frontend.example.com/.example/secure/c4a2aaf7-c388-432d-a09b-fe0b669cd070"
        },
        {
            "rel": "next_url_post",
            "method": "POST",
            "href": "https://frontend.example.com/secure",
            "type": "application/x-www-form-urlencoded",
            "params": {
                "chargeTokenId": "c4a2aaf7-c388-432d-a09b-fe0b669cd070"
            }
        }
    ],
    "charge_id": "d1onfdh8qptnclbs8q7f5ldles",
    "return_url": "https://govservice.example.com/return_from_payments",
    "payment_provider": "sandbox",
    "created_date": "2018-09-04T09:48:24.099Z",
    "refund_summary": {
        "status": "pending",
        "user_external_id": null,
        "amount_available": 5000,
        "amount_submitted": 0
    },
    "settlement_summary": {
        "capture_submit_time": null,
        "captured_date": null
    },
    "card_details": {
        "cardholder_name": "ms foo",
        "billing_address": {
              "line1": "address line 1",
              "line2": "address line 2",
              "postcode": "AB1 2CD",
              "city": "address city",
              "country": "UK"
        }
     },
    "delayed_capture": false
}
```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `charge_id`                 | X | The unique identifier for this charge       |
| `card_details`      |           | Not present when no billing address and cardholder name is posted |                                                                                |
| `card_details.cardholder_name` |           | The cardholder name of this payment                                                                           |
| `card_details.billing_address` | | Not present when no billing address field(s) is posted |
| `card_details.billing_address.line1`    |  | The line 1 of the billing address                                                                                   |
| `card_details.billing_address.line2`    |  | The line 2 of the billing address                                                                                   |
| `card_details.billing_address.postcode` |  | The postcode of the billing address                                                                                 |
| `card_details.billing_address.city`     |  | The city of the billing address                                                                                     |
| `card_details.billing_address.country`  |  | The country of the billing address                                                                                  |


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
        "delayed_capture": true,
        "language": "en",
        "state": {
                "finished": false,
                "status": "submitted"
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
            "last_digits_card_number": "4242",
            "first_digits_card_number": "424242"
        },
        "payment_provider": "sandbox",
        "return_url": "https://govservice.example.com/return_from_payments",
        "links": [
            {
                "href": "https://connector.example.com/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn",
                "method": "GET",
                "rel": "self"
            },
            {
                "href": "https://connector.example.com/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds",
                "method": "GET",
                "rel": "refunds"
            },
            {
                "href": "https://connector.example.com/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/capture",
                "method": "POST",
                "rel": "capture"
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
        },
        "fee": 5
     }]
}
```

#### Response field description

| Field                    | always present | Description                                                                                                          |
| ------------------------ |:--------:| ---------------------------------------------------------------------------------------------------------------------------|
| `results`                | X | List of payments                                                                                                                  |
| `charge_id`              | X | The unique identifier for this charge                                                                                             |
| `amount`                 | X | The amount of this charge                                                                                                         |
| `description`            | X | The payment description                                                                                                           |
| `reference`              | X | There reference issued by the government service for this payment                                                                 |
| `gateway_account_id`     | X | The ID of the gateway account to use with this charge                                                                             |
| `gateway_transaction_id` | X | The gateway transaction reference associated to this charge                                                                       |
| `status`                 | X | The current external status of the charge                                                                                         |
| `language`               | X | The ISO-639-1 code representing the language of the payment e.g. `"en"`                                                           |
| `delayed_capture`        | X | Whether the payment requires or required an explicit request to capture                                                           |
| `card_brand`             |   | The brand label of the card                                                                                                       |
| `card_details.card_brand`      |           | The card brand used for this payment                                                                                |
| `card_details.cardholder_name` |           | The card card holder name of this payment                                                                           |
| `card_details.expiry_date`     |           | The expiry date of this card                                                                                        |
| `card_details.last_digits_card_number`  |  | The last 4 digits of this card                                                                                      |
| `card_details.first_digits_card_number`  |  | The first 6 digits of this card                                                                                      |
| `card_details.billing_address` | | Not present when no billing address is collected |
| `card_details.billing_address.line1`    |  | The line 1 of the billing address                                                                                   |
| `card_details.billing_address.line2`    |  | The line 2 of the billing address                                                                                   |
| `card_details.billing_address.postcode` |  | The postcode of the billing address                                                                                 |
| `card_details.billing_address.city`     |  | The city of the billing address                                                                                     |
| `card_details.billing_address.country`  |  | The country of the billing address                                                                                  |
| `payment_provider`       | X | The gateway provider used by this transaction                                                                                     |
| `return_url`             | X | The url to return the user to after the payment process has completed                                                             |
| `refund_summary`         | X | Provides a refund summary of the total refund amount still available and how much has already been refunded, plus a refund status |
| `settlement_summary`     | X | Provides a settlement summary of the charge containing date and time of capture, if present.                                      |
| `links.rel.capture`      |   | Present when a charge is available for capture. Otherwise                                       |
| `fee`                    |  | The fee charged by payment service provider, if available    |
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

## POST /v1/api/accounts/{accountId}/telephone-charges

This endpoint creates a new telephone payment for the given account.

### Request example

```
POST /v1/api/accounts/3121/telephone-charges
Content-Type: application/json

{
    "amount": 5000,
    "reference": "The reference issued by the government service for this payment",
    "description": "The payment description (shown to the user on the payment pages)",
    "processor_id": "183f2j8923j8",
    "provider_id": "17498-8412u9-1273891239",
    "payment_outcome": {
        "status": "failed",
        "code": "P0010",
        "supplemental": {
            "error_code": "ECKOH01234",
            "error_message": "textual message describing error code"
        }
     },
    "card_type": "master-card",
    "card_expiry": "02/19",
    "last_four_digits": "1234",
    "first_six_digits": "654321"
}
```

#### Request body description

| Field                    | required | Description                                                                            | Supported Values
| ------------------------ |:---------:| --------------------------------------------------------------------------------------|-------------------------|
| `amount`                 | X         | The amount (in minor units) of the charge                                             |                         |
| `reference`              | X         | The reference issued by the government service for this payment                       |                         |
| `description`            | X         | The payment description (shown to the user on the payment pages)                      |                         |
| `created_date`           |           | The date/time when payment was initiated by NHS BSA - ISO8601 including timezone      |                         |
| `authorised_date`        |           | The date/time when payment was authorised by Worldpay - ISO8601 including timezone    |                         |
| `processor_id`           | X         | Unique ECKOH reference                                                                |                         |
| `provider_id`            | X         | The order code generated by Worldpay                                                  |                         |     
| `auth_code`              |          | The authorisation ID generated by Worldpay                                             |                         |
| `payment_outcome.status` | X         | The payment status indicating successful capture                                      | success, failed         |
| `payment_outcome.code`   |          | The payment error code if capture is unsuccessful. This is required if payment_outcome.status is "failed"  |P0010, P0030 or P0050           |
| `payment_outcome.supplemental.error_code`|          | The payment error code given by ECKHO indicating the error reason.     |                         |
| `payment_outcome.supplemental_error_message`|          | A human readable message describing the error.                      |                         |
| `card_type`              | X         | The card type used for this payment                                                   | master-card, visa, maestro, diners-club or american-express |             
| `name_on_card`           |           | The card holder name of this payment                                             |                         |
| `email_address`          |           | The email address of the user of this payment                                         |                         |
| `card_expiry`            | X         | The expiry date of this card                                                          |      MM/YY format       |
| `last_four_digits`       | X         | The last four digits of this card                                                     |                         |
| `first_six_digits`       | X         | The first six digits of this card                                                     |                         |
| `telephone_number`       |           | The telephone number of the user of this payment                                      |                         |


### Response example when charge doesn't already exist in database

```
201 Created
Content-Type: application/json

{
    "amount": 5000,
    "reference": "The reference issued by the government service for this payment",
    "description": "The payment description (shown to the user on the payment pages)",
    "processor_id": "183f2j8923j8",
    "provider_id": "17498-8412u9-1273891239",
    "payment_outcome": {
        "status": "failed",
        "code": "P0010",
            "supplemental": {
                "error_code": "ECKOH01234",
                "error_message": "textual message describing error code"
            }
         },
    "card_type": "master-card",
    "card_expiry": "02/19",
    "last_four_digits": "1234",
    "first_six_digits": "654321",
    "payment_id": "hu20sqlact5260q2nanm0q8u93"
    "state": {
            "status": "failed"
            "finished": true,
            "message": "message describing error code",
            "code": "P0010"
        },
}
```

### Response example when charge already exists in database

```
200 OK
Content-Type: application/json

{
    "amount": 5000,
    "reference": "The reference issued by the government service for this payment",
    "description": "The payment description (shown to the user on the payment pages)",
    "processor_id": "183f2j8923j8",
    "provider_id": "17498-8412u9-1273891239",
    "payment_outcome": {
        "status": "failed",
        "code": "P0010",
        "supplemental": {
            "error_code": "ECKOH01234",
            "error_message": "textual message describing error code"
        }
    },
    "card_type": "master-card",
    "card_expiry": "02/19",
    "last_four_digits": "1234",
    "first_six_digits": "654321",
    "payment_id": "hu20sqlact5260q2nanm0q8u93"
    "state": {
            "status": "failed"
            "finished": true,
            "message": "message describing error code",
            "code": "P0010"
        },
}
```

#### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `amount`                 | X | The amount (in minor units) of the charge      |
| `reference`              | X | The reference issued by the government service for this payment   |                                                                                |
| `description`            | X | The payment description (shown to the user on the payment pages)  |
| `processor_id`           | X | Unique ECKOH reference |
| `provider_id`            | X | The order code generated by Worldpay    |
| `payment_outcome.status` | X | The payment status indicating successful capture. Either success or failed.    |
| `payment_outcome.code`   |  | The payment error code if capture is unsuccessful - present if payment_outcome.status is "failed"  |
| `payment_outcome.supplemental.error_code`     |  | The payment error code given by ECKHO indicating the error reason.  |
| `payment_outcome.supplemental.error_message`  |  | A human readable message describing the error.  |
| `card_type`              | X | The card type used for this payment                                               |
| `card_expiry`            | X | The expiry date of this card                                              |
| `last_four_digits`  | X | The last four digits of this card                                               |
| `first_six_digits`  | X | The first six digits of this card                                           |
| `payment_id`  | X | The unique identifier for the charge                                              |
| `state.status`  | X | This GOV.UK Pay State of the payment lifecycle that has been recorded. This is always success.                                             |
| `state.finished`  | X | This represents whether the charge is in a terminal state. This is always true.                                             |
| `state.message`  |  | This presents a human readable message representing the error if any.                                             |
| `state.code`  |  | This presents the error code if any.                                             |
| `created_date`  |  | The date/time when payment was initiated by NHS BSA - ISO8601 including timezone                                              |
| `authorised_date`  |  | The date/time when payment was authorised by Worldpay - ISO8601 including timezone                                               |
| `auth_code`  |  | The authorisation ID generated by Worldpay                                               |
| `name_on_card`  |  | The card card holder name of this payment                                             |
| `email_address`  |  | The email address of the user of this payment                                               |
| `telephone_number`  |  | The telephone number of the user of this payment                                               |
-----------------------------------------------------------------------------------------------------------

## GET /v1/api/accounts/{accountId}/refunds

Returns all the refunds.

### Request example

```
GET /v1/api/accounts/1/refunds
Content-Type: application/json
```

### Request query param description

| Field                    | Description                         |
| ------------------------ | ----------------------------------------- |
| `from_date`              | The initial date to search refunds        |
| `to_date`                | The end date we should search refunds     |

### Refunds response

```
HTTP/1.1 200 OK
Content-Type: application/json
{
    "results": [
        {
            "refund_id": "7vnpc56fed7b63n3soqm15e3cr",
            "created_date": "2019-02-20T14:26:21.429Z",
            "payment_id": "bnoljgur1f9kcbpltua9fqn0sf",
            "amount": 2000,
            "_links": {
                "self": {
                    "href": "http://publicapi:9100/v1/payments/bnoljgur1f9kcbpltua9fqn0sf/refunds/7vnpc56fed7b63n3soqm15e3cr",
                    "method": "GET"
                },
                "payment": {
                    "href": "http://publicapi:9100/v1/payments/bnoljgur1f9kcbpltua9fqn0sf",
                    "method": "GET"
                }
            },
            "status": "success"
        },
        {
            "refund_id": "9b196g0oqu2k50q4ofo6tg5s49",
            "created_date": "2019-02-20T14:26:01.227Z",
            "payment_id": "bnoljgur1f9kcbpltua9fqn0sf",
            "amount": 5000,
            "_links": {
                "self": {
                    "href": "http://publicapi:9100/v1/payments/bnoljgur1f9kcbpltua9fqn0sf/refunds/9b196g0oqu2k50q4ofo6tg5s49",
                    "method": "GET"
                },
                "payment": {
                    "href": "http://publicapi:9100/v1/payments/bnoljgur1f9kcbpltua9fqn0sf",
                    "method": "GET"
                }
            },
            "status": "success"
        }
    ]
}
```

#### Response field description

| Field                  | Description                                 |
| ---------------------- | --------------------------------------------|
| `refund_id`            | The ID of the refund                        |
| `payment_id`           | The ID of the payment this refund relates to|
| `created_date`         | Date when the refund was created            |
| `amount`               | The amount (in minor units) of the refund   |
| `_links.self`          | Link to the refund                          |
| `_links.payment`       | Link to the payment this refund relates to  |
| `status`               | The refund status                           |

------------------------------------------------------------------------------------------------

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

## PATCH /v1/api/accounts/{accountId}

A generic endpoint that allows the patching of `allow_apple_pay`, `allow_google_pay`, `credentials/gateway_merchant_id`, `notify_settings`, `email_collection_mode`, 
`corporate_credit_card_surcharge_amount`, `corporate_debit_card_surcharge_amount`, `corporate_prepaid_credit_card_surcharge_amount`, 
`corporate_prepaid_debit_card_surcharge_amount` or `allow_zero_amount`

### Request example

```
PATCH /v1/api/accounts/123
Content-Type: application/json
{
    "op":"replace", "path":"allow_apple_pay", "value": true
}

PATCH /v1/api/accounts/123
Content-Type: application/json
{
    "op":"replace", "path":"corporate_credit_card_surcharge_amount", "value": 100
}
```

### Response when update is successful

```
200 {}
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
    "requires3ds": false,
    "notifySettings": null,
    "notificationCredentials": null,
    "live": false,
    "type": "test",
    "corporate_credit_card_surcharge_amount": 0,
    "corporate_debit_card_surcharge_amount": 0,
    "corporate_prepaid_credit_card_surcharge_amount": 0,
    "corporate_prepaid_debit_card_surcharge_amount": 0,
    "credentials: {
      "username:" "Username"
    },
    "allow_apple_pay": false,
    "allow_google_pay": false,
    "allow_zero_amount": false,
    "email_collection_mode": "MANDATORY",
    "email_notifications": {
      "REFUND_ISSUED": {
          "version": 1,
          "enabled": true,
          "template_body": null
      },
      "PAYMENT_CONFIRMED": {
          "version": 1,
          "enabled": true,
          "template_body": null
      }
    }
}
```

#### Response field description

| Field                                            | always present | Description                                                                                                             |
|--------------------------------------------------|----------------|-------------------------------------------------------------------------------------------------------------------------|
| `gateway_account_id`                             | X              | The account Id.                                                                                                         |
| `payment_provider`                               | X              | The payment provider for which this account is created.                                                                 |
| `credentials`                                    | X              | The payment provider credentials. Password is not returned. The default value is the empty JSON document {}.            |
| `description`                                    | X              | An internal description to identify the gateway account. The default value is `null`.                                   |
| `analytics_id`                                   | X              | An identifier used to identify the service in Google Analytics. The default value is `null`.                            |
| `requires_3ds`                                   | X              | Whether 3DS is required. The default value is `false`.                                                                  |
| `notify_settings`                                | X              | A JSON object containing the custom notify credentials for this account. The default value is `null`                    |
| `notification_credentials`                       | X              | A JSON object containing credentials for receiving notifications from the payment provider. The default value is `null` |
| `live`                                           | X              | Whether the gateway account is a live account or not.                                                                   |
| `type`                                           | X              | The account type. Can be `live` or `test`                                                                               |
| `corporate_credit_card_surcharge_amount`         | X              | A corporate credit card surcharge amount in pence. The default value is `0`.                                            |
| `corporate_debit_card_surcharge_amount`          | X              | A corporate debit card surcharge amount in pence. The default value is `0`.                                             |
| `corporate_prepaid_credit_card_surcharge_amount` | X              | A corporate prepaid credit card surcharge amount in pence. The default value is `0`.                                    |
| `corporate_prepaid_debit_card_surcharge_amount`  | X              | A corporate prepaid debit card surcharge amount in pence. The default value is `0`.                                     |
| `allow_apple_pay`                                | X              | Whether apple pay is enabled. The default value is `false`.                                                             |
| `allow_google_pay`                               | X              | Whether google pay is enabled. The default value is `false`.                                                            |
| `allow_zero_amount`                              | X              | Whether the account supports charges with a zero amount. The default value is `false`.                                  |
| `email_collection_mode`                          | X              | Whether email address is required from paying users. Can be `MANDATORY`, `OPTIONAL` or `OFF`                            |
| `email_notifications`                            | X              | The settings for the different emails that are sent out                                                                 |

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
    "state": {
        "finished": true,
        "status": "success"
    },
    "description": "Payment description",
    "language": "en",
    "status": "CAPTURED",
    "links": [
        {
            "rel": "self",
            "method": "GET",
            "href": "https://connector.example.com/v1/frontend/charges/tps417v9td3qpmisi71dhvtb7b"
        },
        {
            "rel": "cardAuth",
            "method": "POST",
            "href": "https://connector.example.com/v1/frontend/charges/tps417v9td3qpmisi71dhvtb7b/cards"
        },
        {
            "rel": "cardCapture",
            "method": "POST",
            "href": "https://connector.example.com/v1/frontend/charges/tps417v9td3qpmisi71dhvtb7b/capture"
        }
    ],
    "charge_id": "tps417v9td3qpmisi71dhvtb7b",
    "gateway_transaction_id": "6dc944c2-0e20-4ad3-af89-8b2c30bbd2a2",
    "return_url": "https://govservice.example.com/return/fe10ff90badbade0e798b70eb2f94369/Payment-reference",
    "email": "user@example.com",
    "created_date": "2018-09-21T08:42:38.230Z",
    "card_details": {
        "last_digits_card_number": "4242",
        "cardholder_name": "Mr. Payment",
        "expiry_date": "10/20",
        "billing_address": {
            "line1": "123 Street",
            "line2": "",
            "postcode": "ABCD EFG",
            "city": "London",
            "county": null,
            "country": "GB"
        },
        "card_brand": "Visa"
    },
    "delayed_capture": false,
    "fee": 5,
    "gateway_account": {
        "version": 1,
        "requires3ds": false,
        "notifySettings": null,
        "live": false,
        "gateway_account_id": 1,
        "payment_provider": "sandbox",
        "type": "test",
        "service_name": "local Pay test",
        "analytics_id": null,
        "corporate_credit_card_surcharge_amount": 0,
        "corporate_debit_card_surcharge_amount": 0,
        "corporate_prepaid_credit_card_surcharge_amount": 0,
        "corporate_prepaid_debit_card_surcharge_amount": 0,
        "card_types": [
            {
                "id": "79404bb9-31fb-4ad6-xxxx-789c3b044059",
                "brand": "visa",
                "label": "Visa",
                "type": "DEBIT",
                "requires3ds": false
            },
            {
                "id": "77b1c923-8ef7-42cc-xxxx-78f8c8f96980",
                "brand": "visa",
                "label": "Visa",
                "type": "CREDIT",
                "requires3ds": false
            },
            {
                "id": "69193ce2-6c07-44d7-xxxx-37debfb83907",
                "brand": "master-card",
                "label": "Mastercard",
                "type": "DEBIT",
                "requires3ds": false
            },
            {
                "id": "f91037af-3b10-4bc6-xxxx-d3ec3d30a8aa",
                "brand": "master-card",
                "label": "Mastercard",
                "type": "CREDIT",
                "requires3ds": false
            },
            {
                "id": "39f11dde-abd3-475a-xxxx-55a431beb592",
                "brand": "american-express",
                "label": "American Express",
                "type": "CREDIT",
                "requires3ds": false
            },
            {
                "id": "74c8fa04-0831-49da-xxxx-b6a6f1a82fca",
                "brand": "diners-club",
                "label": "Diners Club",
                "type": "CREDIT",
                "requires3ds": false
            },
            {
                "id": "de8fb0cd-9fa7-47c8-xxxx-06e1acdefd83",
                "brand": "discover",
                "label": "Discover",
                "type": "CREDIT",
                "requires3ds": false
            },
            {
                "id": "07eaeb25-d268-4c34-xxxx-81c8e9528d1c",
                "brand": "jcb",
                "label": "Jcb",
                "type": "CREDIT",
                "requires3ds": false
            },
            {
                "id": "b8ed6f05-674f-4b75-xxxx-07c2a38d1df4",
                "brand": "unionpay",
                "label": "Union Pay",
                "type": "CREDIT",
                "requires3ds": false
            }
        ]
    }
}
```

#### Response field description

| Field                                              | always present             | Description                                                                     |
| -------------------------------------------------- | -------------------------- | ------------------------------------------------------------------------------------ |
| `amount`                                           | X                          | The amount (in minor units) of the charge.                                           |
| `status`                                           | X                          | The current (internal) status of the charge.                                         |
| `card_brand`                                       |                            | The brand label of the card.                                                         |
| `language`                                         | X                          | The ISO-639-1 code representing the language of the payment e.g. `"en"`.             |
| `delayed_capture`                                  | X                          | Whether the payment requires or required an explicit request to capture.             |
| `fee`                                              |                            | The fee charged by payment service provider, if available                            |
| `corporate_credit_card_surcharge_amount`           | X                          | A corporate credit card surcharge amount in pence. The default value is `0`.         |
| `corporate_debit_card_surcharge_amount`            | X                          | A corporate debit card surcharge amount in pence. The default value is `0`.          |
| `corporate_prepaid_credit_card_surcharge_amount`   | X                          | A corporate prepaid credit card surcharge amount in pence. The default value is `0`. |
| `corporate_prepaid_debit_card_surcharge_amount`    | X                          | A corporate prepaid debit card surcharge amount in pence. The default value is `0`.  |

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

## POST /v1/api/accounts/{accountId}/charges/{chargeId}/capture

* This endpoint should be called to capture a delayed capture charge. The charge needs to have been previously marked as 
`AWAITING CAPTURE REQUEST` for this call to succeed.

* When a charge is in any of the states `CAPTURED, CAPTURE APPROVED, CAPTURE APPROVED RETRY, CAPTURE READY,
 CAPTURE SUBMITTED` then nothing happens and the response will be a 204.

* When a charge is in a status that cannot transition (eg. none of the above) then we return 409

* When a charge doesn't exist then we return 404

### Request example

```
POST /v1/api/accounts/1/charges/abc/capture
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
404 Not Found
Content-Type: application/json

{
    "message": "Charge with id [abc] not found."
}
```

-----------------------------------------------------------------------------------------------------------

## GET /v1/frontend/tokens/{chargeTokenId}

Retrieve a secure redirect token. This is used by frontend to determine that the
frontend redirect URL is a genuine one generated by connector.

### Request example

```
GET /v1/frontend/tokens/a69a2cf3-d5d1-408f-b196-4b716767b507
```

### Response for the success path

```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "used": false,
  "charge": {
    "version": 3,
    "externalId": "gdasul2u207vkb3eatam97975j",
    "amount": 1000,
    "status": "ENTERING CARD DETAILS",
    "gatewayTransactionId": null,
    "returnUrl": "https://www.payments.service.gov.uk",
    "email": null,
    "corporateSurcharge": null,
    "cardDetails": null,
    "gatewayAccount": {
      "version": 1,
      "requires3ds": false,
      "notifySettings": null,
      "live": false,
      "gateway_account_id": 1,
      "payment_provider": "sandbox",
      "type": "test",
      "service_name": "My service",
      "analytics_id": null,
      "allow_google_pay": false,
      "allow_apple_pay": false,
      "corporate_prepaid_credit_card_surcharge_amount": 0,
      "corporate_prepaid_debit_card_surcharge_amount": 0,
      "allow_zero_amount": false,
      "integration_version_3ds": 1,
      "email_notifications": {
        "PAYMENT_CONFIRMED": {
          "version": 1,
          "enabled": true,
          "template_body": null
        },
        "REFUND_ISSUED": {
          "version": 1,
          "enabled": true,
          "template_body": null
        }
      },
      "email_collection_mode": "MANDATORY",
      "card_types": [
        {
          "id": "1ad775e1-3433-4003-87c3-7f94b345512c",
          "brand": "visa",
          "label": "Visa",
          "type": "DEBIT",
          "requires3ds": false
        },
        {
          "id": "6f740791-e02e-4b27-878b-4458935a3f00",
          "brand": "visa",
          "label": "Visa",
          "type": "CREDIT",
          "requires3ds": false
        },
        {
          "id": "b5e8da06-6d22-46df-89ee-32bec7950e46",
          "brand": "master-card",
          "label": "Mastercard",
          "type": "DEBIT",
          "requires3ds": false
        },
        {
          "id": "d19ed1f7-98d9-4b47-95ae-bb8b13c668ca",
          "brand": "master-card",
          "label": "Mastercard",
          "type": "CREDIT",
          "requires3ds": false
        },
        {
          "id": "7498b818-d3b5-4814-bb80-f68a7f939906",
          "brand": "american-express",
          "label": "American Express",
          "type": "CREDIT",
          "requires3ds": false
        },
        {
          "id": "3352066e-d6d6-48b2-a811-fe65dfe72589",
          "brand": "diners-club",
          "label": "Diners Club",
          "type": "CREDIT",
          "requires3ds": false
        },
        {
          "id": "b49e121c-98a2-4a8a-a6f5-b6f9022d0924",
          "brand": "discover",
          "label": "Discover",
          "type": "CREDIT",
          "requires3ds": false
        },
        {
          "id": "0ab5f13d-f12f-4335-96d0-5330380d0feb",
          "brand": "jcb",
          "label": "Jcb",
          "type": "CREDIT",
          "requires3ds": false
        },
        {
          "id": "0f2f3a0e-3143-4e80-aeb5-bdaee58f9c34",
          "brand": "unionpay",
          "label": "Union Pay",
          "type": "CREDIT",
          "requires3ds": false
        }
      ],
      "gateway_merchant_id": null,
      "corporate_credit_card_surcharge_amount": 0,
      "corporate_debit_card_surcharge_amount": 0
    },
    "refunds": [],
    "events": [
      {
        "version": 1,
        "status": "ENTERING_CARD_DETAILS",
        "gatewayEventDate": null,
        "updated": 1568128152.757898000
      },
      {
        "version": 1,
        "status": "CREATED",
        "gatewayEventDate": null,
        "updated": 1568128151.435551000
      }
    ],
    "description": "my payment",
    "reference": "my payment reference",
    "providerSessionId": null,
    "createdDate": 1568128151.316212000,
    "language": "ENGLISH",
    "delayedCapture": false,
    "walletType": null,
    "externalMetadata": null,
    "parityCheckStatus": null,
    "parityCheckDate": null,
    "captureSubmitTime": null,
    "capturedTime": null,
    "3dsDetails": null,
    "feeAmount": null,
    "paymentGatewayName": "SANDBOX",
    "netAmount": null
  }
}
```

#### Response field description

| Field | always present | Description |
| ------|:--------------:|             |
| `used` | X | true or false depending on whether the token has been marked as used or not |
| `charge` | X | The charge associated with the token |

### Response for the failure path

```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "error_identifier": "GENERIC",
  "message": [
    "Token invalid!"
  ]
}
```

#### Response field description

| Field | always present | Description |
| ------|:--------------:|             |
| `error_identifier` | X | Always “GENERIC” |
| `message` | X | Always “Token invalid!” |

-----------------------------------------------------------------------------------------------------------

## GET /v1/frontend/tokens/{chargeTokenId}/charge

Find the charge associated with a secure redirect token. This is used by frontend to determine that the
frontend redirect URL is a genuine one generated by connector.

### Request example

```
GET /v1/frontend/tokens/a69a2cf3-d5d1-408f-b196-4b716767b507/charge
```

### Response for the success path

```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "version": 3,
  "externalId": "gdasul2u207vkb3eatam97975j",
  "amount": 1000,
  "status": "ENTERING CARD DETAILS",
  "gatewayTransactionId": null,
  "returnUrl": "https://www.payments.service.gov.uk",
  "email": null,
  "corporateSurcharge": null,
  "cardDetails": null,
  "gatewayAccount": {
    "version": 1,
    "requires3ds": false,
    "notifySettings": null,
    "live": false,
    "gateway_account_id": 1,
    "payment_provider": "sandbox",
    "type": "test",
    "service_name": "My service",
    "analytics_id": null,
    "allow_google_pay": false,
    "allow_apple_pay": false,
    "corporate_prepaid_credit_card_surcharge_amount": 0,
    "corporate_prepaid_debit_card_surcharge_amount": 0,
    "allow_zero_amount": false,
    "integration_version_3ds": 1,
    "email_notifications": {
      "PAYMENT_CONFIRMED": {
        "version": 1,
        "enabled": true,
        "template_body": null
      },
      "REFUND_ISSUED": {
        "version": 1,
        "enabled": true,
        "template_body": null
      }
    },
    "email_collection_mode": "MANDATORY",
    "card_types": [
      {
        "id": "1ad775e1-3433-4003-87c3-7f94b345512c",
        "brand": "visa",
        "label": "Visa",
        "type": "DEBIT",
        "requires3ds": false
      },
      {
        "id": "6f740791-e02e-4b27-878b-4458935a3f00",
        "brand": "visa",
        "label": "Visa",
        "type": "CREDIT",
        "requires3ds": false
      },
      {
        "id": "b5e8da06-6d22-46df-89ee-32bec7950e46",
        "brand": "master-card",
        "label": "Mastercard",
        "type": "DEBIT",
        "requires3ds": false
      },
      {
        "id": "d19ed1f7-98d9-4b47-95ae-bb8b13c668ca",
        "brand": "master-card",
        "label": "Mastercard",
        "type": "CREDIT",
        "requires3ds": false
      },
      {
        "id": "7498b818-d3b5-4814-bb80-f68a7f939906",
        "brand": "american-express",
        "label": "American Express",
        "type": "CREDIT",
        "requires3ds": false
      },
      {
        "id": "3352066e-d6d6-48b2-a811-fe65dfe72589",
        "brand": "diners-club",
        "label": "Diners Club",
        "type": "CREDIT",
        "requires3ds": false
      },
      {
        "id": "b49e121c-98a2-4a8a-a6f5-b6f9022d0924",
        "brand": "discover",
        "label": "Discover",
        "type": "CREDIT",
        "requires3ds": false
      },
      {
        "id": "0ab5f13d-f12f-4335-96d0-5330380d0feb",
        "brand": "jcb",
        "label": "Jcb",
        "type": "CREDIT",
        "requires3ds": false
      },
      {
        "id": "0f2f3a0e-3143-4e80-aeb5-bdaee58f9c34",
        "brand": "unionpay",
        "label": "Union Pay",
        "type": "CREDIT",
        "requires3ds": false
      }
    ],
    "gateway_merchant_id": null,
    "corporate_credit_card_surcharge_amount": 0,
    "corporate_debit_card_surcharge_amount": 0
  },
  "refunds": [],
  "events": [
    {
      "version": 1,
      "status": "ENTERING_CARD_DETAILS",
      "gatewayEventDate": null,
      "updated": 1568128152.757898000
    },
    {
      "version": 1,
      "status": "CREATED",
      "gatewayEventDate": null,
      "updated": 1568128151.435551000
    }
  ],
  "description": "my payment",
  "reference": "my payment reference",
  "providerSessionId": null,
  "createdDate": 1568128151.316212000,
  "language": "ENGLISH",
  "delayedCapture": false,
  "walletType": null,
  "externalMetadata": null,
  "parityCheckStatus": null,
  "parityCheckDate": null,
  "captureSubmitTime": null,
  "capturedTime": null,
  "3dsDetails": null,
  "feeAmount": null,
  "paymentGatewayName": "SANDBOX",
  "netAmount": null
}
```

### Response for the failure path

```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "error_identifier": "GENERIC",
  "message": [
    "Token invalid!"
  ]
}
```

#### Response field description

| Field | always present | Description |
| ------|:--------------:|             |
| `error_identifier` | X | Always “GENERIC” |
| `message` | X | Always “Token invalid!” |

-----------------------------------------------------------------------------------------------------------

## POST /v1/frontend/tokens/{chargeTokenId}

Mark a secure redirect token as used. This is done so that the token will expire after its first use.

### Request example

```
POST /v1/frontend/tokens/a69a2cf3-d5d1-408f-b196-4b716767b507/used
```

### Response for the success path

```
HTTP/1.1 204 No Content
```

### Response for the failure path

```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "error_identifier": "GENERIC",
  "message": [
    "Token invalid!"
  ]
}
```

#### Response field description

| Field | always present | Description |
| ------|:--------------:|             |
| `error_identifier` | X | Always “GENERIC” |
| `message` | X | Always “Token invalid!” |

-----------------------------------------------------------------------------------------------------------

## DELETE /v1/frontend/tokens/{chargeTokenId}

Delete a secure redirect token. This is done so that the token will expire after its first use.

### Request example

```
DELETE /v1/frontend/tokens/a69a2cf3-d5d1-408f-b196-4b716767b507
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
See [src/test/resources/templates/worldpay/notification.xml](/src/test/resources/templates/worldpay/notification.xml) for an example notification.

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

See [src/test/resources/templates/smartpay/notification-authorisation.json](/src/test/resources/templates/smartpay/notification-authorisation.json) for an example notification.
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

## POST /v1/api/notifications/epdq

This endpoint handles a notification from ePDQ.

### Request example

```
POST /v1/api/notifications/epdq
Content-Type: application/x-www-form-urlencoded

```
See [src/test/resources/templates/epdq/capture-notification.txt](/src/test/resources/templates/epdq/capture-notification.txt) for an example notification.


### Response example

```
200 OK
Content-Type: text/plain

[OK]
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
    "refund_amount_available": 30000,
    "user_external_id": "AA213FD51B3801043FBC"
}
```

#### Request description


| Field                    | required | Description                                             |
| ------------------------ |:--------:| ------------------------------------------------------- |
| `amount`                 | Yes      | Amount to refund in pence                               |
| `refund_amount_available`| Yes      | Total amount still available before issuing the refund  |
| `user_external_id`       | No       | The ID of the user who issued the refund                |

### Refund created response

```
HTTP/1.1 202 Accepted
Content-Type: application/json

{
    "amount":3444,
    "created_date":"2016-10-05T14:15:34.096Z",
    "refund_id":"vijjk08adovg10gfqc46joem2l",
    "user_external_id":"AA213FD51B3801043FBC",
    "status":"success",
    "_links":{
        "self":{"href":"https://connector.example.com/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds/vijjk08adovg10gfqc46joem2l"},
        "payment":{"href":"https://connector.example.com/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn"}
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
| `user_external_id`     | The ID of the user who issued the refund |
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
                        "href": "https://connector.example.com/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn"
                    },
                    "self": {
                        "href": "https://connector.example.com/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds/vijjk08adovg10gfqc46joem2l"
                    }
                },
                "amount": 3444,
                "created_date": "2016-10-05T14:15:34.096Z",
                "refund_id": "vijjk08adovg10gfqc46joem2l",
                "user_external_id":"AA213FD51B3801043FBC",
                "status": "success"
            }
        ]
    },
    "_links": {
        "payment": {
            "href": "https://connector.example.com/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn"
        },
        "self": {
            "href": "https://connector.example.com/v1/api/accounts/1/charges/uqu4s24383qkod35rsb06gv3cn/refunds"
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
| `_embedded.refunds.user_external_id`       | The ID of the user who issued the refund    |
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
                "href": "https://connector.example.com/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn"
            },
            "self": {
                "href": "https://connector.example.com/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn/refunds/vijjk08adovg10gfqc46joem2l"
            }
        },
    "amount": 3444,
    "created_date": "2016-10-05T14:15:34.096Z",
    "refund_id": "vijjk08adovg10gfqc46joem2l",
    "user_external_id": "AA213FD51B3801043FBC",
    "status": "success"
}
```

#### Response field description

| Field                  | Description                               |
| ---------------------- | ----------------------------------------- |
| `refund_id`           | The ID of the created payment             |
| `amount`          | The amount of refund                       |
| `user_external_id`     | The ID of the user who issued the refund |
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
-----------------------------------------------------------------------------------------------------------
## GET /v1/api/accounts/{accountId}/stripe-setup

Retrieves which Stripe Connect account setup tasks have been completed for a given `accountId`

### Request example

```
GET /v1/api/accounts/123/stripe-setup
```

### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{
    "bank_account": true,
    "responsible_person": false,
    "organisation_details": false
}
```

### Response field description

| Field | Always present | Description |
| ------|:--------------:| ------------|
| `bank_account` | X     | Whether bank account details have been submitted to Stripe (`true` or `false`) |
| `responsible_person` | X | Whether a nominated responsible person has been submitted to Stripe  (`true` or `false`) |
| `organisation_details` | X | Whether the organisation address, VAT number and company number have been submitted to Stripe  (`true` or `false`) |

### Response errors

#### Specified `accountId` does not exist

```
HTTP/1.1 404 Not Found
```

#### Specified `accountId` is not a Stripe gateway account

```
HTTP/1.1 404 Not Found
```

## POST /v1/api/accounts/{accountId}/stripe-setup

Updates which Stripe Connect account setup tasks have been completed for a given `accountId`

### Request example

```
POST /v1/api/accounts/123/stripe-setup
[
    {
        "op": "replace",
        "path": "bank_account",
        "value": true
    },
    {
        "op": "replace",
        "path": "responsible_person",
        "value": false
    }
]
```

### Request field description
| Field  | Required | Description                               |
| ------ |:--------:|------------- |
| `op`    | X | Must be `"replace"` |
| `path`  | X | The task (`"bank_account"`, `"responsible_person"` or `"organisation_details"`) |
| `value` | X | Whether the task has been completed (`true` or `false`) |

### Response example

```
HTTP/1.1 200 OK
```

### Response errors

#### Syntax error
```
HTTP/1.1 400 Bad Request
{
    "errors": [
        "Operation [add] not supported for path [bank_account]"
    ]
}
```

#### Specified `accountId` does not exist

```
HTTP/1.1 404 Not Found
```

#### Specified `accountId` is not a Stripe gateway account

```
HTTP/1.1 404 Not Found
```
-----------------------------------------------------------------------------------------------------------
## GET /v1/api/accounts/{accountId}/stripe-account

Retrieves Stripe Connect account information for a given gateway `accountId`

### Request example

```
GET /v1/api/accounts/123/stripe-account
```

### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{
    "stripe_account_id": "acct_123example123"
}
```

### Response field description

| Field               | Always present  | Description               |
| ------------------- | --------------- | --------------------------|
| `stripe_account_id` | X               | Stripe account ID         |

### Response errors

#### Specified `accountId` does not exist

```
HTTP/1.1 404 Not Found
```

#### Specified `accountId` is not a Stripe gateway account

```
HTTP/1.1 404 Not Found
```

#### Specified `accountId` does not have Stripe account credentials

```
HTTP/1.1 404 Not Found
```
-----------------------------------------------------------------------------------------------------------
## GET /v1/api/accounts/{accountId}/transactions-summary

Retrieves payment summary totals for a given `accountId`

### Request query param description

| Field       | Always present | Description                                |
| ------------|:--------------:| ------------------------------------------ |
| `from_date` | X              | Beginning of date range covered by summary |
| `to_date`   | X              | End of date range covered by summary       |

### Request example

```
GET /v1/api/accounts/123/transactions-summary?from_date=2017-11-03T00:00:00Z&to_date=2017-11-10T00:00:00Z
```

### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{
    "successful_payments" : {
        "count": 10,
        "total_in_pence": 55000
    },
    "refunded_payments" : {
        "count": 2,
        "total_in_pence": 11000
    },
    "net_income" : {
        "total_in_pence": 44000
    }
}
```

### Response field description

| Field                 | Always present | Description                                                                        |
| ----------------------|:--------------:| -----------------------------------------------------------------------------------|
| `successful_payments` | X              | Count (`count`) and total value (`total_in_pence`) of successful payments          |
| `refunded_payments`   | X              | Count (`count`) and total value (`total_in_pence`) of refunded payments            |
| `net_income`          | X              | Total value of successful payments minus total value of refunds (`total_in_pence`) |
| `count`               |                | Total number of successful payments or refunded payments                           |
| `total_in_pence`      | X              | Total value of successful payments, refunds or net income (pence)                  |

-----------------------------------------------------------------------------------------------------------
## GET /v1/api/reports/performance-report

Retrieves performance summary

### Request query param description

This endpoint doesn't accept any parameters

### Request example

```
GET /v1/api/reports/performance-report
```

### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{

  "total_volume": 12345,
  "total_amount": 12345,
  "average_amount": 1
}
```

### Response field description

| Field            | Always present | Description                           |
| -----------------|:--------------:| --------------------------------------|
| `total_volume`   | X              | Count of successful payments          |
| `total_amount`   | X              | Sum of successful payments            |
| `average_amount` | X              | Average value of successful payments  |

-----------------------------------------------------------------------------------------------------------
## GET /v1/api/reports/gateway-account-performance-report

Retrieves performance summary segmented by gateway account

### Request query param description

This endpoint doesn't accept any parameters

### Request example

```
GET /v1/api/reports/gateway-account-performance-report
```

### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "1": {
    "total_volume": 100,
    "total_amount": 1000,
    "average_amount": 10,
    "min_amount": 1,
    "max_amount": 9
  }
}
```

### Response field description

The following fields are present for each gateway account returned.

| Field            | Always present | Description                              |
| -----------------|:--------------:| -----------------------------------------|
| `total_volume`   | X              | Count of successful payments             |
| `total_amount`   | X              | Sum of successful payments               |
| `average_amount` | X              | Average value of successful payments     |
| `min_amount`     | X              | Minimum value of all successful payments |
| `max_amount`     | X              | Maximum value of all successful payment  |

This endpoint will not return any statistics for any account that has not conducted a live payment.

## GET /v1/api/reports/daily-performance-report

Retrieves performance summary scoped by day

### Request query param description

| Field       | Always present | Description                                |
| ------------|:--------------:| ------------------------------------------ |
| `date`      | X              | Date for which report should be generated  |


### Request example

```
GET /v1/api/reports/daily-performance-report?date=2018-06-21T00:00:00Z
```

### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{

  "total_volume": 12345,
  "total_amount": 12345,
  "average_amount": 1
}
```

### Response field description

| Field            | Always present | Description                           |
| -----------------|:--------------:| --------------------------------------|
| `total_volume`   | X              | Count of successful payments          |
| `total_amount`   | X              | Sum of successful payments            |
| `average_amount` | X              | Average value of successful payments  |
