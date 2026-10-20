-- Optional description of a course, at most 1000 characters.
ALTER TABLE courses
    ADD COLUMN description varchar(1000);
