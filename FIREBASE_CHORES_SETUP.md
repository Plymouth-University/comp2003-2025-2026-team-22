# Firebase chores (Firestore) – pre-populated defaults

## What this version does
- Uses **Firebase Authentication** (already in the project) to get the current user UID.
- Stores chores in **Cloud Firestore** at:

`users/{uid}/chores/{choreId}`

- On first load of **Chores** screen, if the collection is empty, the app inserts a starter set of chores.
- The chores list is displayed using the existing RecyclerView + adapter and updates live via a snapshot listener.

## Required Firebase setup
1. Enable **Cloud Firestore** in Firebase console.
2. Ensure you have `google-services.json` in `app/` (your project already applies the Google Services plugin).

## Suggested Firestore rules (per-user)
```js
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/chores/{choreId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

## Dependency added
`app/build.gradle.kts` now includes:
- `implementation("com.google.firebase:firebase-firestore")`
