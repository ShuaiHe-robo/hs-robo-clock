# Room supplies its own consumer rules. Keep the database's constructor and
# generated implementation, which Room resolves by name at runtime.
-keep class dev.daybreak.clock.data.ClockDatabase { *; }
-keep class dev.daybreak.clock.data.ClockDatabase_Impl { *; }
