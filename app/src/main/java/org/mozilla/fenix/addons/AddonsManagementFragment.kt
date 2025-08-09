/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.addons

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.fonts.FontStyle.FONT_WEIGHT_MEDIUM
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManager
import mozilla.components.feature.addons.AddonManagerException
import mozilla.components.feature.addons.ui.AddonsManagerAdapter
import mozilla.components.feature.addons.ui.AddonsManagerAdapterDelegate
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.view.hideKeyboard
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.databinding.FragmentAddOnsManagementBinding
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.runIfFragmentIsAttached
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.settings.SupportUtils.AMO_HOMEPAGE_FOR_ANDROID
import org.mozilla.fenix.theme.ThemeManager
import java.util.Locale

/**
 * Fragment use for managing add-ons.
 */
@Suppress("TooManyFunctions", "LargeClass")
class AddonsManagementFragment : Fragment(R.layout.fragment_add_ons_management) {

    private val logger = Logger("AddonsManagementFragment")

    private var binding: FragmentAddOnsManagementBinding? = null

    private var addons: List<Addon> = emptyList()

    private var adapter: AddonsManagerAdapter? = null

    private var addonImportFilePicker: ActivityResultLauncher<Intent>? = null

    private val browsingModeManager by lazy {
        (activity as HomeActivity).browsingModeManager
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logger.info("View created for AddonsManagementFragment")
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentAddOnsManagementBinding.bind(view)
        bindRecyclerView()
        setupMenu()
        (activity as HomeActivity).webExtensionPromptFeature.onAddonChanged = {
            runIfFragmentIsAttached {
                adapter?.updateAddon(it)
            }
        }
        addonImportFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if(result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let{uri ->
                    requireComponents.intentProcessors.addonInstallIntentProcessor.fromUri(uri)
                        .let{ tmpFile ->
                            val extURI = requireComponents.intentProcessors.addonInstallIntentProcessor.parseExtension(tmpFile)
                            requireComponents.intentProcessors.addonInstallIntentProcessor.installExtension(
                                extURI,
                                onSuccess = {
                                    val installedState = provideAddonManger().toInstalledState(it)
                                    val ao = Addon.newFromWebExtension(it, installedState)
                                    runIfFragmentIsAttached {
                                        adapter?.updateAddon(ao)
                                        binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                                    }
                                }
                            )
                        }
                }
            }
        }
    }

    private fun setupMenu() {
        // Menu setup removed due to missing menu resources
        // If you need menu functionality, create the appropriate XML menu files first
    }

    private fun installFromFile() {
        val intent = Intent()
            .setType("application/x-xpinstall")
            .setAction(Intent.ACTION_GET_CONTENT)

        addonImportFilePicker!!.launch(intent)
    }

    private fun showAlertDialog() {
        // Alert dialog removed due to missing string resources
        // If you need cache clearing functionality, add the required string resources first
    }

    private fun searchAddons(addonSearchText: String): Boolean {
        if (adapter == null) {
            return false
        }

        val searchedAddons = arrayListOf<Addon>()
        addons.forEach { addon ->
            val names = addon.translatableName
            val language = Locale.getDefault().language
            names[language]?.let { name ->
                if (name.lowercase().contains(addonSearchText.lowercase())) {
                    searchedAddons.add(addon)
                }
            }
            val description = addon.translatableDescription
            description[language]?.let { desc ->
                if (desc.lowercase().contains(addonSearchText.lowercase())) {
                    if (!searchedAddons.contains(addon)) {
                        searchedAddons.add(addon)
                    }
                }
            }
        }
        updateUI(searchedAddons)

        return true
    }

    private fun updateUI(searchedAddons: List<Addon>) {
        adapter?.updateAddons(searchedAddons)

        if (searchedAddons.isEmpty()) {
            binding?.addOnsEmptyMessage?.visibility = View.VISIBLE
            binding?.addOnsList?.visibility = View.GONE
        } else {
            binding?.addOnsEmptyMessage?.visibility = View.GONE
            binding?.addOnsList?.visibility = View.VISIBLE
        }
    }


    override fun onResume() {
        logger.info("Resumed AddonsManagementFragment")

        super.onResume()
        showToolbar(getString(R.string.preferences_extensions))
        view?.hideKeyboard()
    }

    override fun onDestroyView() {
        logger.info("Destroyed view for AddonsManagementFragment")

        super.onDestroyView()
        // letting go of the resources to avoid memory leak.
        adapter = null
        binding = null
        (activity as HomeActivity).webExtensionPromptFeature.onAddonChanged = {}
    }

    private fun bindRecyclerView() {
        logger.info("Binding recycler view for AddonsManagementFragment")

        val managementView = AddonsManagementView(
            navController = findNavController(),
            onInstallButtonClicked = ::installAddon,
            onMoreAddonsButtonClicked = ::openAMO,
            onLearnMoreClicked = { link, addon ->
                openLearnMoreLink(
                    activity as HomeActivity,
                    link,
                    addon,
                    BrowserDirection.FromAddonsManagementFragment,
                )
            },
        )

        val recyclerView = binding?.addOnsList
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        val shouldRefresh = adapter != null


        logger.info("AddonsManagementFragment should refresh? $shouldRefresh")

        // If the fragment was launched to install an "external" add-on from AMO, we deactivate
        // the cache to get the most up-to-date list of add-ons to match against.
        lifecycleScope.launch(IO) {
            try {
                logger.info("AddonsManagementFragment asking for addons")

                addons = requireContext().components.addonManager.getAddons()
                lifecycleScope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        if (!shouldRefresh) {
                            adapter = AddonsManagerAdapter(
                                addonsManagerDelegate = managementView,
                                addons = addons,
                                style = createAddonStyle(requireContext()),
                                store = requireComponents.core.store,
                            )
                        }
                        binding?.addOnsProgressBar?.isVisible = false
                        binding?.addOnsEmptyMessage?.isVisible = false

                        recyclerView?.adapter = adapter
                        recyclerView?.accessibilityDelegate = object : View.AccessibilityDelegate() {
                            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                                super.onInitializeAccessibilityNodeInfo(host, info)

                                adapter?.let {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo(
                                            it.itemCount,
                                            1,
                                            false,
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
                                            it.itemCount,
                                            1,
                                            false,
                                        )
                                    }
                                }
                            }
                        }

                        if (shouldRefresh) {
                            adapter?.updateAddons(addons)
                        }
                    }
                }
            } catch (e: AddonManagerException) {
                lifecycleScope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        binding?.let {
                            showSnackBar(
                                it.root,
                                getString(R.string.mozac_feature_addons_failed_to_query_extensions),
                            )
                        }
                        binding?.addOnsProgressBar?.isVisible = false
                        binding?.addOnsEmptyMessage?.isVisible = true
                    }
                }
            }
        }
    }

    private fun createAddonStyle(context: Context): AddonsManagerAdapter.Style {
        val sectionsTypeFace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Typeface.create(Typeface.DEFAULT, FONT_WEIGHT_MEDIUM, false)
        } else {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        return AddonsManagerAdapter.Style(
            sectionsTextColor = ThemeManager.resolveAttribute(R.attr.textPrimary, context),
            addonNameTextColor = ThemeManager.resolveAttribute(R.attr.textPrimary, context),
            addonSummaryTextColor = ThemeManager.resolveAttribute(R.attr.textSecondary, context),
            sectionsTypeFace = sectionsTypeFace,
            addonAllowPrivateBrowsingLabelDrawableRes = R.drawable.ic_add_on_private_browsing_label,
        )
    }

    @VisibleForTesting
    internal fun provideAddonManger(): AddonManager {
        return requireContext().components.addonManager
    }

    internal fun installAddon(addon: Addon) {
        binding?.addonProgressOverlay?.overlayCardView?.visibility = View.VISIBLE

        if (browsingModeManager.mode == BrowsingMode.Private) {
            binding?.addonProgressOverlay?.overlayCardView?.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.fx_mobile_private_layer_color_3,
                ),
            )
        }

        val installOperation = provideAddonManger().installAddon(
            url = addon.downloadUrl,
            installationMethod = InstallationMethod.MANAGER,
            onSuccess = {
                runIfFragmentIsAttached {
                    adapter?.updateAddon(it)
                    binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                }
            },
            onError = { _ ->
                binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
            },
        )
        binding?.addonProgressOverlay?.cancelButton?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                val safeBinding = binding
                // Hide the installation progress overlay once cancellation is successful.
                if (installOperation.cancel().await()) {
                    safeBinding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                }
            }
        }
    }

    private fun openAMO() {
        openLinkInNewTab(
            activity as HomeActivity,
            AMO_HOMEPAGE_FOR_ANDROID,
            BrowserDirection.FromAddonsManagementFragment,
        )
    }
}
