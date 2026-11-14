
package com.classroom.dev.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Locale;

import com.classroom.dev.service.ClassroomService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/classroom/content")
public class ClassroomServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_DOCUMENT_ID = "doc-001";

    private ClassroomService classroomService;

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
    // GET: FETCH DOCUMENT CONTENT
    // ========================================

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        String documentId = getDocumentId(request);

        if (!isValidDocumentId(documentId)) {

            response.sendError(
                HttpServletResponse.SC_BAD_REQUEST,
                "Invalid document ID."
            );

            return;
        }

        ClassroomService.ContentSnapshot snapshot =
            classroomService.getSnapshot(documentId);

        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");

        response.setHeader(
            "X-Content-Version",
            String.valueOf(snapshot.version())
        );

        response.getWriter().write(snapshot.content());
    }

    // ========================================
    // POST: TEACHER OR STUDENT SUBMITS CONTENT
    // ========================================

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String documentId = getDocumentId(request);

        if (!isValidDocumentId(documentId)) {

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

        // Read submitted content without adding extra newlines.
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = request.getReader()) {

            char[] buffer = new char[4096];

            int length;

            while ((length = reader.read(buffer)) != -1) {
                content.append(buffer, 0, length);
            }
        }

        String userRole = request.getHeader("X-User-Role");

        if (userRole == null || userRole.isBlank()) {

            response.setStatus(
                HttpServletResponse.SC_FORBIDDEN
            );

            writeJson(
                response,
                false,
                "Missing user role."
            );

            return;
        }

        userRole = userRole.trim().toUpperCase(Locale.ROOT);

        // ========================================
        // TEACHER SUBMISSION
        // ========================================

        if (userRole.equals("TEACHER")) {

            classroomService.submitContent(
                documentId,
                content.toString()
            );

            writeJson(
                response,
                true,
                "Teacher content accepted."
            );

            return;
        }

        // ========================================
        // STUDENT SUBMISSION
        // ========================================

        if (userRole.equals("STUDENT")) {

            String studentId =
                request.getHeader("X-Student-Id");

            boolean accepted =
                classroomService.submitStudentContent(
                    studentId,
                    documentId,
                    content.toString()
                );

            if (!accepted) {

                response.setStatus(
                    HttpServletResponse.SC_FORBIDDEN
                );

                writeJson(
                    response,
                    false,
                    "Student editing is not permitted or you do not own the editing lock."
                );

                return;
            }

            writeJson(
                response,
                true,
                "Student content accepted."
            );

            return;
        }

        // ========================================
        // INVALID ROLE
        // ========================================

        response.setStatus(
            HttpServletResponse.SC_FORBIDDEN
        );

        writeJson(
            response,
            false,
            "Invalid user role."
        );
    }

    // ========================================
    // DOCUMENT ID
    // ========================================

    private String getDocumentId(
            HttpServletRequest request
    ) {

        String documentId =
            request.getParameter("documentId");

        if (documentId == null || documentId.isBlank()) {
            return DEFAULT_DOCUMENT_ID;
        }

        return documentId.trim();
    }

    private boolean isValidDocumentId(String documentId) {

        return documentId != null &&
               documentId.matches("[a-zA-Z0-9_-]{1,100}");
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
