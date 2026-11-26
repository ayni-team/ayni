package pe.ayni.paymentsfacade;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Tests only: a payments service on a local port that answers what the test tells it to and keeps
 * every call it received, so a test can read exactly what the monolith forwarded.
 */
final class FakePaymentsService implements AutoCloseable {

  /** A call as the service received it. */
  record Received(String method, String path, Map<String, String> headers, String body) {
    String header(String name) {
      return headers.get(name.toLowerCase());
    }
  }

  /** What the service answers; a delay longer than the monolith's timeout plays a hung service. */
  record Reply(int status, String body, long delayMillis) {
    static Reply json(int status, String body) {
      return new Reply(status, body, 0);
    }
  }

  private final HttpServer server;
  private final List<Received> received = new CopyOnWriteArrayList<>();
  private volatile Function<Received, Reply> answer = call -> Reply.json(500, "{}");

  FakePaymentsService() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.createContext(
        "/",
        exchange -> {
          Map<String, String> headers = new java.util.HashMap<>();
          exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
          Received call =
              new Received(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getPath(),
                  headers,
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          received.add(call);
          Reply reply = answer.apply(call);
          if (reply.delayMillis() > 0) {
            try {
              Thread.sleep(reply.delayMillis());
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
          }
          byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          try {
            exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
              try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
              }
            }
          } catch (IOException clientGone) {
            // The monolith gave up waiting: what a hung service looks like from here.
          } finally {
            exchange.close();
          }
        });
    server.start();
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  void answer(Function<Received, Reply> answer) {
    this.answer = answer;
  }

  List<Received> received() {
    return received;
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
