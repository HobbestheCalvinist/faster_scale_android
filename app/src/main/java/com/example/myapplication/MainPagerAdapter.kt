package com.fasterscale.app

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CheckinFragment()
            1 -> HistoryFragment()
            2 -> IntrospectionFragment()
            3 -> CallScheduleFragment()
            4 -> CommitmentFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}
