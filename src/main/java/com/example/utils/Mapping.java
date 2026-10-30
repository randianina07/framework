package com.example.utils;

public class Mapping {
    String nomClass;
    String nomMethod;
    private Object controllerInstance; 

    public Mapping(String nomClass, String nomMethod) {
        this.nomClass = nomClass;
        this.nomMethod = nomMethod;
        this.controllerInstance = null;
    }

    public String getNomClass() {
        return nomClass;
    }

    public void setNomClass(String nomClass) {
        this.nomClass = nomClass;
    }

    public String getNomMethod() {
        return nomMethod;
    }

    public void setNomMethod(String nomMethod) {
        this.nomMethod = nomMethod;
    }

  
    public Object getControllerInstance() {
        return controllerInstance;
    }

    public void setControllerInstance(Object controllerInstance) {
        this.controllerInstance = controllerInstance;
    }
}