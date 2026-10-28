# Pull requests and branch protection

Nothing reaches `develop` or `main` except through a pull request approved by the team lead. This
is not a matter of trust: it is the only place where somebody looks at a change as a whole, and it
is what keeps the architecture from eroding one hurried commit at a time.

## What to configure on GitHub, once

After pushing the repository, open **Settings → Branches → Add branch ruleset** (or *Add rule* on
older interfaces) and create one rule for `main` and another for `develop`, both with:

| Setting | Value |
|---|---|
| Require a pull request before merging | on |
| Required approvals | 1 |
| Require review from Code Owners | on |
| Dismiss stale approvals when new commits are pushed | on |
| Require status checks to pass | on, selecting `Backend` and `Web` |
| Require branches to be up to date before merging | on |
| Allow force pushes | off |
| Allow deletions | off |

`Require review from Code Owners` is what makes `.github/CODEOWNERS` binding. Without it, that file
only suggests a reviewer.

Also, in **Settings → General → Pull Requests**, enable **Allow squash merging** and **Allow merge
commits**, and nothing else. Every branch lands on `develop` squashed, as a single commit whose
message is the title of the pull request. That keeps the history readable and is why the title
follows Conventional Commits. Merge commits are for releases only (below).

## How the team works

1. Move the Trello card to **In Process**.
2. Branch from `develop`: `feature/US05-T2-cancel-booking`.
3. Implement the slice end to end: migration, entity, use case, endpoint, screen.
4. Open the pull request. The template asks for what it does and how to verify it.
5. CI runs. A red check is not reviewed: fix it first.
6. The lead reviews, asks for changes if needed, and merges with squash.
7. The card moves to **Done**.

Nobody merges their own pull request, including the lead: if the lead is the author, another member
approves it. That rule exists so that no change reaches `develop` without a second pair of eyes.

## Releases

A release takes `develop` to `main`, and merging it is all there is to do:

1. Take a database backup (`deploy/README.md`, "Backup and restore"): migrations only go forward.
2. Open a pull request from `develop` into `main`, titled `chore(release): v1.2.0`. Without a
   version in the title, the release takes the next patch after the latest tag (`v1.2.0` →
   `v1.2.1`).
3. When CI passes, merge it **with a merge commit**, never squashed:
   `gh pr merge <number> --merge`.

The **Release** workflow then tags the merge commit, publishes the image of the backend, and moves
the backend machine and the web application to it (`deploy/README.md`, "Updating"). Follow it in
the Actions tab; a release that failed half way is finished by running **Deploy** by hand with
its tag.

A squashed release would leave on `main` a commit that `develop` never has, so `main` and
`develop` would share nothing but the first commit, and every following release would conflict
in every file changed since. A merge commit keeps `develop` inside `main`: the next release
carries only what is new. `main` then always holds one commit more than `develop`, its merge
commit, so the rule "up to date before merging" counts a release as behind; the lead merges it
anyway, as the administrator of the rule, and nothing needs to go back into `develop`.

## What the reviewer checks

Beyond whether it works:

- **Module boundaries.** Does this module import another one? CI catches it, but a class placed in
  the wrong module compiles fine and is still wrong.
- **The tenant.** Does every query filter by `tenant_id`? Until the database enforces it, a missing
  filter is a data leak between universities and review is the only thing standing in the way.
- **Migrations.** New file, never an edit to an applied one.
- **The database design.** Columns that do not appear in `docs/database` need a reason.
- **Naming.** English everywhere.

## Why the lead holds the merge button

The team is six people, some of them working on this kind of project for the first time, and the
report is graded on architectural coherence rather than on lines of code. One person keeping a whole
picture of the system is worth more than the hours it costs them, and it is far cheaper than
undoing a week of work built on a wrong boundary.

This is not permanent. As the team gets comfortable with the structure, review can rotate.
