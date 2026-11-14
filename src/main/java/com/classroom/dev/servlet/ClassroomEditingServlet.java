
package com.classroom.dev.servlet;

import java.io.IOException;

import com.classroom.dev.service.ClassroomService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/classroom/editing/*")
public class ClassroomEditingServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private ClassroomService classroomService;

    @Override
    public void init() throws ServletException {

        classroomService = (ClassroomService)
            getServletContext().getAttribute("classroomService");

        if (classroomService == null) {
            throw new ServletException(
                "ClassroomService not initialized"
            );
        }
    }

    // GET: CHECK EDITING STATUS
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String path = request.getPathInfo();

        if (!"/status".equals(path)) {
            response.sendError(
                HttpServletResponse.SC_NOT_FOUND
            );
            return;
        }

        boolean enabled =
            classroomService.isStudentEditingEnabled();

        String editingStudentId =
            classroomService.getEditingStudentId();

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String json = "{"
                + "\"studentEditingEnabled\":" + enabled + ","
                + "\"editingStudentId\":"
                + (editingStudentId == null
                    ? "null"
                    : "\"" + escapeJson(editingStudentId) + "\"")
                + "}";

        response.getWriter().write(json);
    }

    // POST: EDITING OPERATIONS
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String path = request.getPathInfo();

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        switch (path == null ? "" : path) {

            case "/enable":

                classroomService.setStudentEditingEnabled(true);

                writeResponse(
                    response,
                    true,
                    "Student editing enabled"
                );
                break;

            case "/disable":

                classroomService.setStudentEditingEnabled(false);

                writeResponse(
                    response,
                    true,
                    "Student editing disabled"
                );
                break;

            case "/lock":

                String lockStudentId =
                    request.getParameter("studentId");

                boolean acquired =
                    classroomService.acquireEditingLock(lockStudentId);

                writeResponse(
                    response,
                    acquired,
                    acquired
                        ? "Editing lock acquired"
                        : "Editing lock unavailable"
                );
                break;

            case "/heartbeat":

                String heartbeatStudentId =
                    request.getParameter("studentId");

                boolean renewed =
                    classroomService.heartbeat(heartbeatStudentId);

                writeResponse(
                    response,
                    renewed,
                    renewed
                        ? "Editing lock renewed"
                        : "Editing lock not owned"
                );
                break;

            case "/release":

                classroomService.releaseCurrentLock();

                writeResponse(
                    response,
                    true,
                    "Editing lock released"
                );
                break;

            default:

                response.sendError(
                    HttpServletResponse.SC_NOT_FOUND
                );
                break;
        }
    }

    // WRITE JSON RESPONSE
    private void writeResponse(
            HttpServletResponse response,
            boolean success,
            String message) throws IOException {

        String json = "{"
                + "\"success\":" + success + ","
                + "\"message\":\"" + escapeJson(message) + "\""
                + "}";

        response.getWriter().write(json);
    }

    // ESCAPE JSON
    private String escapeJson(String value) {

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
