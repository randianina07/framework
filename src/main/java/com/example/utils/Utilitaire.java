package com.example.utils;

//import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.example.annotation.UrlMapping;

public class Utilitaire{
    public static List<Class<?>> scanClassesInPackage(String nom) {
        List<Class<?>> classes = new ArrayList<>();

        String packagePath = nom.replace('.', '/');

        java.net.URL resource = Thread.currentThread().getContextClassLoader().getResource(packagePath);

        if (resource != null) {

            java.io.File directory = new java.io.File(resource.getFile());

            prendreClasses(directory, nom, classes);
        }
        return classes;
    }

    private static void prendreClasses(java.io.File directory, String currentPackage, List<Class<?>> classes) {
        if (!directory.exists()) {
            return;
        }

        java.io.File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (java.io.File file : files) {

            if (file.isDirectory()) {

                String subPackageName = currentPackage + "." + file.getName();

                prendreClasses(file, subPackageName, classes);

            } else if (file.getName().endsWith(".class")) {

                String classNameOnly = file.getName().replace(".class", "");

                String fullClassName = currentPackage + "." + classNameOnly;

                try {

                    Class<?> cls = Class.forName(fullClassName);

                    classes.add(cls);

                } catch (ClassNotFoundException e) {

                    e.printStackTrace();
                }
            }
        }
    }

 public static List<Class<?>> getClassesAnnotated(String packageName, Class<? extends java.lang.annotation.Annotation> annotClass) {
     List<Class<?>> classes = new ArrayList<>();

 
     List<Class<?>> allclasses = scanClassesInPackage(packageName);


     for (Class<?> cls : allclasses) {
        if (cls.isAnnotationPresent(annotClass)) {
            classes.add(cls); 
        }
     }
     return classes;
}
public static HashMap<UrlMethod, Mapping> getmethodAnnotated(List<Class<?>> classes) {
    HashMap<UrlMethod, Mapping> methodMap = new HashMap<>();

    for (Class<?> cls : classes) {
        java.lang.reflect.Method[] methods = cls.getDeclaredMethods();
        for (java.lang.reflect.Method met : methods) {
            
            if (met.isAnnotationPresent(UrlMapping.class)) {
                UrlMapping annotation = met.getAnnotation(UrlMapping.class);
                
                // 1. On extrait les données de l'annotation
                String url = annotation.value();
                String httpMethod = annotation.method(); // Récupère "GET" ou "POST"
                
                // 2. On instancie la clé composite (URL + VERBE)
                UrlMethod key = new UrlMethod(url, httpMethod);
                
                // 3. On instancie l'objet de stockage avec la classe et le nom de la méthode
                Mapping mapping = new Mapping(cls.getName(), met.getName());
                
                // 4. On vérifie la présence du doublon sur la Map globale
                if (methodMap.containsKey(key)) {
                    throw new IllegalArgumentException("L'URL '" + url + "' avec la méthode '" + httpMethod + "' est déjà associée à un autre contrôleur !");
                }
                
                // 5. Tout est bon, on enregistre
                methodMap.put(key, mapping);
            }
        }
    }

    return methodMap;
}
}
