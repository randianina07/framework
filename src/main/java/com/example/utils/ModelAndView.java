package com.example.utils;

import java.util.HashMap;
import java.util.Map;

public class ModelAndView {
    String view;
    Map<String, Object> data = new HashMap<>();
    
    public ModelAndView(String view, Map<String, Object> data) {
        this.view = view;
        this.data = data;
    }
    public ModelAndView() {
    }
    public String getView() {
        return view;
    }
    public void setView(String view) {
        this.view = view;
    }
    public Map<String, Object> getData() {
        return data;
    }
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
}
