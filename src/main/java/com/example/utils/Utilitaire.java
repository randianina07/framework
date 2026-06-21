package com.example.utils;

import java.util.ArrayList;
import java.util.List;

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
}
