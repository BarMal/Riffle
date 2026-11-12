# Tasks from Todoist (P10)

Part of [ADR 0003](../architecture/adr/0003-places-and-views.md). The Stream's Tasks section shows the tasks you have
due today and the ones that are overdue, from Todoist, and lets you complete them and add one.

## Connect and disconnect

- **Connect:** Todoist > Settings > Integrations > Developer > copy the API token. In Riffle open Settings > Stream >
  Tasks, paste it and Save. The Tasks section's "Connect Todoist" button opens that page.
- **After saving** the page only says Connected. The token is never shown again.
- **Disconnect:** Settings > Stream > Tasks > Remove token. The token is deleted from the phone at once; nothing in
  Todoist changes. Revoking the token in Todoist also works: Riffle then says Todoist did not accept it and offers to
  replace it, and makes no more requests until you do.
- Until you save a token Riffle makes no request to Todoist.

## What is sent to Todoist

Only to `https://api.todoist.com/api/v1`, over https, with `Authorization: Bearer <token>`:

| When | Request |
| --- | --- |
| Refresh | `GET /tasks/filter?query=today | overdue&limit=200` (pages followed, at most five) |
| Project names (only when a task's project is new to the app) | `GET /projects?limit=200` |
| You tick a task | `POST /tasks/{id}/close` |
| You add a task | `POST /tasks` with `content` (your text) and `due_string` "today" |

No analytics, no other host, no redirects followed (so the token cannot be sent elsewhere).

## What is stored

- **The token:** encrypted with an AES-256-GCM key in the Android Keystore (the key cannot leave the device) in the
  private preferences file `riffle_todoist_token`. It is excluded from Auto Backup and device transfer
  (`backup_rules.xml`, `data_extraction_rules.xml`), has no field in Riffle's backup file, and is wrapped in a type whose
  `toString()` is redacted. It is never logged.
- **The tasks:** in memory only, while the app runs. Never written to layout, settings, backup or diagnostics.
- Task text follows the Tasks level on Settings > Privacy: Hide text shows "Hidden content" and turns off the tick box.

## Refresh

On Stream open, on the refresh button or Retry, after an action, and every five minutes while a Tasks section is
shown. The periodic refresh follows the RSS policy (`rss-refresh.md`): it is skipped on a metered network and in battery
saver. There is no background (WorkManager) refresh: tasks are not kept anywhere else, so there is nothing for it to
update. Requests run on one background thread, never the main thread, and one at a time.

## States

| State | Shown |
| --- | --- |
| No token | "Todoist is not connected" and Connect Todoist |
| Token rejected (401/403) | "Todoist did not accept the saved token" and Replace token |
| Offline or error, with earlier tasks | The tasks and "Can't reach Todoist. Showing the tasks from the last refresh." with Retry |
| Offline or error, no tasks yet | "Can't reach Todoist, and there are no saved tasks to show." with Retry |
| Nothing due | The quick-add field and "No tasks due." |

## Actions

- **Complete:** the task ticks at once; if Todoist refuses or cannot be reached it is put back and the Stream shows its
  usual "couldn't do that" message. A recurring task moves to its next date on Todoist's side.
- **Open:** the Todoist app (`todoist://task?id=`) if installed, otherwise `https://app.todoist.com/app/task/{id}`.
- **Add:** the quick-add field creates a task with that text, due today.

## Limits

The API paths and field names live in one file, `TodoistApiMapping.kt`. They follow the official Todoist API v1 and its
official Python client; the web address and app link of a task and the exact due-date formats are not confirmed against
the reference page. Tasks without a due date are not fetched.

## Device check

Save a real token, open the Stream: tasks appear. Tick one: it disappears and is done in Todoist. Add one: it appears.
Turn on airplane mode, tap refresh: the stale note shows. Remove the token: Connect Todoist shows.
