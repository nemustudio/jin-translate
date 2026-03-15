package com.vjup.jintranslate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TranslationService {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final String API = "https://api.mymemory.translated.net/get";

    public static CompletableFuture<String> translate(String text, String from, String to) {
        return getCandidates(text, from, to).thenApply(list -> list == null || list.isEmpty() ? null : list.get(0));
    }

    public static CompletableFuture<List<String>> getCandidates(String text, String from, String to) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String q = URLEncoder.encode(text, StandardCharsets.UTF_8);
                String url = API + "?q=" + q + "&langpair=" + from + "|" + to;

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();

                List<String> results = new ArrayList<>();

                String main = json.getAsJsonObject("responseData").get("translatedText").getAsString();
                results.add(main);

                // TODO: レート制限エラーのハンドリング後でやる
                if (json.has("matches")) {
                    JsonArray matches = json.getAsJsonArray("matches");
                    for (JsonElement el : matches) {
                        JsonObject match = el.getAsJsonObject();
                        if (!match.has("translation")) continue;
                        String t = match.get("translation").getAsString();
                        if (!t.isBlank() && !results.contains(t)) {
                            results.add(t);
                        }
                        if (results.size() >= 3) break;
                    }
                }

                return results;
            } catch (IOException | InterruptedException e) {
                JinTranslateMod.LOGGER.error("translation error", e);
                return null;
            }
        });
    }

    public static boolean isJapanese(String text) {
        for (char c : text.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }
}
