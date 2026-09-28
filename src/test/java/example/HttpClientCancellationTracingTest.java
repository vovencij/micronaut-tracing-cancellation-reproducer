package example;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@MicronautTest
class HttpClientCancellationTracingTest {

  private static final Duration CLIENT_TIMEOUT = Duration.ofMillis(250);

  @Inject
  @Client("/")
  HttpClient client;

  @Inject InMemorySpanExporter spanExporter;

  @AfterEach
  void resetExporter() {
    spanExporter.reset();
  }

  @Test
  void successfulRequestFinishesAndExportsClientSpan() {
    client.toBlocking().exchange(HttpRequest.GET("/reproducer/success"), String.class);

    assertThat(finishedClientSpans())
        .as("A successful request must export one finished CLIENT span")
        .hasSize(1);
  }

  @Test
  void cancelledRequestMustFinishAndExportClientSpan() {
    Mono<?> request =
        Mono.from(client.exchange(HttpRequest.GET("/reproducer/slow"), String.class))
            .timeout(CLIENT_TIMEOUT);

    StepVerifier.create(request)
        .expectError(TimeoutException.class)
        .verify(Duration.ofSeconds(5));

    assertThat(finishedClientSpans())
        .as("A cancelled request must export one finished CLIENT span")
        .hasSize(1);
  }

  private List<SpanData> finishedClientSpans() {
    return spanExporter.getFinishedSpanItems().stream()
        .filter(span -> span.getKind() == SpanKind.CLIENT)
        .toList();
  }

  @Controller("/reproducer")
  static class ReproducerController {

    @Get(value = "/success", produces = MediaType.TEXT_PLAIN)
    String success() {
      return "success";
    }

    @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
    Mono<String> slow() {
      return Mono.delay(Duration.ofSeconds(30)).map(ignored -> "too late");
    }
  }

  @Factory
  static class TestTelemetryFactory {

    @Singleton
    InMemorySpanExporter spanExporter() {
      return InMemorySpanExporter.create();
    }

    @Singleton
    @Replaces(OpenTelemetry.class)
    OpenTelemetry openTelemetry(InMemorySpanExporter exporter) {
      SdkTracerProvider tracerProvider =
          SdkTracerProvider.builder()
              .addSpanProcessor(SimpleSpanProcessor.create(exporter))
              .build();
      return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    }
  }
}
