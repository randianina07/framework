package com.example.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.example.annotation.AnnotationAPI;
import com.example.annotation.AnnotationController;
import com.example.utils.Mapping;
import com.example.utils.ModelAndView;
import com.example.utils.UrlMethod;
import com.example.utils.Utilitaire;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontControllerServlet extends HttpServlet {
    private List<Class<?>> annotatedClasses;
    private HashMap<UrlMethod, Mapping> methods;
    private WebApplicationContext springContext;
    private final Gson gson = new Gson();

    @Override
    public void init() throws ServletException {
        super.init();

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
                Class<?> controllerClass = Class.forName(mapping.getNomClass());
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

                this.springContext.getAutowireCapableBeanFactory().autowireBean(controllerInstance);

                // 1. Recherche de la méthode ciblée
                Method targetMethod = Utilitaire.findTargetMethod(controllerClass, mapping.getNomMethod());

                // 2. Résolution dynamique des arguments
                Object[] methodArgs = Utilitaire.resolveMethodArguments(targetMethod, req);

                // 3. Exécution de la méthode avec ses arguments
                Object result = targetMethod.invoke(controllerInstance, methodArgs);

                boolean api = targetMethod.isAnnotationPresent(AnnotationAPI.class);

                // Debug : Affichage des paramètres reçus
                Map<String, String[]> parameterMap = req.getParameterMap();
                System.out.println("--- PARAMÈTRES REÇUS DANS LA REQUÊTE ---");
                for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                    String paramName = entry.getKey();
                    String[] paramValues = entry.getValue();
                    String displayValue = String.join(", ", paramValues);
                    System.out.println(paramName + " = " + displayValue);
                }
                System.out.println("-----------------------------------------");

                if (api) {
                    resp.setContentType("application/json;charset=UTF-8");
                    PrintWriter out = resp.getWriter();

                    if (result == null) {
                        out.print("null");
                    } else if (result instanceof String) {
                        out.print((String) result);
                    } else {
                        String jsonOutput = gson.toJson(result);
                        System.out.println("JSON Généré : " + jsonOutput);
                        out.print(jsonOutput);
                    }
                    out.flush();

                } else if (result instanceof ModelAndView) {
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

                    // Sécurité par défaut
                    if (prefix == null)
                        prefix = "/WEB-INF/views/";
                    if (suffix == null)
                        suffix = ".jsp";

                    // Reconstitution du chemin et Forward vers la page JSP
                    String viewPath = prefix + mv.getView() + suffix;
                    req.getRequestDispatcher(viewPath).forward(req, resp);

                } else if (result instanceof String) {
                    resp.setContentType("text/plain;charset=UTF-8");
                    resp.getWriter().println((String) result);

                } else if (result != null) {
                    resp.setContentType("text/plain;charset=UTF-8");
                    resp.getWriter().println(result.toString());
                }

            } catch (Exception e) {
                resp.setContentType("text/plain;charset=UTF-8");
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                PrintWriter out = resp.getWriter();
                out.println("[ERREUR SPRINT] Erreur lors de l'exécution du contrôleur : " + mapping.getNomClass());
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