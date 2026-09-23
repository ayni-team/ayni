-- =============================================================================
-- Skills: sample categories and catalog items
-- =============================================================================
-- Nothing in this module's scope creates a category or a global tool: they ship
-- with Ayni. University courses are reported by each institution's academic
-- system, which the platform does not integrate with yet either. Until either
-- exists, this seed is what lets US13 (offer a course I already passed) and
-- US40 (declare what I need and can help with) work against real rows instead
-- of an empty catalogue.
--
-- Tenant "UPC" matches the one every other module's tests already use.
-- =============================================================================

INSERT INTO skills.categories (id, name, sort_order) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'Tecnología',       1),
  ('a0000000-0000-4000-8000-000000000002', 'Idiomas',          2),
  ('a0000000-0000-4000-8000-000000000003', 'Diseño',           3),
  ('a0000000-0000-4000-8000-000000000004', 'Cursos UPC',       4);

-- Global tools: the same for every university, no course code.
INSERT INTO skills.catalog_items
  (id, scope, tenant_id, category_id, name, description, course_code, status) VALUES
  ('b0000000-0000-4000-8000-000000000001', 'GLOBAL', NULL,
   'a0000000-0000-4000-8000-000000000001',
   'Python', 'Programación en Python, desde sintaxis básica hasta librerías de datos.',
   NULL, 'ACTIVE'),
  ('b0000000-0000-4000-8000-000000000002', 'GLOBAL', NULL,
   'a0000000-0000-4000-8000-000000000001',
   'Git y control de versiones', 'Flujo de trabajo con Git, ramas y resolución de conflictos.',
   NULL, 'ACTIVE'),
  ('b0000000-0000-4000-8000-000000000003', 'GLOBAL', NULL,
   'a0000000-0000-4000-8000-000000000002',
   'Inglés conversacional', 'Práctica de conversación en inglés para nivel intermedio.',
   NULL, 'ACTIVE'),
  ('b0000000-0000-4000-8000-000000000004', 'GLOBAL', NULL,
   'a0000000-0000-4000-8000-000000000003',
   'Figma', 'Diseño de interfaces y prototipado en Figma.',
   NULL, 'ACTIVE');

-- University courses: scoped to UPC, each with the course code its academic
-- system would report on an approved-courses record.
INSERT INTO skills.catalog_items
  (id, scope, tenant_id, category_id, name, description, course_code, status) VALUES
  ('b0000000-0000-4000-8000-000000000101', 'UNIVERSITY', 'UPC',
   'a0000000-0000-4000-8000-000000000004',
   'Fundamentos de Arquitectura de Software', 'Estilos, patrones y vistas arquitectónicas.',
   '1ASI0657', 'ACTIVE'),
  ('b0000000-0000-4000-8000-000000000102', 'UNIVERSITY', 'UPC',
   'a0000000-0000-4000-8000-000000000004',
   'Base de Datos I', 'Modelado relacional, SQL y normalización.',
   '1ASI0616', 'ACTIVE'),
  ('b0000000-0000-4000-8000-000000000103', 'UNIVERSITY', 'UPC',
   'a0000000-0000-4000-8000-000000000004',
   'Cálculo I', 'Límites, derivadas e integrales de una variable.',
   '1MAT0101', 'ACTIVE'),
  ('b0000000-0000-4000-8000-000000000104', 'UNIVERSITY', 'UPC',
   'a0000000-0000-4000-8000-000000000004',
   'Desarrollo Backend', 'Diseño e implementación de servicios del lado del servidor.',
   '1ASI0625', 'ACTIVE');
