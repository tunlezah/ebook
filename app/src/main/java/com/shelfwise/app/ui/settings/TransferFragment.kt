package com.shelfwise.app.ui.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.shelfwise.app.R
import com.shelfwise.app.databinding.FragmentTransferBinding
import com.shelfwise.app.service.FileServerService
import com.shelfwise.app.util.appContainer
import com.shelfwise.app.util.showToast

class TransferFragment : Fragment() {

    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!

    private var isServerRunning = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startServer()
        } else {
            requireContext().showToast("Notification permission is required for the server")
        }
    }

    private val serverStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val running = intent?.getBooleanExtra(FileServerService.EXTRA_IS_RUNNING, false) ?: false
            val url = intent?.getStringExtra(FileServerService.EXTRA_URL)
            updateUI(running, url)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnToggleServer.setOnClickListener {
            if (isServerRunning) {
                FileServerService.stopServer(requireContext())
            } else {
                checkPermissionsAndStart()
            }
        }

        updateUI(false, null)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(FileServerService.BROADCAST_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(serverStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(serverStateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(serverStateReceiver)
        } catch (_: Exception) {}
    }

    private fun checkPermissionsAndStart() {
        // Check WiFi connectivity
        if (!isWifiConnected()) {
            requireContext().showToast(getString(R.string.transfer_no_wifi))
            return
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        startServer()
    }

    private fun startServer() {
        val port = appContainer.preferencesManager.serverPort
        FileServerService.startServer(requireContext(), port)
    }

    private fun updateUI(running: Boolean, url: String?) {
        isServerRunning = running

        if (running && url != null) {
            binding.statusText.text = getString(R.string.transfer_active)
            // The URL already contains the rotating auth token; display it so
            // the user can type or copy the full, token-bearing address.
            binding.serverUrlText.text = url
            binding.serverUrlText.isVisible = true
            binding.instructionsText.isVisible = true
            binding.tokenWarningText.isVisible = true
            binding.autoStopText.text = getString(
                R.string.transfer_auto_stop,
                appContainer.preferencesManager.serverAutoStopMinutes
            )
            binding.autoStopText.isVisible = true
            binding.btnToggleServer.text = getString(R.string.transfer_stop)
            binding.statusIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.server_active)
            )
        } else {
            binding.statusText.text = getString(R.string.transfer_inactive)
            binding.serverUrlText.isVisible = false
            binding.instructionsText.isVisible = false
            binding.tokenWarningText.isVisible = false
            binding.autoStopText.isVisible = false
            binding.btnToggleServer.text = getString(R.string.transfer_start)
            binding.statusIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.server_inactive)
            )
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
