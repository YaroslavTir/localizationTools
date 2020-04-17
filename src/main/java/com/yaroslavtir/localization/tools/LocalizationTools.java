package com.yaroslavtir.localization.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.grep4j.core.Grep4j;
import org.grep4j.core.model.Profile;
import org.grep4j.core.model.ProfileBuilder;
import org.grep4j.core.options.Option;
import org.grep4j.core.result.GrepResults;

class LocalizationTools {
    //docode from http and replace all http escaping
    private CloseableHttpClient httpClient = HttpClients.createDefault();

    private String dir;
    private String regex;
    private boolean useKeyForValue;
    private String language;
    private String yandexKey;

    public LocalizationTools(String dir, String regex, boolean useKeyForValue, String language, String yandexKey) {

        this.dir = dir;
        this.regex = regex;
        this.useKeyForValue = useKeyForValue;
        this.language = language;
        this.yandexKey = yandexKey;
    }

    public void generateI18nJson() throws IOException, JSONException, URISyntaxException {

        List<String> i18nLines = getI18nLinesFromDirectory();
        Map<String, String> translatedProperties = getTranslatedProperties(i18nLines, language);
        System.out.println(String.format("Amount of properties %s", translatedProperties.size()));
        String json = convertToJson(translatedProperties, language, useKeyForValue);
        System.out.println(json);
    }

    private List<String> getI18nLinesFromDirectory() {

        Profile build = ProfileBuilder.newBuilder()
                .name("somename")
                .filePath(dir)
                .onLocalhost().build();
        GrepResults results = Grep4j
                .grep(
                        Grep4j.regularExpression(regex),
                        build,
                        Option.onlyMatching(), Option.recursive(), Option.withFileName()
                );

        return Arrays.asList(StringUtils.split(results.getSingleResult().getText(), "\n"));

    }

    private Map<String, String> getTranslatedProperties(List<String> i18nLines, String lang) throws IOException, JSONException, URISyntaxException {

        boolean translated = StringUtils.isNotEmpty(language);
        Map<String, String> result = new HashMap<>();

        i18nLines.forEach(line -> {
            String fileName = StringUtils.substringBefore(line, ":");
            String value = sanitizeString(StringUtils.substringAfter(line, ","));
            String key;
            if (StringUtils.isEmpty(value)) {
                key = sanitizeString(StringUtils.substringAfter(line, ":"));
            } else {
                key = sanitizeString(StringUtils.substringBetween(line, ":", ","));
            }
            if (value.contains("<%=")) {
                System.out.println(String.format("Formatted value, %s", line));
            }
            if (translated) {
                if ("datatable.language.thousands".equals(key)) {
                    result.put(key, ",");
                } else if ("".equals(value)) {
                    result.put(key, value);
                } else {
                    result.put(key, translate(value, lang));
                }
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

    private String translate(String text, String lang) {

        String textForRequest = text;
        Map<String, String> replacments = new HashMap<>();
        for (int i = 0; textForRequest.contains("<%="); i++) {
            String value = StringUtils.substringBetween(textForRequest, "<%=", "%>");
            String replacementPlaceholder = String.format("d%s", i);
            replacments.put(replacementPlaceholder, value);
            textForRequest = textForRequest.replaceFirst("(<%=)[^&]*(%>)", replacementPlaceholder);
        }
        String translatedText = "";
        try {
            URIBuilder builder = new URIBuilder("https://translate.yandex.net/api/v1.5/tr.json/translate")
                    .setParameter("key", yandexKey)
                    .setParameter("lang", lang)
                    .setParameter("text", textForRequest);
            HttpGet request = new HttpGet(builder.build());
            CloseableHttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject jsonObject = new JSONObject(result);


            translatedText = jsonObject.getJSONArray("text").get(0).toString();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        for (Map.Entry<String, String> entry : replacments.entrySet()) {
            translatedText = StringUtils.replace(translatedText, entry.getKey(), "<%=" + entry.getValue() + "%>");
        }
        return translatedText;
    }

    public String convertToJson(Map<String, String> values, String lang, boolean keyValues) {

        Map<String, Object> map = new TreeMap<>();
        values.forEach((key, value) -> {
            List<String> keyList = Arrays.asList((key).split("\\."));
            Map<String, Object> valueMap = createTree(keyList, map);
            String jsonKey = keyList.get(keyList.size() - 1);
            if (keyValues) {
                valueMap.put(jsonKey, key);
            } else {
                valueMap.put(jsonKey, value);
            }
        });
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        return gson.toJson(map);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createTree(List<String> keys, Map<String, Object> map) {

        Map<String, Object> valueMap;
        try {
            valueMap = (Map<String, Object>) map.get(keys.get(0));
        } catch (Exception e) {
            System.out.println(keys.get(0));
            throw e;
        }
        if (valueMap == null) {
            valueMap = new HashMap<>();
        }
        map.put(keys.get(0), valueMap);
        Map<String, Object> out = valueMap;
        if (keys.size() > 2) {
            out = createTree(keys.subList(1, keys.size()), valueMap);
        }
        return out;
    }

    private String sanitizeString(String key) {

        if (StringUtils.isEmpty(key)) {
            return "";
        }
        key = key.trim();
        key = StringUtils.strip(key, "'");
        key = StringUtils.strip(key, "\"");
        return key;
    }
}