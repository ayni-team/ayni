package pe.ayni.sessions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * A participant at the live channel of a whiteboard, over a real WebSocket, the way the web
 * application will be (TS08).
 *
 * <p>It keeps every message it received with the moment it arrived, and rebuilds the board the way
 * ADR 0009 says a client must: start from the board it was sent, then apply the operations in
 * version order.
 */
final class BoardClient implements AutoCloseable {

  /** A message and when it arrived, in {@link System#nanoTime()}. */
  record Received(JsonNode message, long atNanos) {}

  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * A browser takes a message of any size; the Tomcat client defaults to 8 KB, which a whole board
   * exceeds. Raised to what the server's own channel takes in the other direction and more.
   */
  private static final jakarta.websocket.WebSocketContainer CONTAINER = container();

  private static jakarta.websocket.WebSocketContainer container() {
    jakarta.websocket.WebSocketContainer container =
        jakarta.websocket.ContainerProvider.getWebSocketContainer();
    container.setDefaultMaxTextMessageBufferSize(8 * 1024 * 1024);
    return container;
  }

  private final BlockingQueue<Received> inbox = new LinkedBlockingQueue<>();
  private final List<Received> seen = new ArrayList<>();
  private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
  private final WebSocketSession socket;

  private BoardClient(WebSocketSession socket) {
    this.socket = socket;
  }

  /** Opens the channel with the token as the browser sends it: the subprotocol pair. */
  static BoardClient connect(int port, java.util.UUID sessionId, String token) throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setSecWebSocketProtocol(List.of("bearer", token));
    return connect(port, sessionId, headers);
  }

  /** Opens the channel with the token in {@code Authorization}, as a non browser client may. */
  static BoardClient connectWithHeader(int port, java.util.UUID sessionId, String token)
      throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setBearerAuth(token);
    return connect(port, sessionId, headers);
  }

  private static BoardClient connect(int port, java.util.UUID sessionId, WebSocketHttpHeaders headers)
      throws Exception {
    BoardClient[] client = new BoardClient[1];
    CompletableFuture<BoardClient> ready = new CompletableFuture<>();
    TextWebSocketHandler handler =
        new TextWebSocketHandler() {
          @Override
          public void afterConnectionEstablished(WebSocketSession session) {
            client[0] = new BoardClient(session);
            ready.complete(client[0]);
          }

          @Override
          protected void handleTextMessage(WebSocketSession session, TextMessage message)
              throws Exception {
            long now = System.nanoTime();
            ready.get().inbox.add(new Received(JSON.readTree(message.getPayload()), now));
          }

          @Override
          public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            ready.thenAccept(opened -> opened.closed.complete(status));
          }
        };
    new StandardWebSocketClient(CONTAINER)
        .execute(
            handler,
            headers,
            URI.create(
                "ws://localhost:" + port + "/api/v1/sessions/" + sessionId + "/whiteboard/live"))
        .get(5, TimeUnit.SECONDS);
    return ready.get(5, TimeUnit.SECONDS);
  }

  /** Sends a {@code PUT_ELEMENT} of a stroke and returns the operation's id. */
  String draw(String elementId) throws Exception {
    String operationId = "op-" + java.util.UUID.randomUUID();
    send(
        """
        {"type": "operation", "operation": {"id": "%s", "action": "PUT_ELEMENT",
         "element": {"id": "%s", "type": "stroke", "data": {"points": [[1, 2], [3, 4]]}}}}
        """
            .formatted(operationId, elementId));
    return operationId;
  }

  /** Sends a {@code PUT_ELEMENT} with the given data, as JSON, and returns the operation's id. */
  String put(String elementId, String dataJson) throws Exception {
    String operationId = "op-" + java.util.UUID.randomUUID();
    send(
        """
        {"type": "operation", "operation": {"id": "%s", "action": "PUT_ELEMENT",
         "element": {"id": "%s", "type": "stroke", "data": %s}}}
        """
            .formatted(operationId, elementId, dataJson));
    return operationId;
  }

  String remove(String elementId) throws Exception {
    String operationId = "op-" + java.util.UUID.randomUUID();
    send(
        """
        {"type": "operation", "operation": {"id": "%s", "action": "REMOVE_ELEMENT",
         "elementId": "%s"}}
        """
            .formatted(operationId, elementId));
    return operationId;
  }

  synchronized void send(String message) throws Exception {
    socket.sendMessage(new TextMessage(message));
  }

  /** Waits for the first message that matches, keeping every one it reads on the way. */
  Received await(Predicate<JsonNode> matching, Duration within) throws InterruptedException {
    for (Received earlier : List.copyOf(seen)) {
      if (matching.test(earlier.message())) {
        return earlier;
      }
    }
    long deadline = System.nanoTime() + within.toNanos();
    while (true) {
      long left = deadline - System.nanoTime();
      Received next = left <= 0 ? null : inbox.poll(left, TimeUnit.NANOSECONDS);
      if (next == null) {
        throw new AssertionError("No matching whiteboard message within " + within);
      }
      seen.add(next);
      if (matching.test(next.message())) {
        return next;
      }
    }
  }

  Received awaitBoard(Duration within) throws InterruptedException {
    return await(message -> "board".equals(message.path("type").asText()), within);
  }

  Received awaitApplied(String operationId, Duration within) throws InterruptedException {
    return await(
        message ->
            "applied".equals(message.path("type").asText())
                && operationId.equals(message.path("operation").path("id").asText()),
        within);
  }

  Received awaitError(Duration within) throws InterruptedException {
    return await(message -> "error".equals(message.path("type").asText()), within);
  }

  /** Reads whatever has arrived until nothing more comes for a moment. */
  void drain(Duration quiet) throws InterruptedException {
    Received next;
    while ((next = inbox.poll(quiet.toMillis(), TimeUnit.MILLISECONDS)) != null) {
      seen.add(next);
    }
  }

  /**
   * The board this participant sees: the last board it was sent, then the operations after its
   * version, in version order. Element ids in the order they were first drawn.
   */
  Map<String, JsonNode> board() {
    JsonNode base = null;
    for (Received received : seen) {
      if ("board".equals(received.message().path("type").asText())) {
        base = received.message();
      }
    }
    if (base == null) {
      throw new AssertionError("The board was never received");
    }
    long version = base.path("version").asLong();
    Map<String, JsonNode> elements = new LinkedHashMap<>();
    base.path("elements").forEach(element -> elements.put(element.path("id").asText(), element));
    final long from = version;
    seen.stream()
        .map(Received::message)
        .filter(message -> "applied".equals(message.path("type").asText()))
        .filter(message -> message.path("version").asLong() > from)
        .sorted(java.util.Comparator.comparingLong(message -> message.path("version").asLong()))
        .forEach(
            message -> {
              JsonNode operation = message.path("operation");
              switch (operation.path("action").asText()) {
                case "PUT_ELEMENT" ->
                    elements.put(
                        operation.path("element").path("id").asText(), operation.path("element"));
                case "REMOVE_ELEMENT" -> elements.remove(operation.path("elementId").asText());
                case "CLEAR" -> elements.clear();
                default -> throw new AssertionError("Unknown action " + operation);
              }
            });
    return elements;
  }

  /** The highest version this participant has received. */
  long version() {
    return seen.stream()
        .map(Received::message)
        .mapToLong(message -> message.path("version").asLong(0))
        .max()
        .orElse(0);
  }

  CloseStatus awaitClosed(Duration within) throws Exception {
    return closed.get(within.toMillis(), TimeUnit.MILLISECONDS);
  }

  boolean isOpen() {
    return socket.isOpen();
  }

  @Override
  public void close() throws Exception {
    if (socket.isOpen()) {
      socket.close();
    }
  }
}
