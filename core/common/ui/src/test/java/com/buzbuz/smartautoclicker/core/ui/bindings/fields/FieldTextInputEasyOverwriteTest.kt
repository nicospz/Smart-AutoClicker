/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.ui.bindings.fields

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.core.ui.databinding.IncludeFieldTextInputBinding
import com.google.android.material.R as MaterialR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class FieldTextInputEasyOverwriteTest {

    @Test
    fun enableEasyOverwriteOnFocus_showsClearIconWhenPrefilledFieldGainsFocus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val binding = IncludeFieldTextInputBinding.inflate(LayoutInflater.from(context), null, false)
        val field = binding.textField

        // Mirrors dialogs that install hideSoftInputOnFocusLoss before easy overwrite.
        field.setOnFocusChangeListener { _, _ -> }

        binding.setText("Event name")
        binding.enableEasyOverwriteOnFocus()

        field.requestFocus()
        ShadowLooper.idleMainLooper()

        assertTrue(binding.root.isEndIconVisible)
        assertEquals(1f, binding.root.findViewById<View>(MaterialR.id.text_input_end_icon).alpha, 0.01f)
    }

    @Test
    fun setTextWhileFocused_showsClearIcon() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val binding = IncludeFieldTextInputBinding.inflate(LayoutInflater.from(context), null, false)
        val field = binding.textField

        binding.enableEasyOverwriteOnFocus()
        field.requestFocus()
        binding.setText("Updated name")
        ShadowLooper.idleMainLooper()

        assertTrue(binding.root.isEndIconVisible)
        assertEquals(1f, binding.root.findViewById<View>(MaterialR.id.text_input_end_icon).alpha, 0.01f)
    }
}
