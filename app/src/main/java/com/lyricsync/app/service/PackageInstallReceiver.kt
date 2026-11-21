package com.lyricsync.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

class PackageInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_COMMIT) return

        val targetPkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        if (targetPkg != null && targetPkg != context.packageName) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.let {
                    it.removeFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    )
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(it) }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(
                    context.applicationContext,
                    "Oturum kurulumu tamamlandı ✓ Şimdi Bildirim Erişimi'ni açabilirsiniz.",
                    Toast.LENGTH_LONG
                ).show()
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                val message = if (statusMessage != null) {
                    "Kurulum başarısız ($status): $statusMessage"
                } else {
                    "Kurulum tamamlanamadı (kod: $status)"
                }
                Toast.makeText(
                    context.applicationContext,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_COMMIT = "com.lyricsync.app.ACTION_INSTALL_COMMIT"
    }
}
