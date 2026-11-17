# Label history for this golden set

All changes below were made before any search was run on this corpus. Nothing was changed after seeing results.

1. 2026-11-17 19:02 +0530. 44 questions and labels written from the code (20 naming an identifier, 20 prose, 4 development).
2. A separate read-only agent that had not seen the labels located the answer for every question from the code alone (`blind-review.tsv`).
   - Same file as the label: 44 of 44. Overlapping lines: 44 of 44. Mean line overlap (Jaccard) 0.87.
   - The low-overlap rows are span width only (reviewer narrower or wider), never a different location.
   - It flagged five questions as having a second plausible location. NL05 was kept: the question says server and the other place is client-side. NL09, NL11, NL14 and NL19 were reworded to point at one location. Files, line ranges and reference facts did not change.
3. A human spot check of 10 randomly chosen scored questions is requested in `HUMAN_REVIEW.md`. Its outcome is recorded below.

Human review outcome (reviewer: the project owner, 10 randomly chosen scored questions): 4 OK (ID01, ID08, ID17, NL11), 4 LOOSE (ID07, NL10, NL12, NL15), 2 WRONG (ID05, NL07). A 6 of 10 pass rate on first draft means the AI-only check was not sufficient on its own.

Fixes made before any search run:
- ID05 WRONG: the label implied a 60 second cache life, but the cache only drops expired entries on a 60 second sweep and `getPrice` never checks `expiresAt` on read, so a quote can live 60 to 120 seconds. The question now asks only what expiry time is attached when the quote is stored.
- NL07 WRONG: the span missed the user row deletion and the cookie clearing. Span widened to lines 9 to 40.
- ID07, NL10 LOOSE: spans narrowed to the lines that answer the question (22; 84 to 85).
- NL12 LOOSE: span widened to include the required-field check (8 to 22). NL15 LOOSE: span widened to include how user details are built (6 to 26).

Re-audit of the other 30 for the same two faults (a span missing part of a multi-part answer; a claim stronger than the code supports) found three more, fixed the same way:
- ID11 and DEV4 had the same cache and sweep timing nuance as ID05. They now ask for the expiry time attached, and the file age that triggers deletion.
- NL09 had been reworded to ask about price, previous close and change but kept a span covering only the price line. Span widened to lines 15 to 24.

Side finding, not part of the benchmark: the reviewer noticed that the InsightStox sale formula in `insertTransaction.js` lines 84 to 85 subtracts sale proceeds at the current price, whereas average-cost accounting leaves the per-share average unchanged on a partial sale. NL10 asks what the code does, so its label stays valid.
