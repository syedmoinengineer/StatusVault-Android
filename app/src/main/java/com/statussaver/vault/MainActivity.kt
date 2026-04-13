package com.statussaver.vault

import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.statussaver.vault.databinding.ActivityMainBinding
import com.statussaver.vault.repository.StatusRepository
import com.statussaver.vault.ui.SavedFragment
import com.statussaver.vault.ui.StatusListFragment
import com.statussaver.vault.viewmodel.StatusViewModel
import com.statussaver.vault.viewmodel.StatusViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val repository by lazy { StatusRepository(this) }

    val viewModel: StatusViewModel by viewModels {
        StatusViewModelFactory(repository)
    }

    // SAF folder picker launcher
    private val openDocumentTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            repository.persistUri(uri)
            viewModel.loadStatuses(uri)
            showMainContent()
        } else {
            Snackbar.make(binding.root, getString(R.string.permission_denied), Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setupBottomNavigation()
        setupPermissionButtons()
        observePermissionRevoked()
        checkAndInitAccess()
    }

    private fun checkAndInitAccess() {
        val savedUri = repository.getPersistedUri()
        if (savedUri != null && isUriPermissionValid(savedUri)) {
            viewModel.loadStatuses(savedUri)
            showMainContent()
        } else {
            repository.clearPersistedUri()
            showPermissionScreen()
        }
    }

    private fun isUriPermissionValid(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_images -> { showFragment(FragmentType.IMAGES); true }
                R.id.nav_videos -> { showFragment(FragmentType.VIDEOS); true }
                R.id.nav_saved  -> { showFragment(FragmentType.SAVED);  true }
                else -> false
            }
        }
    }

    private fun setupPermissionButtons() {
        binding.btnGrantAccessWa.setOnClickListener {
            showAccessInstructionDialog(isWaBusiness = false)
        }
        binding.btnGrantAccessWaBusiness.setOnClickListener {
            showAccessInstructionDialog(isWaBusiness = true)
        }
        binding.btnRefreshPermission.setOnClickListener {
            checkAndInitAccess()
        }
    }

    private fun showAccessInstructionDialog(isWaBusiness: Boolean) {
        val appName = if (isWaBusiness) "WhatsApp Business" else "WhatsApp"
        val packageName = if (isWaBusiness) "com.whatsapp.w4b" else "com.whatsapp"
        val folderName = if (isWaBusiness) "WhatsApp Business" else "WhatsApp"

        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "In the next screen, navigate to:\n\n" +
                    "📁 Android → media → $packageName → $folderName → Media → .Statuses\n\n" +
                    "Then tap 'Use this folder' → 'Allow'"
        } else {
            "In the next screen, navigate to:\n\n" +
                    "📁 $folderName → Media → .Statuses\n\n" +
                    "Then tap 'Use this folder' → 'Allow'"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Grant Folder Access")
            .setMessage(message)
            .setIcon(R.drawable.ic_app_logo)
            .setPositiveButton("Open Folder Picker") { _, _ ->
                launchFolderPicker(isWaBusiness)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchFolderPicker(isWaBusiness: Boolean) {
        val packageName = if (isWaBusiness) "com.whatsapp.w4b" else "com.whatsapp"
        val folderName = if (isWaBusiness) "WhatsApp Business" else "WhatsApp"

        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "Android/media/$packageName/$folderName/Media/.Statuses"
        } else {
            "$folderName/Media/.Statuses"
        }

        val initialUri = try {
            val encoded = path.replace("/", "%2F")
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$encoded")
        } catch (e: Exception) {
            null
        }

        openDocumentTree.launch(initialUri)
    }

    private fun observePermissionRevoked() {
        viewModel.permissionRevoked.observe(this) { revoked ->
            if (revoked == true) {
                repository.clearPersistedUri()
                showPermissionScreen()
                viewModel.resetPermissionRevoked()
                Snackbar.make(binding.root, getString(R.string.permission_revoked), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    fun showMainContent() {
        binding.layoutPermission.visibility = View.GONE
        binding.layoutMain.visibility = View.VISIBLE
        if (supportFragmentManager.findFragmentByTag(FragmentType.IMAGES.tag) == null) {
            showFragment(FragmentType.IMAGES)
            binding.bottomNavigation.selectedItemId = R.id.nav_images
        }
    }

    private fun showPermissionScreen() {
        binding.layoutMain.visibility = View.GONE
        binding.layoutPermission.visibility = View.VISIBLE
    }

    private fun showFragment(type: FragmentType) {
        val tag = type.tag
        val tx = supportFragmentManager.beginTransaction()
        tx.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        // Hide all current fragments
        supportFragmentManager.fragments.forEach { tx.hide(it) }

        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing != null) {
            tx.show(existing)
        } else {
            val fragment = when (type) {
                FragmentType.IMAGES -> StatusListFragment.newImages()
                FragmentType.VIDEOS -> StatusListFragment.newVideos()
                FragmentType.SAVED  -> SavedFragment()
            }
            tx.add(R.id.fragmentContainer, fragment, tag)
        }
        tx.commit()
    }

    enum class FragmentType(val tag: String) {
        IMAGES("images"),
        VIDEOS("videos"),
        SAVED("saved")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                val uri = repository.getPersistedUri()
                if (uri != null) {
                    viewModel.loadStatuses(uri)
                    Snackbar.make(binding.root, getString(R.string.refreshing), Snackbar.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_change_source -> {
                showAccessInstructionDialog(isWaBusiness = false)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}