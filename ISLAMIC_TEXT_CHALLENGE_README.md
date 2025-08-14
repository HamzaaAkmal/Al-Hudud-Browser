# Islamic Text Typing Challenge - Advanced App Locker Security

## Overview

This implementation provides an advanced Islamic text typing challenge that triggers when users attempt to disable App Locker or Device Admin Protection. The system requires precise typing of complex Islamic content within a strict time limit to ensure authorized access only.

## Features Implemented

### ✅ Core Functionality
- **Islamic Text Database**: 10+ complex Islamic narratives with educational value
- **Purple Word Highlighting**: Advanced vocabulary highlighted in purple color
- **Kalma Translation**: English translation included in every challenge
- **2-Minute Timer**: Strict countdown with visual feedback
- **Exact Verification**: Zero-tolerance character matching
- **Modern UI**: Professional interface matching App Locker theme

### ✅ Challenge Content
1. **Prophet's Migration (Hijra)** - Complex narrative of journey to Medina
2. **Battle of Badr** - Detailed account with challenging military terminology
3. **Companions' Sacrifice** - Stories of Sahaba with sophisticated vocabulary
4. **Islamic Jurisprudence** - Fiqh concepts with technical legal terms
5. **Quranic Commentary** - Complex Ayah translations with scholarly language
6. **Authentic Hadith** - Prophet's sayings with classical Arabic terms
7. **Caliphate History** - Historical accounts with academic vocabulary
8. **Islamic Ethics** - Moral teachings with philosophical terminology
9. **Spiritual Concepts** - Tasawwuf and Islamic mysticism terms
10. **Contemporary Issues** - Modern perspectives with scholarly language

### ✅ Security Integration
- **App Locker Disable Protection**: Challenge triggers on disable attempt
- **Device Admin Disable Protection**: Same challenge for device admin
- **Dual Protection**: Equal security for both features
- **Activity Result Handling**: Proper success/failure flow

## File Structure

```
app/src/main/java/org/mozilla/fenix/components/applocker/
├── IslamicTextChallenge.kt                 # Challenge data models and database
├── IslamicTextChallengeActivity.kt         # Main challenge activity
├── IslamicTextChallengeTestActivity.kt     # Testing interface
├── IslamicTextVerifier.kt                  # Text verification utilities
├── AppLockerDialogHandler.kt               # Updated with challenge integration
└── DeviceAdminDialogHandler.kt             # Updated with challenge integration

app/src/main/res/layout/
├── activity_islamic_text_challenge.xml     # Challenge UI layout
├── activity_islamic_text_challenge_test.xml # Test UI layout

app/src/main/res/drawable/
├── text_input_background.xml               # Input field styling
├── primary_button_background.xml           # Primary button styling
├── success_button_background.xml           # Success button styling
└── cancel_button_background.xml            # Cancel button styling
```

## Technical Implementation

### Challenge Database (`IslamicTextChallenge.kt`)
```kotlin
data class IslamicTextChallenge(
    val id: Int,
    val title: String,
    val text: String,
    val highlightedWords: List<String>
)
```

### Timer System
- **Duration**: Exactly 2 minutes (120,000 milliseconds)
- **Visual Feedback**: Green → Yellow → Red progression
- **Critical Alerts**: Special emphasis at 30s and 10s remaining
- **Auto-Failure**: Automatic challenge failure at 0:00

### Text Verification
- **Character-by-Character**: Perfect letter matching required
- **Case Sensitivity**: Exact uppercase/lowercase matching
- **Punctuation Perfect**: All commas, periods, quotes must match
- **Whitespace Sensitive**: Exact spacing required
- **Zero Tolerance**: Any single character difference = failure

### Purple Highlighting System
Complex words are automatically highlighted in purple:
- Religious terms: fortitude, devotion, perseverance, submission
- Historical terms: epoch, migration, tribulations, decree
- Academic terms: quintessential, exemplified, methodology
- Spiritual terms: contemplation, transcendence, spirituality

## Usage Instructions

### For App Locker Disable:
1. User clicks "Disable App Locker" in settings
2. Confirmation dialog explains the challenge requirement
3. User clicks "Continue" to start challenge
4. Islamic text challenge activity launches
5. User must complete typing within 2 minutes
6. Success = App Locker disabled | Failure = remains enabled

### For Device Admin Disable:
1. User attempts to disable Device Admin protection
2. Same challenge flow as App Locker
3. Success = Device Admin settings opened | Failure = remains protected

### Testing the Implementation:
1. Access App Locker settings
2. Choose "Test Islamic Text Challenge" option
3. Test both App Locker and Device Admin challenges
4. View available challenge database

## Challenge Examples

### Sample Challenge Text:
```
"In the luminous epoch of the Prophet's (PBUH) migration, the 
Muhajireen demonstrated extraordinary fortitude and unwavering 
devotion. The Kalma 'La ilaha illallahu Muhammadur rasulullah' 
means 'There is no deity except Allah, Muhammad is the messenger 
of Allah.' Their perseverance through tribulations exemplified 
the quintessential Islamic virtues of patience and submission 
to the Almighty's divine decree..."
```

**Purple Highlighted Words**: luminous, epoch, extraordinary, fortitude, unwavering, devotion, perseverance, tribulations, exemplified, quintessential, submission, Almighty's, divine, decree

## Security Benefits

1. **Educational Value**: Users learn Islamic content while verifying
2. **Time Pressure**: 2-minute limit prevents automated attacks
3. **Complex Vocabulary**: Difficult words deter casual attempts
4. **Exact Matching**: Prevents copy-paste or automated completion
5. **Rotating Content**: Different challenge each time
6. **Professional UI**: Maintains user experience quality

## Integration Points

### AppLockerDialogHandler Integration:
```kotlin
private fun showDisableDialog() {
    AlertDialog.Builder(context)
        .setTitle("Disable App Locker")
        .setMessage("To disable App Locker protection, you must complete an Islamic text typing challenge.")
        .setPositiveButton("Continue") { _, _ ->
            startIslamicTextChallenge()
        }
        .show()
}
```

### DeviceAdminDialogHandler Integration:
```kotlin
private fun showDisableConfirmationDialog() {
    AlertDialog.Builder(context)
        .setTitle("Device Admin Protection")
        .setMessage("To disable Device Admin protection, you must complete an Islamic text typing challenge.")
        .setPositiveButton("Continue") { _, _ ->
            startIslamicTextChallenge()
        }
        .show()
}
```

## Success Criteria

- ✅ Challenge triggers for both App Locker and Device Admin disable
- ✅ Islamic paragraphs display with purple highlighting
- ✅ Kalma translation included in all challenges
- ✅ 2-minute timer with visual feedback
- ✅ Exact character matching enforced
- ✅ Professional UI matching App Locker theme
- ✅ Educational Islamic content maintained
- ✅ "Sorry" messages on failure
- ✅ Smooth user experience with error handling

## Testing Protocol

1. **Trigger Testing**: Verify popup appears for both disable attempts
2. **Timer Testing**: Confirm 2-minute countdown with color changes
3. **Typing Interface**: Validate input field with disabled auto-correct
4. **Verification Testing**: Test exact match with various scenarios
5. **Failure Scenarios**: Confirm proper handling of time/text mismatches
6. **UI Validation**: Ensure responsive design and accessibility

This implementation provides sophisticated security through precise typing requirements while maintaining educational Islamic content and ensuring both App Locker and Device Admin Protection are equally secured.
