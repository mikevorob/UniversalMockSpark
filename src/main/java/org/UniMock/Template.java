package org.UniMock;
import java.util.*;

public class Template {
    public String method;
    public String endpoint;
    public Map<String, VarDef> bodyVars = new HashMap<>();
    public Map<String, HeaderDef> headers = new HashMap<>();
    public int errorPercent = 0;
    public int errorStatus = 500;
    public String errorBody = null;
    public String successBody = "";
}

class VarDef {
    public String name;
    public String type;
    public String condition;
}

class HeaderDef {
    public String name;
    public String type;
    public String condition;
}

