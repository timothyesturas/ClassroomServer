
package com.classroom.dev.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ClassroomService {

    private static final long LOCK_TIMEOUT_SECONDS = 3;

    private static final String DEFAULT_DOCUMENT_ID = "doc-001";

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

    // ========================================
    // CLASSROOM DOCUMENTS
    // ========================================

    // LinkedHashMap preserves document creation order.
    private final Map<String, Document> documents =
        new LinkedHashMap<>();

    private int nextDocumentNumber = 2;

    // Currently selected document for the entire classroom.
    private String activeDocumentId = DEFAULT_DOCUMENT_ID;

    // Changes whenever the classroom workspace changes.
    private long workspaceVersion = 0;

    private boolean studentEditingEnabled = false;

    private String editingStudentId = null;

    private ScheduledFuture<?> lockTimeoutTask;

    // ========================================
    // DOCUMENT MODEL
    // ========================================

    private static class Document {

        private final String id;
        private final String title;

        private String content = "";
        private long version = 0;

        private Document(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    // Information shared with classroom clients.
    public record DocumentInfo(
        String id,
        String title
    ) {
    }

    // Complete classroom workspace state.
    public record WorkspaceSnapshot(
        List<DocumentInfo> documents,
        String activeDocumentId,
        long workspaceVersion
    ) {
    }

    // ========================================
    // CONSTRUCTOR
    // ========================================

    public ClassroomService() {

        documents.put(
            DEFAULT_DOCUMENT_ID,
            new Document(DEFAULT_DOCUMENT_ID, "Editor 1")
        );
    }

    // ========================================
    // DOCUMENT CREATION
    // ========================================

    public synchronized DocumentInfo createDocument(
            String title
    ) {

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                "Document title cannot be empty."
            );
        }

        String documentId;

        // Generate a unique document ID.
        do {

            documentId = String.format(
                "doc-%03d",
                nextDocumentNumber++
            );

        } while (documents.containsKey(documentId));

        Document document = new Document(
            documentId,
            title.trim()
        );

        documents.put(documentId, document);

        workspaceVersion++;

        System.out.println(
            "Document created: " +
            documentId +
            " | Title: " +
            title
        );

        return new DocumentInfo(
            document.id,
            document.title
        );
    }

    // ========================================
    // DOCUMENT RETRIEVAL
    // ========================================

    private Document getOrCreateDocument(
            String documentId
    ) {

        if (documentId == null || documentId.isBlank()) {

            throw new IllegalArgumentException(
                "Document ID cannot be empty."
            );
        }

        Document document = documents.get(documentId);

        if (document == null) {

            document = new Document(
                documentId,
                documentId
            );

            documents.put(documentId, document);

            workspaceVersion++;
        }

        return document;
    }

    // Retrieve all documents in creation order.
    public synchronized List<DocumentInfo> getDocuments() {

        List<DocumentInfo> result = new ArrayList<>();

        for (Document document : documents.values()) {

            result.add(
                new DocumentInfo(
                    document.id,
                    document.title
                )
            );
        }

        return List.copyOf(result);
    }

    // Retrieve the complete workspace state.
    public synchronized WorkspaceSnapshot getWorkspaceSnapshot() {

        return new WorkspaceSnapshot(
            getDocuments(),
            activeDocumentId,
            workspaceVersion
        );
    }

    // ========================================
    // ACTIVE DOCUMENT MANAGEMENT
    // ========================================

    // Teacher changes the selected document.
    public synchronized boolean setActiveDocument(
            String documentId
    ) {

        if (documentId == null ||
            !documents.containsKey(documentId)) {

            return false;
        }

        if (!documentId.equals(activeDocumentId)) {

            activeDocumentId = documentId;

            workspaceVersion++;

            System.out.println(
                "Active document changed to: " +
                activeDocumentId
            );
        }

        return true;
    }

    public synchronized String getActiveDocumentId() {

        return activeDocumentId;
    }

    // ========================================
    // CONTENT MANAGEMENT
    // ========================================

    // Teacher submits content for a specific document.
    public synchronized void submitContent(
            String documentId,
            String content
    ) {

        Document document =
            getOrCreateDocument(documentId);

        document.content = content;
        document.version++;

        System.out.println(
            "Teacher published document: " +
            documentId +
            " | Version: " +
            document.version
        );
    }

    // Backward compatibility.
    public synchronized void submitContent(String content) {

        submitContent(DEFAULT_DOCUMENT_ID, content);
    }

    // Student submits content for a specific document.
    public synchronized boolean submitStudentContent(
            String studentId,
            String documentId,
            String content
    ) {

        if (!studentEditingEnabled) {
            return false;
        }

        if (studentId == null ||
            !studentId.equals(editingStudentId)) {

            return false;
        }

        Document document =
            getOrCreateDocument(documentId);

        document.content = content;
        document.version++;

        refreshLockTimeout();

        System.out.println(
            "Student " + studentId +
            " published document: " + documentId +
            " | Version: " + document.version
        );

        return true;
    }

    // Backward compatibility.
    public synchronized boolean submitStudentContent(
            String studentId,
            String content
    ) {

        return submitStudentContent(
            studentId,
            DEFAULT_DOCUMENT_ID,
            content
        );
    }

    // ========================================
    // DOCUMENT CONTENT RETRIEVAL
    // ========================================

    public synchronized String getPublishedContent(
            String documentId
    ) {

        return getOrCreateDocument(documentId).content;
    }

    public synchronized long getVersion(
            String documentId
    ) {

        return getOrCreateDocument(documentId).version;
    }

    public synchronized ContentSnapshot getSnapshot(
            String documentId
    ) {

        Document document =
            getOrCreateDocument(documentId);

        return new ContentSnapshot(
            document.content,
            document.version
        );
    }

    // Backward compatibility.
    public synchronized String getPublishedContent() {

        return getPublishedContent(DEFAULT_DOCUMENT_ID);
    }

    public synchronized long getVersion() {

        return getVersion(DEFAULT_DOCUMENT_ID);
    }

    public synchronized ContentSnapshot getSnapshot() {

        return getSnapshot(DEFAULT_DOCUMENT_ID);
    }

    public record ContentSnapshot(
        String content,
        long version
    ) {
    }

    // ========================================
    // STUDENT EDITING PERMISSION
    // ========================================

    public synchronized boolean isStudentEditingEnabled() {

        return studentEditingEnabled;
    }

    public synchronized void setStudentEditingEnabled(
            boolean enabled
    ) {

        studentEditingEnabled = enabled;

        // Disabling editing also releases the current lock.
        if (!enabled) {
            releaseCurrentLock();
        }

        System.out.println(
            "Student editing: " +
            (enabled ? "ENABLED" : "DISABLED")
        );
    }

    // ========================================
    // EDITING LOCK
    // ========================================

    public synchronized boolean acquireEditingLock(
            String studentId
    ) {

        if (!studentEditingEnabled ||
            studentId == null ||
            studentId.isBlank()) {

            return false;
        }

        // Another student already owns the lock.
        if (editingStudentId != null) {

            if (!editingStudentId.equals(studentId)) {
                return false;
            }

            refreshLockTimeout();

            return true;
        }

        // No one owns the lock.
        editingStudentId = studentId;

        refreshLockTimeout();

        System.out.println(
            "Editing lock acquired by: " + studentId
        );

        return true;
    }

    // Student sends a heartbeat.
    public synchronized boolean heartbeat(
            String studentId
    ) {

        if (studentId == null ||
            !studentId.equals(editingStudentId)) {

            return false;
        }

        refreshLockTimeout();

        return true;
    }

    // Refresh automatic lock timeout.
    private synchronized void refreshLockTimeout() {

        if (lockTimeoutTask != null) {
            lockTimeoutTask.cancel(false);
        }

        String currentOwner = editingStudentId;

        lockTimeoutTask = scheduler.schedule(() -> {

            synchronized (ClassroomService.this) {

                // Only release the lock if the same student
                // still owns it.
                if (currentOwner != null &&
                    currentOwner.equals(editingStudentId)) {

                    System.out.println(
                        "Editing lock expired for: " +
                        editingStudentId
                    );

                    releaseCurrentLock();
                }
            }

        }, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // Student manually releases the editing lock.
    public synchronized void releaseEditingLock(
            String studentId
    ) {

        if (studentId != null &&
            studentId.equals(editingStudentId)) {

            releaseCurrentLock();
        }
    }

    // Release the current lock.
    public synchronized void releaseCurrentLock() {

        if (lockTimeoutTask != null) {

            lockTimeoutTask.cancel(false);
            lockTimeoutTask = null;
        }

        if (editingStudentId != null) {

            System.out.println(
                "Editing lock released: " +
                editingStudentId
            );
        }

        editingStudentId = null;
    }

    public synchronized String getEditingStudentId() {

        return editingStudentId;
    }

    // ========================================
    // SHUTDOWN
    // ========================================

    public void shutdown() {

        scheduler.shutdownNow();
    }
}
