package com.example.utils;

public class Mapping {
    String nomClass;
    String nomMethod;
    public Mapping(String nomClass, String nomMethod) {
        this.nomClass = nomClass;
        this.nomMethod = nomMethod;
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
}
