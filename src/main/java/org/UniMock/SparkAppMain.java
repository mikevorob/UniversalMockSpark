package org.UniMock;
import static spark.Spark.*;

import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.HashMap;
import java.util.Map;


//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class SparkAppMain {
    private static final org. slf4j.Logger logger = LoggerFactory.getLogger(SparkAppMain.class);
    public static void main(String[] args) {
        if (!org.slf4j.LoggerFactory.getILoggerFactory().getClass().getName().contains("ch.qos.logback")) {
            System.err.println("⚠️ Logback not active! Check dependencies and logback.xml");
        }
        String pl = AppLogic.preLaunch(args);
        System.out.println("Log level set to: " + System.getProperty("log_level"));
        logger.info(pl);


        port(AppLogic.PORT);
        threadPool(AppLogic.THREAD_MAX, AppLogic.THREAD_MIN, AppLogic.THREAD_IDLE_TIMEOUT);
        Spark.before(((request, response) -> {
            logger.info("Request: " + request.requestMethod() + " " +request.pathInfo() +" " + request.params());
        }));

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

            // path-переменные (если эндпоинт в шаблоне содержит :var)
            Map<String, String> reqPathVars = AppLogic.extractPathVars(method, endpoint);

            ResponseData rd = AppLogic.buildResponse(method, endpoint, reqVars, reqVarsHeaders, reqVarsParams, reqPathVars);

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