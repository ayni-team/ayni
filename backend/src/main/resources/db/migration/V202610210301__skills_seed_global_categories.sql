-- =============================================================================
-- Skills: the global categories every installation starts with
-- =============================================================================
-- Technology, Languages and Design ship with Ayni and are the same for every
-- university, so they are reference data and not demonstration data: without
-- them a deployment has nowhere to put a global tool, and a student cannot
-- propose one (US42) because a proposal names its category. Until now only the
-- dev profile created them, from DemoSkillsData.
--
-- The identifiers are the ones DemoSkillsData always used, so a development
-- database that already has them keeps them, and requests saved in Swagger or
-- in the frontend keep working. ON CONFLICT DO NOTHING leaves alone a category
-- that already exists, by its identifier or by its name.
--
-- "University courses" is not here on purpose: it is created the first time a
-- university loads its curriculum (US51), or by the demo data.
-- =============================================================================

INSERT INTO skills.categories (id, name, sort_order) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'Technology', 1),
  ('a0000000-0000-4000-8000-000000000002', 'Languages', 2),
  ('a0000000-0000-4000-8000-000000000003', 'Design', 3)
ON CONFLICT DO NOTHING;
