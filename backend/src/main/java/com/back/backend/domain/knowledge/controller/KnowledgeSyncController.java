package com.back.backend.domain.knowledge.controller;

import com.back.backend.domain.knowledge.dto.KnowledgeSyncResult;
import com.back.backend.domain.knowledge.service.KnowledgeSyncService;
import com.back.backend.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Knowledge sync 트리거 엔드포인트.
 * localhost IP에서만 접근 가능 (SecurityConfig에서 제한).
 * JWT 불필요 — SSH 접속 후 curl로 실행:
 *   curl -X POST http://localhost:8080/api/v1/knowledge/sync
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeSyncController {

    private final KnowledgeSyncService syncService;

    public KnowledgeSyncController(KnowledgeSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<KnowledgeSyncResult>> sync() {
        KnowledgeSyncResult result = syncService.syncAll();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
