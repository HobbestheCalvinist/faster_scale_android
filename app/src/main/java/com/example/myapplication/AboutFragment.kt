package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentAboutBinding
import java.text.SimpleDateFormat
import java.util.*

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAboutSection()
    }

    private fun setupAboutSection() {
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val version = pInfo.versionName
            val lastUpdateTime = pInfo.lastUpdateTime
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateString = sdf.format(Date(lastUpdateTime))
            binding.textviewVersion.text = "Version $version (Build: $dateString)"
        } catch (e: Exception) {
            binding.textviewVersion.text = "Version 1.0"
        }

        binding.buttonEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:HobbestheCalvinist@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "App Feedback")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonCoffee.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/hobbescalvinist"))
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
