package com.github.copilot.demo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.tool.CopilotTool;
import com.github.copilot.tool.Param;

/**
 * A tool that provides live weather information via Open-Meteo (no API key required).
 * The LLM will invoke this method when asked about weather.
 */
public class WeatherTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @CopilotTool("Get the current weather for a given city")
    public String getWeather(
            @Param(value = "The city to get weather for", required = true) String city,
            @Param(value = "Temperature unit: celsius or fahrenheit", required = false, defaultValue = "celsius") String unit) {

        System.out.println();
        System.out.println(">>> TOOL INVOKED: getWeather");
        System.out.println(">>>   city = " + city);
        System.out.println(">>>   unit = " + unit);
        System.out.println();

        try {
            // Step A: Geocode the city name to lat/lon via Open-Meteo
            String encoded = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=1";
            String geoJson = fetch(geoUrl);
            JsonNode geoRoot = MAPPER.readTree(geoJson);
            JsonNode results = geoRoot.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return "Could not find coordinates for city: " + city;
            }
            double lat = results.get(0).path("latitude").asDouble();
            double lon = results.get(0).path("longitude").asDouble();
            String resolvedName = results.get(0).path("name").asText(city);
            String country = results.get(0).path("country").asText("");

            System.out.println(">>>   Geocoded: " + resolvedName + ", " + country
                    + " (" + lat + ", " + lon + ")");

            // Step B: Fetch current weather from Open-Meteo
            String tempUnit = "fahrenheit".equalsIgnoreCase(unit) ? "fahrenheit" : "celsius";
            String weatherUrl = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code"
                    + "&temperature_unit=" + tempUnit
                    + "&wind_speed_unit=kmh";
            String weatherJson = fetch(weatherUrl);
            JsonNode wxRoot = MAPPER.readTree(weatherJson);
            JsonNode current = wxRoot.path("current");

            double temp = current.path("temperature_2m").asDouble();
            int humidity = current.path("relative_humidity_2m").asInt();
            double wind = current.path("wind_speed_10m").asDouble();
            int code = current.path("weather_code").asInt();
            String condition = describeWeatherCode(code);
            String unitLabel = "fahrenheit".equalsIgnoreCase(unit) ? "°F" : "°C";

            String result = "Weather in " + resolvedName + ", " + country + ": "
                    + condition + ", " + temp + unitLabel
                    + ", humidity " + humidity + "%, wind " + wind + " km/h";

            System.out.println(">>> TOOL RETURNING: " + result);
            System.out.println();
            return result;

        } catch (Exception e) {
            String error = "Error fetching weather for " + city + ": " + e.getMessage();
            System.out.println(">>> TOOL ERROR: " + error);
            return error;
        }
    }

    private String fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " from " + url);
        }
        return response.body();
    }

    private static String describeWeatherCode(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snowfall";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown (code " + code + ")";
        };
    }
}
