-- 资产事实引用与知识引用共用引用登记表的约束修正（03-01）。
-- dt_evidence_reference 自 V2 起是多态表：KNOWLEDGE_DOCUMENT 引用必须带真实知识切片，
-- 而 CONFIGURATION/FACTORY 等资产来源引用没有“切片”概念，chunk_id 由写入方按来源标识派生。
-- 原 chunk_id 外键对资产来源行永远不可满足（CHECK 放行、外键拒绝），
-- 这里仅删除该外键；CHECK“知识引用必须有 chunk_id”保留，
-- 知识切片的存在性由导入路径（先写切片再写引用）保证。

ALTER TABLE dt_evidence_reference
    DROP CONSTRAINT dt_evidence_reference_chunk_id_owner_id_project_id_fkey;
