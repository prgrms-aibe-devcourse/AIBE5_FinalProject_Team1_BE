package com.team1.codedock.domain.document.controller;

import com.team1.codedock.domain.document.dto.DocumentCreateRequest;
import com.team1.codedock.domain.document.dto.DocumentResponse;
import com.team1.codedock.domain.document.dto.DocumentUpdateRequest;
import com.team1.codedock.domain.document.service.DocumentService;
import com.team1.codedock.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentResponse> createDocument(
            @PathVariable Long workspaceId,
            @Valid @RequestBody DocumentCreateRequest request
    ) {
        return ApiResponse.ok(documentService.createDocument(workspaceId, request));
    }

    @GetMapping
    public ApiResponse<List<DocumentResponse>> getDocuments(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.ok(documentService.getDocuments(workspaceId, category));
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentResponse> getDocument(
            @PathVariable Long workspaceId,
            @PathVariable Long documentId
    ) {
        return ApiResponse.ok(documentService.getDocument(workspaceId, documentId));
    }

    @PatchMapping("/{documentId}")
    public ApiResponse<DocumentResponse> updateDocument(
            @PathVariable Long workspaceId,
            @PathVariable Long documentId,
            @Valid @RequestBody DocumentUpdateRequest request
    ) {
        return ApiResponse.ok(documentService.updateDocument(workspaceId, documentId, request));
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteDocument(
            @PathVariable Long workspaceId,
            @PathVariable Long documentId
    ) {
        documentService.deleteDocument(workspaceId, documentId);
        return ApiResponse.ok();
    }
}
