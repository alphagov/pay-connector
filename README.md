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
|[```/v1/api/charges/{chargeId}```](#get-v1apichargeschargeid)                                  | GET |  Find out the status of a charge.            |


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
Location: http://connector.service/v1/api/charges/1

{
    "charge_id": "1",
    "links": [{
        "href": "http://connector.service/v1/api/charges/1",
        "rel" : "self",
        "method" : "GET"
        }
      ]
}
```

##### Response field description
```charge_id``` (always present) The unique identifier for this charge.

### GET /v1/api/charges/{chargeId}

Find a charge by ID.

#### Request example

```
GET /v1/api/charges/1

```

#### Response example

```
200 OK
Content-Type: application/json

{
    "amount": 5000,
    "status": "CREATED",
    "links": [{
        "href": "http://connector.service/v1/api/charges/1",
        "rel" : "self",
        "method" : "GET"
        }
      ]

}
```

##### Response field description
```amount``` (always present) The amount (in minor units) of the charge.
```status``` (always present) The current status of the charge.