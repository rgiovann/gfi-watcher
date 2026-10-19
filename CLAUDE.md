# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

`gfi-watcher` is a small Java 21 program that watches GitHub repositories for new, unassigned
"good first issue" issues and shows a desktop notification (Windows system tray). It is the
maintainer's personal tool; the README describes its behavior, configuration and modes.

Build and test with `mvn package` (produces `target/gfi-watcher.jar`).

`config.json` is the maintainer's own configuration and is not in the repository
(`.gitignore`); `config.example.json` is the template. Tests never read `config.json`.

## Language

The conversation with the maintainer may be in Portuguese (pt-BR). Everything written to the
repository is in English: code, comments, tests, README, commit messages and pull request text.

## GitHub actions need explicit authorization

- **Never open a pull request without the maintainer's explicit authorization for that pull
  request.** Finishing a change, pushing a branch, or an earlier authorization for another
  pull request is not authorization. Ask, and wait for the answer.
- The same applies to anything else posted on GitHub in the maintainer's name: issues,
  comments, reviews, and edits to them.

## Never reference other projects' issues or pull requests in a linkable form

Text in this repository's commits, pull requests, issues and comments must not reference an
issue or pull request of another repository in a form GitHub turns into a link: `owner/repo#123`,
or a full `https://github.com/owner/repo/issues/123` URL.

GitHub records such a reference on the target issue as "mentioned this", under the
maintainer's name, visible to that project's maintainers and contributors. It happened once:
a pull request description here cited two Debezium issues, and the mention appeared on them.
The maintainer is trying to become a contributor to those projects, and such mentions look
like monitoring other people's issues.

When a reference is needed, describe it without the number ("a notified issue that had
already been claimed"), or put it in a code span (`` `owner/repo#123` ``), which GitHub does
not link. This applies to commit messages too: GitHub links references in them when the
commit is pushed.
