# Product Requirement Document (PRD) — My Tuition (FSI)

## Document Metadata
- **Project Name:** My Tuition (FSI)
- **Document Type:** Product Requirement Document (PRD)
- **Status:** Approved / Active Specification
- **Target Release:** v1.5.0 (Mobile-First Android Application)
- **Author:** Lead Systems Architect & Product Team
- **Date:** July 2026

---

## 1. Executive Summary & Vision
**My Tuition (FSI)** is a mobile-first, AI-powered tuition management platform designed to automate administrative tasks, eliminate manual communication overhead, and deliver custom, hyper-personalized AI tutors and helpers for all education stakeholders.

Unlike traditional, rigid educational CRMs, **My Tuition (FSI)** places Artificial Intelligence at the very core of its design. The platform implements a highly optimized, Role-Based Access Control (RBAC) workspace tailored for four distinct user roles: **Administrators**, **Teachers**, **Students**, and **Parents**. By doing so, it serves as the central operations terminal and educational copilot for tuition centers.

---

## 2. Primary Product Goals
1. **Zero-Friction Administration:** Automate tedious tasks like fee reminders, attendance logs, lead management, and scheduling.
2. **AI-First Pedagogical Assist:** Provide continuous student mentoring through the Gemini AI Companion, assist teachers with test analytics, and automate homework feedback.
3. **Transparent Parent-Teacher Loop:** Synchronize academic progress, attendance percentages, fees status, and meeting action items directly into the parent's app interface.
4. **Impeccable Mobile UX:** Maintain a highly responsive, high-contrast, edge-to-edge layout that behaves flawlessly on diverse mobile screens and handles dynamic size configurations.

---

## 3. Core Stakeholders & User Roles

### 3.1. Administrator (Jordan - Institute Director)
* **Goal:** Oversee institute growth, monitor financial health, streamline student/teacher onboarding, and review operational logs.
* **Core Responsibilities:**
  - Student, batch, and faculty lifecycle management.
  - Lead-funnel monitoring (CRM CRM Analytics).
  - Financial auditing (fee status, overdue tracking, automatic reminders).
  - Question bank creation and dynamic timetable adjustments.

### 3.2. Teacher (Sharma Sir - Senior PCM Faculty)
* **Goal:** Focus on teaching and mentoring students rather than bookkeeping and spreadsheet management.
* **Core Responsibilities:**
  - Fast, click-to-mark attendance with instant, historical logs.
  - Recording test marks and analyzing student standings.
  - Addressing student doubts through a centralized Q&A board.
  - Viewing weekly class schedules and real-time announcements.

### 3.3. Student (Rahul Kumar - Class 12 JEE Candidate)
* **Goal:** Master core syllabus objectives, measure performance, resolve doubts instantly, and follow a structured study timeline.
* **Core Responsibilities:**
  - Tracking learning goals, weekly study logs, and earning reward badges.
  - Utilizing the **Gemini AI Study Companion** for physics, chemistry, and mathematics questions.
  - Simulating computer-based test (CBT) environments for JEE/NEET preparation.
  - Viewing class timetables and pending homework.

### 3.4. Parent (Mr. Rajesh Kumar - Guardian)
* **Goal:** Stay informed of academic performance, attendance, fee statuses, and meeting remarks without manual follow-up calls.
* **Core Responsibilities:**
  - Reviewing the child's daily/weekly attendance records.
  - Auditing due fees, transaction history, and direct payment flows.
  - Accessing meeting schedules, action points, and progress graphs.

---

## 4. Comprehensive Feature Scope (Current & Planned)

### 4.1. RBAC Authentication & Multi-Institute Onboarding
* **Universal Login Engine:** Single, intelligent entry screen that resolves the user role and institute membership from verified account records and cryptographic session tokens.
* **Zero Hardcoded Credentials:** All accounts require explicit activation and BCrypt-salted authentication or Firebase Authentication.
* **Tenant Isolation:** Each institute operates in an isolated tenant scope (`instituteId`) enforced by Firestore Security Rules and repository guards.

### 4.2. Admin Operations Workspace
* **BI Dashboard:** Unified summary cards of total active students, average performance metrics, outstanding revenue, and pending CRM leads.
* **Interactive CRM Lead Tracker:** Move inquiries dynamically across stages (*New*, *Contacted*, *Follow-Up*, *Demo*, *Admitted*).
* **Smart Question Bank:** Subject-wise filter, difficulty classifier (Easy/Medium/Hard), type organizer (MCQ/Subjective), and detailed solutions.
* **Financial Ledger & Overdue Reminders:** Monitor paid/unpaid balances. Increment reminder counters and track last contacted timestamps with visual status markers.

### 4.3. Teacher Operations Suite
* **Attendance Ledger:** Fast toggling for Present/Absent/Late records. History tab shows aggregated stats of previous sessions.
* **Test marks & AI Insights:** Record marks out of standard totals. Features AI-generated analyses showing collective Class Strengths, Weaknesses, and remediation suggestions.
* **Doubt Workspace:** List pending or resolved doubt cards from students, complete with full text and formatted responses.

### 4.4. Student Classroom & Companion
* **Gamified Goals Panel:** Set target percentages, tracks actual attendance levels, records weekly study hours, and displays earned academic badges.
* **Computer-Based Testing (CBT):** Timed mock exams with question navigation, review flag, instant feedback, and scoring keys.
* **Gemini AI Chat Copilot:** Seamless, contextual chat interface powered by Gemini API. Includes pre-set rapid prompts (e.g. "Explain Quantum Mechanics", "Doubt in Integration").

### 4.5. Parent Companion Interface
* **Academic Standing Card:** Real-time visibility of total test average, class ranks, and attendance graphs.
* **Fee Receipts Ledger:** List current active dues with instant status flags (Paid/Pending/Overdue).
* **PTM Tracker:** Schedule, date, time, and documented action items of Parent-Teacher Meetings.

---

## 5. Mobile Accessibility & UX/UI Principles
To overcome screen constraints and text clipping common in complex enterprise applications, the following UX principles are enforced:
1. **Scrollable Tab Rows:** All high-density navigators use `ScrollableTabRow` instead of rigid `TabRow` to guarantee text remains fully readable and doesn't squish on compact devices.
2. **Horizontal Pill-Bars:** Interactive switcher panels (e.g., role switchers) use bounded horizontally-scrolling lists with generous padding.
3. **Adaptive Component Sizing:** Elements use dynamic vertical padding rather than hardcoded pixel/DP heights, preventing layout spillovers.
4. **Touch Target Standards:** Maintain interactive elements at a minimum of `48.dp` x `48.dp` with Material Ripples for tactile responsive feedback.
5. **System Inset Handling:** Use of `navigationBarsPadding()` on structural panels to prevent screen content from leaking underneath the Android navigation pill.

---

## 6. Success Metrics & Performance KPIs
* **User Engagement:** Daily Active Users (DAU) across teachers and students.
* **Administrative Automation:** Reduced manual hours on scheduling, fee reminders, and attendance reconciliation (Target: >80% reduction).
* **Communication Latency:** Time to notify parents of test results and absences (Target: <30 seconds via event-driven triggers).
* **Platform Performance:** App launching time <1.2s, list scrolling at steady 60fps, and network API latency below 500ms.
