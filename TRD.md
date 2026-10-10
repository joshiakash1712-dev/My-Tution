# Technical Requirement Document (TRD) — My Tuition (FSI)

## Document Metadata
- **Project Name:** My Tuition (FSI)
- **Document Type:** Technical Requirement Document (TRD)
- **Status:** Approved / Active Specification
- **Target Release:** v1.5.0 (Mobile-First Android Application)
- **Author:** Lead Systems Architect & Senior Technical Team
- **Date:** July 2026

---

## 1. Architectural Overview & Design Pattern
**My Tuition (FSI)** is implemented using modern, native Android development practices. The codebase adheres to the **MVVM (Model-View-ViewModel)** architectural pattern, leveraging Clean Architecture principles to enforce strict separation of concerns.

### Architectural Layers
1. **Presentation Layer (UI):** Built entirely using **Jetpack Compose** and **Material Design 3 (M3)**. Composables read from state variables and trigger events back to the view model or repositories.
2. **Business Logic Layer:** Implemented via standard Kotlin components, state containers, and flow operators.
3. **Data Layer (Repository Pattern):** Enforced by `AppRepository` which serves as the single source of truth, routing data queries to either local data caches, offline stores, or backend network services.

```
       [ Jetpack Compose UI Screens ]
                     │ (Observes Flow State / Triggers Events)
                     ▼
             [ AppRepository ]
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
 [ FirestoreService ]   [ FirebaseAuthService ]
         │                       │
         ▼                       ▼
 (Cloud Firestore)       (Firebase Auth)
```

---

## 2. Core Technical Tech Stack
* **Language:** Kotlin (v1.9+)
* **UI Framework:** Jetpack Compose (M3)
* **Build System:** Gradle (Kotlin DSL - `.gradle.kts`)
* **Asynchronous Operations:** Kotlin Coroutines & Shared/StateFlows
* **Local Caching / State:** Memory-bound reactive singletons and shared preferences (with Room planned for fully persistent offline data)
* **Backend Services:**
  - **Firebase Authentication:** Managed via `FirebaseAuthService`.
  - **Cloud Firestore:** Managed via `FirestoreService` for real-time document sync.
  - **AI Integration:** Google Gemini API integrated via RESTful interfaces in `AiService` for natural language processing and test analysis.

---

## 3. Data Schema & Model Design
All entity definitions reside in `com.example.data.Models.kt`. The main structures include:

### 3.1. Student Model (`Student`)
```kotlin
data class Student(
  val id: String,
  val name: String,
  val photo: String,
  val mobile: String,
  val parentName: String,
  val parentContact: String,
  val email: String,
  val dob: String,
  val gender: String,
  val address: String,
  val school: String,
  val className: String,
  val batch: String,
  val stream: String,
  val admissionDate: String,
  val status: String, // "Active" | "Inactive"
  val attendancePercent: Int,
  val overallAvg: Int,
  val rank: Int,
  val strongestSubject: String,
  val weakestSubject: String,
  val recentScores: List<Pair<String, Int>>
)
```

### 3.2. Attendance Record Model (`AttendanceRecord`)
```kotlin
data class AttendanceRecord(
  val id: String,
  val date: String,
  val studentId: String,
  val studentName: String,
  val batchName: String,
  val status: String, // "Present" | "Absent" | "Late"
  val timestamp: Long = System.currentTimeMillis()
)
```

### 3.3. Test Record Model (`TestRecord`)
```kotlin
data class TestRecord(
  val id: String,
  val testName: String,
  val subject: String,
  val date: String,
  val batch: String,
  val totalMarks: Int,
  val studentMarks: Map<String, Int>, // Student ID -> Score
  val remarks: String,
  val aiAnalysisStrong: List<String>,
  val aiAnalysisWeak: List<String>,
  val aiSuggestion: String
)
```

---

## 4. Authentication & RBAC Configuration
Authentication utilizes `FirebaseAuthService` with integrated fallback mechanisms to allow offline development, UI testing, and presentation.

### 4.1. Authoritative Role & Tenant Resolution (`fetchUserRole` & `smartDetectRole`)
The system resolves the user's `UserRole` and `instituteId` from Firebase Auth Custom Claims, `/user_roles/{uid}` in Firestore, or the local cryptographic credential vault:
```kotlin
private fun smartDetectRole(email: String): UserRole {
  val clean = email.trim().lowercase()
  val status = AppRepository.getAccountStatus(clean)
  if (status.exists && status.role != null) {
    return status.role
  }
  return SecurityEngine.getVerifiedRole(clean) ?: UserRole.STUDENT
}
```

### 4.2. Multi-Institute Tenant Isolation & Privilege Protection
* Every institute-owned entity carries a verified `instituteId`.
* Elevated role assignments (`ADMIN` / `OWNER`) and cross-institute operations are restricted to trusted backend/Admin SDK workflows and never exposed via hardcoded client credentials.

---

## 5. Integration Services & API Interfaces

### 5.1. Gemini AI Service (`AiService`)
The application implements generative AI features using direct SDK requests to `gemini-1.5-flash` (or equivalent current models). Major functionalities include:
1. **Instant Doubt Clearing:** Structured text prompts that constrain the AI's response to be pedagogical, age-appropriate, and step-by-step.
2. **Student Test Analysis:** Automatic ingestion of student test matrices to return personalized corrective paths, strong areas, and weak concepts.
3. **Homework Auto-Evaluations:** Simulates grading and returns helpful contextual tips.

---

## 6. Mobile Performance & UI Layout Adaptations
Due to mobile-first constraints, standard Jetpack Compose designs have been modified as follows:
* **`ScrollableTabRow` Migration:** Prevents text compression and clipping on displays smaller than 360dp.
* **`horizontalScroll` State:** Enforced on role-switching elements to allow clean side-scrolling gestures instead of stack-wrapping or text truncation.
* **Navigation Bars Padding:** Standard screens include `navigationBarsPadding()` modifiers inside the Scaffold components to prevent overlays with device bottom navigators.

---

## 7. Quality Assurance & Test Strategy
Tests are written under the local JVM context using:
- **Robolectric:** To execute unit testing and behavior checks of the `AppRepository` and ViewModels without requiring live Android emulator environments.
- **Roborazzi:** For automated screenshot testing and pixel-for-pixel visual verification of core screens (e.g., login, admin list detail).

### Execution Commands:
- **Run Unit Tests:** `gradle :app:testDebugUnitTest`
- **Verify Screenshots:** `gradle :app:verifyRoborazziDebug`
- **Record Screenshots:** `gradle :app:recordRoborazziDebug`
