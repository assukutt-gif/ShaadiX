package com.assukutt.shaadix.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface ShaadiXRepository {
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(name: String, email: String, password: String)
    suspend fun saveBooking(userId: String, booking: Booking)
}

/** Firebase runs when google-services.json is installed. Never put private keys in this project. */
class FirebaseShaadiXRepository : ShaadiXRepository {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    override suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }
    override suspend fun signUp(name: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user?.uid ?: error("Account was created without a user ID")
        db.collection("users").document(uid).set(mapOf(
            "name" to name.trim(), "email" to email.trim(), "role" to "customer",
            "createdAt" to com.google.firebase.Timestamp.now()
        )).await()
    }
    override suspend fun saveBooking(userId: String, booking: Booking) {
        db.collection("bookings").document(booking.id).set(mapOf(
            "customerId" to userId, "providerId" to booking.service.id, "serviceId" to booking.service.id,
            "eventType" to booking.event, "eventDate" to booking.date, "guestCount" to booking.guests,
            "totalAmount" to booking.total, "status" to booking.status.name
        )).await()
    }
}

