package com.example.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.utils.LoadingClass;
import com.example.utils.Mapping;
import com.example.utils.MySession;
import com.example.utils.UrlMethod;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

public class AppServletContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[INIT] === DÉBUT DU LISTENER ===");

        try {
            Map<UrlMethod, Mapping> toutesLesRoutes = new HashMap<>();
            Map<String, MySession> activeSessions = new ConcurrentHashMap<>();
            sce.getServletContext().setAttribute("activeSessions", activeSessions);

            String authSessionKey = sce.getServletContext().getInitParameter("auth.session.key");
            // if (authSessionKey == null || authSessionKey.trim().isEmpty()) {
            //     authSessionKey = "currentUser";
            // }
            sce.getServletContext().setAttribute("authSessionKey", authSessionKey);

            // 2. Package
            String packageName = sce.getServletContext().getInitParameter("app.package");
            // if (packageName == null || packageName.trim().isEmpty()) {
            //     packageName = "main.java.controller";
            // }
            System.out.println("[INIT] Package cible des contrôleurs : " + packageName);

            // 3. Scan des routes (protégé)
            try {
                LoadingClass.loadUrlMappingsWithMethod(packageName, toutesLesRoutes);
                System.out.println("[SUCCESS] Scan terminé avec succès. " + toutesLesRoutes.size() + " routes chargées.");
            } catch (Throwable t) {
                System.err.println("[ERREUR SCAN] Échec du scan des annotations : " + t.getMessage());
                t.printStackTrace();
            }

            sce.getServletContext().setAttribute("routesWithMethod", toutesLesRoutes);
            System.out.println("[INIT] === FIN DU LISTENER AVEC SUCCÈS ===");

        } catch (Throwable e) {
            // ON CAPTURE TOUT POUR ÉVITER QUE TOMCAT NE PLANTE LE DÉPLOIEMENT
            System.err.println("[ERREUR FATALE LISTENER] : ");
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[SHUTDOWN] L'application s'arrête.");
    }
}