
package com.classroom.dev.servlet;

import java.io.IOException;
import java.util.Locale;

import com.classroom.dev.service.ClassroomService;
import com.classroom.dev.service.ClassroomService.DocumentInfo;
import com.classroom.dev.service.ClassroomService.WorkspaceSnapshot;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/classroom/workspace")
public class ClassroomWorkspaceServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private ClassroomService classroomService;

    // ========================================
    // INITIALIZATION
    // ========================================

    @Override
    public void init() throws ServletException {

        classroomService = (ClassroomService)
            getServletContext().getAttribute("classroomService");

        if (classroomService == null) {

            throw new ServletException(
                "ClassroomService was not initialized."
            );
        }
    }

    // ========================================
    // GET: RETRIEVE CLASSROOM WORKSPACE
    // ========================================

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        WorkspaceSnapshot workspace =
            classroomService.getWorkspaceSnapshot();

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        StringBuilder json = new StringBuilder();

        json.append("{");

        json.append("\"activeDocumentId\":\"")
            .append(escapeJson(workspace.activeDocumentId()))
            .append("\",");

        json.append("\"workspaceVersion\":")
            .append(workspace.workspaceVersion())
            .append(",");

        json.append("\"documents\":[");

        for (int i = 0; i < workspace.documents().size(); i++) {

            DocumentInfo document =
                workspace.documents().get(i);

            if (i > 0) {
                json.append(",");
            }

            json.append("{");

            json.append("\"id\":\"")
                .append(escapeJson(document.id()))
                .append("\",");

            json.append("\"title\":\"")
                .append(escapeJson(document.title()))
                .append("\"");

            json.append("}");
        }

        json.append("]}");

        response.getWriter().write(json.toString());
    }

    // ========================================
    // POST: MANAGE CLASSROOM WORKSPACE
    // ========================================

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Only the teacher can modify the workspace.
        String userRole = request.getHeader("X-User-Role");

        if (userRole == null ||
            !userRole.trim().equalsIgnoreCase("TEACHER")) {

            response.setStatus(
                HttpServletResponse.SC_FORBIDDEN
            );

            writeJson(
                response,
                false,
                "Only the teacher can modify the classroom workspace."
            );

            return;
        }

        String action = request.getParameter("action");

        if (action == null || action.isBlank()) {

            response.setStatus(
                HttpServletResponse.SC_BAD_REQUEST
            );

            writeJson(
                response,
                false,
                "Missing workspace action."
            );

            return;
        }

        action = action.trim().toLowerCase(Locale.ROOT);

        switch (action) {

            case "create":

                createDocument(request, response);

                break;

            case "activate":

                activateDocument(request, response);

                break;

            default:

                response.setStatus(
                    HttpServletResponse.SC_BAD_REQUEST
                );

                writeJson(
                    response,
                    false,
                    "Invalid workspace action."
                );
        }
    }

    // ========================================
    // CREATE DOCUMENT
    // ========================================

    private void createDocument(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        String title = request.getParameter("title");

        if (title == null || title.isBlank()) {

            response.setStatus(
                HttpServletResponse.SC_BAD_REQUEST
            );

            writeJson(
                response,
                false,
                "Document title cannot be empty."
            );

            return;
        }

        DocumentInfo document;

        try {

            document = classroomService.createDocument(title);

        } catch (IllegalArgumentException e) {

            response.setStatus(
                HttpServletResponse.SC_BAD_REQUEST
            );

            writeJson(
                response,
                false,
                e.getMessage()
            );

            return;
        }

        String json =
            "{\"success\":true," +
            "\"message\":\"Document created.\"," +
            "\"document\":{" +
                "\"id\":\"" + escapeJson(document.id()) + "\"," +
                "\"title\":\"" + escapeJson(document.title()) + "\"" +
            "}}";

        response.getWriter().write(json);
    }

    // ========================================
    // ACTIVATE DOCUMENT
    // ========================================

    private void activateDocument(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        String documentId =
            request.getParameter("documentId");

        if (documentId == null ||
            !documentId.matches("[a-zA-Z0-9_-]{1,100}")) {

            response.setStatus(
                HttpServletResponse.SC_BAD_REQUEST
            );

            writeJson(
                response,
                false,
                "Invalid document ID."
            );

            return;
        }

        boolean activated =
            classroomService.setActiveDocument(documentId);

        if (!activated) {

            response.setStatus(
                HttpServletResponse.SC_NOT_FOUND
            );

            writeJson(
                response,
                false,
                "Document does not exist."
            );

            return;
        }

        writeJson(
            response,
            true,
            "Active document updated."
        );
    }

    // ========================================
    // JSON RESPONSE
    // ========================================

    private void writeJson(
            HttpServletResponse response,
            boolean success,
            String message
    ) throws IOException {

        String json =
            "{\"success\":" + success +
            ",\"message\":\"" + escapeJson(message) + "\"}";

        response.getWriter().write(json);
    }

    private String escapeJson(String value) {

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
