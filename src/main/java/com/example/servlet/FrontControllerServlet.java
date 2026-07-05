package com.example.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;

import com.example.annotation.AnnotationController;
import com.example.utils.Mapping;
import com.example.utils.MappingKey; 
import com.example.utils.Utilitaire;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontControllerServlet extends HttpServlet {
    private List<Class<?>> annotatedClasses;
  
    private HashMap<MappingKey, Mapping> methods;

    @Override
    public void init() throws ServletException {
        super.init();

        String packageName = this.getInitParameter("packageTest");
        this.annotatedClasses = Utilitaire.getClassesAnnotated(packageName, AnnotationController.class);
        this.methods = Utilitaire.getmethodAnnotated(annotatedClasses);
    }
    
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    } 

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    } 

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        
        
        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();
        String pathInfo = requestURI.substring(contextPath.length());
        String httpMethod = req.getMethod();

        out.println("URL complète : " + req.getRequestURL().toString());
        out.println("Méthode HTTP détectée : " + httpMethod);
        out.println("Route détectée : " + pathInfo);
        out.println("--------------------------------------------------\n");

        
        MappingKey queryKey = new MappingKey(pathInfo, httpMethod);

        
        if (this.methods.containsKey(queryKey)) {
            out.println(" La route existe !");
            Mapping mapping = this.methods.get(queryKey);
            out.println("Controller : " + mapping.getNomClass());
            out.println("Method : " + mapping.getNomMethod());
            try {
            
            Class<?> clazz = Class.forName(mapping.getNomClass());

            Object controllerInstance = clazz.getDeclaredConstructor().newInstance();

            java.lang.reflect.Method methodToInvoke = clazz.getDeclaredMethod(mapping.getNomMethod());

            out.println("method à executer " + mapping.getNomMethod());
            
            methodToInvoke.invoke(controllerInstance);

        } catch (ClassNotFoundException e) {
            out.println(" La classe " + mapping.getNomClass() + " est introuvable.");
            e.printStackTrace(out);
        } catch (NoSuchMethodException e) {
            out.println(" La méthode " + mapping.getNomMethod() + "() n'existe pas dans la classe.");
            e.printStackTrace(out);
        } catch (java.lang.reflect.InvocationTargetException e) {
            out.println(" Une exception a été levée dans le contrôleur :");
            e.getCause().printStackTrace(out); 
        } catch (Exception e) {
            out.println(" Erreur générale lors de l'instanciation ou de l'invocation : " + e.getMessage());
            e.printStackTrace(out);
        }
        } else {
            out.println(" Route introuvable pour [" + httpMethod + "] " + pathInfo);
            out.println("Voici la liste de toutes les routes disponibles avec leurs méthodes :\n");
            
            if (this.methods.isEmpty()) {
                out.println("(Aucune route n'a été configurée avec @UrlMapping)");
            } else {
                for (HashMap.Entry<MappingKey, Mapping> entry : this.methods.entrySet()) {
                    MappingKey availableKey = entry.getKey();
                    Mapping mappingDisponible = entry.getValue();
                    
                    out.println("[" + availableKey.getMethod() + "] URL : " + availableKey.getUrl());
                    out.println("    Class  : " + mappingDisponible.getNomClass());
                    out.println("    Method : " + mappingDisponible.getNomMethod());
                    out.println("(-------------------------------------------------------------)");
                }
            }
        }
    }
}