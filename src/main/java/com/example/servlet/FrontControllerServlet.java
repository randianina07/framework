package com.example.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.example.annotation.AnnotationController;
import com.example.utils.Mapping;
import com.example.utils.ModelAndView;
import com.example.utils.UrlMethod;
import com.example.utils.Utilitaire; // Importation de ta nouvelle classe

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontControllerServlet extends HttpServlet {
    private List<Class<?>> annotatedClasses;
    private HashMap<UrlMethod, Mapping> methods;
    private WebApplicationContext springContext;

    @Override
    public void init() throws ServletException {
        super.init();

        // Récupérer le conteneur Spring lié au ServletContext de Tomcat
        this.springContext = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
        System.out.println("Conteneur Spring initialisé avec succès !");
        
        Object routesAttribute = this.getServletContext().getAttribute("routesWithMethod");
        if (routesAttribute instanceof HashMap) {
            this.methods = (HashMap<UrlMethod, Mapping>) routesAttribute;
        } else {
            // Fallback historique si le ServletContextListener n'est pas activé
            String packageName = this.getInitParameter("packageTest");
            this.annotatedClasses = Utilitaire.getClassesAnnotated(packageName, AnnotationController.class);
            this.methods = Utilitaire.getmethodAnnotated(annotatedClasses);
        }
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
        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();
        String pathInfo = requestURI.substring(contextPath.length());
        String httpMethod = req.getMethod();

        UrlMethod queryKey = new UrlMethod(pathInfo, httpMethod);
        
        if (this.methods != null && this.methods.containsKey(queryKey)) {
            Mapping mapping = this.methods.get(queryKey);
            
            try {
                // 1. Charger la classe du contrôleur dynamiquement
                Class<?> controllerClass = Class.forName(mapping.getNomClass());
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                
                // [Nouveau] Lier notre contrôleur "maison" aux beans gérés par Spring
                this.springContext.getAutowireCapableBeanFactory().autowireBean(controllerInstance);
                
                // 2. Trouver et invoquer la méthode correspondante
                Method targetMethod = controllerClass.getDeclaredMethod(mapping.getNomMethod());
                Object result = targetMethod.invoke(controllerInstance);
                
                // 3. Traiter le retour de la méthode test, test
                if (result instanceof ModelAndView) {
                    ModelAndView mv = (ModelAndView) result;
                    
                    // Extraire et injecter les données du modèle dans la requête HTTP
                    Map<String, Object> data = mv.getData();
                    if (data != null) {
                        for (Map.Entry<String, Object> entry : data.entrySet()) {
                            req.setAttribute(entry.getKey(), entry.getValue());
                        }
                    }
                    
                    // Récupérer le préfixe et le suffixe configurés dans le web.xml
                    String prefix = this.getServletContext().getInitParameter("view.prefix");
                    String suffix = this.getServletContext().getInitParameter("view.suffix");
                    
                    // Sécurité par défaut si rien n'est configuré dans web.xml
                    if (prefix == null) prefix = "/WEB-INF/views/";
                    if (suffix == null) suffix = ".jsp";
                    
                    // Reconstitution du chemin d'accès vers le fichier JSP
                    String viewPath = prefix + mv.getView() + suffix;
                    
                    // Forward de la requête vers la page JSP
                    req.getRequestDispatcher(viewPath).forward(req, resp);
                    
                } else if (result instanceof String) {
                    // Si la méthode renvoie une chaîne brute, on l'affiche directement
                    resp.setContentType("text/plain;charset=UTF-8");
                    resp.getWriter().println(result);
                } else if (result != null) {
                    // Pour tout autre type d'objet, on affiche sa représentation String
                    resp.setContentType("text/plain;charset=UTF-8");
                    resp.getWriter().println(result.toString());
                }
                
            } catch (Exception e) {
                resp.setContentType("text/plain;charset=UTF-8");
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                PrintWriter out = resp.getWriter();
                out.println("[ERREUR SPRINT 5] Erreur lors de l'exécution du contrôleur : " + mapping.getNomClass());
                e.printStackTrace(out);
            }
            
        } else {
            // Affichage de secours en cas de route introuvable
            resp.setContentType("text/plain;charset=UTF-8");
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            PrintWriter out = resp.getWriter();
            out.println(" Route introuvable pour [" + httpMethod + "] " + pathInfo);
            out.println("Voici la liste de toutes les routes disponibles avec leurs méthodes :\n");
            
            if (this.methods == null || this.methods.isEmpty()) {
                out.println("(Aucune route n'a été configurée avec @UrlMapping)");
            } else {
                for (HashMap.Entry<UrlMethod, Mapping> entry : this.methods.entrySet()) {
                    UrlMethod availableKey = entry.getKey();
                    Mapping mappingDisponible = entry.getValue();
                    
                    out.println(" -> [" + availableKey.getMethod() + "] URL : " + availableKey.getUrl());
                    out.println("    Class  : " + mappingDisponible.getNomClass());
                    out.println("    Method : " + mappingDisponible.getNomMethod());
                    out.println("--------------------------------------------------");
                }
            }
        }
    }
}