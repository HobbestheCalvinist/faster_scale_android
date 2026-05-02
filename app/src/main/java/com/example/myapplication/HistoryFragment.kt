package com.example.myapplication

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
                    _binding?.recyclerviewHistory?.adapter = HistoryAdapter(
                        checkIns = checkIns,
                        onEditClick = { checkIn ->
                            val bundle = Bundle().apply {
                                putString("selectedDate", checkIn.date)
                            }
                            findNavController().navigate(R.id.action_HistoryFragment_to_FirstFragment, bundle)
                        },
                        onDeleteClick = { checkIn ->
                            showDeleteConfirmation(checkIn)
                        }
                    )
                }
            }
        }
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
