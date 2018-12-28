-- assumes db, user and pass all equal to 'ahungry'

\c ahungry

create table project (project_id SERIAL PRIMARY KEY, code TEXT, name TEXT, description TEXT);

INSERT INTO project (code, name, description)
VALUES ('ruse', 'RUSE', 'Fun with FUSE filesystems.')
, ('org-jira', 'Emacs Org Jira Mode', 'A useful mode for GNU Emacs.')
;
