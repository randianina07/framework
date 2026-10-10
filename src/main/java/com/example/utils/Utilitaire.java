package com.example.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
        if (!directory.exists()) return;

        java.io.File[] files = directory.listFiles();
        if (files == null) return;

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

   public static Object[] resolveMethodArguments(Method method, HttpServletRequest req) throws Exception {
        Parameter[] parameters = method.getParameters();

        if (parameters.length == 0) {
            return new Object[0];
        }

        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();

            if (paramType.equals(HttpServletRequest.class)) {
                args[i] = req;
            } 
            // Argument direct de type List<T>
            else if (List.class.isAssignableFrom(paramType)) {
                Type genericType = param.getParameterizedType();
                Class<?> itemClass = String.class; // Défaut si type brut
                if (genericType instanceof ParameterizedType) {
                    itemClass = (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[0];
                }
                args[i] = populateList("", itemClass, req);
            } 
            //  Argument direct de type simple / primitif
            else if (isBasicType(paramType)) {
                String[] values = req.getParameterValues(param.getName());
                if (values != null && values.length > 0 && !values[0].trim().isEmpty()) {
                    args[i] = convertType(values[0], paramType);
                } else {
                    args[i] = getDefaultValue(paramType);
                }
            } 
            //  Objet complexe (DTO / Entité)
            else {
                args[i] = populateObject(paramType, "", req);
            }
        }

        return args;
    }

    /**
     * Instanciation et remplissage récursif d'un objet
     * @param clazz La classe de l'objet à instancier
     * @param prefix Le préfixe de chemin (ex: "" pour la racine, "adresse." pour un sous-objet)
     */
    public static Object populateObject(Class<?> clazz, String prefix, HttpServletRequest req) throws Exception {
        Object instance = clazz.getDeclaredConstructor().newInstance();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();
            String fullPath = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
            Class<?> fieldType = field.getType();

            // 1. Attribut de type Simple / Primitif
            if (isBasicType(fieldType)) {
                String[] values = req.getParameterValues(fullPath);
                if (values != null && values.length > 0 && !values[0].trim().isEmpty()) {
                    field.set(instance, convertType(values[0], fieldType));
                }
            } 
            // 2. Attribut de type List<T> (Ex: List<Telephone>)
            else if (List.class.isAssignableFrom(fieldType)) {
                Type genericType = field.getGenericType();
                Class<?> itemClass = String.class;
                if (genericType instanceof ParameterizedType) {
                    itemClass = (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[0];
                }
                List<?> list = populateList(fullPath, itemClass, req);
                field.set(instance, list);
            } 
            // 3. Attribut de type Objet imbriqué (RÉCURSIVITÉ)
            else {
                // On vérifie si la requête contient au moins un paramètre commençant par le chemin de l'objet
                if (hasParametersStartingWith(req, fullPath + ".")) {
                    Object nestedObject = populateObject(fieldType, fullPath, req);
                    field.set(instance, nestedObject);
                }
            }
        }

        return instance;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List populateList(String prefix, Class<?> itemClass, HttpServletRequest req) throws Exception {
        List list = new ArrayList<>();

        if (isBasicType(itemClass)) {
            // Liste de types simples (ex: tags=java&tags=spring ou tags[]=java)
            String paramName = prefix.isEmpty() ? "items" : prefix;
            String[] values = req.getParameterValues(paramName);
            if (values == null && !prefix.isEmpty()) {
                values = req.getParameterValues(prefix + "[]");
            }
            if (values != null) {
                for (String val : values) {
                    if (val != null && !val.trim().isEmpty()) {
                        list.add(convertType(val, itemClass));
                    }
                }
            }
        } else {
            // Liste d'objets complexes (ex: telephones[0].numero, telephones[1].numero)
            int index = 0;
            while (true) {
                String itemPrefix = prefix.isEmpty() ? "[" + index + "]" : prefix + "[" + index + "]";
                
                // Si aucun paramètre ne commence par "telephones[index].", on arrête la boucle
                if (!hasParametersStartingWith(req, itemPrefix + ".")) {
                    break;
                }

                Object itemInstance = populateObject(itemClass, itemPrefix, req);
                list.add(itemInstance);
                index++;
            }
        }

        return list;
    }

    private static boolean hasParametersStartingWith(HttpServletRequest req, String prefix) {
        Map<String, String[]> parameterMap = req.getParameterMap();
        for (String paramName : parameterMap.keySet()) {
            if (paramName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBasicType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               type == Integer.class ||
               type == Long.class ||
               type == Double.class ||
               type == Float.class ||
               type == Boolean.class;
    }

    private static Object convertType(String value, Class<?> targetType) {
        if (targetType == String.class) return value;
        if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(value);
        if (targetType == Long.class || targetType == long.class) return Long.parseLong(value);
        if (targetType == Double.class || targetType == double.class) return Double.parseDouble(value);
        if (targetType == Float.class || targetType == float.class) return Float.parseFloat(value);
        if (targetType == Boolean.class || targetType == boolean.class) return Boolean.parseBoolean(value);
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