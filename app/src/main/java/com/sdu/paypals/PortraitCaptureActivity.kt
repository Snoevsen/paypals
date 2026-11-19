package com.sdu.paypals

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Custom CaptureActivity locked to Portrait orientation in AndroidManifest.xml
 * so the QR code scanner always starts and remains in portrait mode.
 */
class PortraitCaptureActivity : CaptureActivity()
