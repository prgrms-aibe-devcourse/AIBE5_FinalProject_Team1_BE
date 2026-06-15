ALTER TABLE documents DROP CONSTRAINT chk_documents_category;
ALTER TABLE documents ADD CONSTRAINT chk_documents_category
    CHECK (category IS NULL OR category IN ('manual', 'faq', 'release'));
