
package com.classroom.dev.listener;

import com.classroom.dev.service.ClassroomService;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class ApplicationListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {

        ServletContext context = event.getServletContext();

        ClassroomService classroomService = new ClassroomService();

        context.setAttribute("classroomService", classroomService);

        System.out.println("================================");
        System.out.println("CLASSROOM SERVER STARTED");
        System.out.println("ClassroomService initialized");
        System.out.println("================================");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {

        ClassroomService classroomService =
            (ClassroomService) event.getServletContext()
                .getAttribute("classroomService");

        if (classroomService != null) {
            classroomService.shutdown();
        }

        System.out.println("CLASSROOM SERVER STOPPED");
    }
}
