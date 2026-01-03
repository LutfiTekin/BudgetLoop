# Color Set

Derived from the abstract loop app icon inspired by €500 banknote tones.

This palette is designed to feel **premium, controlled, and quietly powerful**, with restrained contrast and no aggressive highlights. It works well for financial or value-flow concepts and remains readable at small sizes.

---

## Core Backgrounds

```kotlin
val DeepViolet = Color(0xFF2E2645)   // primary background, launcher-safe
val DarkViolet = Color(0xFF3A2F5E)   // secondary background, cards
```

Use for:

* App background
* Navigation bars
* Primary surfaces that must feel dense and stable

---

## Primary Loop Colors

These colors come from the loop segments. Treat them as **peers**, not a hierarchy.

```kotlin
val MutedPurple = Color(0xFF8F5FA6)
val SoftViolet = Color(0xFFB894C6)
val GrayLavender = Color(0xFF9E8FB3)
```

Use for:

* Primary accents
* Progress and flow indicators
* Charts and financial movement
* Selected states without brightness

---

## Embedded Value Highlight

This pale tone represents value, trust, and clarity. It should remain restrained.

```kotlin
val PaleBeige = Color(0xFFF1E6D8)
```

Use sparingly for:

* Key numbers or balances
* Focus indicators
* Important separators

---

## Neutral Support Colors

Optional but recommended for typography and system affordances.

```kotlin
val CoolLavenderGray = Color(0xFFC6BED6)
val DisabledLavender = Color(0xFF6E638A)
```

Use for:

* Secondary text
* Dividers
* Disabled states

---

## Suggested Material ColorScheme Mapping

Optimized for a dark, premium UI with calm contrast.

```kotlin
val AppColorScheme = darkColorScheme(
    primary = MutedPurple,
    secondary = SoftViolet,
    tertiary = GrayLavender,

    background = DeepViolet,
    surface = DarkViolet,

    onPrimary = PaleBeige,
    onSecondary = PaleBeige,
    onTertiary = PaleBeige,

    onBackground = CoolLavenderGray,
    onSurface = CoolLavenderGray,

    outline = DisabledLavender
)
```

---

## Usage Notes

* Keep **DeepViolet** as the dominant background tone
* Avoid using **PaleBeige** as a large surface color
* Prefer **MutedPurple** for primary actions
* Use **GrayLavender** for subtle motion and rhythm
* Maintain low saturation to preserve the financial tone

---

## Optional Extensions

This palette can be extended with:

* OLED-optimized near-black variant
* Material You dynamic seed colors
* Monochrome accessibility version
* Guilloche or security-pattern inspired accents

These can be added without breaking the core identity.
