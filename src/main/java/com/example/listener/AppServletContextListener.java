package com.example.listener;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.example.utils.LoadingClass;
import com.example.utils.Mapping;
import com.example.utils.UrlMethod;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

public class AppServletContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[INIT] Tomcat démarre l'application. Lancement du scan des routes...");

        try {
            String packageName;
            Map<UrlMethod, Mapping> toutesLesRoutes = new HashMap<>();
            Properties prop = new Properties();
            try (InputStream input = LoadingClass.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (input == null) {
                    throw new RuntimeException("[ERREUR] Impossible de trouver le fichier config.properties.");
                }
                prop.load(input);

                packageName = prop.getProperty("app.package");

            } catch (IOException e) {
                throw new RuntimeException("Erreur lors de la lecture de config.properties", e);
            } 

            LoadingClass.loadUrlMappingsWithMethod(packageName,toutesLesRoutes);

            sce.getServletContext().setAttribute("routesWithMethod", toutesLesRoutes);

            System.out.println("[SUCCESS] Scan terminé avec succès. " + toutesLesRoutes.size() + " routes chargées.");

        } catch (IllegalStateException e) {
            System.err.println("[ERREUR CRITIQUE DÉMARRAGE] " + e.getMessage());
            throw new RuntimeException("Échec du déploiement de l'application à cause d'un conflit de routes.", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[SHUTDOWN] L'application s'arrête. ");
    }
}