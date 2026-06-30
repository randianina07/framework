package com.example.utils;

import java.util.Objects;

public class MappingKey {
    private String url;
    private String method;

    public MappingKey(String url, String method) {
        this.url = url;
        this.method = method.toUpperCase();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MappingKey that = (MappingKey) o;
        return Objects.equals(url, that.url) && 
               Objects.equals(method, that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method);
    }
}
