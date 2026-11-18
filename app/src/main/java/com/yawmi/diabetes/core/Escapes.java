package com.yawmi.diabetes.core;
public final class Escapes {
    private Escapes() {}
    public static String html(String text) {
        return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    public static String csv(String value) {
        int start=0; while(start<value.length() && Character.isWhitespace(value.charAt(start))) start++;
        String trimmed=value.substring(start);
        String safe=(!trimmed.isEmpty() && "=+-@\t\r".indexOf(trimmed.charAt(0))>=0) ? "'"+value : value;
        return "\""+safe.replace("\"","\"\"")+"\"";
    }
}
