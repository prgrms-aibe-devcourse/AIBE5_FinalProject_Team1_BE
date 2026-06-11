-- ============================================================
-- V7: ApiSpec source_type 제약 확장 + Workspace swagger_url 추가
-- ============================================================

-- workspaces에 swagger_url 컬럼 추가
ALTER TABLE workspaces ADD (swagger_url VARCHAR2(500) NULL);

-- api_specs source_type 제약 수정 ('swagger', 'AI' 추가)
ALTER TABLE api_specs DROP CONSTRAINT chk_api_specs_source;
ALTER TABLE api_specs ADD CONSTRAINT chk_api_specs_source
    CHECK (source_type IS NULL OR source_type IN ('manual', 'github', 'imported', 'swagger', 'AI'));
