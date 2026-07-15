# Studdy Application Reconstruction Specification

> This document specifies the behavior present in the repository as audited on 4 July 2026. It is intentionally platform-agnostic. Where the repository contains client queries but no database migration or policy definitions, the schema is marked **inferred** and RLS is marked **not present in the repository** rather than inventing server behavior. “Implemented” means reachable code exists; “legacy/unreachable” and known defects are called out explicitly.

## 1. App Overview

Studdy is a two-sided, AI-assisted examination platform for teachers and students.

- Teachers authenticate with email/password, generate MCQ exams from pasted text or PDF content, review and edit questions, configure result visibility and timing, publish an exam as a six-character code/QR, monitor participation, inspect results and proctoring signals, and delete exams.
- Teachers can also generate a separate, printable multi-section exam paper from a PDF. This workflow supports MCQ, short-answer, long-answer, true/false, and fill-in-the-blank questions, answer keys, editing, cloud history, and PDF export. These printable papers are not student-taken online exams.
- Students do not have password-based accounts. They identify themselves with a name and enrollment number, see all exams, take MCQ exams, resume saved answers, receive instant or teacher-published results, and review correct answers.

The core value proposition is fast exam authoring from existing teaching material, simple code/QR distribution, automatic objective scoring, and lightweight client-side anti-cheat telemetry.

The application is primarily mobile-oriented. A web build can use manual exam-code entry, but camera/QR and several file-system operations are disabled or mobile-specific.

## 2. User Roles & Permissions

### 2.1 Teacher

Identity: a Supabase Auth user created with email, password, and user metadata `{name, role: "teacher"}`.

Implemented capabilities:

- Sign up, sign in, request a password-reset email, sign out, and restore an existing Supabase session.
- Create an online MCQ exam and publish it with a random six-character code.
- Configure instant/manual results and no timer/per-attempt duration/fixed availability window.
- View only exams whose `teacher_id` matches the current Auth user in the normal teacher exam query.
- View results, search students, inspect each response, publish manual results, monitor sessions in real time, show/share/download a QR, and delete an exam plus its results.
- Generate, edit, export, list, open, and delete printable papers.

Limitations/security boundary:

- Authorization enforcement depends on database RLS, but no RLS definitions are committed. Client-side filtering by `teacher_id` is not a security boundary.
- Generated papers are neither written with a teacher ID nor filtered by teacher. All rows visible under database policies appear to every teacher client.
- The dashboard reads all generated papers and the paper save/update operation matches duplicate titles globally.
- The app puts `role: teacher` in user-editable signup metadata; it does not independently validate that role.

### 2.2 Student

Identity: an enrollment-number row in `students` plus a locally persisted enrollment string. There is no Supabase Auth session, password, OTP, signed student token, or server-issued student session.

Implemented capabilities:

- Enter or scan an exam code; provide name and an enrollment number of at least five characters.
- Create a student row automatically or update the name on an existing row.
- View all exams returned by `exams`, regardless of teacher.
- Attempt an exam if the client finds no prior result for the same code and the local clock considers its window valid.
- Select answers, move backward/forward, resume progressively stored answers, submit, and view published results.
- View the complete correct answer set after results become visible.

Limitations/security boundary:

- Enrollment ownership is not authenticated. Anyone who knows an enrollment number can assume it and overwrite its stored name.
- Duplicate-attempt prevention is a client query against `results`; there is no repository evidence of a unique database constraint.
- Timing checks use the device clock and occur in the client.
- Students receive complete question objects, including `correctIndex`, before submission. Security relies entirely on the client UI not revealing it.

### 2.3 Anonymous/unauthenticated client

Because students use the Supabase anonymous client without Auth, the deployed database must allow some anonymous reads/writes for `students`, `exams`, `results`, and `exam_sessions` for the current application to work. Exact grants and RLS policies are absent. There is no administrator role or teacher approval workflow in this repository.

## 3. Complete Screen Inventory

### 3.1 Role Selection / Home

Purpose: initial screen when no saved teacher or student session exists.

UI:

- Book icon, title **“Studdy”**, subtitle **“Smart AI Exam Platform”**.
- Role card **“Teacher”** / **“Create & manage exams”**.
- Role card **“Student”** / **“Join & take exams”**.

Navigation:

- Teacher card → Teacher Portal.
- Student card → Student Entry.
- Startup bypasses this screen when a teacher Auth session exists; teacher is checked first. Otherwise a locally saved student enrollment opens Student Dashboard.

### 3.2 Teacher Portal (Sign In / Sign Up)

Purpose: teacher authentication.

UI:

- Back button; heading **“Teacher Portal”**; subtitle **“Sign in to manage your exams”**.
- Mode tabs **“Sign In”** and **“Sign Up”**.
- Sign-up-only field **“Full Name”**, hint **“e.g. Dr. Arshad Ali”**.
- **“Email Address”**, hint **“teacher@school.com”**.
- **“Password”**, hint **“Min 6 characters”**, with visibility toggle.
- Sign-in-only link **“Forgot Password?”**.
- Primary button **“Sign In”** or **“Create Account”**.
- Footer **“Protected for teachers only”**.
- Reset dialog: title **“Reset Password”**, explanatory text, **“Email”** field, **“Cancel”**, **“Send Reset Link”**.

Validation and behavior:

- Email required and must contain `@` (no stricter format check).
- Password required and length at least 6.
- Name required only for sign-up.
- Successful signup switches back to sign-in, clears password, and says **“Account created successfully! Please sign in.”** It does not enter the dashboard even if the Auth provider returns a session.
- Successful sign-in clears all navigation history and opens Teacher Dashboard.
- Known Auth errors are normalized to invalid credentials, already registered, or minimum password messages; all other failures become **“Something went wrong. Please try again.”**
- Reset requires only non-empty email. Success says **“Password reset email sent!”**.

### 3.3 Student Entry (canonical join flow)

Purpose: two-step verification of exam code, then student identity.

Step 1 UI:

- App bar **“Join Exam”** with no back button.
- **“Enter Exam Code”**, **“Get the code from your teacher”**.
- Field label **“Exam Code”**, hint **“ABC123”**, maximum six characters, automatically uppercased.
- Button **“Verify Code”**.
- On non-web builds: separator **“OR”** and **“Scan QR Code”**.

Step 1 validation/behavior:

- Code must be exactly six characters; message says **“Please enter a valid 6-digit code”** although alphabetic characters are allowed.
- Fetches the full exam row by code.
- For a fixed window, blocks before start with **“Not Started Yet”** and local start time, or after end with **“Exam Expired”**. Duration and untimed exams pass.
- Invalid/fetch failure says **“Invalid exam code. Please check and try again.”**.
- A prefilled code (normally from QR) is verified automatically after first render.

Step 2 UI:

- App bar **“Enter Enrollment”**, back arrow returns to step 1.
- Confirmed exam card showing title and **“Code: …”**.
- **“Who are you?”**, **“Enter your details to identify yourself”**.
- **“Your Name”**, hint **“e.g. Arshad Ali”**.
- **“Enrollment Number”**, numeric keyboard, hint **“e.g. 2405112070013”**.
- Button **“Continue”**.

Step 2 validation/behavior:

- Name must be non-empty.
- Enrollment must be at least five trimmed characters; no numeric-only validation despite numeric keyboard.
- Looks up student; any lookup error is treated as “not found” and triggers insert.
- Always calls a name update after lookup/creation, so an existing name is overwritten.
- If the student already has a result for this exam, saves the enrollment locally and opens Dashboard without a pending exam.
- Otherwise saves enrollment locally and opens Dashboard. The verified exam is not auto-started: `pendingExamCode` and timer data are explicitly passed as null, so the student must select the exam again.

### 3.4 QR Scanner

Purpose: obtain an exam code using live camera or an image.

UI:
