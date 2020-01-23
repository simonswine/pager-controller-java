package dev.swine.kubernetes.pagercontroller.tlkio;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TlkIo {

    private String username;
    private String channel;
    private OkHttpClient httpClient;
    private String baseURL;
    private String chatId;
    private String csrf;
    private static final Logger log = LoggerFactory.getLogger(TlkIo.class);


    public TlkIo(String channel, String username) {
        this.channel = channel;
        this.username = username;
        this.baseURL = "https://tlk.io";
        this.chatId = null;
        this.csrf = null;

        // intialise http client
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.cookieJar(new MyCookieJar());
        this.httpClient = builder.build();
    }

    private void connect() throws IOException {
        JsonNode user = this.getUser();
        TlkIoSocket s = new TlkIoSocket("wss://ws.tlk.io", this.chatId, user);
        s.run();
    }

    public void close() {
        this.httpClient.connectionPool().evictAll();
    }

    public void say(String text) throws IOException {
        if (this.csrf == null || this.chatId == null) {
            this.getUser();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonFactory factory = new JsonFactory();
        JsonGenerator generator = factory.createGenerator(outputStream, JsonEncoding.UTF8);
        generator.writeStartObject();
        generator.writeStringField("body", text);
        generator.writeBooleanField("expired", false);
        generator.writeEndObject();
        generator.close();

        String url = this.baseURL + "/" + this.channel;
        Request request = new Request.Builder()
                .url(this.baseURL + "/api/chats/" + this.chatId + "/messages")
                .addHeader("Referer", url)
                .addHeader("X-CSRF-Token'", this.csrf)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.get("application/json"), outputStream.toString()))
                .build();

        Response response = this.httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }
        response.close();
        log.info("sent message '"+ outputStream.toString() + "' successfully");

    }

    private JsonNode getUser() throws IOException {
        String url = this.baseURL + "/" + this.channel;

        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = this.httpClient.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }
        log.info("initial request successful");
        String body = response.body().string();
        response.close();

        // find csrf
        Pattern regexCsrf = Pattern.compile("<meta name=\"csrf-token\" content=\"(.*?)\" />");
        Matcher matchCsrf = regexCsrf.matcher(body);
        if (matchCsrf.find()) {
            this.csrf = matchCsrf.group(1);
            log.info("got csrf=" + this.csrf);
        } else {
            throw new IOException("No CSRF found");
        }

        // find chat id
        Pattern regexChatId = Pattern.compile("Talkio.Variables.chat_id = '(.*?)'");
        Matcher matchChatId = regexChatId.matcher(body);
        if (matchChatId.find()) {
            this.chatId = matchChatId.group(1);
            log.info("got chat_id=" + this.chatId);
        } else {
            throw new IOException("No chat id found");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonFactory factory = new JsonFactory();
        JsonGenerator generator = factory.createGenerator(outputStream, JsonEncoding.UTF8);
        generator.writeStartObject();
        generator.writeStringField("nickname", this.username);
        generator.writeEndObject();
        generator.close();

        // request user object
        request = new Request.Builder()
                .url(this.baseURL + "/api/participant")
                .addHeader("Referer", url)
                .addHeader("X-CSRF-Token'", this.csrf)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.get("application/json"), outputStream.toString()))
                .build();

        response = this.httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }

        body = response.body().string();
        response.close();
        log.info("got respose user=" + body);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode userNode = objectMapper.readTree(body);
        userNode.findPath("avatar");
        return userNode;
    }

    public static void main(String[] args) throws IOException {
        TlkIo tlkio = new TlkIo("test-building-operators", "my-test-user");
        tlkio.say("Hello world!");
        tlkio.say("What a time to be a controller...");
        tlkio.close();
    }

}
