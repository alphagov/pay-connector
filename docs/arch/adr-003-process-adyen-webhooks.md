# ADR 003 - How to Process Adyen Webhooks

## Context

Currently, we have multiple dedicated webhooks for each endpoint, the team wants investigate whether a single endpoint would be better to handle all Adyen webhooks.

Adyen sends different webhooks for various events. Adyen doesn’t support configuring one webhook to handle all events. Instead, we need to configure separate webhooks with differences below:

- Structure - event payload is different for [payment events](https://docs.adyen.com/development-resources/webhooks/webhook-types#standard-webhooks), [platform events](https://docs.adyen.com/development-resources/webhooks/webhook-types#platforms-webhooks), reporting, [recurring tokens](https://docs.adyen.com/development-resources/webhooks/webhook-types#other-webhooks) and so on.
- HMAC signature - the signature to verify the payload is in different places. For payment events (Standard webhooks), it is in the body (additionalData), and for recurring tokens and platform webhooks the signature is in the header.
- HMAC Key - For each webhook, a separate HMAC key is generated to validate the signature. There is also a possibility to re-use the HMAC key created in another webhook.

This RFC is to evaluate whether to:

- Use a single webhook endpoint to receive all Adyen event types and route them internally.
- Use multiple dedicated webhook endpoints, each responsible for a specific set of events or domains.
- Whether we can use the same HMAC Key for the next webhook event

## Decision

**Use a single webhook endpoint to receive all Adyen event types and route them internally**

Pros:
- Uses one central entry point to manage, filter, and route all external web traffic coming into Connector.
- An issue with Connector is that it's very big and complicated, having 1 Adyen notifications endpoint is a step towards simplifying the code.

Cons:
- Violates single responsibility principle
- Single point of failure

Security considerations

- No traffic isolation, a surge in payments could overwhelm the endpoint causing dropped or delayed Platform and Token webhooks. 
However, I'm not sure how likely this is to happen AND we're using SQS. 
This endpoint would solely be responsible for receiving the request, validation then pushing the payload to TaskQueueService.java.

We came to our decision by the following discussion points...
- The same validation and testing are required for every endpoint. This multiplies the effort required, whereas our focus should be on handling the business logic.
- A single endpoint would help in the long term to reduced duplication, clear module to maintain, especially if we think there's likelihood of future Adyen services evolving and new endpoints being needed for different scenarios/journeys.
- It would probably be easier to split it out into several later than to roll several into one, if either approach proved troublesome.
- For security considerations, the use of the AWS service along with the reduced likelihood of a surge to the endpoint means that such a situation could be weathered if it did happen.

**We will not be reusing HMAC keys for multiple webhook events**

We decided not to reuse HMAC keys, from a perspective of security and reducing the complication of inconsistency and maintenance that would come from needing to manage and keep track of which 
HMAC keys are being reused and which aren't.

## Status

Accepted.