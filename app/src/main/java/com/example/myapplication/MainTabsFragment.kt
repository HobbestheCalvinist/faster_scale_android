package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication.databinding.FragmentMainTabsBinding

class MainTabsFragment : Fragment() {

    private var _binding: FragmentMainTabsBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val EXTRA_OPEN_TAB = "OPEN_TAB"
        const val EXTRA_SHOW_CHECKIN = "SHOW_CHECKIN"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = true

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.FirstFragment -> binding.viewPager.currentItem = 0
                R.id.HistoryFragment -> binding.viewPager.currentItem = 1
                R.id.CallScheduleFragment -> binding.viewPager.currentItem = 2
                R.id.CommitmentFragment -> binding.viewPager.currentItem = 3
            }
            true
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.bottomNavigation.menu.getItem(position).isChecked = true
                updateTitle(position)
            }
        })

        // Handle Back Swipe: Intercept back press on the main tabs.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.viewPager.currentItem > 0) {
                    // Navigate to the first tab (Check In) instead of exiting
                    binding.viewPager.currentItem = 0
                } else {
                    // On the first tab, allow the default behavior (exit app)
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        // Handle Navigation from Notifications
        handleIntentExtras()
    }

    private fun handleIntentExtras() {
        val intent = activity?.intent ?: return
        
        val tabToOpen = intent.getIntExtra(EXTRA_OPEN_TAB, -1)
        if (tabToOpen != -1) {
            binding.viewPager.post {
                binding.viewPager.currentItem = tabToOpen
            }
            intent.removeExtra(EXTRA_OPEN_TAB)
        }

        if (intent.getBooleanExtra(EXTRA_SHOW_CHECKIN, false)) {
            intent.removeExtra(EXTRA_SHOW_CHECKIN)
            val dialog = CheckInDialogFragment.newInstance(System.currentTimeMillis())
            dialog.show(childFragmentManager, "CheckInDialog")
        }
    }

    private fun updateTitle(position: Int) {
        val title = when (position) {
            0 -> getString(R.string.mood_tracker_title)
            1 -> getString(R.string.history_title)
            2 -> getString(R.string.call_schedule_title)
            3 -> "Commitment to Change"
            else -> getString(R.string.app_name)
        }
        (activity as? MainActivity)?.supportActionBar?.title = title
    }

    override fun onResume() {
        super.onResume()
        // Ensure title is correct when returning from Settings or About
        updateTitle(binding.viewPager.currentItem)
        
        // Also check for new intents if MainActivity was already running
        // Note: For full robustness, MainActivity should override onNewIntent and update its intent
        handleIntentExtras()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
