package com.onemind.core.data.config

import platform.Foundation.NSBundle

internal actual fun platformAppVersion(): String? =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)?.takeIf { it.isNotBlank() }
