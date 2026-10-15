# NovaRehab – modul UČENJE, FAZA 1 (temelji brez zaslona)

Navodila za Claude Code. Pripravljeno 15. 10. 2026 na podlagi analize veje
`origin/dev/v1.2.804-image-backend` (commit `58088fa`) in specifikacije NovaRehab 1.2.804 (12. 10. 2026).
Upošteva: Lana ne bere zanesljivo besedila in številk, uporablja levo roko; spodnja navigacija in AAC mreža sta zaščiteni.

---

## 0. Pravila (obvezno, pred vsem drugim)

Velja `docs/CODEX_RULES.md` in dodatno:

1. **Potek dela:** preglej → razloži tveganje → predlagaj → POČAKAJ POTRDITEV → izvedi → validiraj → poročaj.
2. **Brez commita, pusha ali taga** brez izrecne potrditve uporabnika.
3. **Brez novih odvisnosti** (Gradle, JUnit, knjižnice). Brez sprememb `build.gradle`, manifesta, podpisovanja.
4. **Prepovedano spreminjati** (niti ene vrstice):
   - `app/src/main/java/com/rehab2/aac/**` (ves paket AAC, vključno z `AacFixedTopRow.kt`,
     `AacStarterContentV1.kt`, `AacContentBootstrap.kt`, `AacUsageStats.kt`, `AacMemoryEngine.kt`)
   - `app/src/main/java/com/rehab2/update/**`
   - `BackupSettingsActivity.kt`, `MainActivity.kt`, `SettingsActivity.kt`, `AacCommunicatorActivity.kt`,
     `AacPackSettingsActivity.kt`
   - `app/src/trial/**`, `.github/**`, `app/build.gradle`, `AndroidManifest.xml`
5. **Dovoljeno ustvariti samo:**
   - nove datoteke v `app/src/main/java/com/rehab2/learning/`
   - `NovaRehab/tools/tests/Learning001Tests.kt`
   - `docs/LEARNING_MODULE_FAZA1.md` (kopija tega dokumenta)
6. Novi paket **ne sme klicati** ničesar iz `com.rehab2.aac`, `com.rehab2.update`, `com.rehab2.patient`
   ali katerekoli aktivnosti. Faza 1 je popolnoma samostojna.
7. **Brez omrežja:** v paketu `learning` ni `URL`, `HttpURLConnection`, `OkHttp`, `Socket`.
8. **minSdk je 21:** ne uporabljaj `java.time` (LocalDate ipd.). Datumi so `epochDay: Long`.

Ker v fazi 1 nobena obstoječa koda ne kliče novega paketa, se vedenje aplikacije ne spremeni.

---

## 1. Veja

Izhodišče je `trial-1.2.804` (da se lahko pozneje zgradi kot trial in pošlje na testno tablico).

```
git checkout trial-1.2.804
git status --short          # mora biti prazno
git checkout -b feat/ucenje-faza1
```

Če `git status` ni prazen: USTAVI SE in poročaj, ne nadaljuj.

---

## 2. Datoteke faze 1

Vse v `app/src/main/java/com/rehab2/learning/`, paket `com.rehab2.learning`.

| Datoteka | Vsebina | Sme uporabljati Android/org.json |
|---|---|---|
| `LearningModels.kt` | enumi in podatkovni razredi | NE |
| `LearningReviewScheduler.kt` | razpored ponovitev | NE |
| `LearningTaskGenerator.kt` | izbor motilcev in sestava seje | NE |
| `LearningJsonStore.kt` | varno branje/pisanje besedila v datoteko | samo `java.io` |
| `LearningJsonCodec.kt` | pretvorba modelov v JSON in nazaj | `org.json` |
| `LearningRepository.kt` | naloge, nastavitve, odgovori, ponovitve v mapi | `java.io` |
| `LearningPaths.kt` | pot `File(context.filesDir, "learning_v1")` | `android.content.Context` |

Pravilo ločevanja: `Models`, `Scheduler`, `Generator` in `Store` morajo biti čisti Kotlin (brez `android.*`
in brez `org.json`), da jih lahko testira samostojni JVM program.

---

## 3. Specifikacija

### 3.1 `LearningModels.kt`

```kotlin
package com.rehab2.learning

const val LEARNING_SCHEMA_VERSION = 1

enum class LearningTaskType { VISUAL_RECOGNITION, SPOKEN_INSTRUCTION, YES_NO_QUESTION, SYMBOL_LEARNING, SPEECH_ATTEMPT, RHYTHM }
enum class LearningOutcome { INDEPENDENT, WITH_HINT, ASSISTED, INCORRECT, NO_RESPONSE, SKIPPED }
enum class LearningHintLevel { REPEAT_INSTRUCTION, HIGHLIGHT_TARGET, SHOW_ANSWER }
enum class DistractorStrategy { SAME_GROUP, OTHER_GROUP }
enum class AssessedBy { SYSTEM, THERAPIST }

/** Ista imena kot obstoječi learningRepresentations.mode v AAC. */
object LearningPresentation {
    const val IMAGE_TEXT = "image_text"
    const val IMAGE_ONLY = "image_only"
    const val TEXT_ONLY = "text_only"
    val ALL = setOf(IMAGE_TEXT, IMAGE_ONLY, TEXT_ONLY)
}

data class LearningTask(
    val taskId: String,
    val taskType: LearningTaskType,
    val targetItemId: String,                 // AAC id; AAC vsebina se ne kopira
    val distractorItemIds: List<String> = emptyList(), // prazno = samodejno po strategiji
    val questionItemId: String? = null,       // samo YES_NO_QUESTION: pričakovano DA, če == targetItemId
    val distractorStrategy: DistractorStrategy = DistractorStrategy.OTHER_GROUP,
    val presentation: String = LearningPresentation.IMAGE_ONLY,
    val instructionTextByLanguage: Map<String, String> = emptyMap(),
    val speechAttemptWord: String? = null,
    val musicTrackId: String? = null,
    val enabled: Boolean = true
)

data class LearningSettings(
    val patientId: String,
    val choiceCount: Int = 2,                 // 2..6
    val hintDelaySec: Int = 8,                // 3..60
    val maxHints: Int = 2,                    // 0..3
    val hintLevels: List<LearningHintLevel> = listOf(
        LearningHintLevel.REPEAT_INSTRUCTION, LearningHintLevel.HIGHLIGHT_TARGET, LearningHintLevel.SHOW_ANSWER),
    val sessionMaxTasks: Int = 8,             // 1..20
    val allowedTaskTypes: Set<LearningTaskType> = setOf(LearningTaskType.VISUAL_RECOGNITION),
    val answerSide: String = "left",          // left | right | auto (auto = iz profila, sicer left)
    val allowTextOnly: Boolean = false,       // Lana ne bere zanesljivo; text_only le po izrecnem vklopu
    val feedbackSound: Boolean = true
) {
    /** Vrednosti izven meja se omejijo, nikoli ne vržejo izjeme. */
    fun normalized(): LearningSettings
}

data class LearningAttempt(
    val attemptId: String,
    val patientId: String,
    val sessionId: String,
    val taskId: String,
    val taskType: LearningTaskType,
    val targetItemId: String,
    val shownItemIds: List<String>,
    val selectedItemId: String?,
    val outcome: LearningOutcome,
    val hintsUsed: Int,
    val responseMs: Long,
    val languageCode: String,
    val assessedBy: AssessedBy,
    val speechRating: Int? = null,            // 0..3, samo SPEECH_ATTEMPT
    val timestamp: Long
)

data class ReviewEntry(
    val patientId: String,
    val itemId: String,
    val taskType: LearningTaskType,
    val box: Int,                             // 1..5
    val nextDueEpochDay: Long,
    val lastOutcome: LearningOutcome,
    val updatedAt: Long
)
```

Opomba: poročilo je omenjalo `nextDueDate`; zaradi minSdk 21 se hrani kot `nextDueEpochDay`.

### 3.2 `LearningReviewScheduler.kt`

```kotlin
object LearningReviewScheduler {
    val INTERVAL_DAYS = intArrayOf(1, 2, 4, 7, 14)   // škatla 1..5

    fun epochDay(timestampMs: Long, timeZone: java.util.TimeZone): Long
        // = Math.floorDiv(timestampMs + timeZone.getOffset(timestampMs), 86_400_000L)

    fun apply(existing: ReviewEntry?, patientId: String, itemId: String, taskType: LearningTaskType,
              outcome: LearningOutcome, todayEpochDay: Long, now: Long): ReviewEntry?

    fun due(entries: List<ReviewEntry>, todayEpochDay: Long): List<ReviewEntry>
}
```

Pravila `apply` (`existing == null` pomeni začetno škatlo 1):

| outcome | nova škatla | naslednji datum |
|---|---|---|
| INDEPENDENT | min(škatla + 1, 5) | danes + INTERVAL_DAYS[nova škatla − 1] |
| WITH_HINT, ASSISTED | nespremenjena | danes + 1 |
| INCORRECT, NO_RESPONSE | 1 | danes + 1 |
| SKIPPED | brez spremembe; vrne `existing` (lahko `null`) | brez spremembe |

`due`: vnosi z `nextDueEpochDay <= todayEpochDay`, razvrščeni po `nextDueEpochDay`, nato `box` naraščajoče,
nato `itemId`. Funkciji ne bereta ure sami; čas vedno prejmeta kot parameter.

### 3.3 `LearningTaskGenerator.kt`

Ne pozna `AacItem`. Most do AAC (`LearningItemSource`) pride šele v fazi 2.

```kotlin
data class LearningCandidate(
    val itemId: String,
    val meaningGroup: String?,
    val hasImage: Boolean,
    val learningModeEnabled: Boolean,
    val activeForPatient: Boolean,
    val presentations: Set<String>   // prazno = vsi načini dovoljeni
)

data class PlannedTask(val task: LearningTask, val shownItemIds: List<String>)

object LearningTaskGenerator {
    fun isUsable(c: LearningCandidate): Boolean
        // learningModeEnabled && activeForPatient && hasImage

    fun distractors(target: LearningCandidate, pool: List<LearningCandidate>,
                    strategy: DistractorStrategy, count: Int, random: kotlin.random.Random): List<String>

    fun planSession(settings: LearningSettings, tasks: List<LearningTask>, reviews: List<ReviewEntry>,
                    candidatesById: Map<String, LearningCandidate>, todayEpochDay: Long,
                    random: kotlin.random.Random): List<PlannedTask>
}
```

Pravila:

1. Cilj in motilci morajo biti `isUsable`. Neuporaben cilj → naloga se preskoči.
2. `distractors`: nikoli cilj, nikoli dvakrat isti id. `SAME_GROUP` najprej vzame iz iste
   `meaningGroup` (ne `null`), `OTHER_GROUP` najprej iz druge; manjkajoče dopolni iz preostalih.
   Če jih ni dovolj, vrne kolikor jih je.
3. Ročno podani `distractorItemIds` imajo prednost; neuporabni se izločijo, manjkajoči dopolnijo po strategiji.
4. `planSession`:
   - le naloge z `enabled == true` in `taskType` v `settings.allowedTaskTypes`;
   - najprej naloge z zapadlo ponovitvijo (vrstni red kot `LearningReviewScheduler.due`),
     nato nove (brez vnosa v ponovitvah) v vrstnem redu seznama `tasks`;
   - največ `settings.normalized().sessionMaxTasks` nalog;
   - `shownItemIds` = cilj + (`choiceCount − 1`) motilcev, premešano z `random`;
     naloga z manj kot 2 prikazanima elementoma se izpusti, razen `YES_NO_QUESTION`,
     ki prikaže samo cilj (`shownItemIds = [targetItemId]`) in zahteva uporaben `questionItemId`;
   - naloga s `presentation == text_only` se izpusti, če `allowTextOnly == false`;
   - če je v seji vsaj 2 nalogi, se na konec premakne naloga z najvišjo škatlo (že znan element),
     da se seja konča z uspehom;
   - z istim `random` seme (seed) mora biti rezultat vedno enak (ponovljivost testov).

### 3.4 `LearningJsonStore.kt`

Po vzorcu `aac/ai/AiJsonStore.kt` (vzorec prepiši, datoteke NE uvozi ali spreminjaj).

```kotlin
class LearningJsonStore(private val file: java.io.File) {
    companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
    fun readText(): String?          // null = datoteka ne obstaja
    fun writeText(text: String)      // .tmp → fsync → obstoječa v .bak → .tmp v končno → izbriši .bak
}
```

- Ob obstoječem `.bak` najprej obnovi iz njega.
- Preseženo velikost: vrže izjemo, NIČ ne briše.
- Brez samodejnega brisanja starih podatkov.

### 3.5 `LearningJsonCodec.kt`

Pretvorba vseh štirih modelov v `org.json` in nazaj. Vsaka datoteka ima korensko obliko
`{"schemaVersion":1,"rows":[...]}`. Neznana vrednost enuma ali manjkajoče obvezno polje → ta vrstica se
preskoči (ostale se naložijo), nikoli izjema navzven. `schemaVersion` višji od 1 → vrne prazno in
označi `unsupportedVersion = true`, datoteke ne prepiše.

### 3.6 `LearningRepository.kt` in `LearningPaths.kt`

```kotlin
object LearningPaths {
    const val DIR_NAME = "learning_v1"
    fun directory(context: android.content.Context): java.io.File = java.io.File(context.filesDir, DIR_NAME)
}

class LearningRepository(private val directory: java.io.File) {
    fun loadTasks(): List<LearningTask>
    fun saveTasks(tasks: List<LearningTask>)
    fun loadSettings(patientId: String): LearningSettings     // privzete, če datoteke ni
    fun saveSettings(settings: LearningSettings)
    fun loadAttempts(patientId: String): List<LearningAttempt>
    fun loadReviews(patientId: String): List<ReviewEntry>
    /** Doda odgovor in posodobi ponovitev z LearningReviewScheduler. */
    fun recordAttempt(attempt: LearningAttempt, todayEpochDay: Long)
}
```

Datoteke: `tasks.json`, `settings_<id>.json`, `attempts.json`, `review_<id>.json`.
`<id>` = `patientId` le z znaki `[A-Za-z0-9_-]`; drugi znaki se zamenjajo z `_`, prazen → `legacy`.
Notranji pomnilnik (`filesDir`), NE `getExternalFilesDir` in NE `NovaRehab/`.

---

## 4. Testi – `NovaRehab/tools/tests/Learning001Tests.kt`

Samostojen program z `fun main(args: Array<String>)` (enako kot `Patient804Tests.kt`), brez JUnit.
`args[0]` = začasna mapa. Ob napaki `check(...)` izjema in izhodna koda ≠ 0; ob uspehu izpiše
`Learning001Tests OK`. Testira samo čisti Kotlin (Models, Scheduler, Generator, Store).

Obvezni primeri (14):

1. Scheduler: iz `null` + INDEPENDENT → škatla 2, danes + 2.
2. Pet zaporednih INDEPENDENT → škatla 5, interval 14; šesti INDEPENDENT ostane na 5.
3. WITH_HINT in ASSISTED ohranita škatlo, naslednji dan.
4. INCORRECT in NO_RESPONSE iz škatle 4 → škatla 1, naslednji dan.
5. SKIPPED vrne nespremenjen vnos; iz `null` vrne `null`.
6. `epochDay`: 23:59 in 00:01 lokalnega časa v `Europe/Ljubljana` dasta zaporedna dneva;
   preveri tudi dan prehoda na zimski čas (25. 10. 2026).
7. `due`: pravilno filtrira in razvrsti.
8. Generator: cilj nikoli med motilci, brez podvojitev, `SAME_GROUP`/`OTHER_GROUP` spoštovana, dopolnjevanje.
9. Generator: neuporaben kandidat (`hasImage=false`, `learningModeEnabled=false`, `activeForPatient=false`)
   ni nikoli prikazan.
10. `planSession`: zapadle naprej, nato nove; omejitev `sessionMaxTasks`; zadnja naloga ima najvišjo škatlo;
    isti seed → enak rezultat.
11. `LearningSettings.normalized()` omeji `choiceCount=9` na 6 in `maxHints=-1` na 0.
12. Store: zapis in branje; ob obstoječem `.bak` obnovi; prevelika vsebina vrže izjemo in pusti staro datoteko.
13. `YES_NO_QUESTION`: prikaže en element; brez `questionItemId` ali z neuporabnim se izpusti.
14. `text_only` naloga se izpusti pri `allowTextOnly=false` in vključi pri `true`.

Kako zagnati: najprej ugotovi, kako se trenutno zaganja `Patient804Tests.kt` (prej je bil uporabljen
`build/tmp/kotlin-classes/...` + `kotlin-stdlib` jar). Python na tem računalniku NI nameščen.
Če `kotlinc` ni na voljo, predlagaj način zagona in POČAKAJ potrditev; ne nameščaj ničesar.

---

## 5. Validacija

1. `.\gradlew assembleDebug` → BUILD SUCCESSFUL.
2. `.\gradlew assembleTrial -PtrialBuild=3` → BUILD SUCCESSFUL (APK se NE objavi).
3. `Learning001Tests` → `Learning001Tests OK`.
4. `git status --short` in `git diff --stat trial-1.2.804` pokažeta SAMO nove datoteke iz točke 0.5.
5. Iskanje po `app/src/main/java/com/rehab2/learning/`:
   - ni `import com.rehab2.aac`, `com.rehab2.update`, `com.rehab2.patient`;
   - ni `URL`, `HttpURLConnection`, `OkHttp`, `Socket`;
   - ni `java.time`.
6. `grep -r "com.rehab2.learning"` izven paketa `learning` in testa → nič (nihče ga še ne kliče).

---

## 6. Poročilo, ki ga pričakujem

- veja in izhodiščni commit;
- seznam ustvarjenih datotek z velikostjo;
- izid vseh 6 korakov validacije (dobesedni izpisi);
- morebitna odstopanja od te specifikacije in zakaj;
- predlagan commit sporočilo (npr. `feat(learning): faza 1 – modeli, razpored, generator, shramba, testi`),
  a commit šele po potrditvi.

---

## 7. Kaj NI v fazi 1 (ne implementiraj)

`LearningItemSource`, `LearningActivity`, `LearningPrompter`, gumb v nastavitvah, vnos v manifest,
govor, slike, mikrofon, glasba, izvoz CSV. To so faze 2–5 in pridejo z ločenimi navodili.
