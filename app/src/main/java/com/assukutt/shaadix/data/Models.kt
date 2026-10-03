package com.assukutt.shaadix.data

data class Service(
    val id: String, val title: String, val category: String, val provider: String,
    val area: String, val price: Int, val rating: Double, val reviews: Int,
    val image: String, val description: String,
    val includes: List<String> = listOf("Dedicated event coordinator", "Setup and teardown", "Flexible date rescheduling")
)

data class Booking(
    val id: String, val service: Service, val date: String, val event: String,
    val guests: Int, val total: Int, val status: BookingStatus
)

enum class BookingStatus { CONFIRMED, PENDING, COMPLETED, CANCELLED }

object SampleData {
    val categories = listOf("Wedding", "Engagement", "College Function", "Birthday", "Reception", "Corporate", "Cultural")
    val serviceTypes = listOf("Venues", "Decor", "Catering", "Photography", "Makeup", "Music", "Mehendi", "Planners")
    val services = listOf(
        Service("v1", "The Courtyard Palace", "Venues", "The Courtyard Palace", "Kakkanad, Kochi", 85000, 4.9, 128, "https://images.unsplash.com/photo-1519167758481-83f550bb49b3?w=1000", "A light-filled celebration space with garden lawns and a grand indoor hall for unforgettable gatherings."),
        Service("d1", "Petal & Pearl Decor", "Decor", "Petal & Pearl", "Panampilly Nagar, Kochi", 42000, 4.8, 86, "https://images.unsplash.com/photo-1519225421980-715cb0215aed?w=1000", "Thoughtful floral styling, warm candlelight and custom mandap design for celebrations with character."),
        Service("p1", "Golden Hour Studio", "Photography", "Golden Hour Studio", "Fort Kochi", 36000, 4.9, 74, "https://images.unsplash.com/photo-1537633552985-df8429e8048b?w=1000", "A documentary-led photo and film team capturing the small moments and big feelings."),
        Service("c1", "Saffron Table", "Catering", "Saffron Table", "Edappally, Kochi", 1200, 4.7, 63, "https://images.unsplash.com/photo-1555244162-803834f70033?w=1000", "Seasonal Kerala favourites and modern menus, served with polished hospitality.", listOf("Curated menu tasting", "Vegetarian and non-vegetarian menus", "On-site service team")),
        Service("m1", "The Ivory Room", "Makeup", "Anika Beauty Studio", "Kadavanthra, Kochi", 18000, 4.8, 51, "https://images.unsplash.com/photo-1487412947147-5cebf100ffc2?w=1000", "Camera-ready bridal looks designed to feel like you, with a calm and considered prep experience."),
        Service("dj1", "Afterglow Sound", "Music", "Afterglow Sound Co.", "Vyttila, Kochi", 26000, 4.6, 39, "https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?w=1000", "A beautifully balanced sound setup, lighting and an open-format DJ for a packed dance floor.")
    )
    val initialBookings = listOf(Booking("SX-2048", services[0], "18 Dec 2026", "Wedding", 280, 91000, BookingStatus.CONFIRMED))
}
