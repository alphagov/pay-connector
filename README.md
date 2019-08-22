# pay-connector
The GOV.UK Pay Connector in Java (Dropwizard)


## Environment Variables

| Variable | Default | Purpose |
|---------|---------|---------|
| `AUTH_READ_TIMEOUT_SECONDS` | `10 seconds` | the timeout before the resource responds with an awaited auth response (202), so that frontend can choose to show a spinner and poll for auth response. Supports any duration parsable by dropwizard [Duration](https://github.com/dropwizard/dropwizard/blob/master/dropwizard-util/src/main/java/io/dropwizard/util/Duration.java)|
| `SECURE_WORLDPAY_NOTIFICATION_ENABLED` | false | whether to filter incoming notifications by domain; they will be rejected with a 403 unless they match the required domain |
| `SECURE_WORLDPAY_NOTIFICATION_DOMAIN` | `worldpay.com` | incoming requests will have a reverse DNS lookup done on their domain. They must resolve to a domain with this suffix (see `DnsUtils.ipMatchesDomain()`) |
| `NOTIFY_EMAIL_ENABLED` | false | Whether confirmation emails will be sent using GOV.UK Notify |
| `NOTIFY_PAYMENT_RECEIPT_EMAIL_TEMPLATE_ID` | - | ID of the email template specified in the GOV.UK Notify to be used for sending emails. An email template can accept personalisation (placeholder values which are passed in by the code). |
| `NOTIFY_API_KEY` | - | API Key for the account created at GOV.UK Notify |
| `NOTIFY_BASE_URL` | `https://api.notifications.service.gov.uk` | Base URL of GOV.UK Notify API to be used|
| `GDS_CONNECTOR_WORLDPAY_3DS_FLEX_CHALLENGE_TEST_URL` | `https://secure-test.worldpay.com/shopper/3ds/challenge.html` | Pointing to Worldpay's TEST 3ds flex challenge URL. |
| `GDS_CONNECTOR_WORLDPAY_3DS_FLEX_CHALLENGE_LIVE_URL` | - | Pointing to Worldpay's LIVE 3ds flex challenge URL. |
| `GDS_CONNECTOR_WORLDPAY_TEST_URL` | - | Pointing to the TEST gateway URL of Worldpay payment provider. |
| `GDS_CONNECTOR_WORLDPAY_LIVE_URL` | - | Pointing to the LIVE gateway URL of Worldpay payment provider. |
| `GDS_CONNECTOR_SMARTPAY_TEST_URL` | - | Pointing to the TEST gateway URL of Smartpay payment provider. |
| `GDS_CONNECTOR_SMARTPAY_LIVE_URL` | - | Pointing to the LIVE gateway URL of Smartpay payment provider. |
| `GDS_CONNECTOR_EPDQ_TEST_URL` | - | Pointing to the TEST gateway URL of ePDQ payment provider. |
| `GDS_CONNECTOR_EPDQ_LIVE_URL` | - | Pointing to the LIVE gateway URL of ePDQ payment provider. |
| `COLLECT_FEE_FEATURE_FLAG` | false | enable or disable collecting fees for the Stripe payment gateway. |
| `STRIPE_TRANSACTION_FEE_PERCENTAGE` | - | percentage of total charge amount to recover GOV.UK Pay platform costs. |
| `STRIPE_PLATFORM_ACCOUNT_ID` | - | the account ID for the Stripe Connect GOV.UK Pay platform. |


### Queues
| Variable | Default | Purpose |
|---------|---------|---------|
| `AWS_SQS_REGION`            | - | SQS capture queue region |
| `AWS_SQS_CAPTURE_QUEUE_URL` | - | SQS capture queue URL  |
| `AWS_SQS_NON_STANDARD_SERVICE_ENDPOINT`  | false | Set to true to use non standard (eg: http://my-own-sqs-endpoint) SQS endpoint |
| `AWS_SQS_ENDPOINT`          | - |  URL that is the entry point for SQS. Only required when AWS_SQS_NON_STANDARD_SERVICE_ENDPOINT is `true` |
| `AWS_SECRET_KEY`            | - | Secret key. Only required when AWS_SQS_NON_STANDARD_SERVICE_ENDPOINT is `true` |
| `AWS_ACCESS_KEY`            | - | Access key. Only required when AWS_SQS_NON_STANDARD_SERVICE_ENDPOINT is `true`|
| `AWS_SQS_MESSAGE_MAXIMUM_WAIT_TIME_IN_SECONDS` | `20` | Maximum wait time for long poll message requests to queue. |
| `AWS_SQS_MESSAGE_MAXIMUM_BATCH_SIZE` | `10` | Maximum number of messages that should be received in an individual message batch. |

### Background captures

The background capture mechanism will capture all payments in the `CAPTURE_APPROVED` state that have been published to
the SQS queue (`AWS_SQS_CAPTURE_QUEUE_URL`).

A background thread managed by dropwizard runs on all connector nodes. It polls the SQS capture queue to retrieve the
 list of charges that are waiting to be captured.

If a capture attempt fails it will be retried again after a specified delay (`CAPTURE_PROCESS_FAILED_CAPTURE_RETRY_DELAY_IN_SECONDS`).
It is achieved by setting up the visibility timeout with the delay value which prevents consumers from receiving the message.
After this timeout the message becomes visible for consumers again.
More information of how the visibility timeout works can be found [here](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html).

The following variables control the background process:

| Variable | Default | Purpose |
|---------|---------|---------|
| `BACKGROUND_PROCESSING_ENABLED` | `true` | enables registering scheduled processes - at the moment it includes only queue based capture methods |
| `CHARGES_CONSIDERED_OVERDUE_FOR_CAPTURE_AFTER` | `60 minutes` | this value is used for calculating the metric gauge of messages awaiting capture (not attempted within this interval) |
| `CAPTURE_PROCESS_MAXIMUM_RETRIES` | `96` | connector keeps track of the number of times capture has been attempted for each charge. If a charge fails this number of times or more it will be marked as a permanent failure. An error log message will be written as well. This should *never* happen and if it does it should be investigated. |
| `CAPTURE_PROCESS_FAILED_CAPTURE_RETRY_DELAY_IN_SECONDS` | `3600` | the duration in seconds that a message should be deferred before it should be retried. |
| `CAPTURE_PROCESS_QUEUE_SCHEDULER_THREAD_DELAY_IN_SECONDS` | `1` | the duration in seconds that the queue message receiver should wait between running threads. |
| `CAPTURE_PROCESS_QUEUE_SCHEDULER_NUMBER_OF_THREADS` | `1` | the number of polling threads started by the queue message scheduler. |

## Graceful shutdown
When the connector is being stopped it needs to gracefully terminate its background tasks (managed in `QueueMessageReceiver`).
The main concern it to drain the in-memory queue that stores all the state transition events. Killing the emitter task
(that reads from this in-memory queue) without making sure the queue is empty would cause state transition events to be
lost and Ledger not having the full history of changes that happened to the payment.
The current logic will check whether the emitter process is being ready for shutdown (by verifying whether the queue is
empty) before actually invoking it.
In order to make sure that it eventually happens there is a limit to the number of checks.

Example log from the connector shutdown:
 ```shell script
[2019-08-27 10:53:01.231] [thread=Thread-1] [logger=u.g.p.c.p.s.CardExecutorService] - Shutting down CardExecutorService
[2019-08-27 10:53:01.285] [thread=Thread-1] [logger=u.g.p.c.p.s.CardExecutorService] - Awaiting for CardExecutorService threads to terminate
[2019-08-27 10:53:01.369] [thread=Thread-11] [logger=o.e.j.s.AbstractConnector] - Stopped application@72fedd85{HTTP/1.1,[http/1.1]}{0.0.0.0:9300}
[2019-08-27 10:53:01.400] [thread=Thread-11] [logger=o.e.j.s.AbstractConnector] - Stopped admin@5cd9439a{HTTP/1.1,[http/1.1]}{0.0.0.0:9301}
[2019-08-27 10:53:01.405] [thread=Thread-11] [logger=o.e.j.s.h.ContextHandler] - Stopped i.d.j.MutableServletContextHandler@2b843043{/,null,UNAVAILABLE}
[2019-08-27 10:53:01.455] [thread=Thread-11] [logger=o.e.j.s.h.ContextHandler] - Stopped i.d.j.MutableServletContextHandler@35ac70a{/,null,UNAVAILABLE}
[2019-08-27 10:53:01.460] [thread=Thread-11] [logger=u.g.p.c.q.m.QueueMessageReceiver] - State transition receiver is not ready for shutdown
[2019-08-27 10:53:01.514] [thread=Thread-11] [logger=u.g.p.c.q.m.QueueMessageReceiver] - State transition receiver is not ready for shutdown
[2019-08-27 10:53:01.561] [thread=payment-state-transition-message-poller] [logger=u.g.p.c.e.StateTransitionEmitterProcess] - Emitted new state transition event for [eventId=0] [eventType=PaymentCreated]
[2019-08-27 10:53:01.563] [thread=payment-state-transition-message-poller] [logger=u.g.p.c.e.StateTransitionEmitterProcess] - Emitted new state transition event for [eventId=9] [eventType=PaymentCreated]
[2019-08-27 10:53:01.564] [thread=Thread-11] [logger=u.g.p.c.q.m.QueueMessageReceiver] - State transition receiver is not ready for shutdown
[2019-08-27 10:53:01.566] [thread=payment-state-transition-message-poller] [logger=u.g.p.c.e.StateTransitionEmitterProcess] - Emitted new state transition event for [eventId=8] [eventType=PaymentCreated]
[2019-08-27 10:53:01.568] [thread=payment-state-transition-message-poller] [logger=u.g.p.c.e.StateTransitionEmitterProcess] - Emitted new state transition event for [eventId=7] [eventType=PaymentCreated]
[2019-08-27 10:53:01.570] [thread=payment-state-transition-message-poller] [logger=u.g.p.c.e.StateTransitionEmitterProcess] - Emitted new state transition event for [eventId=6] [eventType=PaymentCreated]
[2019-08-27 10:53:01.573] [thread=payment-state-transition-message-poller] [logger=u.g.p.c.e.StateTransitionEmitterProcess] - Emitted new state transition event for [eventId=5] [eventType=PaymentCreated]
[2019-08-27 10:53:01.574] [thread=payment-state-transition-message-poller] [logger=u.g.p.c.e.StateTransitionEmitterProcess] - Emitted new state transition event for [eventId=4] [eventType=PaymentCreated]
[2019-08-27 10:53:01.576] [thread=payment-state-transition-message-poller] [logger=u.g.p.c.e.StateTransitionEmitterProcess] - Emitted new state transition event for [eventId=3] [eventType=PaymentCreated]
[2019-08-27 10:53:01.578] [thread=payment-state-transition-message-poller] [logger=u.g.p.c.e.StateTransitionEmitterProcess] - Emitted new state transition event for [eventId=2] [eventType=PaymentCreated]
[2019-08-27 10:53:01.580] [thread=payment-state-transition-message-poller] [logger=u.g.p.c.e.StateTransitionEmitterProcess] - Emitted new state transition event for [eventId=1] [eventType=PaymentCreated]
[2019-08-27 10:53:01.617] [thread=Thread-11] [logger=u.g.p.c.q.m.QueueMessageReceiver] - State transition receiver - number of not processed messages 0

```

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
    mvn verify
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



