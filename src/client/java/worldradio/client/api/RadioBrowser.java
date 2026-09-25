package worldradio.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import worldradio.WorldRadio;
import worldradio.client.audio.StationStream;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The free Radio-Browser catalogue (radio-browser.info). The API server is picked once per session from the DNS entry
 * all.api.radio-browser.info (falling back to de1); every request runs off the render thread. Only MP3 stations are
 * listed, since that is all the decoder plays.
 */
public final class RadioBrowser {
    private static final String FALLBACK = "de1.api.radio-browser.info";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static volatile String server;
    private static volatile CompletableFuture<List<Country>> countries;

    public record Country(String name, String code, int stations) {
    }

    public record Region(String name, int stations) {
    }

    public record Station(String name, String url, String country, String state, int bitrate, String tags, int clicks) {
        /** The first {@code n} tags, comma separated. */
        public String firstTags(int n) {
            List<String> out = new ArrayList<>();
            for (String tag : tags.split(",")) {
                if (!tag.isBlank() && out.size() < n) out.add(tag.strip());
            }
            return String.join(", ", out);
        }
    }

    private RadioBrowser() {
    }

    /** Cached for the session after the first successful call. */
    public static CompletableFuture<List<Country>> countries() {
        CompletableFuture<List<Country>> cached = countries;
        if (cached != null && !cached.isCompletedExceptionally()) return cached;
        CompletableFuture<List<Country>> fresh = get("/json/countries?hidebroken=true&order=name").thenApply(json -> {
            List<Country> out = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                String code = str(o, "iso_3166_1");
                if (code.isEmpty()) continue;
                out.add(new Country(str(o, "name"), code, o.get("stationcount").getAsInt()));
            }
            return out;
        });
        countries = fresh;
        return fresh;
    }

    public static CompletableFuture<List<Region>> regions(Country country) {
        return get("/json/states/" + path(country.name()) + "/?hidebroken=true&order=stationcount&reverse=true")
                .thenApply(json -> {
                    List<Region> out = new ArrayList<>();
                    for (JsonElement e : json.getAsJsonArray()) {
                        JsonObject o = e.getAsJsonObject();
                        String name = str(o, "name").strip();
                        if (!name.isEmpty()) out.add(new Region(name, o.get("stationcount").getAsInt()));
                    }
                    return out;
                });
    }

    /** MP3 stations of a country (region null = all regions), most clicked first. */
    public static CompletableFuture<List<Station>> stations(Country country, Region region) {
        String query = "/json/stations/search?countrycode=" + query(country.code())
                + (region == null ? "" : "&state=" + query(region.name()) + "&stateExact=true")
                + "&codec=MP3&hidebroken=true&order=clickcount&reverse=true&limit=300";
        return get(query).thenApply(RadioBrowser::stationList);
    }

    /**
     * MP3 stations whose name or genre tag contains the text: both queries run at once, name matches come first, then
     * the genre matches; within each, stations from the player's own country ({@link #homeCountry}) lead, then the most
     * clicked. A station found by both is listed once.
     */
    public static CompletableFuture<List<Station>> search(String text) {
        String tail = "&codec=MP3&hidebroken=true&order=clickcount&reverse=true&limit=100";
        CompletableFuture<List<Station>> byName = get("/json/stations/search?name=" + query(text) + tail)
                .thenApply(RadioBrowser::stationList);
        CompletableFuture<List<Station>> byTag = get("/json/stations/search?tag=" + query(text) + "&tagExact=false" + tail)
                .thenApply(RadioBrowser::stationList);
        return byName.thenCombine(byTag, (names, tags) -> {
            java.util.Set<String> seen = new java.util.HashSet<>();
            List<Station> out = new ArrayList<>();
            for (List<Station> part : List.of(names, tags)) {
                List<Station> sorted = new ArrayList<>(part);
                String home = homeCountry();
                sorted.sort(java.util.Comparator.comparing((Station st) -> !st.country().equalsIgnoreCase(home))
                        .thenComparing(java.util.Comparator.comparingInt(Station::clicks).reversed()));
                for (Station st : sorted) {
                    if (seen.add(st.url())) out.add(st);
                }
            }
            return out;
        });
    }

    /** Set by the client from the game language (en_us → US) as the fallback when Windows has no region. */
    public static volatile String languageCountry = "";

    /**
     * The player's country code: the region of the system's number and date format (Windows "Region", which Prism's
     * -Duser.language does not touch), else the country part of the game language.
     */
    public static String homeCountry() {
        String region = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT).getCountry();
        return region.isEmpty() ? languageCountry : region;
    }

    private static List<Station> stationList(JsonElement json) {
        List<Station> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            String url = str(o, "url_resolved");
            if (url.isEmpty()) url = str(o, "url");
            if (!url.startsWith("http")) continue;
            out.add(new Station(str(o, "name").strip(), url, str(o, "countrycode"), str(o, "state"),
                    integer(o, "bitrate"), str(o, "tags"), integer(o, "clickcount")));
        }
        return out;
    }

    private static int integer(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsInt();
    }

    private static CompletableFuture<JsonElement> get(String path) {
        return CompletableFuture.supplyAsync(RadioBrowser::server).thenCompose(host -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + host + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", StationStream.userAgent)
                    .GET().build();
            return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }).thenApply(response -> {
            if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
            JsonElement json = JsonParser.parseString(response.body());
            return json.isJsonArray() ? json : new JsonArray();
        });
    }

    /** A random server from all.api.radio-browser.info by reverse DNS, as the API documentation asks. */
    private static String server() {
        String s = server;
        if (s != null) return s;
        try {
            List<String> names = new ArrayList<>();
            for (InetAddress address : InetAddress.getAllByName("all.api.radio-browser.info")) {
                String name = address.getCanonicalHostName();
                if (name.endsWith(".api.radio-browser.info")) names.add(name);
            }
            Collections.shuffle(names);
            s = names.isEmpty() ? FALLBACK : names.getFirst();
        } catch (Exception e) {
            s = FALLBACK;
        }
        WorldRadio.LOGGER.info("Radio-Browser server: {}", s);
        server = s;
        return s;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static String query(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String path(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String describe(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
