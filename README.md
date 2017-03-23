# pay-connector
The GOV.UK Pay Connector in Java (Dropwizard)

## Config

Important configurations.
  
  ```
     worldpay:
        url: <HTTP endpoint for worldpay payments>
        username: <Worldpay merchantID | this will be removed when gateway accounts have the capability to store username/passwords >
        password: <Worldpay merchat password for integration | this will be removed when gateway accounts have the capability to store username/passwords>
  ```
  
## Environment Variables

`AUTH_READ_TIMEOUT_SECONDS`:  The env variable AUTH_READ_TIMEOUT_SECONDS can be passed into the app to override the default value of 10     seconds, i.e. the timeout before the resource responds with an awaited auth response (202), so that frontend can choose to show a spinner and poll for auth response.

`SECURE_WORLDPAY_NOTIFICATION_ENABLED`: The env variable to enable ip filtering of incoming notifications; they will be rejected with a 403 unless they come from Worldpay. Defaults to false.

`SECURE_WORLDPAY_NOTIFICATION_DOMAIN`: The env variable of the domain we might filter notifications with. Defaults to `worldpay.com`.

`NOTIFY_EMAIL_ENABLED`: The env variable to enable confirmation emails to be sent over by GOV.UK Notify, defaults to false.

`NOTIFY_PAYMENT_RECEIPT_EMAIL_TEMPLATE_ID`: ID of the email template specified in the GOV.UK Notify to be used for sending emails, there are no defaults for this one. An email template can accept personalisation (placeholder values which are passed in by the code).

`NOTIFY_SERVICE_ID`: Service ID for the account created at GOV.UK Notify, no defaults.

`NOTIFY_SECRET`: Secret for the account created at GOV.UK Notify, no defaults.

`NOTIFY_BASE_URL`: Base URL of GOV.UK Notify API to be used, defaults to `https://api.notifications.service.gov.uk`.

`GDS_CONNECTOR_WORLDPAY_TEST_URL`: Pointing to the TEST gateway URL of Worldpay payment provider.

`GDS_CONNECTOR_WORLDPAY_LIVE_URL`: Pointing to the LIVE gateway URL of Worldpay payment provider.

`GDS_CONNECTOR_SMARTPAY_TEST_URL`: Pointing to the TEST gateway URL of Smartpay payment provider.

`GDS_CONNECTOR_SMARTPAY_LIVE_URL`: Pointing to the LIVE gateway URL of Smartpay payment provider.

## Integration tests

To run the integration tests, the `DOCKER_HOST` and `DOCKER_CERT_PATH` environment variables must be set up correctly. On OS X the environment can be set up with:


## Contract tests

`$GDS_CONNECTOR_WORLDPAY_PASSWORD` and`$GDS_CONNECTOR_WORLDPAY_PASSWORD` environment variable must be set for Worldpay contract tests.
`GDS_CONNECTOR_SMARTPAY_USER`, `GDS_CONNECTOR_SMARTPAY_PASSWORD` must be set for the smartpay contract tests. 

```
    eval $(boot2docker shellinit)
    eval $(docker-machine env <virtual-machine-name>)

```

The command to run all the tests is:

```
    mvn test
```

## API Specification

### TASKS NAMESPACE

| Path                          | Supported Methods | Description                        |
| ----------------------------- | ----------------- | ---------------------------------- |
|[```/v1/tasks/expired-charges-sweep```](doc/api_specification.md#post-v1tasksexpired-charges-sweep)  | POST    |  Spawns a task to expire charges with a default window of 1 Hr |   

### API NAMESPACE

| Path                          | Supported Methods | Description                        |
| ----------------------------- | ----------------- | ---------------------------------- |
|[```/v1/api/accounts```](doc/api_specification.md#post-v1apiaccounts)              | POST    |  Create a new account to associate charges with            |
|[```/v1/api/accounts```](doc/api_specification.md#get-v1apiaccounts)              | GET    |  Retrieves a collection of all the accounts |
|[```/v1/api/accounts/{gatewayAccountId}```](doc/api_specification.md#get-v1apiaccountsaccountsid)     | GET    |  Retrieves an existing account without the provider credentials  |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}```](doc/api_specification.md#get-v1apiaccountsaccountidchargeschargeid)                 | GET    |  Returns the charge with `chargeId`  belongs to account `accountId` |
|[```/v1/api/accounts/{accountId}/charges```](doc/api_specification.md#post-v1apiaccountsaccountidcharges)                                  | POST    |  Create a new charge for this account `accountId`           |
|[```/v1/api/accounts/{accountId}/charges```](doc/api_specification.md#get-v1apiaccountsaccountidcharges)                                  | GET    |  Searches transactions for this account `accountId` returns JSON or CSV as requested           |
|[```/v1/api/notifications/worldpay```](doc/api_specification.md#post-v1apinotificationsworldpay)                                  | POST |  Handle charge update notifications from Worldpay.            |
|[```/v1/api/notifications/smartpay```](doc/api_specification.md#post-v1apinotificationssmartpay)                                  | POST |  Handle charge update notifications from Smartpay.            |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}/cancel```](doc/api_specification.md#post-v1apiaccountsaccountidchargeschargeidcancel)  | POST    |  Cancels the charge with `chargeId` for account `accountId`           |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}/events```](doc/api_specification.md#post-v1apiaccountsaccountidchargeschargeidevents)  | GET     |  Retrieves all the transaction history for the given `chargeId` of account `accountId`           |
|[```/v1/api/accounts/{accountId}/email-notification```](doc/api_specification.md#post-v1apiaccountsaccountidchargeschargeidcancel)  | POST    |  Updates an email notification template body for account `accountId`           |
|[```/v1/api/accounts/{accountId}/email-notification```](doc/api_specification.md#post-v1apiaccountsaccountidchargeschargeidevents)  | GET     |  Retrieves the email notification template body for the given account `accountId`           |
|[```/v1/api/accounts/{accountId}/email-notification```](doc/api_specification.md#post-v1apiaccountsaccountidchargeschargeidevents)  | PATCH   |  Enables/Disables email notifications for the given account `accountId`           |
|[```/v1/api/accounts/{accountId}/description-analytics-id```](doc/api_specification.md#patch-v1apiaccountsdescriptionanalyticsid)  | PATCH   |  Allows editing description and/or analyticsId for the given account `accountId`           |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}/refunds```](doc/api_specification.md#post-v1apiaccountschargesrefunds)  | POST   |  Submits a refund for a given charge `chargeId` and a given `accountId`           |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}/refunds```](doc/api_specification.md#get-v1apiaccountschargesrefunds)  | GET   |  Retrieves all refunds associated to a charge `chargeId` and a given `accountId`           |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}/refunds/{refundId}```](doc/api_specification.md#get-v1apiaccountschargesrefundsrefundid)  | GET   |  Retrieves a refund by `refundId` for a given charge `chargeId` and a given `accountId`           |

### FRONTEND NAMESPACE

| Path                          | Supported Methods | Description                        |
| ----------------------------- | ----------------- | ---------------------------------- |
|[```/v1/frontend/accounts/{accountId}```](doc/api_specification.md#get-v1frontendaccountsaccountid)              | GET    |  Retrieves an existing account together with the provider credentials             |
|[```/v1/frontend/accounts/{accountId}```](doc/api_specification.md#put-v1frontendaccountsaccountid)              | PUT    |  Update gateway credentials associated with this account             |
|[```/v1/frontend/charges/{chargeId}/status```](doc/api_specification.md#put-v1frontendchargeschargeidstatus)         | PUT    |  Update status of the charge     |
|[```/v1/frontend/charges/{chargeId}```](doc/api_specification.md#get-v1frontendchargeschargeid)                                  | GET |  Find out the status of a charge            |
|[```/v1/frontend/charges/{chargeId}/cards```](doc/api_specification.md#post-v1frontendchargeschargeidcards)                      | POST |  Authorise the charge with the card details            |
|[```/v1/frontend/charges/{chargeId}/capture```](doc/api_specification.md#post-v1frontendchargeschargeidcapture)                      | POST |  Confirm a card charge that was previously authorised successfully.            |
|[```/v1/frontend/charges?gatewayAccountId={gatewayAccountId}```](doc/api_specification.md#get-v1frontendchargesgatewayAccountIdgatewayAccountId)    | GET |  List all transactions for a gateway account     |
|[```/v1/frontend/tokens/{chargeTokenId}/charge```](doc/api_specification.md#get-v1frontendtokenschargetokenid)                                  | GET |  Retrieve information about a secure redirect token.            |
|[```/v1/frontend/tokens/{chargeTokenId}```](doc/api_specification.md#delete-v1frontendtokenschargetokenid)                                  | DELETE |  Delete the secure redirect token.            |

## Licence

[MIT License](LICENSE)

## Responsible Disclosure

GOV.UK Pay aims to stay secure for everyone. If you are a security researcher and have discovered a security vulnerability in this code, we appreciate your help in disclosing it to us in a responsible manner. We will give appropriate credit to those reporting confirmed issues. Please e-mail gds-team-pay-security@digital.cabinet-office.gov.uk with details of any issue you find, we aim to reply quickly.


