const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

// 1. Unauthenticated denial across all collections
test("Unauthenticated user: cannot read or write any user profile or subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      appLanguage: "en",
      elderlyMode: false,
      notificationThreshold: 60,
      totalScansSynced: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("scanSummaries").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("trustedContacts").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("incidents").get()
  );
});

// 2. UserProfile create, update, ghost-field, and cross-user isolation
test("UserProfile: owner create/update succeed; shadow fields, bad types, future timestamps, and cross-user access fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid create payload mirroring Kotlin CloudSyncRepository
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Sharma",
      email: "alice@example.com",
      appLanguage: "en",
      elderlyMode: false,
      notificationThreshold: 60,
      totalScansSynced: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      appLanguage: "hi",
      elderlyMode: true,
      notificationThreshold: 70,
      totalScansSynced: 3,
      updatedAt: new Date(Date.now() - 1000),
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

  // Immutable createdAt mutation must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost field & invalid @DocumentId "id" key must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      id: BOB_UID,
      userId: BOB_UID,
      displayName: "Bob",
      appLanguage: "en",
      elderlyMode: false,
      notificationThreshold: 60,
      totalScansSynced: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Future timestamp must fail
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      appLanguage: "en",
      elderlyMode: false,
      notificationThreshold: 60,
      totalScansSynced: 0,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

// 3. CloudScanSummary create, query, partial feedback update, and bounds
test("CloudScanSummary: Kotlin-aligned create, list query, feedback update succeed; cross-user & invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const scanRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("scanSummaries")
    .doc("scan_001");

  // Create without optional feedbackOption
  await assertSucceeds(
    scanRef.set({
      userId: ALICE_UID,
      source: "MANUAL",
      riskScore: 85,
      riskLevel: "HIGH",
      uncertain: false,
      categoryName: "Fake KYC Alert",
      shortExplanation: "Asks for OTP via shortened link under urgent threat.",
      createdAt: created,
      updatedAt: created,
    })
  );

  // List query matching Kotlin snapshots()
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("scanSummaries")
      .orderBy("createdAt", "desc")
      .get()
  );

  // Update feedback only
  await assertSucceeds(
    scanRef.update({
      feedbackOption: "CORRECT_SCAM",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject mutating score/category after create
  await assertFails(
    scanRef.update({
      riskScore: 10,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid @DocumentId "id" key inside body
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("scanSummaries")
      .doc("scan_002")
      .set({
        id: "scan_002",
        userId: ALICE_UID,
        source: "URL",
        riskScore: 50,
        riskLevel: "MEDIUM",
        uncertain: true,
        categoryName: "Lookalike Domain",
        shortExplanation: "Suspicious TLD",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject out-of-range riskScore
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("scanSummaries")
      .doc("scan_003")
      .set({
        userId: ALICE_UID,
        source: "URL",
        riskScore: 150,
        riskLevel: "CRITICAL",
        uncertain: false,
        categoryName: "Scam",
        shortExplanation: "Too high",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Cross-user list and read must fail
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("scanSummaries").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("scanSummaries")
      .doc("scan_001")
      .get()
  );
});

// 4. CloudTrustedContact PII isolation, CRUD, and validation
test("CloudTrustedContact: owner CRUD succeeds; cross-user PII read & bad phone lengths fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const contactRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("trustedContacts")
    .doc("contact_1");

  await assertSucceeds(
    contactRef.set({
      userId: ALICE_UID,
      displayName: "Rahul (Son)",
      relationship: "Son",
      phoneNumber: "+919876543210",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trustedContacts").get()
  );

  await assertSucceeds(
    contactRef.update({
      phoneNumber: "+919811122233",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read Alice's trusted contacts (PII)
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trustedContacts")
      .doc("contact_1")
      .get()
  );

  // Too-short phone rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trustedContacts")
      .doc("contact_2")
      .set({
        userId: ALICE_UID,
        displayName: "Bad",
        relationship: "Friend",
        phoneNumber: "12",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Owner delete succeeds
  await assertSucceeds(contactRef.delete());
});

// 5. CloudIncident lifecycle + terminal RESOLVED state locking
test("CloudIncident: create OPEN, update OPEN, transition to RESOLVED succeed; post-RESOLVED mutation fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const incRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("incidents")
    .doc("inc_1");

  // Cannot create directly as RESOLVED
  await assertFails(
    incRef.set({
      userId: ALICE_UID,
      title: "Skipped State",
      highestRiskScore: 90,
      status: "RESOLVED",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create OPEN without optional notes
  await assertSucceeds(
    incRef.set({
      userId: ALICE_UID,
      title: "Digital Arrest Call",
      highestRiskScore: 92,
      status: "OPEN",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Update while OPEN
  await assertSucceeds(
    incRef.update({
      highestRiskScore: 96,
      notes: "Reported on 1930 helpline.",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Transition OPEN -> RESOLVED
  await assertSucceeds(
    incRef.update({
      status: "RESOLVED",
      notes: "Case filed with cybercrime.gov.in",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Post-RESOLVED mutation MUST FAIL (terminal state lock)
  await assertFails(
    incRef.update({
      status: "OPEN",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    incRef.update({
      notes: "Trying to edit resolved incident",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user delete must fail
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("incidents").doc("inc_1").delete()
  );
});

// 6. Extended UserProfile personal info & CloudUserEntry real-time entries
test("UserProfile personal info & CloudUserEntry: owner CRUD & query succeed; cross-user & invalid types fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 10000);

  // Profile create with personal info + bookmarks
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Sharma",
      email: "alice@example.com",
      phoneNumber: "+919876543210",
      city: "Bengaluru",
      bio: "Looking out for parents against cyber scams.",
      emergencyNotes: "Primary bank: SBI",
      guardianRole: "Family Guardian",
      bookmarkedTips: ["l_da", "l_kyc"],
      trustedDomains: ["sbi.co.in"],
      appLanguage: "en",
      elderlyMode: false,
      notificationThreshold: 60,
      totalScansSynced: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Profile personal update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      city: "Mumbai",
      guardianRole: "Senior Citizen",
      phoneNumber: "+919800011122",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const entryRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("userEntries")
    .doc("entry_vault_1");

  // Create CloudUserEntry
  await assertSucceeds(
    entryRef.set({
      userId: ALICE_UID,
      entryType: "VAULT",
      title: "Vault: Fake SBI KYC",
      category: "Fake Bank KYC",
      sourceLabel: "SMS",
      riskScore: 90,
      riskLevel: "CRITICAL",
      summary: "Suspicious KYC link saved to evidence vault.",
      reviewed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List query matching Kotlin snapshots()
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("userEntries")
      .orderBy("updatedAt", "desc")
      .get()
  );

  // Partial update on CloudUserEntry (mark reviewed)
  await assertSucceeds(
    entryRef.update({
      reviewed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable entryType change must fail
  await assertFails(
    entryRef.update({
      entryType: "PRE_ACTION",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Unauthenticated and cross-user access must fail
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("userEntries").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("userEntries").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("userEntries").doc("entry_vault_1").get());

  // Invalid entryType rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("userEntries")
      .doc("entry_bad")
      .set({
        userId: ALICE_UID,
        entryType: "UNKNOWN_TYPE",
        title: "Bad",
        category: "Bad",
        sourceLabel: "SMS",
        riskScore: 10,
        riskLevel: "LOW",
        summary: "",
        reviewed: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Owner delete succeeds
  await assertSucceeds(entryRef.delete());
});

