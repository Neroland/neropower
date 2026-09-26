# MANUAL-GITHUB

Files in this folder are **workflow changes that must be applied by hand** under `.github/`.
Automated tooling in this repository does not write to `.github/` directly, so anything that
belongs there is staged here for a maintainer to copy across and commit.

## `wiki.yml` — new workflow

Copy to `.github/workflows/wiki.yml`. It is NeroTech's wiki-sync workflow (mirrors `wiki/*.md`
into `<repo>.wiki.git` on every push to `main` that touches `wiki/`, or on manual dispatch) with
one extra step in front of the push: a **private-reference guard** that fails the job if any file
under `wiki/` matches

```text
neroland-mc-[e]cosystem|REWORK-PROMPT|PLAN-0.1.0|AUDIT.md|SMOKE-TEST|MANUAL-
```

The wiki is public; planning documents, audit notes and the umbrella repository must never be
named there. Run the same grep locally before pushing:

```sh
grep -rniE 'neroland-mc-[e]cosystem|REWORK-PROMPT|PLAN-0.1.0|AUDIT.md|SMOKE-TEST|MANUAL-' wiki/
```

One-time setup (from the workflow header): create any page in the repository's Wiki tab once so
the wiki git repository exists, and allow `GITHUB_TOKEN` write access under
Settings → Actions → General → Workflow permissions.

## `publish.yml` — patch already delivered separately

The release-pipeline change for `.github/workflows/publish.yml` (manual `workflow_dispatch` only,
gated on a `confirm_release` input; no `push` trigger) was delivered as a patch in the working
session that neutralised the pipeline, not as a file here. Apply that patch by hand as well;
nothing in this folder supersedes it.

## Re-arming `publish.yml` for 0.1.0-beta.1 (Stage 11 — do NOT do this before runtime verification)

1. Restore a trigger: either keep `workflow_dispatch` + `confirm_release` (recommended) or re-add the
   `push: branches: [main], paths: [gradle.properties]` trigger from before Stage 0.
2. Replace the `mc-publish` CurseForge upload with the ecosystem's direct-curl upload: `max-parallel: 1`,
   a 4-attempt retry on HTTP 5xx (CurseForge returns 500s on concurrent uploads) — copy the block from
   NeroTech's `.github/workflows/publish.yml` (its `publish` job, "Upload to CurseForge" step).
3. Keep the Modrinth **v3** `PATCH /v3/version/{id}` environment-metadata step that follows `mc-publish`.
4. Confirm the four secrets exist and point at **NeroPower's** CurseForge/Modrinth projects (the earlier
   runs failed at upload — most likely missing/incorrect project ids), and that the Modrinth description
   workflow no longer carries any Nerospace copy (it doesn't, after Stage 0).
5. Bump `mod_version=0.1.0-beta.1`; the `detect` job cuts tag `v0.1.0-beta.1` only after all nine
   uploads succeed.
