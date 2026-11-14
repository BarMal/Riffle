# Tasks from Todoist (P10)

Part of [ADR 0003](../architecture/adr/0003-places-and-views.md). The Stream's Today page (the agenda and the tasks in
one page, dock modes slice 4b) shows the tasks you have due today and the ones that are overdue, from Todoist, as a "To
do today" checklist card, and lets you complete them and add one. The old Tasks page is gone; everything it did is on
that card (or, in the List view, under a "Tasks" heading after the agenda).

## Connect and disconnect

- **Connect:** Todoist > Settings > Integrations > Developer > copy the API token. In Riffle open Settings > Stream >
  Tasks, paste it and Save. The "Connect Todoist" button of the Today page's notice opens that page.
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

On the Today page the tasks are one input beside the calendar: what they cannot show is a card, not a page.

| State | Shown |
| --- | --- |
| No token | With events: the events, and a "Todoist is not connected" card behind them with Connect Todoist. With nothing else: that card alone |
| Token rejected (401/403) | "Todoist did not accept the saved token" and Replace token, the same way |
| Offline or error, with earlier tasks | The checklist, and in place of its "+N more" line "Can't reach Todoist. Showing the tasks from the last refresh."; the refresh button beside the add field retries |
| Offline or error, no tasks yet | A card "Can't reach Todoist, and there are no saved tasks to show." with Retry (after the events, if there are any) |
| Connected, nothing due | The "To do today" card with "Nothing left to do today." and the add field (in front when there are no events) |
| No calendar access | The tasks, and a "Allow access to show this" card behind them |
| Nothing at all (no events, the tasks source off) | One calm card: "Nothing planned today. Enjoy the quiet." |

## The Today page

Stack view, front to back (`TodayPlanner`, pure and tested):

1. **The next event** (the first timed event that has not ended, else the first all-day one): its time range, title and
   place, a live countdown ("in 25 min", "now", "ends in 10 min", from `TodayCountdown`; it has its own clock that wakes
   when the words change, so the page does not recompose for it) and "Open in calendar". The calendar's other event
   actions (Hide in its menu) are unchanged.
2. **To do today**: today's and overdue tasks as a checklist with "3 left · 1 overdue" (the first three, "+N more" after
   them), a checkbox that completes one through the Tasks action (the source ticks it at once and puts it back if Todoist
   refuses; the Stream's usual message shows), the title opens it, and the add field and refresh button.
3. **Later events today**, one card each (timed ones in order, then all-day ones).
4. **Tomorrow**: how many events and tasks tomorrow has and the first event; a tap opens that event (else the first
   task). Tasks due tomorrow are only counted when the Todoist source returns them; it asks for today and overdue, so the
   count is of events in practice (see the limits).
5. The notices for what could not be read (calendar access, no task account), as cards after the events.

With no events the "To do today" card is in front. The List view (Show as List) is the old Agenda list, its days under
their headings, followed by the old Tasks list (add field, tasks, stale note) under "Tasks".

## Actions

- **Complete:** the task ticks at once; if Todoist refuses or cannot be reached it is put back and the Stream shows its
  usual "couldn't do that" message. A recurring task moves to its next date on Todoist's side.
- **Open:** the Todoist app (`todoist://task?id=`) if installed, otherwise `https://app.todoist.com/app/task/{id}`.
- **Add:** the quick-add field on the "To do today" card (or under "Tasks" in the List view) creates a task with that text,
  due today.

## Limits

The API paths and field names live in one file, `TodoistApiMapping.kt`. They follow the official Todoist API v1 and its
official Python client; the web address and app link of a task and the exact due-date formats are not confirmed against
the reference page. Tasks without a due date are not fetched, and neither are tasks due after today: the Tomorrow card's task
count waits for the source to ask for them (deferred).

## Device check

Save a real token, open the Stream: tasks appear. Tick one: it disappears and is done in Todoist. Add one: it appears.
Turn on airplane mode, tap refresh: the stale note shows. Remove the token: Connect Todoist shows.
