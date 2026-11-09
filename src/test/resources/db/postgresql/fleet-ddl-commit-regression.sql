CREATE TABLE IF NOT EXISTS public.fleet_ddl_commit_regression (
    id integer PRIMARY KEY,
    executions integer NOT NULL
);
INSERT INTO public.fleet_ddl_commit_regression(id, executions) VALUES (1, 0)
ON CONFLICT DO NOTHING;
UPDATE public.fleet_ddl_commit_regression SET executions = executions + 1 WHERE id = 1;
