package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AttendanceRepository(private val context: Context, private val dao: AttendanceDao) {

    private var database: FirebaseDatabase? = null
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        initFirebase()
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey("AIzaSyDvTP5LUzQWFzSBFd1BhW4fgQnoYXOuGpc")
                    .setApplicationId("1:1043373324292:web:bc211abd843b32e1119d51")
                    .setDatabaseUrl("https://foxy-d8209-default-rtdb.firebaseio.com")
                    .setProjectId("foxy-d8209")
                    .setStorageBucket("foxy-d8209.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
            }
            database = FirebaseDatabase.getInstance()
            // Enable offline persistence for Firebase Database
            database?.setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "Firebase init error: ${e.message}", e)
        }
    }

    private fun getFirebaseRef() = database?.getReference("users/pranay/attendance")

    val allRecords: Flow<List<AttendanceRecord>> = dao.getAllRecordsFlow()

    suspend fun getRecordByDate(date: String): AttendanceRecord? = withContext(Dispatchers.IO) {
        dao.getRecordByDate(date)
    }

    suspend fun insertOrUpdate(record: AttendanceRecord) = withContext(Dispatchers.IO) {
        val existing = dao.getRecordByDate(record.date)
        val recordToSave = if (existing != null) {
            // Merge existing record fields if necessary (e.g., if a manual note was added)
            record.copy(
                id = existing.id,
                note = record.note ?: existing.note,
                isManual = record.isManual || existing.isManual
            )
        } else {
            record
        }

        val id = dao.insertRecord(recordToSave)
        val savedRecord = recordToSave.copy(id = id.toInt())

        // Try pushing to Firebase asynchronously
        pushRecordToFirebase(savedRecord)
    }

    suspend fun deleteRecord(record: AttendanceRecord) = withContext(Dispatchers.IO) {
        dao.deleteRecord(record)
        // Also remove from Firebase
        val ref = getFirebaseRef()
        if (ref != null) {
            val dateKey = sanitizeKey(record.date)
            try {
                ref.child(dateKey).removeValue().await()
            } catch (e: Exception) {
                Log.e("AttendanceRepository", "Firebase delete error for ${record.date}: ${e.message}")
            }
        }
    }

    suspend fun deleteRecordById(id: Int) = withContext(Dispatchers.IO) {
        // Find record to get date first for Firebase removal
        val records = dao.getAllRecords()
        val record = records.find { it.id == id }
        if (record != null) {
            deleteRecord(record)
        } else {
            dao.deleteRecordById(id)
        }
    }

    suspend fun pushRecordToFirebase(record: AttendanceRecord) = withContext(Dispatchers.IO) {
        val ref = getFirebaseRef()
        if (ref != null) {
            val dateKey = sanitizeKey(record.date)
            // Convert to a map for Firebase
            val recordMap = mapOf(
                "date" to record.date,
                "studentName" to record.studentName,
                "coachingName" to record.coachingName,
                "inwardTime" to record.inwardTime,
                "outwardTime" to record.outwardTime,
                "status" to record.status,
                "studyHours" to record.studyHours,
                "isManual" to record.isManual,
                "note" to record.note
            )
            try {
                ref.child(dateKey).setValue(recordMap).await()
                // Mark as synced locally
                if (!record.synced) {
                    dao.insertRecord(record.copy(synced = true))
                }
                Log.d("AttendanceRepository", "Pushed record to Firebase for date: ${record.date}")
            } catch (e: Exception) {
                Log.e("AttendanceRepository", "Firebase push error for ${record.date}: ${e.message}")
                // Keep marked as unsynced
                if (record.synced) {
                    dao.insertRecord(record.copy(synced = false))
                }
            }
        }
    }

    suspend fun syncNow(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ref = getFirebaseRef() ?: return@withContext Result.failure(Exception("Firebase Database offline or uninitialized"))

            // 1. Push all unsynced local records to Firebase
            val unsynced = dao.getUnsyncedRecords()
            for (record in unsynced) {
                pushRecordToFirebase(record)
            }

            // 2. Pull all records from Firebase and merge
            val snapshot = ref.get().await()
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val dateKey = child.key ?: continue
                    val date = desanitizeKey(dateKey)
                    
                    val fbStudentName = child.child("studentName").value as? String ?: "Pranay Sah"
                    val fbCoachingName = child.child("coachingName").value as? String ?: "Sukrishna Commerce"
                    val fbInwardTime = child.child("inwardTime").value as? String
                    val fbOutwardTime = child.child("outwardTime").value as? String
                    val fbStatus = child.child("status").value as? String ?: "Absent"
                    val fbStudyHours = (child.child("studyHours").value as? Number)?.toDouble() ?: 0.0
                    val fbIsManual = child.child("isManual").value as? Boolean ?: false
                    val fbNote = child.child("note").value as? String

                    val localRecord = dao.getRecordByDate(date)
                    if (localRecord == null) {
                        // Insert locally
                        val newRecord = AttendanceRecord(
                            date = date,
                            studentName = fbStudentName,
                            coachingName = fbCoachingName,
                            inwardTime = fbInwardTime,
                            outwardTime = fbOutwardTime,
                            status = fbStatus,
                            studyHours = fbStudyHours,
                            synced = true,
                            isManual = fbIsManual,
                            note = fbNote
                        )
                        dao.insertRecord(newRecord)
                    } else {
                        // Conflict Resolution:
                        // If local has not been synced, or if firebase record is manual/different,
                        // we can merge them or let manual edits override. Let's merge:
                        // If local is manual, don't overwrite with standard, unless firebase is also manual.
                        // Let's replace local if local is not manual and firebase is manual, or just keep manual priority.
                        if (!localRecord.isManual && fbIsManual) {
                            dao.insertRecord(localRecord.copy(
                                studentName = fbStudentName,
                                coachingName = fbCoachingName,
                                inwardTime = fbInwardTime,
                                outwardTime = fbOutwardTime,
                                status = fbStatus,
                                studyHours = fbStudyHours,
                                isManual = true,
                                note = fbNote,
                                synced = true
                            ))
                        } else if (!localRecord.isManual && !fbIsManual) {
                            // If neither is manual, sync other fields
                            dao.insertRecord(localRecord.copy(
                                inwardTime = localRecord.inwardTime ?: fbInwardTime,
                                outwardTime = localRecord.outwardTime ?: fbOutwardTime,
                                status = if (localRecord.status == "Still in Coaching" && fbStatus == "Forgot OUTWARD") fbStatus else localRecord.status,
                                studyHours = if (localRecord.studyHours == 0.0) fbStudyHours else localRecord.studyHours,
                                note = localRecord.note ?: fbNote,
                                synced = true
                            ))
                        } else {
                            // Local is manual. Mark synced to firebase since we pushed it in step 1.
                            dao.insertRecord(localRecord.copy(synced = true))
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "Sync error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun sanitizeKey(key: String): String {
        // Firebase keys cannot contain '.', '#', '$', '[', or ']'
        // Our date format "dd-MM-yyyy" uses hyphens which are valid, but let's make sure it's fully sanitized.
        return key.replace(".", "_")
    }

    private fun desanitizeKey(key: String): String {
        return key.replace("_", ".")
    }
}
