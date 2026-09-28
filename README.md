# Micronaut HTTP client cancellation tracing reproducer

This stand-alone project reproduces an unfinished OpenTelemetry HTTP client span when Reactor cancels a Micronaut HTTP request.

It does not use Kubernetes. This keeps the test focused on `OpenTelemetryClientFilter` from `micronaut-tracing-opentelemetry-http`.

## Requirements

- JDK 25 or newer
- Internet access for the first Gradle dependency download

## Run the reproducer

```shell
./gradlew test
```

The build is expected to fail in `cancelledRequestMustFinishAndExportClientSpan`.

The control test sends a successful request. It finds one finished `CLIENT` span and passes.

The failing test sends a request to an endpoint that responds after 30 seconds. Reactor applies a 250 millisecond timeout and cancels the HTTP request. The test expects one finished `CLIENT` span, but the exporter receives no client span.

A typical failure is:

```text
A cancelled request must export one finished CLIENT span
Expected size: 1 but was: 0
```

## Cause

`OpenTelemetryClientFilter` starts the client instrumenter. It ends the instrumenter from `doOnNext` and `doOnError` only.

Reactor `timeout` sends a cancellation upstream. It creates the `TimeoutException` downstream. The tracing filter receives the cancellation, but it does not receive a response or an error. The client span remains unfinished and is not exported.

## Version

The default Micronaut Platform version is in `gradle.properties`. To test a different platform version, run:

```shell
./gradlew test -PmicronautPlatformVersion=5.0.2
```

The following combinations reproduce the same failure:

- Micronaut Platform 5.1.5 with Micronaut Tracing 8.2.0
- Micronaut Platform 5.0.2 with Micronaut Tracing 8.0.0

## Expected fix

The HTTP client tracing filter must call `Instrumenter.end(...)` exactly once for response, error, empty completion, and cancellation signals. A cancellation can use `CancellationException` as the terminal error.

After that fix, both tests must pass.

## Project isolation

This directory has its own `settings.gradle.kts` and Gradle wrapper. The parent project does not include it as a module.
