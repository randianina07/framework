package com.example.utils;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

public class MySession {
    private HttpSession httpSession;
    private Map<String, Object> sessionData = new HashMap<>();

    public MySession(HttpSession httpSession) {
        this.httpSession = httpSession;
        if (httpSession != null) {
            Enumeration<String> attributeNames = httpSession.getAttributeNames();
            while (attributeNames.hasMoreElements()) {
                String key = attributeNames.nextElement();
                sessionData.put(key, httpSession.getAttribute(key));
            }
        }
    }

  
    public Object getAttribute(String key) {
        return sessionData.get(key);
    }


    public void setAttribute(String key, Object value) {
        sessionData.put(key, value);
    }


    public void removeAttribute(String key) {
        sessionData.remove(key);
    }

 
    public void invalidate() {
        sessionData.clear();
        if (httpSession != null) {
            httpSession.invalidate();
        }
    }


    public void synchronize() {
        if (httpSession != null) {
            for (Map.Entry<String, Object> entry : sessionData.entrySet()) {
                httpSession.setAttribute(entry.getKey(), entry.getValue());
            }
        }
    }

   
    public String getId() {
        return httpSession != null ? httpSession.getId() : null;
    }
}