package com.tabslify.remote

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tabslify.R

@Composable
fun DevRemoteDesktopTabContent() {
    Toast.makeText(
        LocalContext.current,
        stringResource(R.string.forbidden),
        Toast.LENGTH_SHORT
    ).show()
}
