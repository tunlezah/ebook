# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# NanoHTTPD
-keep class fi.iki.elonen.** { *; }

# Coil
-dontwarn coil.**

# Keep data classes for serialization
-keep class com.shelfwise.app.data.model.** { *; }
-keep class com.shelfwise.app.data.db.entity.** { *; }
