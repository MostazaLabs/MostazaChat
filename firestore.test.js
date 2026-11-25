const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validChatPayload(userId, now = new Date(Date.now() - 5000)) {
  return {
    userId,
    contactName: "Laura Oficina",
    groupChat: false,
    statusText: "en línea",
    online: true,
    verified: false,
    avatarEmoji: "👩🏻‍💻",
    avatarColorHex: "#00796B",
    wallpaperTheme: "CLASSIC",
    unreadCount: 0,
    pinned: false,
    lastMessagePreview: "¿Nos vemos a las 5?",
    lastMessageTime: "14:20",
    lastMessageSender: "OTHER",
    lastMessageTick: "READ",
    createdAt: now,
    updatedAt: now,
  };
}

function validMessagePayload(userId, chatId, now = new Date(Date.now() - 3000)) {
  return {
    userId,
    chatId,
    senderRole: "ME",
    senderName: "Yo",
    text: "¡Claro, allí nos vemos!",
    messageType: "TEXT",
    displayTime: "14:21",
    dateLabel: "HOY",
    tickStatus: "READ",
    orderIndex: 1,
    createdAt: now,
    updatedAt: now,
  };
}

function validCallPayload(userId, now = new Date(Date.now() - 3000)) {
  return {
    userId,
    contactName: "Mamá",
    avatarEmoji: "❤️",
    avatarColorHex: "#E11D48",
    callType: "VOICE",
    direction: "INCOMING",
    durationText: "02:14",
    displayTime: "Hoy, 13:10",
    createdAt: now,
    updatedAt: now,
  };
}

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

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("chats").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_1")
      .set(validChatPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("fakeCalls").get()
  );
});

test("User profile: owner create, partial update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      defaultMyName: "Yo",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      defaultMyName: "Alice M.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      defaultMyName: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field and immutable createdAt rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("FakeChat: owner CRUD, list query, partial update, and security boundaries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const chatRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("chats")
    .doc("chat_laura");

  await assertSucceeds(chatRef.set(validChatPayload(ALICE_UID)));

  // List query matching repository: users/{uid}/chats
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").get()
  );

  // Partial update mirroring updateChatMeta
  await assertSucceeds(
    chatRef.update({
      pinned: true,
      statusText: "escribiendo...",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId `id` field in payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_with_id")
      .set({
        id: "chat_with_id",
        ...validChatPayload(ALICE_UID),
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_future")
      .set(validChatPayload(ALICE_UID, new Date(Date.now() + 60000)))
  );

  // Reject oversized contactName (> 60 chars)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_long")
      .set({
        ...validChatPayload(ALICE_UID),
        contactName: "A".repeat(65),
      })
  );

  // Partial update with groupMembersJson
  await assertSucceeds(
    chatRef.update({
      groupChat: true,
      groupMembersJson: JSON.stringify([
        { name: "Tú", statusText: "en línea", isAdmin: true, isMe: true },
        { chatId: "chat_sarah", name: "Sarah Aalvik", statusText: "en línea", isAdmin: false, isMe: false }
      ]),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject oversized groupMembersJson (> 32000 chars)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_oversized_members")
      .set({
        ...validChatPayload(ALICE_UID),
        groupChat: true,
        groupMembersJson: "A".repeat(32005),
      })
  );

  // Reject cross-user read, list, update, delete
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chats").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_laura")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_laura")
      .delete()
  );

  await assertSucceeds(chatRef.delete());
});

test("FakeMessage: owner create with optional fields omitted or set, partial update, and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const chatId = "chat_laura";
  const messagesCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("chats")
    .doc(chatId)
    .collection("messages");

  // Standard message (optional keys omitted)
  await assertSucceeds(
    messagesCol.doc("msg_1").set(validMessagePayload(ALICE_UID, chatId))
  );

  // Voice note with reply and reaction
  await assertSucceeds(
    messagesCol.doc("msg_2").set({
      ...validMessagePayload(ALICE_UID, chatId),
      senderRole: "OTHER",
      senderName: "Laura",
      messageType: "VOICE",
      text: "Nota de voz",
      voiceDuration: "0:18",
      reactionEmoji: "🔥",
      replyToText: "¡Claro, allí nos vemos!",
      replyToSender: "Yo",
      orderIndex: 2,
    })
  );

  // Image and Video messages with thumbnail preview and filename
  await assertSucceeds(
    messagesCol.doc("msg_img").set({
      ...validMessagePayload(ALICE_UID, chatId),
      messageType: "IMAGE",
      text: "Mira esta foto",
      mediaPreviewBase64: "AQIDBAU=",
      localMediaPath: "/data/user/0/app/files/chat_media/photo.jpg",
      mediaFileName: "foto_vacaciones.jpg",
      orderIndex: 3,
    })
  );
  await assertSucceeds(
    messagesCol.doc("msg_vid").set({
      ...validMessagePayload(ALICE_UID, chatId),
      senderRole: "OTHER",
      senderName: "Laura",
      messageType: "VIDEO",
      text: "Vídeo de ayer",
      mediaPreviewBase64: "AQIDBAU=",
      localMediaPath: "/data/user/0/app/files/chat_media/clip.mp4",
      mediaFileName: "clip_playa.mp4",
      mediaDuration: "0:14",
      orderIndex: 4,
    })
  );

  // Reject oversized mediaPreviewBase64 (> 120000 chars)
  await assertFails(
    messagesCol.doc("msg_oversized").set({
      ...validMessagePayload(ALICE_UID, chatId),
      messageType: "IMAGE",
      mediaPreviewBase64: "A".repeat(120005),
      orderIndex: 5,
    })
  );

  // Partial update (reaction & tickStatus)
  await assertSucceeds(
    messagesCol.doc("msg_1").update({
      reactionEmoji: "❤️",
      tickStatus: "READ",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List query matching repository: orderBy orderIndex
  await assertSucceeds(messagesCol.orderBy("orderIndex").get());

  // Reject mismatched chatId inside body
  await assertFails(
    messagesCol.doc("msg_bad_chat").set({
      ...validMessagePayload(ALICE_UID, "other_chat"),
    })
  );

  // Reject invalid senderRole enum
  await assertFails(
    messagesCol.doc("msg_bad_role").set({
      ...validMessagePayload(ALICE_UID, chatId),
      senderRole: "ADMIN",
    })
  );

  // Cross-user read/write denied
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc(chatId)
      .collection("messages")
      .get()
  );
});

test("FakeCallRecord: owner CRUD and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const callsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("fakeCalls");

  await assertSucceeds(callsCol.doc("call_1").set(validCallPayload(ALICE_UID)));
  await assertSucceeds(callsCol.get());
  await assertSucceeds(
    callsCol.doc("call_1").update({
      durationText: "05:30",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("fakeCalls").get()
  );
  await assertSucceeds(callsCol.doc("call_1").delete());
});
