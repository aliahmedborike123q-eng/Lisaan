/**
 * سكريبت رفع المحتوى الأصلي لتطبيق "لِسان (Lisaan)" إلى Firebase Firestore
 * التشغيل:
 *   npm install firebase-admin
 *   GOOGLE_APPLICATION_CREDENTIALS="./serviceAccountKey.json" node scripts/upload_seed_to_firestore.js
 */
const admin = require("firebase-admin");
const fs = require("fs");
const path = require("path");

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
});

const db = admin.firestore();

async function seedFirestoreCurriculum() {
  const seedFilePath = path.join(__dirname, "lisaan_en_from_ar_seed.json");
  const curriculum = JSON.parse(fs.readFileSync(seedFilePath, "utf8"));

  console.log(`Uploading Course: ${curriculum.courseId} (v${curriculum.version})...`);

  const courseRef = db.collection("courses").doc(curriculum.courseId);
  await courseRef.set({
    id: curriculum.courseId,
    sourceLangCode: "ar",
    targetLangCode: "en",
    titleAr: "الإنجليزية الشاملة من لِسان",
    titleEn: "Comprehensive English by Lisaan",
    totalUnits: 3,
    version: curriculum.version,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  const batch = db.batch();
  for (const lesson of curriculum.lessons) {
    const lessonRef = courseRef.collection("lessons").doc(lesson.lessonId);
    batch.set(lessonRef, lesson);
  }
  await batch.commit();
  console.log(`Successfully seeded ${curriculum.lessons.length} lessons (150 exercises) to Firestore!`);
}

seedFirestoreCurriculum().catch(console.error);
