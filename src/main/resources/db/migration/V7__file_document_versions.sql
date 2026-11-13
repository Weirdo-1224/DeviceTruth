-- 文件内容不再等同于文档版本：每次上传保留自己的字节与导入元数据，可关联到可复用的文档。
ALTER TABLE dt_uploaded_file DROP CONSTRAINT dt_uploaded_file_owner_id_project_id_checksum_key;
CREATE INDEX dt_uploaded_file_content ON dt_uploaded_file(owner_id, project_id, checksum);

-- V6 之前同一文件可能已挂多个文档版本；先保留首条，其余克隆为带明确版本的文件记录。
WITH first_document AS (
    SELECT DISTINCT ON (source_file_id) id,source_file_id,title,domain,device_model,doc_version,data_nature
    FROM dt_knowledge_document ORDER BY source_file_id,created_at,id
)
UPDATE dt_uploaded_file f SET import_title=COALESCE(f.import_title,d.title),
    import_domain=COALESCE(f.import_domain,d.domain),
    import_device_model=COALESCE(f.import_device_model,d.device_model),
    import_version=COALESCE(f.import_version,d.doc_version),
    import_data_nature=COALESCE(f.import_data_nature,d.data_nature)
FROM first_document d WHERE d.source_file_id=f.id;

WITH ranked AS (
    SELECT id,source_file_id,row_number() OVER (PARTITION BY source_file_id ORDER BY created_at,id) AS position
    FROM dt_knowledge_document
)
INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,
    parse_status,parse_error,created_at,import_title,import_domain,import_device_model,import_version,
    import_data_nature)
SELECT gen_random_uuid(),d.owner_id,d.project_id,'knowledge/legacy-'||d.id,f.original_name,f.mime,f.checksum,
    f.byte_size,f.parse_status,f.parse_error,d.created_at,d.title,d.domain,d.device_model,d.doc_version,d.data_nature
FROM dt_knowledge_document d
JOIN ranked r ON r.id=d.id AND r.position>1
JOIN dt_uploaded_file f ON f.id=d.source_file_id;

UPDATE dt_knowledge_document d SET source_file_id=f.id
FROM dt_uploaded_file f WHERE f.path_key='knowledge/legacy-'||d.id;

CREATE TABLE dt_knowledge_document_file (
    file_id UUID NOT NULL,
    document_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (file_id),
    FOREIGN KEY (file_id, owner_id, project_id)
        REFERENCES dt_uploaded_file(id, owner_id, project_id),
    FOREIGN KEY (document_id, owner_id, project_id)
        REFERENCES dt_knowledge_document(id, owner_id, project_id)
);
CREATE INDEX dt_knowledge_document_file_document ON dt_knowledge_document_file(document_id);

INSERT INTO dt_knowledge_document_file(file_id, document_id, owner_id, project_id)
SELECT source_file_id, id, owner_id, project_id FROM dt_knowledge_document;
