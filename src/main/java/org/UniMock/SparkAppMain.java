package org.UniMock;
import static spark.Spark.*;
import spark.Request;
import spark.Response;
import java.util.HashMap;
import java.util.Map;
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class SparkAppMain {
    public static void main(String[] args) {
        AppLogic.preLaunch(args);

        port(AppLogic.PORT);
        threadPool(AppLogic.THREAD_MAX, AppLogic.THREAD_MIN, AppLogic.THREAD_IDLE_TIMEOUT);

        get("/", SparkAppMain::handleAll);
        get("/*", SparkAppMain::handleAll);
        post("/", SparkAppMain::handleAll);
        post("/*", SparkAppMain::handleAll);
        put("/", SparkAppMain::handleAll);
        put("/*", SparkAppMain::handleAll);
        delete("/", SparkAppMain::handleAll);
        delete("/*", SparkAppMain::handleAll);
        patch("/", SparkAppMain::handleAll);
        patch("/*", SparkAppMain::handleAll);
        head("/", SparkAppMain::handleAll);
        head("/*", SparkAppMain::handleAll);
        options("/", SparkAppMain::handleAll);
        options("/*", SparkAppMain::handleAll);

        System.out.println("🚀 SparkApp is running on port " + AppLogic.PORT);
    }

    private static Object handleAll(Request req, Response res) {
        try {
            String method = req.requestMethod();
            String endpoint = req.uri();
            String body = req.body() != null ? req.body() : "";

            // --- Собираем переменные из запроса ---
            Map<String, String> reqVars = new HashMap<>();
            reqVars.put("body", body);

            // query-параметры
            Map<String, String> reqVarsParams = new HashMap<>();
            for (String qp : req.queryParams()) {
                reqVarsParams.put(qp, req.queryParams(qp));
            }

            // заголовки
            Map<String, String> reqVarsHeaders = new HashMap<>();
            for (String h : req.headers()) {
                reqVarsHeaders.put(h, req.headers(h));
            }

            // --- Генерируем ответ ---
            ResponseData rd = AppLogic.buildResponse(method, endpoint, reqVars, reqVarsHeaders, reqVarsParams);

            // --- Устанавливаем ответ ---
            res.status(rd.status);
            res.type("application/json; charset=utf-8");
            rd.headers.forEach(res::header);
            return rd.body;

        } catch (Exception e) {
            res.status(500);
            return "{\"error\":\"internal server error\"}";
        }
    }
}