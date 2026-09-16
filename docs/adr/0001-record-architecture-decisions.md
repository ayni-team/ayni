# 1. Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-09-01

## Context

The architecture of Ayni is evaluated on the reasoning behind its decisions, not only on the
resulting code. Decisions taken in a meeting and never written down are impossible to defend
weeks later, and impossible for a new team member to understand.

## Decision

Every architecturally significant decision is recorded here as a short Markdown file, numbered
sequentially, following the format proposed by Michael Nygard.

A decision is architecturally significant when reversing it would require changing code across
more than one module, or when it constrains what the system can do later.

## Consequences

- Chapter IV of the report is assembled from these files instead of written from memory.
- A decision that turns out to be wrong is superseded by a new record, never edited in place,
  so the reasoning at the time remains visible.
- There is a small ongoing cost: roughly fifteen minutes per decision.
