const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied all access", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
});

test("Owner can create, update, and delete UserProfile; cross-user and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Daud",
      email: "daud@example.com",
      preferredLanguage: "en",
      selectedPersonality: "FRIENDLY",
      memoryEnabled: true,
      voiceSpeechRate: 1.0,
      voiceAutoSpeak: true,
      themeMode: "DARK_NAVY",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Update mutable preferences while keeping createdAt unchanged
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "bn",
      selectedPersonality: "TEACHER",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Tampering createdAt must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Cross-user read & write must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Owner can create, list, rename, message, and delete conversations; Bob is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 3000);
  const convId = "conv_1";

  // Empty list query succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );

  // Create conversation (NO @DocumentId `id` field)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc(convId)
      .set({
        userId: ALICE_UID,
        title: "Study Physics",
        personality: "TEACHER",
        language: "en",
        lastMessagePreview: "Explain quantum tunneling",
        messageCount: 1,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Including @DocumentId `id` fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_bad")
      .set({
        id: "conv_bad",
        userId: ALICE_UID,
        title: "Bad",
        personality: "FRIENDLY",
        language: "en",
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Rename conversation succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc(convId)
      .update({
        title: "Quantum Physics Notes",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Create message inside conversation
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        conversationId: convId,
        role: "user",
        content: "Explain quantum tunneling simply.",
        personality: "TEACHER",
        hasImage: false,
        imageCount: 0,
        createdAt: now,
      })
  );

  // Bob cannot list, read, or write Alice's conversations or messages
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc(convId)
      .delete()
  );

  // Owner can delete message and conversation
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .doc("msg_1")
      .delete()
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc(convId)
      .delete()
  );
});

test("Owner can CRUD memories with strict validation; future timestamp and cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .set({
        userId: ALICE_UID,
        title: "Preferred Language",
        content: "Likes concise bilingual Bengali and English explanations.",
        category: "PREFERENCE",
        sensitive: false,
        approvedByUser: true,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Update memory
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .update({
        title: "Study Preference",
        content: "Prefers step-by-step code examples in Kotlin.",
        category: "PREFERENCE",
        sensitive: false,
        approvedByUser: true,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_future")
      .set({
        userId: ALICE_UID,
        title: "Future",
        content: "Invalid future time",
        category: "CONTEXT",
        sensitive: false,
        approvedByUser: true,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Bob denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .delete()
  );

  // Alice deletes memory
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .delete()
  );
});
