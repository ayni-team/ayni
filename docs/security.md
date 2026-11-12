# Security of the repository

## What happened

In November 2026 obfuscated code was found on branches of this repository, added by force pushes
that kept the author and the date of the commit they replaced. It never reached `develop` or `main`,
which do not accept force pushes. It ran in two ways, with nobody executing anything on purpose:

- **On a build:** hidden at the end of `web/tailwind.config.js`, after thousands of tabs. `npm run
dev`, `npm run build` and the Tailwind extension of an editor all load that file.
- **On opening the folder:** `.vscode/tasks.json`, with a task set to run by itself, launched a file
  named like a font under `public/fonts/`.

An account that pushes code like this has probably been compromised. The person behind it is
probably a victim too, so treat it as an incident to fix together.

## What guards against it

| Guard                                 | What it does                                                                                                             |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `web/scripts/check-injected-code.mjs` | Looks for the marks of this code. It runs first in the CI, before `npm ci`, and before `npm run dev` and `npm run build` |
| `Security` workflow                   | Runs that check on every push to every branch, including branches with no pull request                                   |
| No force pushes on any branch         | A ruleset on GitHub. It is what stopped this from reaching `develop` and `main`                                          |
| Review of every pull request          | Read the list of changed files: a screen has no reason to touch a configuration file                                     |

The check is a net for what has been seen. A new variant may pass it.

## What each of us does

- **Keep editor tasks from running by themselves.** In the settings of your user, in VS Code and
  Cursor: `"task.allowAutomaticTasks": "off"`, and keep Workspace Trust on. Do not trust a folder
  because it is a university project.
- **Never run `npm install` or open in an editor a branch that you have not looked at**, least of all
  one that appeared with a force push. `git diff origin/develop... --stat` reads it safely.
- **Use a token with the least it needs,** and sign out of what you do not use on that machine.
- **Do not bypass the check.** If it fires by mistake, fix the rule in a pull request.

## If the check fires, or you find something like this

1. **Do not run anything in that folder and do not open it in an editor.** Do not delete it yet.
2. Tell the lead in the team chat, with the name of the branch and of the file.
3. Assume that machine may be compromised. From **another** device, revoke the GitHub tokens and
   sessions of that account (Settings → Applications, Developer settings, Sessions), then the
   rest of what that machine could reach: SSH keys, cloud credentials, saved passwords, wallets.
4. Check that machine, or reinstall it, before signing in again.
5. The lead removes the code, checks every branch, rotates the secrets of the repository and, if
   commits need to disappear from GitHub, asks GitHub Support: pull request refs cannot be deleted
   by anybody else.
