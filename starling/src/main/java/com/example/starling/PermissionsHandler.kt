package com.example.starling

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener

object PermissionsHandler {
  private const val TAG = "PermissionsHandler"

  /*fun requestAdvertisement(context: Context, onResult: (success: Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
      TODO("Support android version < Marshmallow")

    val requiredPermissions = mutableListOf(
      Manifest.permission.BLUETOOTH,
      Manifest.permission.BLUETOOTH_ADMIN,
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION
    );

    // Check if permissions are already granted
    var allGranted = true
    for (permission in requiredPermissions) {
      if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
        allGranted = false
        break
      }
    }
    if (allGranted) {
      onResult(true)
      return
    }

    Dexter.withContext(context)
      .withPermissions(requiredPermissions).withListener(object : MultiplePermissionsListener {
        override fun onPermissionsChecked(report: MultiplePermissionsReport) { /* ... */

          if (report.areAllPermissionsGranted()) {
            Log.d(TAG, "Bluetooth permissions granted")
            onResult(true)
          } else {
            Log.w(TAG, "Following permissions were explicitly not allowed")
            for (perm in report.deniedPermissionResponses) {
              Log.w(TAG, perm.permissionName.toString())
            }
            onResult(false)
          }
        }

        override fun onPermissionRationaleShouldBeShown(
          permissions: List<PermissionRequest?>?,
          token: PermissionToken?
        ) {
          TODO("onPermissionRationaleShouldBeShown not implemented yet")
        }
      }).check()
  }*/
}
