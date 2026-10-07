package com.example.utils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.annotation.UrlMapping;

import jakarta.servlet.http.HttpServletRequest;

public class Utilitaire {

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
            Method[] methods = cls.getDeclaredMethods();
            for (Method met : methods) {
                if (met.isAnnotationPresent(UrlMapping.class)) {
                    UrlMapping annotation = met.getAnnotation(UrlMapping.class);
                    String url = annotation.value();
                    String httpMethod = annotation.method();

                    UrlMethod key = new UrlMethod(url, httpMethod);
                    Mapping mapping = new Mapping(cls.getName(), met.getName());

                    if (methodMap.containsKey(key)) {
                        throw new IllegalArgumentException("L'URL '" + url + "' avec la méthode '" + httpMethod + "' est déjà associée à un autre contrôleur !");
                    }

                    methodMap.put(key, mapping);
                }
            }
        }

        return methodMap;
    }

  
    public static Method findTargetMethod(Class<?> controllerClass, String methodName) throws NoSuchMethodException {
        for (Method method : controllerClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new NoSuchMethodException("Méthode " + methodName + " introuvable dans la classe " + controllerClass.getName());
    }

    public static Object[] resolveMethodArguments(Method method, HttpServletRequest req) {
        Parameter[] parameters = method.getParameters();

        if (parameters.length == 0) {
            return new Object[0];
        }

        Object[] args = new Object[parameters.length];
        Map<String, String[]> parameterMap = req.getParameterMap();

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();

            // // 1. Cas particulier : La méthode demande directement HttpServletRequest
            // if (paramType.equals(HttpServletRequest.class)) {
            //     args[i] = req;
            //     continue;
            // }

            // 2. Récupération de la valeur envoyée par le formulaire / l'URL
            // Note: Nécessite la compilation avec l'option -parameters
            String paramName = param.getName();
            String[] paramValues = parameterMap.get(paramName);

            if (paramValues != null && paramValues.length > 0 && !paramValues[0].trim().isEmpty()) {
                args[i] = convertType(paramValues[0], paramType);
            } else {
                args[i] = getDefaultValue(paramType);
            }
        }

        return args;
    }

 
    private static Object convertType(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(value);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(value);
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(value);
        } else if (targetType == Float.class || targetType == float.class) {
            return Float.parseFloat(value);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }


    private static Object getDefaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class) return false;
            if (type == int.class || type == long.class || type == double.class || type == float.class) return 0;
        }
        return null;
    }
}