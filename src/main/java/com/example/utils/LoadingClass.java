package com.example.utils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

public class LoadingClass {

    private static Properties loadConfigProperties() {
        Properties prop = new Properties();
        try (InputStream input = LoadingClass.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                prop.load(input);
            }
        } catch (IOException e) {
            // Lecture silencieuse si le fichier est absent
        }
        return prop;
    }

    public static boolean hasAnnotation(Class<?> clazzScanned, String monAnnotation) {
        try {
            Class<?> clazz = Class.forName(monAnnotation);
            Class<? extends Annotation> annotationClass = clazz.asSubclass(Annotation.class);

            Target target = annotationClass.getAnnotation(Target.class);

            if (target != null && Arrays.asList(target.value()).contains(ElementType.METHOD)) {
                return Arrays.stream(clazzScanned.getDeclaredMethods())
                        .anyMatch(m -> m.isAnnotationPresent(annotationClass));
            }

            return clazzScanned.isAnnotationPresent(annotationClass);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Annotation non trouvée : " + monAnnotation, e);
        }
    }

    public static List<String> loadClassWithMyAnnotation(String packageName, List<String> mesAnnotations) {
        List<String> listeClasse = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph().enableAllInfo().acceptPackages(packageName).scan()) {
            if (mesAnnotations.size() < 1) {
                return listeClasse;
            }
            ClassInfoList classesAvecAnnotation = scanResult.getAllClasses();
            for (ClassInfo kilassy : classesAvecAnnotation) {
                try {
                    Class<?> clazz = Class.forName(kilassy.getName());
                    boolean hasAllAnnotations = true;

                    for (String monAnnotation : mesAnnotations) {
                        if (!hasAnnotation(clazz, monAnnotation)) {
                            hasAllAnnotations = false;
                            break;
                        }
                    }
                    if (hasAllAnnotations) {
                        listeClasse.add(kilassy.getName());
                    }
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Erreur lors du chargement de la classe : " + kilassy.getName(), e);
                }
            }
        }
        return listeClasse;
    }

    public static boolean isARouteInsideMapping(String url, Map<String, Mapping> routes) {
        return routes.containsKey(url);
    }

    public static boolean isARouteInsideMappingWithMethod(UrlMethod urlMethod, Map<UrlMethod, Mapping> routes) {
        return routes.containsKey(urlMethod);
    }

    public static void loadUrlMappingsWithMethod(String packageName, Map<UrlMethod, Mapping> routes)
            throws IllegalStateException {

        Class<? extends Annotation> controllerAnnotationClass;
        Class<? extends Annotation> urlMappingAnnotationClass;

        Properties prop = loadConfigProperties();
        try {
            // Utilisation de valeurs par défaut si absentes du config.properties
            String controllerClassName = prop.getProperty("annotation.controller", "com.example.annotation.AnnotationController");
            String urlMappingClassName = prop.getProperty("annotation.mapping", "com.example.annotation.UrlMapping");

            controllerAnnotationClass = Class.forName(controllerClassName).asSubclass(Annotation.class);
            urlMappingAnnotationClass = Class.forName(urlMappingClassName).asSubclass(Annotation.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Une des annotations configurées n'existe pas", e);
        }

        try (ScanResult scanResult = new ClassGraph().enableAllInfo().acceptPackages(packageName).scan()) {
            ClassInfoList classesInfo = scanResult.getAllClasses();

            for (ClassInfo classInfo : classesInfo) {
                try {
                    Class<?> clazz = Class.forName(classInfo.getName());

                    if (!clazz.isAnnotationPresent(controllerAnnotationClass)) {
                        continue;
                    }

                    for (Method method : clazz.getDeclaredMethods()) {
                        Annotation urlMapping = method.getAnnotation(urlMappingAnnotationClass);

                        if (urlMapping != null) {
                            String url = (String) urlMappingAnnotationClass.getMethod("value").invoke(urlMapping);
                            String methodType = (String) urlMappingAnnotationClass.getMethod("method")
                                    .invoke(urlMapping);

                            UrlMethod urlMethod = new UrlMethod(url, methodType);

                            if (routes.containsKey(urlMethod)) {
                                Mapping mappingExistant = routes.get(urlMethod);
                                throw new IllegalStateException(
                                        """
                                                [ERREUR] Conflit de routes détecté !
                                                La route [%s %s] est déjà associée à la méthode : %s.%s()
                                                """.formatted(
                                                methodType,
                                                url,
                                                mappingExistant.getNomClass(), 
                                                mappingExistant.getNomMethod(), 
                                                clazz.getName(),
                                                method.getName()));
                            }

                            routes.put(urlMethod, new Mapping(clazz.getName(), method.getName()));
                        }
                    }

                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Erreur lors du chargement de la classe : " + classInfo.getName(), e);
                } catch (IllegalAccessException | IllegalStateException | NoSuchMethodException | SecurityException
                        | InvocationTargetException e) {
                    throw new RuntimeException("Erreur lors de la lecture dynamique des méthodes de l'annotation", e);
                }
            }
        }
    }

    public static Map<UrlMethod, Mapping> loadUrlMappingsWithMethod() throws IllegalStateException {
        Map<UrlMethod, Mapping> routes = new HashMap<>();

        Class<? extends Annotation> controllerAnnotationClass;
        Class<? extends Annotation> urlMappingAnnotationClass;

        Properties prop = loadConfigProperties();
        try {
            String controllerClassName = prop.getProperty("annotation.controller", "com.example.annotation.AnnotationController");
            String urlMappingClassName = prop.getProperty("annotation.mapping", "com.example.annotation.UrlMapping");

            controllerAnnotationClass = Class.forName(controllerClassName).asSubclass(Annotation.class);
            urlMappingAnnotationClass = Class.forName(urlMappingClassName).asSubclass(Annotation.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Une des annotations configurées n'existe pas", e);
        }

        try (ScanResult scanResult = new ClassGraph().enableAllInfo().scan()) {
            ClassInfoList classesInfo = scanResult.getAllClasses();

            for (ClassInfo classInfo : classesInfo) {
                try {
                    Class<?> clazz = Class.forName(classInfo.getName());

                    if (!clazz.isAnnotationPresent(controllerAnnotationClass)) {
                        continue;
                    }

                    for (Method method : clazz.getDeclaredMethods()) {
                        Annotation urlMapping = method.getAnnotation(urlMappingAnnotationClass);

                        if (urlMapping != null) {
                            String url = (String) urlMappingAnnotationClass.getMethod("value").invoke(urlMapping);
                            String methodType = (String) urlMappingAnnotationClass.getMethod("method")
                                    .invoke(urlMapping);

                            UrlMethod urlMethod = new UrlMethod(url, methodType);

                            if (routes.containsKey(urlMethod)) {
                                Mapping mappingExistant = routes.get(urlMethod);
                                throw new IllegalStateException(
                                        """
                                                [ERREUR] Conflit de routes détecté !
                                                La route [%s %s] est déjà associée à la méthode : %s.%s()
                                                """.formatted(
                                                methodType,
                                                url,
                                                mappingExistant.getNomClass(),
                                                mappingExistant.getNomMethod(),
                                                clazz.getName(),
                                                method.getName()));
                            }

                            routes.put(urlMethod, new Mapping(clazz.getName(), method.getName()));
                        }
                    }

                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Erreur lors du chargement de la classe : " + classInfo.getName(), e);
                } catch (IllegalAccessException | IllegalStateException | NoSuchMethodException | SecurityException
                        | InvocationTargetException e) {
                    throw new RuntimeException("Erreur lors de la lecture dynamique des méthodes de l'annotation", e);
                }
            }
        }

        return routes;
    }

    public static Map<String, Mapping> loadUrlMappings(String packageName, String monAnnotationClasse,
            String monAnnotationMethode) {
        Map<String, Mapping> routes = new HashMap<>();

        try (ScanResult scanResult = new ClassGraph().enableAllInfo().acceptPackages(packageName).scan()) {
            ClassInfoList classesInfo = scanResult.getAllClasses();

            for (ClassInfo classInfo : classesInfo) {
                try {
                    Class<?> clazz = Class.forName(classInfo.getName());

                    if (!hasAnnotation(clazz, monAnnotationClasse)) {
                        continue;
                    }

                    Class<?> annotationMethodeClass = Class.forName(monAnnotationMethode);
                    Class<? extends Annotation> urlMappingAnnotationClass = annotationMethodeClass
                            .asSubclass(Annotation.class);

                    for (Method method : clazz.getDeclaredMethods()) {
                        Annotation urlMapping = method.getAnnotation(urlMappingAnnotationClass);
                        if (urlMapping != null) {
                            String url = (String) urlMappingAnnotationClass
                                    .getMethod("value")
                                    .invoke(urlMapping);

                            routes.put(url, new Mapping(clazz.getName(), method.getName()));
                        }
                    }

                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Erreur lors du chargement de la classe : " + classInfo.getName(), e);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException("Erreur lors de la lecture de l'annotation " + monAnnotationMethode, e);
                }
            }
        }

        return routes;
    }

    public static List<String> loadClassWithMyAnnotation(String packageName, String monAnnotation) {
        List<String> listeClasse = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph().enableAllInfo().acceptPackages(packageName).scan()) {
            ClassInfoList classesAvecAnnotation = scanResult.getClassesWithAnnotation(monAnnotation);
            for (ClassInfo classInfo : classesAvecAnnotation) {
                listeClasse.add(classInfo.getName());
            }
        }
        return listeClasse;
    }

    public static List<String> loadClassWithMyMethodeAnnotation(String packageName, String monAnnotation) {
        List<String> listeClasse = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph().enableAllInfo().acceptPackages(packageName).scan()) {
            ClassInfoList classesAvecAnnotation = scanResult.getClassesWithMethodAnnotation(monAnnotation);
            for (ClassInfo classInfo : classesAvecAnnotation) {
                listeClasse.add(classInfo.getName());
            }
        }
        return listeClasse;
    }

    public static List<String> loadClassWithMyAnnotation(String monAnnotation) {
        List<String> listeClasse = new ArrayList<>();

        try (ScanResult scanResult = new ClassGraph()
                .enableAllInfo()
                .scan()) {

            ClassInfoList classesAvecAnnotation = scanResult.getClassesWithAnnotation(monAnnotation);
            for (ClassInfo classInfo : classesAvecAnnotation) {
                listeClasse.add(classInfo.getName());
            }
        }
        return listeClasse;
    }

    public static List<String> loadAllClasses() {
        List<String> listeClasse = new ArrayList<>();

        try (ScanResult scanResult = new ClassGraph()
                .enableAllInfo()
                .scan()) {

            ClassInfoList classesAvecAnnotation = scanResult.getAllClasses();
            for (ClassInfo classInfo : classesAvecAnnotation) {
                listeClasse.add(classInfo.getName());
            }
        }
        return listeClasse;
    }
}