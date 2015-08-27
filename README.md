# pay-connector
The Charges Connector in Java (Dropwizard)

## Integration tests

To run the integration tests, the `DOCKER_HOST` and `DOCKER_CERT_PATH` environment variables must be set up correctly. On OS X, with boot2docker, this can be done like this:

```
    eval $(boot2docker shellinit)
```

The command to run the integration tests is:

```
    mvn test
```

## API

| Path                          | Supported Methods | Description                        |
| ----------------------------- | ----------------- | ---------------------------------- |
|[```/v1/api/accounts```](#post-v1apiaccounts)              | POST    |  Create a new account to associate charges with.            |
|[```/v1/api/charges```](#post-v1apicharges)                                  | POST    |  Create a new charge.            |
|[```/v1/frontend/charges/{chargeId}```](#get-v1frontendchargeschargeid)                                  | GET |  Find out the status of a charge.            |
|[```/v1/frontend/charges/{chargeId}/cards```](#post-v1frontendchargeschargeidcards)                      | POST |  Authorise the charge with the card details.            |


### POST /v1/api/accounts

This endpoint creates a new account in this connector.

#### Request example

```
POST /v1/api/accounts
Content-Type: application/json

{
    "name": "Service Number 1"
}
```

##### Request body description

```name``` (mandatory) The human friendly name of the account.

#### Response example

```
200 OK
Content-Type: application/json
Location: http://connector.service/v1/api/accounts/1

{
    "name": "Service Number 1",
    "links": [{
        "href": "http://connector.service/v1/api/accounts/1",
        "rel" : "self",
        "method" : "GET"
        }
      ]
}
```

##### Response field description
```service-name``` (always present) The account name.


### POST /v1/api/charges

This endpoint creates a new charge through this connector.

#### Request example

```
POST /v1/api/charges
Content-Type: application/json

{
    "amount": 5000,
    "gateway_account": "1"
}
```

##### Request body description

```amount``` (mandatory) The amount (in minor units) of the charge.

#### Response example

```
200 OK
Content-Type: application/json
Location: http://connector.service/v1/frontend/charges/1

{
    "charge_id": "1",
    "links": [{
        "href": "http://connector.service/v1/frontend/charges/1",
        "rel" : "self",
        "method" : "GET"
        }
      ]
}
```

##### Response field description
```charge_id``` (always present) The unique identifier for this charge.

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
        }
      ]

}
```

##### Response field description
```amount``` (always present) The amount (in minor units) of the charge.
```status``` (always present) The current status of the charge.

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
}
```

##### Request body description

```card_number``` (mandatory) The card number (16 digits).

```cvc``` (mandatory) The cvc of the card (3 digits).

```expiry_date``` (mandatory) The expiry date (no validation other than format being mm/yy).

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

##### Authorization success

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
