-- 归一化 embedding 档案标签为 provider:model@dimension-cosine-r<revision>。
-- 仅重写本计划未发布期间产生的两种旧标签（<model>@<dim>-v1、<model>@<dim>-cosine-v1），
-- 不删除、不重算正文与向量；新档案标签把 provider 编码进去，离线与真实向量无法再共用标签。
UPDATE dt_knowledge_chunk c
SET embedding_profile = (CASE WHEN d.embedding_model LIKE 'deterministic%'
                              THEN 'deterministic:' ELSE 'openai:' END)
                        || d.embedding_model || '@' || d.embedding_dimension || '-cosine-r1'
FROM dt_knowledge_document d
WHERE c.document_id = d.id
  AND (c.embedding_profile = d.embedding_model || '@' || d.embedding_dimension || '-v1'
       OR c.embedding_profile = d.embedding_model || '@' || d.embedding_dimension || '-cosine-v1');

UPDATE dt_knowledge_document d
SET embedding_profile = (CASE WHEN d.embedding_model LIKE 'deterministic%'
                              THEN 'deterministic:' ELSE 'openai:' END)
                        || d.embedding_model || '@' || d.embedding_dimension || '-cosine-r1'
WHERE d.embedding_profile = d.embedding_model || '@' || d.embedding_dimension || '-v1'
   OR d.embedding_profile = d.embedding_model || '@' || d.embedding_dimension || '-cosine-v1';
