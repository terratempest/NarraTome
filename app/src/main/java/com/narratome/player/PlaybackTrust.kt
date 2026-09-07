package com.narratome.player

object PlaybackTrust {

    fun isTrusted(controllerPackage: String?, appPackageName: String): Boolean {
        val pkg = controllerPackage ?: return false
        if (pkg == appPackageName) return true
        if (TRUSTED_PREFIXES.any { pkg.startsWith(it) }) return true
        
        // Android Auto and Automotive packages can vary by OEM or region
        return pkg.contains("android.projection") || 
               pkg.contains("android.automotive") ||
               pkg.contains("com.google.android.projection.gearhead")
    }

    private val TRUSTED_PREFIXES = listOf(
        "com.google.android.projection.gearhead",
        "com.google.android.gms",
        "com.google.android.gms.car",
        "com.google.android.apps.googleassistant",
        "androidx.media3.session",
    )
}
