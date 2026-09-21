ALTER TABLE electronic_document
    ADD COLUMN issue_time time NOT NULL DEFAULT LOCALTIME;
