-- V2 seeds explicit role IDs (1, 3, 4), leaving the identity behind them.
-- Advance it from existing data without changing any role or user reference.
SELECT setval(
    pg_get_serial_sequence('public.roles', 'id'),
    COALESCE((SELECT MAX(id) FROM public.roles), 1),
    EXISTS (SELECT 1 FROM public.roles)
);
