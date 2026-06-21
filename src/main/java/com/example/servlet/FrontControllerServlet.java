package com.example.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import com.example.annotation.AnnotationController;
import com.example.utils.Utilitaire;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontControllerServlet extends HttpServlet{
    private List<Class<?>> annotatedClasses;
    @Override
    public void init() throws ServletException{
        super.init();

        String packageName = this.getInitParameter("packageTest");

        this.annotatedClasses = Utilitaire.getClassesAnnotated(packageName, AnnotationController.class);
    }
    
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
           processRequest(req,resp);
    } 

     @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
        processRequest(req,resp);
    } 

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
        resp.setContentType("text/plain");
        String url = req.getRequestURL().toString();
        PrintWriter out = resp.getWriter();
        out.println(url);   
        
        out.println("URL demandée : " + url);       
        out.println("-------------------------------------");
        out.println("Contrôleurs détectés par le Framework :");

        for(Class<?> cls: this.annotatedClasses){
            out.println(cls.getName());
        }
    }
}
