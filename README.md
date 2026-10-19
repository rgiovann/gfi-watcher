# gfi-watcher

Watches GitHub repositories for **new, unassigned "good first issue" issues** and shows a
desktop notification, so you can pick one up before someone else does.

GitHub's own *Watch → Custom → Issues* notifies every new issue of a repository. In a large
project that is dozens a day. gfi-watcher filters by label across several repositories and only
tells you about the ones you can actually take.

## How it works

- Every `pollIntervalMinutes`, it runs one GitHub search per repository and label:
  `repo:<repo> is:issue is:open no:assignee label:"<label>"`.
- The **first** check of a repository records the issues already open, without notifying.
  From then on, only issues that appear later are notified.
- What was already seen is saved, so restarting the program doesn't repeat notifications.
- It runs as an icon in the system tray (next to the clock on Windows). Clicking the
  notification, or *Open latest issue* in the icon's menu, opens the issue in the browser.

## Requirements

- Java 21 or later
- Maven 3.9 or later, to build

## Build

```shell
mvn package
```

This runs the tests and produces `target/gfi-watcher.jar`, a single jar with its dependencies.

## Configure

Copy `config.example.json` to `config.json` and edit it:

```shell
cp config.example.json config.json
```

`config.json` is your own file: it is in `.gitignore`, so your changes never show up in
`git status` or reach a commit. The example:

```json
{
  "pollIntervalMinutes": 30,
  "repositories": [
    { "repo": "debezium/dbz",     "labels": ["good first issue"] },
    { "repo": "trinodb/trino",    "labels": ["good first issue"] },
    { "repo": "questdb/questdb",  "labels": ["Good first issue"] },
    { "repo": "JabRef/jabref",    "labels": ["good first issue"] }
  ]
}
```

- `labels` are per repository because their spelling differs between projects.
  Check the exact name on the repository's *Issues → Labels* page.
- `pollIntervalMinutes` must be at least 5. New good first issues are usually claimed within a
  day or two, so checking every 30 minutes is plenty.
- `maxComments` is optional. When set, only issues with at most that many comments are listed
  and notified: `0` keeps only issues nobody has commented on yet. Left out, there is no limit.
  A comment doesn't always mean someone took the issue (it may be a maintainer adding context,
  or a claim later released), which is why every issue shows its comment count either way.
- An unknown key is an error, so a typo doesn't go unnoticed.

The config file is looked up next to the jar first, then in the current directory, unless
`--config=<path>` is given.

### GitHub token (optional)

Without a token, GitHub's search API allows 10 requests per minute; with one, 30. Each
repository and label is one request, and gfi-watcher spaces them to stay under the limit, so a
token is only useful with many repositories. To use one, create a fine-grained token with
read-only access to public repositories, and set it in the `GITHUB_TOKEN` environment variable.
Don't put it in `config.json`.

## Run

```shell
java -jar target/gfi-watcher.jar --list    # print the issues open right now, fewest comments first (changes nothing)
java -jar target/gfi-watcher.jar --once    # run one check in the console and exit
java -jar target/gfi-watcher.jar           # stay in the system tray and check periodically
```

On Windows, `javaw -jar gfi-watcher.jar` starts the tray mode without a console window.

In Git Bash, use forward slashes (`target/gfi-watcher.jar`): a backslash is an escape character
there. gfi-watcher detects Git Bash and prints in UTF-8, which its terminal expects, so accented
letters in issue titles show correctly. In PowerShell, run `chcp 65001` first if they don't.

### Start with Windows

1. Copy `target/gfi-watcher.jar` and your `config.json` to a folder of their own,
   e.g. `C:\Tools\gfi-watcher`.
2. Press `Win+R`, type `shell:startup` and press Enter.
3. In that folder, create a shortcut with this target:

   ```
   javaw -jar C:\Tools\gfi-watcher\gfi-watcher.jar
   ```

Only one instance runs at a time; a second one exits.

## Files

Kept in `%APPDATA%\gfi-watcher` on Windows, `~/.gfi-watcher` elsewhere:

| File | Contents |
|---|---|
| `seen.json` | Issues already seen, and the repositories already baselined |
| `gfi-watcher.0.log` | What each check found, and any errors |

Deleting `seen.json` makes the next check baseline every repository again.

## Limitations

- Only issues created **after** the first check are notified. To see what is open right now,
  use `--list`.
- An issue whose assignee is removed later isn't notified again: it was already seen.
- A failing repository (network error, rate limit, a renamed repository) is logged and
  skipped; the others are still checked.
