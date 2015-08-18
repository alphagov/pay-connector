# pay-connector
The Charges Connector in Java (Dropwizard)

## Integration tests

To run the integration tests, the `DOCKER_HOST` and `DOCKER_CERT_PATH` environment variables must be set up correctly. On OS X, with boot2docker, this can be done like this:

```
    eval $(boot2docker shellinit)
```

The command to run the integration tests is:

```
    mvn test-integration
```