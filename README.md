# Thinking of You 
## An open source ToY - some assembly required

A Glyph Matrix toy for the Nothing Phone 3. Two devices. One small pattern. No words.

---

## What It Is

*Thinking of You* is a Glyph Matrix Toy that syncs a tiny ambient state between two devices over Firebase Realtime Database. When one person updates their pattern, the other person's matrix updates too. That's it. There's no message. There's no notification. You just know.

It was originally designed as part of a broader collection of useless-but-sincere Glyph Matrix toys — the kind of thing that doesn't solve a problem so much as quietly exist.

The concept: two people share a connection through a 25×25 LED grid on the back of their phones. No text. No emoji. Just light.

---

## How It Works

Two devices share a pairing code. Each device connects to a Firebase Realtime Database document using that code as a key. When one person updates their pattern, the other device receives it in realtime and renders it on the Glyph Matrix.

There's no server. There's no account system. There's no notification. There's just the matrix changing state.

The Firebase side is one document per pair:

```
/pairs/{code}/pattern → current pattern state
```

Both devices write to and listen on the same path. Whoever updates last wins. That's the whole protocol.

---

## Device Compatibility

| Device | Support |
|---|---|
| Nothing Phone 3 | ✅ Native Glyph Matrix |
| Nothing Phone 1 / Phone 2/3/4 family | 🔧 Possible with work |
| Other / unsupported devices | ⚠️ Overlay fallback |

On unsupported devices, the sync still works — the pattern is rendered as an on-screen overlay instead. It's not the same thing, but it's not nothing.

The Phone 1, Phone 2 family, Phone 3 Family and 4a use a different Glyph system (strips, not a matrix). Getting it working there would require mapping the pattern state to that API yourself.

---

## Set It Up Yourself

This app doesn't have a hosted backend. You'll need your own Firebase project.

### 1. Create a Firebase Project

Go to [console.firebase.google.com](https://console.firebase.google.com), create a new project, and enable **Realtime Database**. The free Spark plan is enough.

### 2. Configure Database Rules

```json
{
  "rules": {
    "pairs": {
      "$pairCode": {
        ".read": "auth != null",
        ".write": "auth != null && (
          !data.exists() ||
          data.child('devices/deviceA/uid').val() === auth.uid ||
          data.child('devices/deviceB/uid').val() === auth.uid ||
          (!data.child('devices/deviceB').exists() && newData.child('devices/deviceB/uid').val() === auth.uid)
        )"
      }
    }
  }
}
```

Reads require authentication. Writes are restricted to the two devices in the pair — the second device can join an unclaimed slot, but once both slots are filled neither can be replaced.

**A note on encryption:** data is encrypted in transit (TLS) and at rest by Firebase, but the pattern state itself is stored as plaintext. Anyone with Firebase console access or a valid auth token can read it. For a toy that sends a heart pattern this is probably fine — just don't put anything sensitive in the state.

### 3. Add Your Firebase Config

The app uses Firebase REST API only — no SDK, no `google-services.json`. You'll need to update the database URL in the source to point at your own project.

Your database URL will look like:
```
https://YOUR-PROJECT-ID-default-rtdb.YOUR-REGION.firebasedatabase.app/
```

### 4. Build and Sideload

This is an Android app targeting the Nothing Phone 3 Glyph Matrix. It uses the [GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit). Build it with Android Studio and sideload the APK, or submit it to the Nothing Community Marketplace yourself.

---

## Why I'm Releasing This

A few people in the Nothing community asked for something like this — a simple way to send a silent ping to someone. This app does that. It works.

I build things mostly for myself, so I don't have the bandwidth to turn this into a maintained, supported product. No Play Store listing, no hosted backend, no ongoing development. Releasing the source feels like the right middle ground — if the community wants this, the community can have it.

You'll need to bring your own Firebase project. Everything else is here.

---

## What This Is Not

- Maintained
- Supported
- On the Play Store
- Going to get updates
- Going to get bug fixes
- Going to have a hosted Firebase backend you can just use

---
