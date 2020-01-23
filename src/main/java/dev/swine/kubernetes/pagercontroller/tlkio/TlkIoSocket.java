package dev.swine.kubernetes.pagercontroller.tlkio;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

final class TlkIoSocket extends WebSocketListener {
    private static final Logger log = LoggerFactory.getLogger(TlkIoSocket.class);
    private static final int NORMAL_CLOSURE_STATUS = 1000;
    private String url;
    private String chatId;
    private JsonNode user;

    private static final String CONTROL_PREFIX = "5:::";

    TlkIoSocket(String url, String chatId, JsonNode user) {
        this.url = url;
        this.chatId = chatId;
        this.user = user;
    }

    void run() {
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        Request request = new Request.Builder()
                .url(this.url)
                .build();
        client.newWebSocket(request, this);

        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        client.dispatcher().executorService().shutdown();
    }

    private String subscribeMessage() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonFactory factory = new JsonFactory();
        JsonGenerator generator = factory.createGenerator(outputStream, JsonEncoding.UTF8);
        generator.writeStartObject();
        generator.writeStringField("name", "subscribe");
        generator.writeArrayFieldStart("args");
        generator.writeStartObject();
        generator.writeStringField("chat_id", this.chatId);
        generator.writeStringField("user_info", null);
        generator.writeEndObject();
        generator.writeEndArray();
        generator.writeEndObject();
        generator.close();
        log.debug("subscribe message generated: " + outputStream.toString());
        return CONTROL_PREFIX + outputStream.toString();
    }

    private String authenticateMessage() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        factory.setCodec(mapper);
        JsonGenerator generator = factory.createGenerator(outputStream, JsonEncoding.UTF8);
        generator.writeStartObject();
        generator.writeStringField("name", "authenticate");
        generator.writeArrayFieldStart("args");
        generator.writeTree(this.user);
        generator.writeEndArray();
        generator.writeEndObject();
        generator.close();
        log.debug("authenticate message generated: " + outputStream.toString());
        return CONTROL_PREFIX + outputStream.toString();
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        try {
            webSocket.send(subscribeMessage());
            webSocket.send(authenticateMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        log.debug("Receiving : " + text);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        log.debug("Receiving bytes : " + bytes.hex());
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocket.close(NORMAL_CLOSURE_STATUS, null);
        log.debug("Closing : " + code + " / " + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        log.debug("Error : " + t.getMessage());
    }
}
