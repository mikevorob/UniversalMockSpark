package org.UniMock;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppLogic {

    // Конфигурация
    public static int PORT = 4567;
    public static int THREAD_MAX = 50;
    public static int THREAD_MIN = 10;
    public static int THREAD_IDLE_TIMEOUT = 30000;
    public static String TEMPLATE_PATH = "templates/";

    private static final Random random = new Random();
    private static final Map<String, Template> templateCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Template> endpointCache = new ConcurrentHashMap<>();
    private static final org. slf4j.Logger logger = LoggerFactory.getLogger(AppLogic.class);



    public static String preLaunch(String[] args) {
        String configPath = "config.properties";
        if (args != null) {
            for (String arg : args) {
                if (arg.startsWith("config=")) {
                    configPath = arg.substring("config=".length());
                }
            }
        }
// ← если ничего не указано, ставим WARN


        System.out.println("Config file: " + configPath);
        System.out.println("Log level set to: " + System.getProperty("log_level"));


        Properties props = new Properties();
        File configFile = new File(configPath);
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
            PORT = Integer.parseInt(props.getProperty("port", String.valueOf(PORT)));
            THREAD_MAX = Integer.parseInt(props.getProperty("thread.max", String.valueOf(THREAD_MAX)));
            THREAD_MIN = Integer.parseInt(props.getProperty("thread.min", String.valueOf(THREAD_MIN)));
            THREAD_IDLE_TIMEOUT = Integer.parseInt(props.getProperty("thread.idleTimeout", String.valueOf(THREAD_IDLE_TIMEOUT)));
            TEMPLATE_PATH = props.getProperty("templates.path", TEMPLATE_PATH);
            System.out.println("✅ Config loaded from " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("⚠️ Using default config, file not found: " + configFile.getAbsolutePath());
        }

        loadAllTemplates();
        return "SparkApp started at " + Instant.now();
    }

    private static void loadAllTemplates() {
        try {
            File folder = new File(TEMPLATE_PATH);
            if (!folder.exists() || !folder.isDirectory()) {
                System.out.println("⚠️ Templates folder not found: " + folder.getAbsolutePath());
                return;
            }

            Files.walk(folder.toPath())
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            Template t = parseTemplate(content);
                            if (t != null) {
                                String key = t.method.toUpperCase() + " " + t.endpoint;
                                templateCache.put(key, t);
                                System.out.println("✅ Loaded template: " + key);
                            }
                        } catch (Exception e) {
                            System.out.println("❌ Failed to parse template " + path + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.out.println("⚠️ Error reading templates: " + e.getMessage());
        }
    }

    private static Template parseTemplate(String content) {
        Template t = new Template();
        String[] lines = content.split("\n");
        if (lines.length == 0) return null;

        String[] first = lines[0].trim().split(" ", 2);
        if (first.length < 2) return null;
        t.method = first[0].trim();
        t.endpoint = first[1].trim();

        Map<String, List<String>> sections = new LinkedHashMap<>();
        String currentSection = "default";

        // Список известных секций + флаг "уже начата"
        Map<String, Boolean> KNOWN_SECTIONS = new LinkedHashMap<>();
        KNOWN_SECTIONS.put("Additional Headers", false);
        KNOWN_SECTIONS.put("Vars", false);
        KNOWN_SECTIONS.put("Error Config", false);
        KNOWN_SECTIONS.put("Error body", false);
        KNOWN_SECTIONS.put("Success body", false);
        KNOWN_SECTIONS.put("Response time", false);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.endsWith(":")) {
                String candidate = line.substring(0, line.length() - 1).trim();
                // Проверяем, что это известная секция и она ещё не начиналась
                if (KNOWN_SECTIONS.containsKey(candidate) && !KNOWN_SECTIONS.get(candidate)) {
                    currentSection = candidate;
                    KNOWN_SECTIONS.put(candidate, true); // флаг "секция началась"
                    sections.putIfAbsent(currentSection, new ArrayList<>());
                    continue;
                }
            }

            sections.computeIfAbsent(currentSection, k -> new ArrayList<>()).add(line);
        }

        // --- Обработка секций ---
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            String section = entry.getKey();
            List<String> body = entry.getValue();

            switch (section) {
                case "Additional Headers":
                    for (String line : body) {
                        if (line.contains(":")) {
                            String[] p = line.split(":", 2);
                            HeaderDef h = new HeaderDef();
                            h.name = p[0].trim();
                            String[] parts = p[1].split(";", 2);
                            h.type = parts[0].trim();
                            h.condition = parts.length > 1 ? parts[1].trim() : "";
                            t.headers.put(h.name, h);
                        }
                    }
                    break;

                case "Vars":
                    for (String line : body) {
                        if (line.contains(":")) {
                            String[] p = line.split(":", 2);
                            VarDef v = new VarDef();
                            v.name = p[0].trim();
                            String[] parts = p[1].split(";", 2);
                            v.type = parts[0].trim();
                            v.condition = parts.length > 1 ? parts[1].trim() : "";
                            t.bodyVars.put(v.name, v);
                        }
                    }
                    break;

                case "Error Config":
                    for (String line : body) {
                        if (line.startsWith("Percent:"))
                            t.errorPercent = Integer.parseInt(line.replace("Percent:", "").replace("%", "").trim());
                        else if (line.startsWith("Status:"))
                            t.errorStatus = Integer.parseInt(line.replace("Status:", "").trim());
                    }
                    break;

                case "Error body":
                    t.errorBody = String.join("\n", body).trim();
                    break;

                case "Success body":
                    t.successBody = String.join("\n", body).trim();
                    break;

                case "Response time":
                    if (body != null && !body.isEmpty()) {
                        // Берём первую непустую строку секции и парсим её как long
                        String candidate = null;
                        for (String s : body) {
                            if (s != null && !s.trim().isEmpty()) {
                                candidate = s.trim();
                                break;
                            }
                        }
                        if (candidate != null) {
                            try {
                                long val = Long.parseLong(candidate);
                                if (val >= 0) {
                                    t.responseTimeMs = val;
                                } else {
                                    // отрицательное значение не имеет смысла — игнорируем
                                    logger.warn("Invalid Response Time (negative) in template '{}': {}", t.endpoint, candidate);
                                }
                            } catch (NumberFormatException ex) {
                                logger.warn("Invalid Response Time value in template '{}': {}", t.endpoint, candidate);
                            }
                        } else {
                            // секция пуста — оставляем значение по умолчанию (-1)
                            logger.debug("Response Time section is empty in template '{}'", t.endpoint);
                        }
                    } else {
                        // секция отсутствует/пустая — ничего не делаем
                    }
                    break;

                default:
                    // Игнорируем неизвестные секции
                    break;
            }
        }

        return t;
    }

    public static Template getTemplate(String method, String endpoint) {
        String key = method + " " + endpoint;

        // 1. Проверяем быстрый кэш соответствий
        Template cached = endpointCache.get(key);
        if (cached != null) return cached;

        // 2. Пробуем точное совпадение
        Template exact = templateCache.get(key);
        if (exact != null) {
            endpointCache.put(key, exact);
            return exact;
        }

        // 3. Ищем подходящий шаблон по паттернам
        Template matched = findMatchingTemplate(method, endpoint);
        if (matched != null) {
            endpointCache.put(key, matched);
        }
        return matched;
    }

    private static Template findMatchingTemplate(String method, String requestUri) {
        for (Map.Entry<String, Template> entry : templateCache.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(" ", 2);
            if (parts.length != 2) continue;
            String m = parts[0];
            String pattern = parts[1];

            if (!m.equalsIgnoreCase(method)) continue;

            // Преобразуем шаблон в regex: :var → ([^/]+), * → .*
            String regex = Pattern.quote(pattern)
                    .replace("\\*", ".*")
                    .replaceAll(":(\\w+)", "([^/]+)")
                    .replace("\\?", "\\?");

            Pattern compiled = Pattern.compile("^" + regex + "$");
            Matcher matcher = compiled.matcher(requestUri);

            if (matcher.matches()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String processTemplate(String template, Map<String,String> vars) {
        return new StringSubstitutor(vars, "$", "$").replace(template);
    }

    public static ResponseData buildResponse(
            String method,
            String endpoint,
            Map<String,String> reqVars,
            Map<String,String> reqHeaders,
            Map<String,String> reqParams,
            Map<String,String> reqPathVars) {
        long start = System.currentTimeMillis();

        Template t = getTemplate(method, endpoint);
        if (t == null)
            return new ResponseData(404, "{\"error\":\"template not found\"}", Map.of());

        Map<String,String> finalVars = new HashMap<>(reqVars);

        // Генерация переменных из шаблона
        for (VarDef v : t.bodyVars.values()) {
            String val = VarsGenerator.generate(
                    v.type,
                    reqVars.getOrDefault("body", ""),
                    v.condition,
                    reqHeaders,
                    reqParams,
                    reqPathVars
            );
            finalVars.put(v.name, val);
        }

        // Определяем, будет ли ошибка
        boolean isError = new Random().nextInt(100) < t.errorPercent;
        String bodyTemplate = isError ? t.errorBody : t.successBody;
        int status = isError ? t.errorStatus : 200;

        // Подставляем переменные в шаблон
        String body = processTemplate(bodyTemplate, finalVars);

        // Генерация заголовков ответа
        Map<String,String> headers = new HashMap<>();
        t.headers.forEach((k,h) -> headers.put(k, "auto"));
        long elapsed = System.currentTimeMillis() - start;
        long delay = t.responseTimeMs - elapsed;

        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {}
        } else if (t.responseTimeMs > 0) {
            logger.warn("Response time exceeded: expected {}ms, actual {}ms for {} {}",
                    t.responseTimeMs, elapsed, method, endpoint);
        }

        return new ResponseData(status, body, headers);
    }

    public static Map<String, String> extractPathVars(String method, String requestUri) {
        Map<String, String> vars = new HashMap<>();

        // ищем шаблон, соответствующий методу и endpoint'у
        Template t = findMatchingTemplate(method, requestUri);
        if (t == null) return vars;

        String[] templateParts = t.endpoint.split("/");
        String[] requestParts = requestUri.split("/");

        for (int i = 0; i < Math.min(templateParts.length, requestParts.length); i++) {
            String tp = templateParts[i];
            if (tp.startsWith(":")) {
                vars.put(tp.substring(1), requestParts[i]);
            }
        }
        return vars;
    }
}


