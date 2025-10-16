package org.UniMock;
import org.apache.commons.text.StringSubstitutor;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AppLogic {

    // Конфигурация
    public static int PORT = 4567;
    public static int THREAD_MAX = 50;
    public static int THREAD_MIN = 10;
    public static int THREAD_IDLE_TIMEOUT = 30000;
    public static String TEMPLATE_PATH = "templates/";

    private static final Random random = new Random();
    private static final Map<String, Template> templateCache = new ConcurrentHashMap<>();

    public static String preLaunch(String[] args) {
        String configPath = "config.properties";
        if (args != null) {
            for (String arg : args) {
                if (arg.startsWith("config=")) {
                    configPath = arg.substring("config=".length());
                    break;
                }
            }
        }

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

        String section = "";
        StringBuilder bodyBuffer = new StringBuilder();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.endsWith(":") && !line.startsWith("Error body") && !line.startsWith("Success body")) {
                section = line.replace(":", "").trim();
                continue;
            }

            switch (section) {
                case "Additional Headers":
                    if (line.contains(":")) {
                        String[] p = line.split(":", 2);
                        HeaderDef h = new HeaderDef();
                        h.name = p[0].trim();
                        String[] parts = p[1].split(";", 2);
                        h.type = parts[0].trim();
                        h.condition = parts.length > 1 ? parts[1].trim() : "";
                        t.headers.put(h.name, h);
                    }
                    break;
                case "Body vars":
                    if (line.contains(":")) {
                        String[] p = line.split(":", 2);
                        VarDef v = new VarDef();
                        v.name = p[0].trim();
                        String[] parts = p[1].split(";", 2);
                        v.type = parts[0].trim();
                        v.condition = parts.length > 1 ? parts[1].trim() : "";
                        t.bodyVars.put(v.name, v);
                    }
                    break;
                default:
                    if (line.startsWith("Error percent:"))
                        t.errorPercent = Integer.parseInt(line.replace("Error percent:", "").replace("%", "").trim());
                    else if (line.startsWith("Error Status:"))
                        t.errorStatus = Integer.parseInt(line.replace("Error Status:", "").trim());
                    else if (line.startsWith("Error body:")) {
                        section = "Error body";
                        bodyBuffer = new StringBuilder();
                    } else if (line.startsWith("Success body:")) {
                        if (section.equals("Error body"))
                            t.errorBody = bodyBuffer.toString().trim();
                        section = "Success body";
                        bodyBuffer = new StringBuilder();
                    } else if (section.equals("Error body") || section.equals("Success body"))
                        bodyBuffer.append(line).append("\n");
                    break;
            }
        }

        if (section.equals("Error body")) t.errorBody = bodyBuffer.toString().trim();
        else if (section.equals("Success body")) t.successBody = bodyBuffer.toString().trim();
        return t;
    }

    public static Template getTemplate(String method, String endpoint) {
        return templateCache.get(method.toUpperCase() + " " + endpoint);
    }

    private static String processTemplate(String template, Map<String,String> vars) {
        return new StringSubstitutor(vars, "$", "$").replace(template);
    }

    public static ResponseData buildResponse(
            String method,
            String endpoint,
            Map<String,String> reqVars,
            Map<String,String> reqHeaders,
            Map<String,String> reqParams) {

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
                    reqParams
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

        return new ResponseData(status, body, headers);
    }
}
