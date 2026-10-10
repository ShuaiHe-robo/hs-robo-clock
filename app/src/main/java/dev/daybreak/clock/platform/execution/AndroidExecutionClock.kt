package dev.daybreak.clock.platform.execution

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dev.daybreak.clock.domain.execution.ExecutionClock
import dev.daybreak.clock.domain.execution.ExecutionTime
import java.time.ZoneId

class AndroidExecutionClock(private val context: Context) : ExecutionClock {
    override fun now() = ExecutionTime(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1), ZoneId.systemDefault())
}
