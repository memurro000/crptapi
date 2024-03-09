package com.murro.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

//Следовало бы вынести обработку исключений за пределы класса,
//но в задании упомянуто, что класс не должен выбрасывать исключений
//при превышении лимита, так что для простоты тестирования 
//я обработал все исключения, помимо неправильного URI, внутри,
//хоть это и скажется на читаемости.
public class CrptApi{

    private final String CREATE_URI = "https://ismp.crpt.ru/api/v3/lk/documents/create"; 

    private final TimeUnit timeUnit;
    private final int requestLimit;

    private final HttpClient client;
    private final URI create_uri;
    private final Gson serializer;

    private volatile int requestCount;
    private volatile long lastResetTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) throws URISyntaxException {
        client = HttpClient.newHttpClient();
        create_uri = new URI(CREATE_URI);
        serializer = new Gson();

        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;

        requestCount = 0;
        lastResetTime = System.currentTimeMillis();
    }

    //В задании не было указано, по каким ключам должны быть доступны подпись и документ в записи,
    //документацию API честного знака мне также найти не удалось,
    //поэтому расположил их под ключами document и sign.
    public synchronized void insert(Object document, String sign) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("document", serializer.toJsonTree(document));
        jsonObject.add("sign", serializer.toJsonTree(sign));

        String json = serializer.toJson(jsonObject);
        System.err.println(json);
        HttpRequest request = 
            HttpRequest.newBuilder()
            .uri(create_uri)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        sendAsyncAndHandleResponse(request);
    }

    private synchronized void waitIfNeeded() {
        try{
            long currentTime = System.currentTimeMillis();

            if(currentTime - lastResetTime >= timeUnit.toMillis(1)){
                requestCount = 0;
                lastResetTime = currentTime;
            }
            if(requestCount >= requestLimit){
                wait(timeUnit.toMillis(1) - (currentTime - lastResetTime));
                waitIfNeeded();
            }
        }
        catch(InterruptedException ex){
            System.err.print("Interrupted while waiting!");
            System.err.println(ex.getMessage());
        }
    }

    private synchronized void handleResponse(HttpResponse<String> resp) {
        if(resp.statusCode() == 200){
            System.out.println(resp.body());
        } 
        else{
            System.err.println("Failed with HTTP error " + resp.statusCode());
        }
    }

    private void sendAsyncAndHandleResponse(HttpRequest request) {
        try{
            waitIfNeeded();
            ++requestCount;

            CompletableFuture<HttpResponse<String>> futureResponse = 
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            futureResponse.thenAccept(resp -> handleResponse(resp));
            futureResponse.get();
        }
        catch(ExecutionException | InterruptedException ex){
            System.err.print("Interrupted while requesting!");
            System.err.println(ex.getMessage());
        }
    }

}






