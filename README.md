# pay-connector
The GOV.UK Pay Connector in Java (Dropwizard)

  
## Environment Variables

| Varible | Default | Purpose |
|---------|---------|---------|
| `AUTH_READ_TIMEOUT_SECONDS` | `10 seconds` | the timeout before the resource responds with an awaited auth response (202), so that frontend can choose to show a spinner and poll for auth response. Supports any duration parsable by dropwizard [Duration](https://github.com/dropwizard/dropwizard/blob/master/dropwizard-util/src/main/java/io/dropwizard/util/Duration.java)|
| `SECURE_WORLDPAY_NOTIFICATION_ENABLED` | false | whether to filter incoming notifications by domain; they will be rejected with a 403 unless they match the required domain |
| `SECURE_WORLDPAY_NOTIFICATION_DOMAIN` | `worldpay.com` | incoming requests will have a reverse DNS lookup done on their domain. They must resolve to a domain with this suffix (see `DnsUtils.ipMatchesDomain()`) |
| `NOTIFY_EMAIL_ENABLED` | false | Whether confirmation emails will be sent using GOV.UK Notify |
| `NOTIFY_PAYMENT_RECEIPT_EMAIL_TEMPLATE_ID` | - | ID of the email template specified in the GOV.UK Notify to be used for sending emails. An email template can accept personalisation (placeholder values which are passed in by the code). |
| `NOTIFY_API_KEY` | - | API Key for the account created at GOV.UK Notify |
| `NOTIFY_BASE_URL` | `https://api.notifications.service.gov.uk` | Base URL of GOV.UK Notify API to be used|
| `GDS_CONNECTOR_WORLDPAY_TEST_URL` | - | Pointing to the TEST gateway URL of Worldpay payment provider. |
| `GDS_CONNECTOR_WORLDPAY_LIVE_URL` | - | Pointing to the LIVE gateway URL of Worldpay payment provider. |
| `GDS_CONNECTOR_SMARTPAY_TEST_URL` | - | Pointing to the TEST gateway URL of Smartpay payment provider. |
| `GDS_CONNECTOR_SMARTPAY_LIVE_URL` | - | Pointing to the LIVE gateway URL of Smartpay payment provider. |
| `GDS_CONNECTOR_EPDQ_TEST_URL` | - | Pointing to the TEST gateway URL of ePDQ payment provider. |
| `GDS_CONNECTOR_EPDQ_LIVE_URL` | - | Pointing to the LIVE gateway URL of ePDQ payment provider. |
| `ASYNCHRONOUS_CAPTURE` | true | whether to handle capture asynchronously. When asynchronous capture is enabled, capture requests are deferred and operated in batch by a background task  |
| `COLLECT_FEE_FEATURE_FLAG` | false | enable or disable collecting fees for the Stripe payment gateway. |
| `STRIPE_TRANSACTION_FEE_PERCENTAGE` | - | percentage of total charge amount to recover GOV.UK Pay platform costs. |
| `STRIPE_PLATFORM_ACCOUNT_ID` | - | the account ID for the Stripe Connect GOV.UK Pay platform. |

### Background captures

The background capture mechanism will capture all payments in the `CAPTURE_APPROVED` state. 

A background thread managed by dropwizard runs on all connector nodes. It polls the database periodically to check for payments which need to be captured.

The polling interval is a random value between 150-200 seconds.

If a capture attempt fails it will be retried again after a specified delay (`CAPTURE_PROCESS_RETRY_FAILURES_EVERY`).

The following variables control the background process: 

| Varible | Default | Purpose |
|---------|---------|---------|
| `CAPTURE_PROCESS_BATCH_SIZE` | `10` | limits the batch window size processed at each polling attempt. If connector is not managing to clear the queue of captures, increase this value. |
| `CAPTURE_PROCESS_RETRY_FAILURES_EVERY` | `60 minutes` | a failed capture attempt will be returned to the queue, and will not be retried until this time has passed |
| `CAPTURE_PROCESS_MAXIMUM_RETRIES` | `48` | connector keeps track of the number of times capture has been attempted for each charge. If a charge fails this number of times or more it will be marked as a permanent failure. An error log message will be written as well. This should *never* happen and if it does it should be investigated. |

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

The [API Specification](docs/api_specification.md) provides more detail on the paths and operations including examples.

### Tasks namespace

| Path                          | Supported Methods | Description                        |
| ----------------------------- | ----------------- | ---------------------------------- |
|[```/v1/tasks/expired-charges-sweep```](docs/api_specification.md#post-v1tasksexpired-charges-sweep)  | POST    |  Spawns a task to expire charges with a default window of 90 minutes|   

### Command line tasks

There are a number of
[commands](http://www.dropwizard.io/1.1.0/docs/manual/core.html#commands)
which can run from the command line. Invoke the all-in-one jar to see a list
of the commands:

```
$ java -jar target/pay-connector-0.1-SNAPSHOT-allinone.jar
```

* `waitOnDependencies [-h] [file]` - Waits for dependent resources to become available

   positional arguments: `file` - application configuration file

* `render-state-transition-graph` - Outputs a representation of the connector state 
                                    transitions as a graphviz 'dot' file

### API namespace

| Path                          | Supported Methods | Description                        |
| ----------------------------- | ----------------- | ---------------------------------- |
|[```/v1/api/accounts```](docs/api_specification.md#post-v1apiaccounts)              | POST    |  Create a new account to associate charges with            |
|[```/v1/api/accounts```](docs/api_specification.md#get-v1apiaccounts)              | GET    |  Retrieves a collection of all the accounts |
|[```/v1/api/accounts/{gatewayAccountId}```](docs/api_specification.md#get-v1apiaccountsaccountsid)     | GET    |  Retrieves an existing account without the provider credentials  |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}```](docs/api_specification.md#get-v1apiaccountsaccountidchargeschargeid)                 | GET    |  Returns the charge with `chargeId`  belongs to account `accountId` |
|[```/v1/api/accounts/{accountId}/charges```](docs/api_specification.md#post-v1apiaccountsaccountidcharges)                                  | POST    |  Create a new charge for this account `accountId`           |
|[```/v1/api/accounts/{accountId}/charges```](docs/api_specification.md#get-v1apiaccountsaccountidcharges)                                  | GET    |  Searches transactions for this account `accountId` returns JSON or CSV as requested           |
|[```/v1/api/accounts/{accountId}/refunds```](docs/api_specification.md#get-v1apiaccountsaccountidrefunds)                                  | GET    |  Retrieves all refunds for this account `accountId`           |
|[```/v1/api/notifications/worldpay```](docs/api_specification.md#post-v1apinotificationsworldpay)                                  | POST |  Handle charge update notifications from Worldpay.            |
|[```/v1/api/notifications/smartpay```](docs/api_specification.md#post-v1apinotificationssmartpay)                                  | POST |  Handle charge update notifications from Smartpay.            |
|[```/v1/api/notifications/epdq```](docs/api_specification.md#post-v1apinotificationsepdq)                                  | POST |  Handle charge update notifications from ePDQ.                |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}/cancel```](docs/api_specification.md#post-v1apiaccountsaccountidchargeschargeidcancel)  | POST    |  Cancels the charge with `chargeId` for account `accountId`           |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}/events```](docs/api_specification.md#post-v1apiaccountsaccountidchargeschargeidevents)  | GET     |  Retrieves all the transaction history for the given `chargeId` of account `accountId`           |
|[```/v1/api/accounts/{accountId}/email-notification```](docs/api_specification.md#post-v1apiaccountsaccountidchargeschargeidevents)  | PATCH   |  Changes settings for email notifications for the given account `accountId`           |
|[```/v1/api/accounts/{accountId}/description-analytics-id```](docs/api_specification.md#patch-v1apiaccountsdescriptionanalyticsid)  | PATCH   |  Allows editing description and/or analyticsId for the given account `accountId`           |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}/refunds```](docs/api_specification.md#post-v1apiaccountschargesrefunds)  | POST   |  Submits a refund for a given charge `chargeId` and a given `accountId`           |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}/refunds```](docs/api_specification.md#get-v1apiaccountschargesrefunds)  | GET   |  Retrieves all refunds associated to a charge `chargeId` and a given `accountId`           |
|[```/v1/api/accounts/{accountId}/charges/{chargeId}/refunds/{refundId}```](docs/api_specification.md#get-v1apiaccountschargesrefundsrefundid)  | GET   |  Retrieves a refund by `refundId` for a given charge `chargeId` and a given `accountId`           |
|[```/v1/api/accounts/{accountId}/transactions-summary```](docs/api_specification.md#get-v1apiaccountsaccountidtransactions-summary)|GET|Retrieves payment summary totals for a given `accountId`
|[```/v1/api/accounts/{accountId}/stripe-setup```](docs/api_specification.md#get-v1apiaccountsaccountidstripe-setup)|GET|Retrieves which Stripe Connect account setup tasks have been completed for a given `accountId`
|[```/v1/api/accounts/{accountId}/stripe-setup```](docs/api_specification.md#post-v1apiaccountsaccountidstripe-setup)|POST|Updates which Stripe Connect account setup tasks have been completed for a given `accountId`
|[```/v1/api/reports/performance-report```](docs/api_specification.md#get-v1apireportsperformance-report)|GET|Retrieves performance summary |
|[```/v1/api/reports/gateway-account-performance-report```](docs/api_specification.md#get-v1apireportsgateway-account-performance-report)|GET|Retrieves performance summary segmented by gateway account |
|[```/v1/api/reports/daily-performance-report```](docs/api_specification.md#get-v1apireportsdaily-performance-report)|GET|Retrieves performance summary for a given day |

### Frontend namespace

| Path                          | Supported Methods | Description                        |
| ----------------------------- | ----------------- | ---------------------------------- |
|[```/v1/frontend/accounts/{accountId}```](docs/api_specification.md#get-v1frontendaccountsaccountid)              | GET    |  Retrieves an existing account together with the provider credentials             |
|[```/v1/frontend/accounts/{accountId}```](docs/api_specification.md#put-v1frontendaccountsaccountid)              | PUT    |  Update gateway credentials associated with this account             |
|[```/v1/frontend/charges/{chargeId}/status```](docs/api_specification.md#put-v1frontendchargeschargeidstatus)         | PUT    |  Update status of the charge     |
|[```/v1/frontend/charges/{chargeId}```](docs/api_specification.md#get-v1frontendchargeschargeid)                                  | GET |  Find out the status of a charge            |
|[```/v1/frontend/charges/{chargeId}/cards```](docs/api_specification.md#post-v1frontendchargeschargeidcards)                      | POST |  Authorise the charge with the card details            |
|[```/v1/frontend/charges/{chargeId}/capture```](docs/api_specification.md#post-v1frontendchargeschargeidcapture)                      | POST |  Confirm a card charge that was previously authorised successfully.            |
|[```/v1/frontend/charges?gatewayAccountId={gatewayAccountId}```](docs/api_specification.md#get-v1frontendchargesgatewayAccountIdgatewayAccountId)    | GET |  List all transactions for a gateway account     |
|[```/v1/frontend/tokens/{chargeTokenId}/charge```](docs/api_specification.md#get-v1frontendtokenschargetokenid)                                  | GET |  Retrieve information about a secure redirect token.            |
|[```/v1/frontend/tokens/{chargeTokenId}```](docs/api_specification.md#delete-v1frontendtokenschargetokenid)                                  | DELETE |  Delete the secure redirect token.            |

## Licence

[MIT License](LICENSE)

## Responsible Disclosure

GOV.UK Pay aims to stay secure for everyone. If you are a security researcher and have discovered a security vulnerability in this code, we appreciate your help in disclosing it to us in a responsible manner. We will give appropriate credit to those reporting confirmed issues. Please e-mail gds-team-pay-security@digital.cabinet-office.gov.uk with details of any issue you find, we aim to reply quickly.



