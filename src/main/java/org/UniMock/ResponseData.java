package org.UniMock;

import java.util.Map;

public class ResponseData {
    public int status;
    public String body;
    public Map<String,String> headers;

    public ResponseData(int status, String body, Map<String,String> headers) {
        this.status = status;
        this.body = body;
        this.headers = headers;
    }
}
