# Agent Instructions: Daily Affirmation for Women

Read this whole file before you write any code. Follow it exactly.

## 0. Rules for you, the agent

1. This file and `affirmation_dataset.json` are the only sources of truth.
2. If a fact is not in these two files, do not invent it. Ask the developer, or leave a `TODO(developer)` comment.
3. Build only the features listed in section 4. Do not add extra features.
4. Do not guess library names, function names, or API calls. Check the installed package and its version first.
5. Do not say a test passed unless you ran it and saw it pass.
6. Do not add a dependency without writing one line in your summary on why it is needed.
7. Never write, edit, shorten, or "improve" any affirmation sentence, research note, or UI copy string.
8. Read all numbers (seconds, limits, hours) from `meta.session` and the category fields in the JSON. Do not hard code them.

## 1. What this app is

A mobile app named **Daily Affirmation for Women**.

- Home shows a "Today's practice" card and a list of categories.
- The user taps a category and sees affirmation sentences.
- The main value is a short guided practice: when to use it, how to read each sentence, and what to do after reading.
- The audience is women in Western Europe. It is not made for South Asia.
- Language for version 1: English.
- Every built-in sentence comes from `affirmation_dataset.json`.
- Some categories have a short research note. It says what a study found, in careful words.
- The user can add their own sentences in any category.
- The app adapts the strength of the sentences to what the user says feels true.

## 2. What this app is NOT

- It is not therapy, medical care, or a diagnosis tool.
- It is not a chatbot. No AI generates text inside the app.
- It is not a quote feed. Reading alone is not the product.
- It is not a social network. No sharing feed, no comments, no followers.
- It is not a mood tracker, journal, or habit tracker in version 1.
- It does not collect age, health information, or location.
- It does not promise results. It never says "proven" or "guaranteed".

## 3. The Practice (the core of the app)

Each rule below is marked **Research** (a study supports it, see the JSON notes) or **Design choice** (no study tests it). Never describe a Design choice to users as research.

### 3.1 The opening moment

- The app opens straight to Home. No splash longer than 1 second. No pop-ups. No sound. No ads.
- The top of Home shows the **Today's practice** card: the suggested category title, its `when_to_use` line, the time ("About 2 minutes", or "About 5 minutes" for `paper_timer`), and one **Start** button.
- One tap on Start begins the session. The user never has to pick anything first.
- If the user already finished a session today (device local date), the card shows the `done_today` text and no Start button. The category list stays usable.

**Which category to suggest.** Apply in order. Stop at the first rule that gives a result.

1. First launch ever: `kind_to_myself`.
2. If the local hour is from `night_start_hour` to `night_end_hour` (21:00 to 04:59) and no session is done today: suggest the category with `suggest_policy` = `night_only`.
3. Otherwise suggest the next category in file order after the last practiced category. Skip categories with `suggest_policy` = `night_only`. Skip `opt_in` categories unless the user has opened that category before. Skip `limited` categories if that category was practiced or suggested in the last `limited_category_min_days_between_suggestions` days.

Basis: night rule is **Research** (bedtime writing study). Rotation is a **Design choice**, supported loosely by a review that found varied positive activities worked better than repeating the same one. The limited rule is a **Design choice** based on one study where counting blessings once a week beat three times a week.

### 3.2 The session: four screens

The session uses built-in sentences. Read the three durations from the JSON. Target total: about 2 to 3 minutes (`standard_target_seconds`). **Design choice.** Research writing exercises often lasted 5 to 15 minutes, and a daily app practice is shorter. This exact length is not tested.

**Screen A. Read (about 20 seconds).**
- Show one sentence, at the category's current level, in large type.
- Show the `read_instruction` text above it. For `session_mode` = `say_aloud`, show `read_instruction_say_aloud` instead. (Research: people who said "I am excited" aloud before a task did better than those who said "I am calm".)
- Button: Next. Never force a wait.
- Do not offer a repeat counter or a loop. The user reads once, maybe twice. (Research: repeating a positive statement made people with low self-esteem feel worse.)

**Screen B. Check (about 5 seconds).**
- Show `check_question` and two buttons: `check_true` and `check_not_yet`.
- `check_true`: move the category up one level (stay at strong). Go to Screen C.
- `check_not_yet`: move the category down one level (stay at gentle). Show `not_yet_message`, then go to Screen C. (Research: people with low self-esteem felt better when they considered both a time the statement was true and a time it was not.)
- Count a "Not yet" in a row per category. After `no_fit_after_not_yet_in_a_row` in a row at gentle, show `category_no_fit` once. Reset the count on any "Feels true".

**Screen C. Do (about 60 to 90 seconds).**
- Show `do_header`, then the sentence's `prompt`.
- Show an optional answer box with `answer_box_hint`. The text is **never saved**. Clear it when the session ends. Never log it, never send it, never include it in crash reports.
- Show a link `think_instead` for users who do not want to write.
- Show `do_time_hint`. No countdown. No timer.
- Why this screen exists: the studies behind this app used short active exercises (writing about values, body function, good things, or tomorrow's tasks). Reading only is weaker, and a reading-only version has not been tested. **Research** for the action idea. **Design choice** for the exact box and timing.

**Screen D. Close (about 10 seconds).**
- Show `close_message`. Buttons: heart (save sentence), `done_button`, and `another_sentence`.
- Show `another_sentence` only if fewer than `max_sentences_per_session` sentences are done in this session. After that, only Done.
- Pick the next sentence in the same category: the next built-in sentence in file order that was not practiced in the last `avoid_repeating_sentence_days` days. If all were practiced, use the oldest one.
- No streak. No score. No "well done" badge. No confetti.

### 3.3 Special session modes

**`paper_timer` (category `bedtime_unload`).** Research: in a sleep lab study, writing a specific to-do list for five minutes helped young adults fall asleep faster, and the lead author advised paper over a phone.
- Run Screens A and B as normal.
- Screen C becomes: `sleep_instruction`, a button `sleep_start`, then a dim dark screen with the remaining time (`session_seconds`, 300).
- When the time ends: vibrate once, show `sleep_end`. No sound. No further sentences.
- Do not show an answer box. The writing is on paper.

**Deep values option (category `my_values` only).** Design choice based on studies where a single 5 to 15 minute writing exercise about one value was used.
- On the category screen, show a secondary button `deep_values_button` (`deep_option_seconds`, 600).
- It opens a plain screen with `deep_values_text`, the optional answer box (not saved), and no timer. At the end show `deep_values_end`.

**User's own sentences.** Tapping a "Mine" card opens Screen A then Screen D only. No Check, no Do, no level change.

### 3.4 How to read: the six rules shown to the user

Show these once after onboarding (the "How it works" cards) and keep them under Settings. Use the exact copy in section 5.

1. Read slowly, once or twice. Do not repeat it many times. (Research)
2. Notice if it feels true. "Not yet" is a fine answer. (Research)
3. If it does not feel true, think of a time it was true and a time it was not. (Research)
4. Do the small step under the sentence. It matters more than the sentence. (Research for active exercises, untested for this app)
5. Stop after one to three sentences. One session a day is enough. (Design choice)
6. If something makes you feel worse, stop. Try another category or take a break. (Design choice, in line with the researchers' own advice on fit)

### 3.5 Honesty rules for the Practice

- The app never says "2 minutes is enough to change your life" or any claim about results from the session length.
- Timing numbers are shown only as plain time ("About 2 minutes"), never as a research claim.
- Never push the user to do more. No "one more" nudges. No guilt.

## 4. Features for version 1

### F1. Onboarding (all steps skippable)
1. Optional first name. Used only for sentences that contain `{name}`.
2. One question: "When you read a kind sentence about yourself, what usually happens?" Options: "I believe it", "I want to believe it", "I doubt it". Set each category's starting level from `meta.level_rules.onboarding_start_level`. Skipped means gentle.
3. Three "How it works" cards (section 5), then Home.

Do not show the words gentle, medium, or strong to the user.

### F2. Home
- Top: the Today's practice card (section 3.1).
- Below: all categories from the JSON, in file order. Show each `title` and its `when_to_use` line.
- Show every category to every user. For a category with an `audience` field, show that text as a small subtitle.
- Do not show `evidence`, `mechanism`, `best_time`, `session_mode`, or `suggest_policy`.

### F3. Category screen
- Title, `when_to_use`, and a **Start practice** button. It starts a session with the next sentence (same pick rule as Screen D).
- A vertical list of sentence cards. Built-in sentences first, then the user's own, marked "Mine".
- Each card shows the sentence at the current level, the `prompt` in smaller text, and a heart. Tapping a built-in card starts a session on that sentence. The list has no Feels true or Not yet buttons.
- Research note: show **once per category**, in a collapsible block labelled "What research found", only if `research_note_id` is not null. Show `claim_ui` and `population`, and `source` as a "Source" line. Never show `do_not_say`, `extra_fact`, or `source_check`.
- `{name}` rule: if the user has a name, replace `{name}`. If not, do not show sentences that contain `{name}`. Show a card that asks for a name and links to Settings. Never rewrite a sentence.
- `my_values` also shows the deep values option (section 3.3).

### F4. Level adaptation
- Three levels: gentle, medium, strong. Stored per category, not per sentence.
- Changes happen only on Screen B (section 3.2).

### F5. User's own sentences
- Button "Add my own sentence" on each category screen.
- Hint text above the box, exactly: `Write something you can believe today.`
- Maximum 200 characters. Edit and delete are allowed.
- Stored on the device only. Never sent to a server. Never put in analytics.
- No research note and no levels apply.

### F6. Favorites
- The heart saves a sentence. A Favorites tab lists saved sentences, built-in and own.

### F7. Settings and About
- Edit name. Show the six reading rules again. Delete all my data (clears everything local, with a confirmation).
- About screen with this exact text: `This app is for everyday well-being. It is not medical care or therapy. If you are in distress, please contact a local health professional or emergency services.`
- Do not add phone numbers or website links. `TODO(developer)`: the developer will add them.

### Optional, build only if the developer asks
- Daily reminder notification. If added: off by default, time chosen by the user, neutral wording, never more than one a day.
- Anonymous analytics. If added, send only category id, sentence id, and the taps Feels true, Not yet, and heart. Never send user text or name.

## 5. Exact UI copy

Put every string below in the string resource file with this key and this text. Do not change the words.

| Key | Text |
|---|---|
| `today_card_title` | Today's practice |
| `time_standard` | About 2 minutes |
| `time_sleep` | About 5 minutes |
| `start_button` | Start |
| `done_today` | That is enough for today. Come back tomorrow, or open any category. |
| `read_instruction` | Read it slowly, once. Say it out loud if you can. |
| `read_instruction_say_aloud` | Say it out loud, once, just before you begin. |
| `check_question` | Does it feel true right now? |
| `check_true` | Feels true |
| `check_not_yet` | Not yet |
| `not_yet_message` | That is okay. Think of one time it was true for you, and one time it was not. |
| `do_header` | One small step |
| `do_time_hint` | Take about a minute. |
| `answer_box_hint` | Write a line (optional). It will not be saved. |
| `think_instead` | I will think about it |
| `sleep_instruction` | Put your phone down. Write on paper for five minutes. Be specific. |
| `sleep_start` | Start five minutes |
| `sleep_end` | Time is up. Good night. |
| `close_message` | That is enough for now. |
| `another_sentence` | Another sentence |
| `done_button` | Done |
| `category_no_fit` | This category may not fit you right now. You can try another one, or skip it for a while. |
| `deep_values_button` | Take 10 minutes (optional) |
| `deep_values_text` | Pick one value. Write about it for ten minutes: why it matters to you, and one time it guided you. |
| `deep_values_end` | You do not need to repeat this often. |
| `how_card_1` | Read it slowly, once or twice. Say it out loud if you can. |
| `how_card_2` | Ask yourself if it feels true. "Not yet" is a fine answer. |
| `how_card_3` | Do the small step under the sentence. That is the whole practice. About 2 minutes. |
| `rule_5` | Stop after one to three sentences. One session a day is enough. |
| `rule_6` | If something makes you feel worse, stop. Try another category or take a break. |

## 6. Avoid list

- Do not write new sentences, "sample data", placeholders, or lorem ipsum. Use the JSON.
- Do not paraphrase or extend research notes. Do not add study names, numbers, or claims from memory.
- Do not use the words: proven, guaranteed, cure, treat, heal, therapy, clinically.
- Do not add streaks, badges, points, progress bars, or messages like "You missed a day".
- Do not add a repeat counter or a loop that shows one sentence many times.
- Do not store the answer box text anywhere. Do not log it.
- Do not play sound in the sleep mode.
- Do not show more than `max_sentences_per_session` sentences in one session.
- Do not add accounts, login, a backend, or cloud sync in version 1.
- Do not call any AI API from the app.
- Do not request permissions that are not needed (location, contacts, camera, microphone).
- Do not use stock photos of women, faces, or body images.
- Do not use red for errors on sentence cards. Use a neutral tone.
- Do not show a sentence that contains `{name}` as raw text.
- Do not store user text outside the device.
- Do not change the JSON file unless the developer asks.

## 7. UI direction

- Calm, quiet, uncluttered. Wide spacing. One idea per screen in the session.
- Colors: soft neutral background, one calm accent color. Do not default to pink.
- Text: sentence in 20 to 24 sp, prompt in 14 to 16 sp, body at least 16 sp. Support the system's larger text setting.
- Contrast: text must meet WCAG AA (4.5 to 1 for body text).
- Support light and dark mode. The sleep timer screen is always dark and dim.
- Tap targets at least 48 by 48 dp.
- Motion: simple fades under 250 ms between session screens. Respect the system's reduced motion setting.
- Navigation: bottom tabs "Home", "Favorites", "Settings". The session opens full screen with a small close button.
- Screens: Onboarding, Home, Category, Session (A to D), Sleep timer, Deep values, Favorites, Settings, About.
- Empty state for Favorites: "No saved sentences yet."
- Put all visible text in a string resource file. Do not hard code text in screens.
- Add accessibility labels to the heart, the two Check buttons, and the close button.

## 8. Technical direction

- **Stack is not chosen in this file.** If the repository already has a stack, use it. If not, stop and ask the developer. Do not pick one.
- Offline first. The app must work with no internet.
- Bundle `affirmation_dataset.json` inside the app as a read-only asset.
- On launch, parse the JSON. Check that every category has `id`, `title`, and `affirmations`, and every affirmation has `levels.gentle`, `levels.medium`, `levels.strong`. If parsing fails, show a simple error screen. Do not crash.
- Use the device's local time zone for "night" and "today".
- Local storage model:

| Key | Content |
|---|---|
| `settings` | `{ name, onboardingAnswer, howItWorksSeen }` |
| `categoryLevel` | map of category id to `gentle`, `medium`, or `strong` |
| `notYetStreak` | map of category id to a number |
| `favorites` | list of sentence ids |
| `userSentences` | list of `{ id, categoryId, text, createdAt }` |
| `sessionLog` | last 60 entries of `{ date, categoryId, sentenceIds }`. No user text. |
| `openedCategories` | list of category ids the user has opened |

- User sentence ids must differ from dataset ids. Start them with `u_`.
- Keep the suggestion logic in one pure function with unit tests: input is the time, the session log, and the opened categories; output is a category id.

## 9. Dataset fields

| Field | Meaning | Show to user? |
|---|---|---|
| `meta.session.*` | Numbers for the Practice | No |
| `meta.session_modes`, `meta.suggest_policies` | Explanations for developers | No |
| `categories[].id` | Stable id | No |
| `categories[].title` | Category name | Yes |
| `categories[].when_to_use` | One line on when to use it | Yes |
| `categories[].session_mode` | `standard`, `say_aloud`, or `paper_timer` | No |
| `categories[].session_seconds` | Timer length for `paper_timer` | No |
| `categories[].deep_option_seconds` | Length of the deep values option | No |
| `categories[].suggest_policy` | `normal`, `night_only`, `opt_in`, or `limited` | No |
| `categories[].evidence` | Strength of research | No |
| `categories[].research_note_id` | Link to a note, or null | No |
| `categories[].audience` | Optional subtitle | Yes |
| `affirmations[].levels` | Three versions of the sentence | Yes, one at a time |
| `affirmations[].prompt` | Small action under the sentence | Yes |
| `affirmations[].mechanism`, `best_time` | Developer tags. Version 1 may ignore them. | No |
| `research_notes[].claim_ui` | Exact note text | Yes |
| `research_notes[].population` | Who was studied | Yes |
| `research_notes[].source` | Citation | Yes |
| `research_notes[].do_not_say`, `extra_fact`, `source_check` | Developer warnings | No |

## 10. Done checklist

Run each check. Report the real result.

1. The app opens straight to Home with a Today's practice card and 12 categories.
2. On first launch the card suggests "Kind to myself".
3. With the device time at 22:00 and no session done today, the card suggests "Before sleep".
4. After a session is finished, the card shows the `done_today` text.
5. A standard session shows Screens A, B, C, D in order, and "Another sentence" disappears after 3 sentences.
6. "Feels true" moves a category from gentle to medium. "Not yet" moves it back and shows the not-yet message.
7. Three "Not yet" taps in a row at gentle show the `category_no_fit` text once.
8. Text typed in the answer box is gone after the session ends and after an app restart.
9. The "Before sleep" session shows a dim five minute timer, vibrates once at the end, plays no sound, and offers no further sentence.
10. "Before something big" shows the say-it-out-loud instruction.
11. The "Talking to myself by name" category shows a name prompt when no name is set, and shows the sentences with the name when one is set.
12. Adding, editing, and deleting a user sentence works and survives an app restart.
13. Favorites survive an app restart.
14. "Delete all my data" clears everything.
15. The app works in airplane mode.
16. The suggestion function has unit tests for: first launch, night, rotation, an opt-in category, and a limited category.
17. No visible text contains the words in the avoid list.

## 11. Questions to ask the developer before you start

1. Which platform and stack: Android, iOS, or both? Which framework?
2. Analytics: yes or no?
3. Daily reminder: yes or no?
4. Show the research note once per category (default in this file), or under each sentence?
5. App icon, name styling, and brand colors?
6. Help line text for the About screen?
