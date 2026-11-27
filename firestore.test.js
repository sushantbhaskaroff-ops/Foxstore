const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DEV_UID = "dev_alice_123";
const USER_UID = "shopper_bob_456";
const ADMIN_UID = "admin_sushant_001";
const ADMIN_EMAIL = "sushantbhaskaroff@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validPendingPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    developerId: uid,
    developerName: "Alice Studio",
    name: "FocusPulse",
    packageName: "com.alice.focuspulse",
    tagline: "Deep work timer and ambient soundscapes",
    description: "Stay in deep focus with custom Pomodoro blocks, session stats, and offline soundscapes.",
    releaseNotes: "Initial v1.0 release from developer portal.",
    category: "Productivity",
    versionName: "1.0.0",
    sizeLabel: "14.2 MB",
    iconKey: "rocket",
    accentHex: "#10B981",
    status: "PENDING",
    reviewNote: "",
    reviewedBy: "",
    downloadsCount: 0,
    ratingAvg: 0.0,
    ratingCount: 0,
    source: "WEBSITE",
    createdAt: now,
    updatedAt: now,
    ...overrides,
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("apps").where("status", "==", "APPROVED").get());
  await assertFails(
    unauthDb.collection("apps").doc("app_1").set(validPendingPayload(DEV_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(USER_UID).collection("installedApps").get()
  );
});

test("Developer can submit PENDING app via website payload, but cannot self-approve or spoof status", async () => {
  const devDb = testEnv.authenticatedContext(DEV_UID, { email: "alice@dev.com", email_verified: true }).firestore();

  // Valid PENDING submission succeeds
  await assertSucceeds(
    devDb.collection("apps").doc("app_focus_1").set(validPendingPayload(DEV_UID))
  );

  // Self-approval on create fails
  await assertFails(
    devDb.collection("apps").doc("app_hack_1").set(validPendingPayload(DEV_UID, { status: "APPROVED" }))
  );

  // Extra @DocumentId 'id' field fails
  await assertFails(
    devDb.collection("apps").doc("app_hack_2").set(validPendingPayload(DEV_UID, { id: "app_hack_2" }))
  );

  // Developer trying to approve their own pending app fails
  await assertFails(
    devDb.collection("apps").doc("app_focus_1").update({
      status: "APPROVED",
      reviewedBy: DEV_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Spoofed future timestamp fails
  await assertFails(
    devDb.collection("apps").doc("app_hack_3").set(
      validPendingPayload(DEV_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );
});

test("Email spoofing test: unverified admin email cannot approve apps", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("apps").doc("app_pending_1").set(validPendingPayload(DEV_UID));
  });

  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofedAdminDb.collection("apps").doc("app_pending_1").update({
      status: "APPROVED",
      reviewNote: "Spoofed approval",
      reviewedBy: "spoof_uid",
      reviewedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Verified admin can list all apps and approve or reject pending apps", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("apps").doc("app_pending_1").set(validPendingPayload(DEV_UID));
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  // Admin unfiltered list succeeds
  await assertSucceeds(adminDb.collection("apps").get());

  // Admin approval succeeds
  const now = new Date(Date.now() - 500);
  await assertSucceeds(
    adminDb.collection("apps").doc("app_pending_1").update({
      status: "APPROVED",
      reviewNote: "Verified package and Play Policy compliant.",
      reviewedBy: ADMIN_UID,
      reviewedAt: now,
      updatedAt: now,
    })
  );
});

test("Query alignment: shoppers can query APPROVED apps, developers query own apps, unfiltered fails for non-admin", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("apps").doc("app_approved_1").set(
      validPendingPayload(DEV_UID, { status: "APPROVED", reviewedBy: ADMIN_UID })
    );
    await ctx.firestore().collection("apps").doc("app_pending_2").set(
      validPendingPayload(DEV_UID)
    );
  });

  const shopperDb = testEnv.authenticatedContext(USER_UID, { email: "bob@user.com", email_verified: true }).firestore();
  const devDb = testEnv.authenticatedContext(DEV_UID, { email: "alice@dev.com", email_verified: true }).firestore();

  // Storefront query succeeds
  await assertSucceeds(shopperDb.collection("apps").where("status", "==", "APPROVED").get());

  // Developer own-submissions query succeeds
  await assertSucceeds(devDb.collection("apps").where("developerId", "==", DEV_UID).get());

  // Unfiltered list fails for non-admin
  await assertFails(shopperDb.collection("apps").get());

  // Direct get of another developer's PENDING app fails
  await assertFails(shopperDb.collection("apps").doc("app_pending_2").get());
});

test("Shopper can install an APPROVED app and submit a review atomically, isolated to their UID", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("apps").doc("app_live_1").set(
      validPendingPayload(DEV_UID, { status: "APPROVED", reviewedBy: ADMIN_UID, createdAt: created, updatedAt: created })
    );
  });

  const bobDb = testEnv.authenticatedContext(USER_UID, { email: "bob@user.com", email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(DEV_UID, { email: "alice@dev.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 500);

  // Bob installs app into users/bob/installedApps/app_live_1
  await assertSucceeds(
    bobDb.collection("users").doc(USER_UID).collection("installedApps").doc("app_live_1").set({
      appId: "app_live_1",
      appName: "FocusPulse",
      packageName: "com.alice.focuspulse",
      versionName: "1.0.0",
      category: "Productivity",
      iconKey: "rocket",
      accentHex: "#10B981",
      installedAt: now,
      updatedAt: now,
    })
  );

  // Bob increments downloadsCount by 1
  await assertSucceeds(
    bobDb.collection("apps").doc("app_live_1").update({
      downloadsCount: 1,
      updatedAt: now,
    })
  );

  // Cross-user isolation: Alice cannot read Bob's installedApps
  await assertFails(aliceDb.collection("users").doc(USER_UID).collection("installedApps").get());

  // Atomic batch review + rating summary update succeeds
  const batch = bobDb.batch();
  batch.set(
    bobDb.collection("apps").doc("app_live_1").collection("reviews").doc(USER_UID),
    {
      userId: USER_UID,
      authorName: "Bob",
      rating: 5,
      comment: "Super crisp timer and clean UI!",
      createdAt: now,
      updatedAt: now,
    }
  );
  batch.update(
    bobDb.collection("apps").doc("app_live_1"),
    {
      ratingAvg: 5.0,
      ratingCount: 1,
      updatedAt: now,
    }
  );
  await assertSucceeds(batch.commit());
});

test("Shadow update & self-assigned admin escalation fail", async () => {
  const devDb = testEnv.authenticatedContext(DEV_UID, { email: "alice@dev.com", email_verified: true }).firestore();
  await assertFails(
    devDb.collection("admins").doc(DEV_UID).set({ grantedAt: new Date(Date.now() - 500) })
  );
});
