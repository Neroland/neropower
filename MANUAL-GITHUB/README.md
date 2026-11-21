# MANUAL-GITHUB

Files in this folder are **workflow changes that must be applied by hand** under `.github/`.
Automated tooling in this repository does not write to `.github/` directly, so anything that
belongs there is staged here for a maintainer to copy across and commit.

## `wiki.yml` — private-reference guard

Copy over `.github/workflows/wiki.yml`. It is the wiki-sync workflow (mirrors `wiki/*.md` into
`<repo>.wiki.git` on every push to `main` that touches `wiki/`, or on manual dispatch) with one
extra step in front of the push: a **private-reference guard** that fails the job if any file
under `wiki/` matches

```text
neroland-mc-[e]|BUILD-[P]ROMPT|AUDIT-[0]|AUDIT[.]md|PROGRESS[.]md|RUNTIME-[S]CRIPT|decision[s]/|REWORK-[P]ROMPT|PLAN-[0]|SMOKE-[T]EST|MANUAL-|[m]odrinth
```

The wiki is public; private planning documents and audit notes must never be named there. Run the
same grep locally before pushing:

```sh
grep -rniE 'neroland-mc-[e]|BUILD-[P]ROMPT|AUDIT-[0]|AUDIT[.]md|PROGRESS[.]md|RUNTIME-[S]CRIPT|decision[s]/|REWORK-[P]ROMPT|PLAN-[0]|SMOKE-[T]EST|MANUAL-|[m]odrinth' wiki/
```

One-time setup (from the workflow header): create any page in the repository's Wiki tab once so
the wiki git repository exists, and allow `GITHUB_TOKEN` write access under
Settings → Actions → General → Workflow permissions.
