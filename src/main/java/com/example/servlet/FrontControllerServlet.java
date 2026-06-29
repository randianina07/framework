package com.example.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;

import com.example.annotation.AnnotationController;
import com.example.utils.Mapping;
import com.example.utils.Utilitaire;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontControllerServlet extends HttpServlet{
    private List<Class<?>> annotatedClasses;
    private  HashMap<String,Mapping> methods;

    @Override
    public void init() throws ServletException{
        super.init();

        String packageName = this.getInitParameter("packageTest");

        this.annotatedClasses = Utilitaire.getClassesAnnotated(packageName, AnnotationController.class);

        this.methods = Utilitaire.getmethodAnnotated(annotatedClasses);
    }
    
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
           processRequest(req,resp);
    } 

     @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
        processRequest(req,resp);
    } 

   private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.setContentType("text/plain;charset=UTF-8");
    PrintWriter out = resp.getWriter();
    String requestURI = req.getRequestURI();
    String contextPath = req.getContextPath();
    String pathInfo = requestURI.substring(contextPath.length());

    out.println("URL complète : " + req.getRequestURL().toString());
    out.println("Route détectée: " + pathInfo);
   

    if (this.methods.containsKey(pathInfo)) {
        
       
        out.println(" route existe");
        Mapping mapping = this.methods.get(pathInfo);
        out.println("Controller : " + mapping.getNomClass());
        out.println("Method: " + mapping.getNomMethod());
        
    } else {
        
      
        out.println("Voici la liste de toutes les routes disponibles avec leurs méthodes :\n");
        
        if (this.methods.isEmpty()) {
            out.println("(Aucune route n'a été configurée avec @UrlMapping)");
        } else {
            for (HashMap.Entry<String, Mapping> entry : this.methods.entrySet()) {
                String routeDisponible = entry.getKey();
                Mapping mappingDisponible = entry.getValue();
                
                out.println(" URL : " + routeDisponible);
                out.println(" Class  : " + mappingDisponible.getNomClass());
                out.println(" method : " + mappingDisponible.getNomMethod());
            }
        }
    }
}
}
