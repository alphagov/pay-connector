# pay-connector
The Pay Connector in Java (Dropwizard)

## Integration tests

To run the integration tests, the `DOCKER_HOST` and `DOCKER_CERT_PATH` environment variables must be set up correctly. On OS X the environment can be set up with:

```
    eval $(boot2docker shellinit)
    eval $(docker-machine env <virtual-machine-name>)

```

The command to run the integration tests is:

```
    mvn test
```

## API

| Path                          | Supported Methods | Description                        |
| ----------------------------- | ----------------- | ---------------------------------- |
|[```/v1/api/accounts```](#post-v1apiaccounts)              | POST    |  Create a new account to associate charges with            |
|[```/v1/api/accounts/{gatewayAccountId}```](#get-v1apiaccountsaccountsid)     | GET    |  Retrieves an existing account  |
|[```/v1/api/charges/{chargeId}```](#get-v1apichargeschargeid)                 | GET    |  Returns the charge with `chargeId`            |
|[```/v1/api/charges```](#post-v1apicharges)                                  | POST    |  Create a new charge            |
|[```/v1/api/charges/{chargeId}/cancel```](#post-v1apichargeschargeidcancel)  | POST    |  Cancels the charge with `chargeId`            |
|[```/v1/frontend/charges/{chargeId}```](#get-v1frontendchargeschargeid)                                  | GET |  Find out the status of a charge            |
|[```/v1/frontend/charges/{chargeId}/cards```](#post-v1frontendchargeschargeidcards)                      | POST |  Authorise the charge with the card details            |
|[```/v1/frontend/charges/{chargeId}/capture```](#post-v1frontendchargeschargeidcapture)                      | POST |  Confirm a card charge that was previously authorised successfully.            |
|[```/v1/frontend/tokens/{chargeTokenId}```](#get-v1frontendtokenschargetokenid)                                  | GET |  Retrieve information about a secure redirect token.            |
|[```/v1/frontend/tokens/{chargeTokenId}```](#delete-v1frontendtokenschargetokenid)                                  | DELETE |  Delete the secure redirect token.            |


### POST /v1/api/accounts

This endpoint creates a new account in this connector.

#### Request example

```
POST /v1/api/accounts
Content-Type: application/json

{
    "payment_provider": "sandbox"
}
```

##### Request body description

| Field                    | required | Description                               | Supported Values     |
| ------------------------ |:--------:| ----------------------------------------- |----------------------|
| `payment_provider`                 | X | The payment provider for which this account is created.       | sandbox, worldpay |

#### Response example

```
201 OK
Content-Type: application/json
Location: http://connector.service/v1/api/accounts/1

{
    "payment_provider": "sandbox",
    "gateway_account_id": "1" 
    "links": [{
        "href": "http://connector.service/v1/api/accounts/1",
        "rel" : "self",
        "method" : "GET"
        }
      ]
}
```

##### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `gateway_account_id`                 | X | The account Id created by the connector       |
| `payment_provider`                 | X | The payment provider for which this account is created.       |
| `links`                 | X | HTTP self link containing resource reference to the account.       |

-----------------------------------------------------------------------------------------------------------

### GET /v1/api/accounts/{accountsId}

Retrieves an existing gateway account.

#### Request example

```
GET /v1/api/accounts/1
```


#### Response example

```
200 OK
Content-Type: application/json
{
    "payment_provider": "sandbox",
    "gateway_account_id": "1" 
}

##### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `gateway_account_id`                 | X | The account Id        |
| `payment_provider`                 | X | The payment provider for which this account is created.       |

-----------------------------------------------------------------------------------------------------------

### GET /v1/api/charges/{chargeId}

Find a charge by ID. This endpoint is very similar to [```/v1/frontend/charges/{chargeId}```](#get-v1frontendchargeschargeid)
except it translates the status of the charge to an external representation (see [Payment States](https://sites.google.com/a/digital.cabinet-office.gov.uk/payments-platform/payment-states---evolving-diagram)).

#### Request example

```
GET /v1/api/charges/1
```

#### Response example

```
HTTP/1.1 200 OK
Content-Type: application/json

{
    "charge_id": "1",
    "amount": 5000,
    "gateway_account_id": "10",
    "status": "CREATED",
    "return_url": "http://example.service/return_from_payments" 
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
        }
    ],
}
```

##### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `amount`                 | X | The unique identifier for this charge       |
| `gateway_account_id`     | X | The ID of the gateway account to use with this charge       |
| `status`                 | X | The current external status of the charge       |

-----------------------------------------------------------------------------------------------------------

### POST /v1/api/charges

This endpoint creates a new charge through this connector.

#### Request example

```
POST /v1/api/charges
Content-Type: application/json

{
    "amount": 5000,
    "gateway_account_id": "10",
    "return_url": "http://example.service/return_from_payments"
}
```

##### Request body description

| Field                    | required | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `amount`                 | X | The amount (in minor units) of the charge       |
| `gateway_account_id`     | X | The gateway account to use for this charge |
| `return_url`             | X | The url to return the user to after the payment process has completed.|

#### Response example

```
201 Created
Content-Type: application/json
Location: http://connector.service/v1/api/charges/1

{
    "charge_id": "1",
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

##### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `charge_id`                 | X | The unique identifier for this charge       |

-----------------------------------------------------------------------------------------------------------

### POST /v1/api/charges/{chargeId}/cancel

This endpoint cancels a charge.

#### Request example

```
POST /v1/api/charges/123456/cancel
```

#### Response when cancellation successful

```
204 No Content
```

#### Response when cancellation unsuccessful due to invalid state

```
HTTP/1.1 400 Bad Request
Content-Type: application/json
Content-Length: 72

{
    "message": "Cannot cancel a charge with status AUTHORIZATION REJECTED."
}
```

#### Response when the payment does not exist

```
HTTP/1.1 404 Not Found
Content-Type: application/json
Content-Length: 52

{
    "message": "Charge with id [123456] not found."
}
```

-----------------------------------------------------------------------------------------------------------

### GET /v1/frontend/charges/{chargeId}

Find a charge by ID.

#### Request example

```
GET /v1/frontend/charges/1

```

#### Response example

```
200 OK
Content-Type: application/json

{
    "amount": 5000,
    "status": "CREATED",
    "links": [{
                "href": "http://connector.service/v1/frontend/charges/1",
                "rel" : "self",
                "method" : "GET"
            },
            {
                "rel": "cardAuth",
                "method": "POST",
                "href": "http://connector.service/v1/frontend/charges/1/cards"
            }],
}
```

##### Response field description

| Field                    | always present | Description                         |
| ------------------------ |:--------:| ----------------------------------------- |
| `amount`                 | X | The amount (in minor units) of the charge       |
| `status`                 | X | The current (internal) status of the charge |

-----------------------------------------------------------------------------------------------------------

### POST /v1/frontend/charges/{chargeId}/cards

This endpoint takes card details and authorises them for the specified charge.

#### Request example

```
POST /v1/frontend/charges/1/cards
Content-Type: application/json

{
    "card_number": "4242424242424242",
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

##### Request body description

| Field                    | required | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `card_number`                 | X | The card number (16 digits)       |
| `cvc`     | X | The cvc of the card (3 digits) |
| `expiry_date`     | X | The expiry date (no validation other than format being mm/yy) |
| `address`     | X | The billing address associated to this charge. Mandatory Address fields are `line1, city, postcode, country`. Optional Address fields are `line2, line3, county`  |

#### Valid card numbers (inspired from Stripe)

| Card Number                          |  Status | Message                        |
| ----------------------------- | ----------------- | ---------------------------------- |
|```4242424242424242```|Auth success|-|
|```5105105105105100```|Auth success|-|
|```4000000000000002```|Auth rejected|This transaction was declined.|
|```4000000000000069```|Auth rejected|The card is expired.|
|```4000000000000127```|Auth rejected|The CVC code is incorrect.|
|```4000000000000119```|System error|This transaction could be not be processed.|

#### Response example

##### Authorisation success

```
204 No content
Content-Type: application/json
```
##### Error
```
400 Bad Request
Content-Type: application/json

{
    "message": "This transaction was declined."
}
```
-----------------------------------------------------------------------------------------------------------

### POST /v1/frontend/charges/{chargeId}/capture

This endpoint proceeds to the capture of the card for the specified charge. The charge needs to have been previously authorised for this call to succeed.

#### Request example

```
POST /v1/frontend/charges/1/capture
Content-Type: application/json
```

##### The request body is empty


#### Response example

##### Authorisation success

```
204 No content
Content-Type: application/json
```
##### Error
```
400 Bad Request
Content-Type: application/json

{
    "message": "Cannot capture a charge with status AUTHORISATION REJECTED."
}
```
-----------------------------------------------------------------------------------------------------------
### GET /v1/frontend/tokens/{chargeTokenId}

Find a secure redirect token. This is used by the Frontend to determine that the chargeId in the frontend redirect url
is a genuine one and was generated by the Connector.

#### Request example

```
GET /v1/frontend/tokens/2344
```

#### Response for the success path

```
HTTP/1.1 200 OK
Content-Type: application/json

{
    "chargeId": "1"
}
```

#### Response for the failure path

```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
    "message": "Token has expired!"
}
```

##### Response field description

| Field                    | always present | Description                               |
| ------------------------ |:--------:| ----------------------------------------- |
| `chargeId`               | X | The chargeId corresponding to the secure redirect token       |
| `message`               | X | 'Token has expired!' to indicate that the token is no longer valid       |

-----------------------------------------------------------------------------------------------------------
### DELETE /v1/frontend/tokens/{chargeTokenId}

Delete a secure redirect token. This is done so that the token will expire on its first use.

#### Request example

```
GET /v1/frontend/tokens/32344
```

#### Response example

```
HTTP/1.1 204 No Content
```
-----------------------------------------------------------------------------------------------------------
