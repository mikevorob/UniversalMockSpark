package org.UniMock;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.io.*;
import java.util.Map;
import java.util.Random;
import java.util.regex.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class VarsGenerator {

    private static final Random random = new Random();

    /**
     * Универсальная точка вызова: вызывает нужный генератор по имени.
     * @param type тип генератора, например xmlParse, regexParse, randomNum
     * @param body тело запроса (может быть XML, JSON, текст)
     * @param condition аргумент или выражение для генерации
     */
    public static String generate(String type,
                                  String body,
                                  String condition,
                                  Map<String,String> reqHeaders,
                                  Map<String,String> reqParams,
                                  Map<String,String> reqPathVars) {
        if (type == null) return "";
        type = type.trim();
        try {
            switch (type) {
                case "xmlParse": return xmlParse(body, condition);
                case "jsonParse": return jsonParse(body, condition);
                case "regexParse": return regexParse(body, condition);
                case "randomNum": return randomNum(condition);
                case "randomString": return randomString(condition);
                case "fixed": return fixed(condition);
                case "reqHeader": return reqHeader(reqHeaders, condition);
                case "reqParam": return reqParam(reqParams, condition);
                case "reqPath": return reqPath(reqPathVars, condition);
                default:
                    System.out.println("⚠️ Unknown variable type: " + type);
                    return "";
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error generating var for type=" + type + ": " + e.getMessage());
            return "";
        }
    }

    // --- Реализации генераторов ---

    /** 1️⃣ xmlParse(body, xpath) — возвращает значение по XPath из XML */
    public static String xmlParse(String body, String xpathExpr) throws Exception {
        if (body == null || body.isEmpty() || xpathExpr == null) return "";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(body.getBytes()));

        XPath xpath = XPathFactory.newInstance().newXPath();
        String val = xpath.evaluate(xpathExpr.trim(), doc);
        return val != null ? val : "";
    }

    /** 2️⃣ jsonParse(body, path) — возвращает значение по JSON-пути вида "user/name" */
    public static String jsonParse(String body, String condition) {
        if (body == null || condition == null) return "";
        try {
            JsonNode root = new ObjectMapper().readTree(body);
            String[] parts = condition.split("/");
            JsonNode node = root;
            for (String p : parts) {
                if (p.isEmpty()) continue;
                node = node.path(p);
            }
            return node.isMissingNode() ? "" : node.asText();
        } catch (Exception e) {
            return "";
        }
    }

    /** 3️⃣ regexParse(body, regex) — возвращает первую группу, если найдено совпадение */
    public static String regexParse(String body, String regex) {
        if (body == null || regex == null) return "";
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                if (matcher.groupCount() >= 1) {
                    return matcher.group(1); // возвращаем первую захваченную группу
                } else {
                    return matcher.group(0);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ regexParse failed: " + e.getMessage());
        }
        return "";
    }

    /** 4️⃣ randomNum("1000-9999") — случайное число в диапазоне */
    public static String randomNum(String condition) {
        if (condition == null || condition.isEmpty()) {
            return String.valueOf(random.nextInt(1000));
        }
        String[] parts = condition.split("-");
        int min = 0, max = 999;
        try {
            if (parts.length == 2) {
                min = Integer.parseInt(parts[0].trim());
                max = Integer.parseInt(parts[1].trim());
            } else if (parts.length == 1) {
                max = Integer.parseInt(parts[0].trim());
            }
        } catch (Exception ignored) {}
        int value = min + random.nextInt(max - min + 1);
        return String.valueOf(value);
    }

    /** 5️⃣ randomString("len=8") — случайная строка заданной длины */
    public static String randomString(String condition) {
        int len = 6;
        if (condition != null && condition.startsWith("len=")) {
            try { len = Integer.parseInt(condition.substring(4)); } catch (Exception ignored) {}
        }
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    /** 6️⃣ fixed("Hello") — просто возвращает значение */
    public static String fixed(String condition) {
        return condition == null ? "" : condition.trim();
    }
    public static String reqHeader(Map<String,String> headers, String headerName) {
        if (headers == null || headerName == null) return "";
        return headers.getOrDefault(headerName, "");
    }

    public static String reqParam(Map<String,String> params, String paramName) {
        if (params == null || paramName == null) return "";
        return params.getOrDefault(paramName, "");
    }
    public static String reqPath(Map<String,String> pathVars, String varName) {
        if (pathVars == null || varName == null) return "";
        return pathVars.getOrDefault(varName, "");
    }
}

