CREATE TABLE public.course_accreditations (
    course_id integer NOT NULL REFERENCES public.courses(id),
    accreditation_id integer NOT NULL REFERENCES public.accreditations(id),
    starts integer,
    ends integer,
    PRIMARY KEY (course_id, accreditation_id)
);
