-- Run once on an existing MindMerge database before deploying rejection support.
ALTER TABLE decision MODIFY status ENUM('proposed', 'decided', 'superseded', 'rejected') NOT NULL DEFAULT 'proposed';
