package com.example.utils;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.annotation.UrlMapping;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

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
        if (!directory.exists())
            return;

        java.io.File[] files = directory.listFiles();
        if (files == null)
            return;

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

    public static List<Class<?>> getClassesAnnotated(String packageName,
            Class<? extends java.lang.annotation.Annotation> annotClass) {
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
                        throw new IllegalArgumentException("L'URL '" + url + "' avec la méthode '" + httpMethod
                                + "' est déjà associée à un autre contrôleur !");
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
        throw new NoSuchMethodException(
                "Méthode " + methodName + " introuvable dans la classe " + controllerClass.getName());
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
            } else if (List.class.isAssignableFrom(paramType)) {
                Type genericType = param.getParameterizedType();
                Class<?> itemClass = String.class;
                if (genericType instanceof ParameterizedType) {
                    itemClass = (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[0];
                }
                args[i] = populateList("", itemClass, req);
            } else if (isBasicType(paramType)) {
                String[] values = req.getParameterValues(param.getName());
                if (values != null && values.length > 0 && !values[0].trim().isEmpty()) {
                    args[i] = convertType(values[0], paramType);
                } else {
                    args[i] = getDefaultValue(paramType);
                }
            } // Dans ta méthode resolveMethodArguments (ou équivalent)
            else if (paramType == MySession.class) {
                HttpSession httpSession = req.getSession();
                String sessionId = httpSession.getId();

                ServletContext context = req.getServletContext();
                @SuppressWarnings("unchecked")
                Map<String, MySession> activeSessions = (Map<String, MySession>) context.getAttribute("activeSessions");

                if (activeSessions == null) {
                    activeSessions = new ConcurrentHashMap<>();
                    context.setAttribute("activeSessions", activeSessions);
                }

                // Récupérer la session existante ou en créer une nouvelle pour cet utilisateur
                MySession session = activeSessions.get(sessionId);
                if (session == null) {
                    session = new MySession(httpSession);
                    activeSessions.put(sessionId, session);
                }

                args[i] = session;
                // Plus besoin de req.setAttribute("injectedSession", session) ici !
            } else if (paramType == MapFile.class) {
                args[i] = extractMapfile(param.getName(), req);
            } else {
                args[i] = populateObject(paramType, "", req);
            }
        }

        return args;
    }

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
            // 2. Attribut de type List<T>
            else if (List.class.isAssignableFrom(fieldType)) {
                Type genericType = field.getGenericType();
                Class<?> itemClass = String.class;
                if (genericType instanceof ParameterizedType) {
                    itemClass = (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[0];
                }
                List<?> list = populateList(fullPath, itemClass, req);
                field.set(instance, list);
            } else if (fieldType == MapFile.class) {
                MapFile mf = extractMapfile(fullPath, req);
                if (mf != null && mf.getBytes() != null && mf.getBytes().length > 0) {
                    field.set(instance, mf);
                }
            }
            // 4. Attribut de type Objet imbriqué (RÉCURSIVITÉ)
            else {
                if (hasParametersStartingWith(req, fullPath + ".") || hasPartStartingWith(req, fullPath + ".")) {
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
            int index = 0;
            while (true) {
                String itemPrefix = prefix.isEmpty() ? "[" + index + "]" : prefix + "[" + index + "]";

                if (!hasParametersStartingWith(req, itemPrefix + ".") && !hasPartStartingWith(req, itemPrefix + ".")) {
                    break;
                }

                Object itemInstance = populateObject(itemClass, itemPrefix, req);
                list.add(itemInstance);
                index++;
            }
        }

        return list;
    }

    private static MapFile extractMapfile(String fieldName, HttpServletRequest req) {
        try {
            String contentType = req.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("multipart/")) {
                return null;
            }

            Part part = req.getPart(fieldName);
            if (part == null || part.getSize() == 0) {
                return null;
            }

            String submittedFileName = extractFileName(part);
            if (submittedFileName == null || submittedFileName.isEmpty()) {
                return null;
            }

            String extension = "";
            int i = submittedFileName.lastIndexOf('.');
            if (i > 0) {
                extension = submittedFileName.substring(i + 1);
            }

            // Lecture directe des bytes au format primitif
            byte[] bytes;
            try (InputStream is = part.getInputStream()) {
                bytes = is.readAllBytes();
            }

            // Utilisation de la classe MapFile
            MapFile mapFile = new MapFile();
            mapFile.setNom(submittedFileName);
            mapFile.setExtension(extension);
            mapFile.setBytes(bytes);

            return mapFile;

        } catch (Exception e) {
            return null;
        }
    }

    private static String extractFileName(Part part) {
        String contentDisp = part.getHeader("content-disposition");
        for (String cd : contentDisp.split(";")) {
            if (cd.trim().startsWith("filename")) {
                return cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
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

    private static boolean hasPartStartingWith(HttpServletRequest req, String prefix) {
        try {
            String contentType = req.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("multipart/")) {
                return false;
            }
            for (Part part : req.getParts()) {
                if (part.getName().startsWith(prefix)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignorer si non multipart
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
        if (targetType == String.class)
            return value;
        if (targetType == Integer.class || targetType == int.class)
            return Integer.parseInt(value);
        if (targetType == Long.class || targetType == long.class)
            return Long.parseLong(value);
        if (targetType == Double.class || targetType == double.class)
            return Double.parseDouble(value);
        if (targetType == Float.class || targetType == float.class)
            return Float.parseFloat(value);
        if (targetType == Boolean.class || targetType == boolean.class)
            return Boolean.parseBoolean(value);
        return value;
    }

    private static Object getDefaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class)
                return false;
            if (type == int.class || type == long.class || type == double.class || type == float.class)
                return 0;
        }
        return null;
    }
}