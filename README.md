# pay-connector

The GOV.UK Pay Connector in Java (Dropwizard).

## API Specification

The [Open API Specification](/openapi/connector_spec.yaml) provides details on the paths and operations including
examples.

[View the API specification for connector in Swagger Editor](https://editor.swagger.io/?url=https://raw.githubusercontent.com/alphagov/pay-connector/master/openapi/connector_spec.yaml).

Alternatively, docs can be generated using [Pay API Docs generator](https://github.com/alphagov/pay-api-docs-generator)

## Environment Variables

| Variable                                                        | Default                                    | Purpose                                                                                                                                                                                                                                                                                                                              |
|-----------------------------------------------------------------|--------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AUTH_READ_TIMEOUT_MILLISECONDS`                                | `1000`                                     | the timeout before the resource responds with an awaited auth response (202), so that frontend can choose to show a spinner and poll for auth response. Supports any duration parsable by dropwizard [Duration](https://github.com/dropwizard/dropwizard/blob/master/dropwizard-util/src/main/java/io/dropwizard/util/Duration.java) |
| `SYNCHRONOUS_AUTH_TIMEOUT_IN_MILLISECONDS`                      | `10000`                                    | the timeout before requests made to the synchronous authorisation API for MOTO payments timeout and the payment is failed                                                                                                                                                                                                            |
| `SECURE_WORLDPAY_NOTIFICATION_ENABLED`                          | false                                      | whether to filter incoming notifications by domain; they will be rejected with a 403 unless they match the required domain                                                                                                                                                                                                           |
| `SECURE_WORLDPAY_NOTIFICATION_DOMAIN`                           | `worldpay.com`                             | incoming requests will have a reverse DNS lookup done on their domain. They must resolve to a domain with this suffix (see `DnsUtils.ipMatchesDomain()`)                                                                                                                                                                             |
| `NOTIFY_EMAIL_ENABLED`                                          | false                                      | Whether confirmation emails will be sent using GOV.UK Notify                                                                                                                                                                                                                                                                         |
| `NOTIFY_PAYMENT_RECEIPT_EMAIL_TEMPLATE_ID`                      | -                                          | ID of the email template specified in the GOV.UK Notify to be used for sending emails. An email template can accept personalisation (placeholder values which are passed in by the code).                                                                                                                                            |
| `NOTIFY_API_KEY`                                                | -                                          | API Key for the account created at GOV.UK Notify                                                                                                                                                                                                                                                                                     |
| `NOTIFY_BASE_URL`                                               | `https://api.notifications.service.gov.uk` | Base URL of GOV.UK Notify API to be used                                                                                                                                                                                                                                                                                             |
| `LEDGER_URL`                                                    | -                                          | Base URL of Ledger                                                                                                                                                                                                                                                                                                                   |
| `GDS_CONNECTOR_WORLDPAY_TEST_URL`                               | -                                          | Pointing to the TEST gateway URL of Worldpay payment provider.                                                                                                                                                                                                                                                                       |
| `GDS_CONNECTOR_WORLDPAY_LIVE_URL`                               | -                                          | Pointing to the LIVE gateway URL of Worldpay payment provider.                                                                                                                                                                                                                                                                       |
| `WORLDPAY_3DS_FLEX_DDC_TEST_URL`                                | -                                          | Pointing to Worldpay's TEST Device Data Collection URL.                                                                                                                                                                                                                                                                              |
| `WORLDPAY_3DS_FLEX_DDC_LIVE_URL`                                | -                                          | Pointing to Cardinal Commerce's LIVE Device Data Collection URL.                                                                                                                                                                                                                                                                     |
| `COLLECT_FEE_FEATURE_FLAG`                                      | false                                      | enable or disable collecting fees for the Stripe payment gateway.                                                                                                                                                                                                                                                                    |
| `SANDBOX_ALLOWED_CIDRS`                                         | -                                          | A list of allowed Sandbox CIDRs used for IP firewalling on notifications coming from Sandbox.                                                                                                                                                                                                                                        |
| `STRIPE_ALLOWED_CIDRS`                                          | -                                          | A list of allowed Stripe CIDRs used for IP firewalling on notifications coming from Stripe.                                                                                                                                                                                                                                          |
| `STRIPE_TRANSACTION_FEE_PERCENTAGE`                             | -                                          | percentage of total charge amount to recover GOV.UK Pay platform costs.                                                                                                                                                                                                                                                              |
| `STRIPE_PLATFORM_ACCOUNT_ID`                                    | -                                          | the account ID for the Stripe Connect GOV.UK Pay platform.                                                                                                                                                                                                                                                                           |
| `DISABLE_INTERNAL_HTTPS`                                        | false                                      | disable secure connection for calls to internal APIs                                                                                                                                                                                                                                                                                 |
| `DEFAULT_DO_NOT_RETRY_EMITTING_EVENT_UNTIL_DURATION_IN_SECONDS` | 7200                                       | Sets the default duration in seconds for events (emitted by parity checker worker) until which the emitted events sweeper ignores to re-emit. Value can be overridden by passing `do_not_retry_emit_until` query parameter to parity checker worker or historical event emitter tasks                                                |
| `EMIT_PAYOUT_EVENTS`                                            | false                                      | enable or disable emitting payout specific events to payment queue                                                                                                                                                                                                                                                                   |
| `BIND_HOST`                                                     | `127.0.0.1`                                | The IP address for the application to bind to                                                                                                                                                                                                                                                                                        |

### Queues

| Variable                                       | Default | Purpose                                                                                                 |
|------------------------------------------------|---------|---------------------------------------------------------------------------------------------------------|
| `AWS_SQS_REGION`                               | -       | SQS capture queue region                                                                                |
| `AWS_SQS_CAPTURE_QUEUE_URL`                    | -       | SQS capture queue URL                                                                                   |
| `AWS_SQS_EVENT_QUEUE_URL`                      | -       | SQS event queue URL                                                                                     |
| `AWS_SQS_PAYOUT_RECONCILE_QUEUE_URL`           | -       | SQS payout reconcile queue URL                                                                          | 
| `AWS_SQS_NON_STANDARD_SERVICE_ENDPOINT`        | false   | Set to true to use non standard (eg: http://my-own-sqs-endpoint) SQS endpoint                           |
| `AWS_SQS_ENDPOINT`                             | -       | URL that is the entry point for SQS. Only required when AWS_SQS_NON_STANDARD_SERVICE_ENDPOINT is `true` |
| `AWS_SECRET_KEY`                               | -       | Secret key. Only required when AWS_SQS_NON_STANDARD_SERVICE_ENDPOINT is `true`                          |
| `AWS_ACCESS_KEY`                               | -       | Access key. Only required when AWS_SQS_NON_STANDARD_SERVICE_ENDPOINT is `true`                          |
| `AWS_SQS_MESSAGE_MAXIMUM_WAIT_TIME_IN_SECONDS` | `20`    | Maximum wait time for long poll message requests to queue.                                              |
| `AWS_SQS_MESSAGE_MAXIMUM_BATCH_SIZE`           | `10`    | Maximum number of messages that should be received in an individual message batch.                      |

### Expunger

| Variable                                                        | Default | Purpose                                                                                                                                      |
|-----------------------------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `EXPUNGE_EXCLUDE_CHARGES_OR_REFUNDS_PARITY_CHECKED_WITHIN_DAYS` | 7       | Exclude charges or refunds from expunging if parity checked within the configured days                                                       |
| `EXPUNGE_CHARGES_ENABLED`                                       | false   | Set to true to enable expunging charges (in expungeable state)                                                                               |
| `EXPUNGE_NO_OF_CHARGES_PER_TASK_RUN`                            | 15000   | Number of charges to expunge each time expunge resource endpoint is invoked                                                                  |
| `EXPUNGE_CHARGES_OLDER_THAN_DAYS`                               | 7       | Expunge charges older than 7 days (or as configured) based on created date                                                                   | 
| `EXPUNGE_HISTORIC_CHARGE_EXCEPTIONS_OLDER_THAN_DAYS`            | 90      | Number of days after which charges in a certain state (ex: CAPTURE_SUBMITTED for charge) can be expunged, even when not in expungeable state |
| `EXPUNGE_REFUNDS_ENABLED`                                       | false   | Set to true to enable expunging refunds in terminal state                                                                                    |
| `EXPUNGE_NO_OF_REFUNDS_PER_TASK_RUN`                            | 1000    | Number of refunds to expunge each time expunge resource endpoint is invoked                                                                  |
| `EXPUNGE_REFUNDS_OLDER_THAN_DAYS`                               | 7       | Expunge refunds older than 7 days (or as configured) based on created date                                                                   |
| `EXPUNGE_HISTORIC_REFUND_EXCEPTIONS_OLDER_THAN_DAYS`            | 90      | Number of days after which refunds in a certain state (ex: REFUND_SUBMITTED) can be expunged, even when not in terminal state                |

### Background captures

The background capture mechanism will capture all payments in the `CAPTURE_APPROVED` state that have been published to
the SQS queue (`AWS_SQS_CAPTURE_QUEUE_URL`).

A background thread managed by dropwizard runs on all connector nodes. It polls the SQS capture queue to retrieve the
list of charges that are waiting to be captured.

If a capture attempt fails it will be retried again after a specified delay (
`CAPTURE_PROCESS_FAILED_CAPTURE_RETRY_DELAY_IN_SECONDS`).
It is achieved by setting up the visibility timeout with the delay value which prevents consumers from receiving the
message.
After this timeout the message becomes visible for consumers again.
More information of how the visibility timeout works can be
found [here](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html).

The following variables control the background process:

| Variable                                                  | Default      | Purpose                                                                                                                                                                                                                                                                                              |
|-----------------------------------------------------------|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `BACKGROUND_PROCESSING_ENABLED`                           | `true`       | enables registering scheduled processes - at the moment it includes only queue based capture methods                                                                                                                                                                                                 |
| `CHARGES_CONSIDERED_OVERDUE_FOR_CAPTURE_AFTER`            | `60 minutes` | this value is used for calculating the metric gauge of messages awaiting capture (not attempted within this interval)                                                                                                                                                                                |
| `CAPTURE_PROCESS_MAXIMUM_RETRIES`                         | `96`         | connector keeps track of the number of times capture has been attempted for each charge. If a charge fails this number of times or more it will be marked as a permanent failure. An error log message will be written as well. This should *never* happen and if it does it should be investigated. |
| `CAPTURE_PROCESS_FAILED_CAPTURE_RETRY_DELAY_IN_SECONDS`   | `3600`       | the duration in seconds that a message should be deferred before it should be retried.                                                                                                                                                                                                               |
| `CAPTURE_PROCESS_QUEUE_SCHEDULER_THREAD_DELAY_IN_SECONDS` | `1`          | the duration in seconds that the queue message receiver should wait between running threads.                                                                                                                                                                                                         |
| `CAPTURE_PROCESS_QUEUE_SCHEDULER_NUMBER_OF_THREADS`       | `1`          | the number of polling threads started by the queue message scheduler.                                                                                                                                                                                                                                |

## Graceful shutdown

When the connector is being stopped it needs to gracefully terminate its background tasks (managed in
`QueueMessageReceiver`).
The main concern is to drain the in-memory queue (StateTransitionQueue) that stores all the state transition events.
Killing the `stateTransitionMessageExecutorService` of QueueMessageReceiver (that reads from this in-memory queue)
without making sure the queue is empty would cause state transition events to be lost and Ledger not having the full
history of changes that happened to the payment.
The current logic will check whether the emitter process is ready for shutdown (by verifying whether the queue is
empty) before actually invoking it.
In order to make sure that the `StateTransitionEmitterProcess` is shutdown eventually, there is a limit to the number of
attempts to check readiness for shutdown.

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

To run the integration tests, the `DOCKER_HOST` and `DOCKER_CERT_PATH` environment variables must be set up correctly.
On OS X the environment can be set up with:

## Contract tests

`$GDS_CONNECTOR_WORLDPAY_PASSWORD` and`$GDS_CONNECTOR_WORLDPAY_PASSWORD` environment variable must be set for Worldpay
contract tests.

```
    eval $(boot2docker shellinit)
    eval $(docker-machine env <virtual-machine-name>)

```

The command to run all the tests is:

```
    mvn verify
```

## Command line tasks

There are a number of
[commands](https://www.dropwizard.io/en/latest/manual/core.html#commands)
which can run from the command line. Invoke the all-in-one jar to see a list
of the commands:

```
$ java -jar target/pay-connector-0.1-SNAPSHOT-allinone.jar
```

* `waitOnDependencies [-h] [file]` - Waits for dependent resources to become available

  positional arguments: `file` - application configuration file

* `render-state-transition-graph` - Outputs a representation of the connector state
  transitions as a graphviz 'dot' file

## SQS Queues

Connector currently has three SQS queues, one of these is used to propagate payment events to Ledger, and the other two
are used to distribute events between running instances of connector.

#### Event Queue

The event queue is used to send payment event information to Ledger (such as the payment has been refuned, payment
details have been entered), connector pushes these events onto the queue and Ledger retrieves the on the other end.

#### Capture Queue & Payout Reconcile Queue

These queues are used to distribute events amongst instances of connector for processing, connector places the events
onto the queue and then reads off the end of the queue, processing each event as it reads them. This replaced a legacy
implementation where a database table was used and connector would scan the database looking for events to process,
using a lock to 'claim' an event for processing. The new implementation removes the needs for locks as each connector
instance takes the next event on the queue and the distribution is managed by logic within the queue. As connector
scales out more instances of connector poll the queue for new events.

## Licence

[MIT License](LICENSE)

## Vulnerability Disclosure

GOV.UK Pay aims to stay secure for everyone. If you are a security researcher and have discovered a security
vulnerability in this code, we appreciate your help in disclosing it to us in a responsible manner. Please refer to
our [vulnerability disclosure policy](https://www.gov.uk/help/report-vulnerability) and
our [security.txt](https://vdp.cabinetoffice.gov.uk/.well-known/security.txt) file for details.
