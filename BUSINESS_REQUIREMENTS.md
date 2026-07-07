# Business Requirements (functional)

Short functional scope for the Berlin Demo Map demo build.

## Purpose

Show public assemblies, demonstrations, and rallies in Berlin on a map and a timeline, so a
user can see where and when events take place and how much **operational disruption** to expect
when moving through the city on a given day.

The city-impact score describes **events**, never people. No demographic attribute (nationality,
ethnicity, religion, language, sex, gender, origin, identity, or political cause) is ever used as
an impact factor, and every assembly's public label is deliberately neutral.

## Data source

All data comes from the public Berlin Police assembly page (no authentication):
`https://www.berlin.de/polizei/service/versammlungsbehoerde/versammlungen-aufzuege/`

## What the user can do

1. Open the app in a browser — no login.
2. Filter events by **date range**, **district**, **impact level**, and **category**.
3. Switch the map **display mode**: **Points**, **Heatmap**, or **PLZ** (per-postal-code density).
4. See matching events on a **Berlin map** as colored points (color = impact level), with
   impact-zone circles in Points mode.
5. See the same events in a **timeline** panel (title, date/time, district, location, impact
   badge + score, and the reasons behind the score).
6. Click a timeline card → the map highlights and pans to that event; click a map point → the
   timeline scrolls to and highlights that card.
7. Export the currently filtered events as **CSV**.
8. See a **stale-data warning** if the last successful import is older than 6 hours.

## City-impact model

Impact is a purely operational score (0–100), driven mostly by operational signals rather than
topic, so two assemblies on different topics get the same impact if they disrupt the city the same way.

| Level | Score |
|---|---|
| LOW | 0–24 |
| MEDIUM | 25–49 |
| HIGH | 50–74 |
| VERY_HIGH | 75–100 |

Signals include: large / very large expected crowd, long or all-day duration, evening start, and
visible counter-event or blockade wording. A small topic weight is added on top. Every score comes
with human-readable reason sentences.

## Data freshness

- Data refreshes automatically every 4 hours; a manual refresh can be triggered via an
  internal, token-protected endpoint.
- A failed import **never** replaces the last good snapshot — the user always sees the last
  known good state.

## Not in scope

User accounts, event history across imports, prediction of future events, real-time push,
native mobile, street-level march-route rendering, and standalone secondary map pages.
