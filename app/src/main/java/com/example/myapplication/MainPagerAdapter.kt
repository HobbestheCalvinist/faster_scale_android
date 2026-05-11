package com.example.myapplication

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CheckinFragment()
            1 -> HistoryFragment()
            2 -> CallScheduleFragment()
            3 -> CommitmentFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}
