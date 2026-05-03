package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the third destination in the navigation.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerviewHistory.layoutManager = LinearLayoutManager(context)

        val db = AppDatabase.getDatabase(requireContext())
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.checkInDao().getAllCheckIns().collect { checkIns ->
                    binding.recyclerviewHistory.adapter = HistoryAdapter(
                        checkIns = checkIns,
                        onEditClick = { checkIn ->
                            val bundle = Bundle().apply {
                                putString("selectedDate", checkIn.date)
                            }
                            findNavController().navigate(R.id.action_HistoryFragment_to_FirstFragment, bundle)
                        },
                        onDeleteClick = { checkIn ->
                            showDeleteConfirmation(checkIn)
                        },
                        onShareClick = { checkIn ->
                            shareCheckIn(checkIn)
                        }
                    )

                    binding.buttonShareAll.setOnClickListener {
                        shareAllHistory(checkIns)
                    }
                }
            }
        }
    }

    private fun shareCheckIn(checkIn: CheckIn) {
        val shareText = """
            Faster Scale Check-in
            Date: ${checkIn.date}
            Level: ${checkIn.scaleOption}
            Notes: ${checkIn.description}
        """.trimIndent()

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun shareAllHistory(checkIns: List<CheckIn>) {
        if (checkIns.isEmpty()) return

        val report = StringBuilder("Faster Scale Recovery - Full History\n\n")
        checkIns.forEach { checkIn ->
            report.append("Date: ${checkIn.date}\n")
            report.append("Level: ${checkIn.scaleOption}\n")
            if (checkIn.description.isNotBlank()) {
                report.append("Notes: ${checkIn.description}\n")
            }
            report.append("-------------------\n")
        }

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, report.toString())
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Share History Report")
        startActivity(shareIntent)
    }

    private fun showDeleteConfirmation(checkIn: CheckIn) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirmation_title)
            .setMessage(getString(R.string.delete_confirmation_message, checkIn.date))
            .setPositiveButton(R.string.menu_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(requireContext())
                    db.checkInDao().deleteCheckIn(checkIn)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
