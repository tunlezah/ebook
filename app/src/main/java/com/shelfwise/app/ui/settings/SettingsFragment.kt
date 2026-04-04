package com.shelfwise.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shelfwise.app.BuildConfig
import com.shelfwise.app.R
import com.shelfwise.app.databinding.FragmentSettingsBinding
import com.shelfwise.app.util.PreferencesManager
import com.shelfwise.app.util.appContainer
import com.shelfwise.app.util.showToast
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy { appContainer.preferencesManager }

    private val fonts = listOf(
        "literata" to "Literata",
        "source_serif" to "Source Serif 4",
        "noto_serif" to "Noto Serif",
        "merriweather" to "Merriweather",
        "lora" to "Lora",
        "source_sans" to "Source Sans 3"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        setupTheme()
        setupAnimations()
        setupLowMemory()
        setupFont()
        setupFontSize()
        setupLineSpacing()
        setupBlueLight()
        setupClearCache()
        setupVersion()
    }

    private fun setupTheme() {
        val themes = arrayOf(
            getString(R.string.settings_theme_light),
            getString(R.string.settings_theme_dark),
            getString(R.string.settings_theme_system)
        )
        binding.themeValue.text = themes[prefs.themeMode.coerceIn(0, 2)]

        binding.themeRow.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(themes, prefs.themeMode) { dialog, which ->
                    prefs.themeMode = which
                    binding.themeValue.text = themes[which]
                    when (which) {
                        PreferencesManager.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        PreferencesManager.THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    }
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupAnimations() {
        binding.switchNoAnimations.isChecked = prefs.noAnimations
        binding.switchNoAnimations.setOnCheckedChangeListener { _, checked ->
            prefs.noAnimations = checked
        }
    }

    private fun setupLowMemory() {
        binding.switchLowMemory.isChecked = prefs.lowMemoryMode
        binding.switchLowMemory.setOnCheckedChangeListener { _, checked ->
            prefs.lowMemoryMode = checked
        }
    }

    private fun setupFont() {
        val currentFont = fonts.find { it.first == prefs.readerFont }
        binding.fontValue.text = currentFont?.second ?: "Literata"

        binding.fontRow.setOnClickListener {
            val names = fonts.map { it.second }.toTypedArray()
            val currentIndex = fonts.indexOfFirst { it.first == prefs.readerFont }.coerceAtLeast(0)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_font)
                .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                    prefs.readerFont = fonts[which].first
                    binding.fontValue.text = fonts[which].second
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupFontSize() {
        // Font size range: 12-36, seekbar 0-24
        val currentSize = prefs.readerFontSize
        binding.fontSizeSeekBar.progress = (currentSize - 12).coerceIn(0, 24)
        binding.fontSizeValue.text = "${currentSize}px"

        binding.fontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 12
                binding.fontSizeValue.text = "${size}px"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.readerFontSize = (seekBar?.progress ?: 6) + 12
            }
        })
    }

    private fun setupLineSpacing() {
        // Line spacing: 1.0-3.0, seekbar 0-10 (steps of 0.2)
        val currentSpacing = prefs.readerLineSpacing
        binding.lineSpacingSeekBar.progress = ((currentSpacing - 1.0f) / 0.2f).toInt().coerceIn(0, 10)
        binding.lineSpacingValue.text = String.format("%.1f", currentSpacing)

        binding.lineSpacingSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val spacing = 1.0f + progress * 0.2f
                binding.lineSpacingValue.text = String.format("%.1f", spacing)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.readerLineSpacing = 1.0f + (seekBar?.progress ?: 3) * 0.2f
            }
        })
    }

    private fun setupBlueLight() {
        binding.switchBlueLight.isChecked = prefs.blueLightFilter
        binding.switchBlueLight.setOnCheckedChangeListener { _, checked ->
            prefs.blueLightFilter = checked
        }
    }

    private fun setupClearCache() {
        binding.clearCacheRow.setOnClickListener {
            val cacheDir = File(requireContext().cacheDir, "covers")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
            requireContext().showToast(getString(R.string.settings_cache_cleared))
        }
    }

    private fun setupVersion() {
        binding.versionText.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
