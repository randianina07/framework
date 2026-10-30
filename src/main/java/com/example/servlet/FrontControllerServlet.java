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
import com.example.annotation.Auth;
import com.example.utils.Mapping;
import com.example.utils.ModelAndView;
import com.example.utils.MySession;
import com.example.utils.UrlMethod;
import com.example.utils.Utilitaire;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, // 2MB
        maxFileSize = 1024 * 1024 * 10, // 10MB
        maxRequestSize = 1024 * 1024 * 50 // 50MB
)
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

        
                Object controllerInstance = mapping.getControllerInstance();
                if (controllerInstance == null) {
                    synchronized (mapping) {
                        if (mapping.getControllerInstance() == null) {
                            controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                            this.springContext.getAutowireCapableBeanFactory().autowireBean(controllerInstance);
                            mapping.setControllerInstance(controllerInstance);
                            System.out.println("[SINGLETON] Instance unique créée et injectée par Spring pour : " + controllerClass.getName());
                        } else {
                            controllerInstance = mapping.getControllerInstance();
                        }
                    }
                }

                Method targetMethod = Utilitaire.findTargetMethod(controllerClass, mapping.getNomMethod());

                if (targetMethod.isAnnotationPresent(Auth.class)) {
                    HttpSession httpSession = req.getSession(false);
                    boolean isAuthenticated = false;
                    Object connectedUser = null;

                    String authSessionKey = (String) getServletContext().getAttribute("authSessionKey");

                    if (httpSession != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, MySession> activeSessions = (Map<String, MySession>) getServletContext().getAttribute("activeSessions");
                        if (activeSessions != null) {
                            MySession session = activeSessions.get(httpSession.getId());
                            if (session != null) {
                                connectedUser = session.getAttribute(authSessionKey);
                                if (connectedUser != null) {
                                    isAuthenticated = true;
                                }
                            }
                        }
                    }

                    if (!isAuthenticated) {
                        throw new RuntimeException("Accès refusé : Vous devez être connecté.");
                    }

                    Auth authAnnotation = targetMethod.getAnnotation(Auth.class);
                    String[] requiredRoles = authAnnotation.roles();

                    if (requiredRoles.length > 0) {
                        String userRole = "";
                        try {
                            Method getRoleMethod = connectedUser.getClass().getMethod("getRole");
                            userRole = (String) getRoleMethod.invoke(connectedUser);
                        } catch (Exception e) {
                            throw new RuntimeException("Erreur framework : L'objet utilisateur en session doit posséder une méthode getRole().");
                        }

                        boolean hasRole = false;
                        for (String role : requiredRoles) {
                            if (role.equalsIgnoreCase(userRole)) {
                                hasRole = true;
                                break;
                            }
                        }

                        if (!hasRole) {
                            throw new RuntimeException("Accès interdit : Votre rôle (" + userRole + ") ne vous permet pas d'accéder à cette ressource.");
                        }
                    }
                }

                Object[] methodArgs = Utilitaire.resolveMethodArguments(targetMethod, req);

                Object result = targetMethod.invoke(controllerInstance, methodArgs);

                HttpSession httpSession = req.getSession(false);
                if (httpSession != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, MySession> activeSessions = (Map<String, MySession>) getServletContext().getAttribute("activeSessions");
                    if (activeSessions != null) {
                        MySession session = activeSessions.get(httpSession.getId());
                        if (session != null) {
                            session.synchronize();
                        }
                    }
                }

                boolean api = targetMethod.isAnnotationPresent(AnnotationAPI.class);

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
                    String viewName = mv.getView();

                    Map<String, Object> data = mv.getData();
                    if (data != null) {
                        for (Map.Entry<String, Object> entry : data.entrySet()) {
                            req.setAttribute(entry.getKey(), entry.getValue());
                        }
                    }

                
                    if (viewName != null && viewName.startsWith("redirect:")) {
                        String redirectTarget = viewName.substring(9).trim();
                        String fullRedirectUrl = redirectTarget.startsWith("http") ? redirectTarget : contextPath + (redirectTarget.startsWith("/") ? "" : "/") + redirectTarget;
                        resp.sendRedirect(fullRedirectUrl);
                    } else {
                        String prefix = this.getServletContext().getInitParameter("view.prefix");
                        String suffix = this.getServletContext().getInitParameter("view.suffix");

                        if (prefix == null)
                            prefix = "/WEB-INF/views/";
                        if (suffix == null)
                            suffix = ".jsp";

                        String viewPath = prefix + viewName + suffix;
                        req.getRequestDispatcher(viewPath).forward(req, resp);
                    }

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