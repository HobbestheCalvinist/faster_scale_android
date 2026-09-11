package com.fasterscale.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterscale.app.databinding.FragmentIntrospectionBinding
import com.fasterscale.app.databinding.ItemFeelingBinding
import com.fasterscale.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class IntrospectionFragment : Fragment() {

    private var _binding: FragmentIntrospectionBinding? = null
    private val binding get() = _binding!!

    private var selectedFeeling: IntrospectionFeeling? = null
    private var selectedIntensity: Int = 3
    private var determinedLevel: FasterScaleLevel? = null
    private val behaviorsTally = mutableMapOf<String, Boolean>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntrospectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSteps()
        setupFeelingsGrid()
        setupIntensitySlider()
        setupQuestionsButtons()
        setupReviewButtons()
    }

    private fun setupSteps() {
        binding.wheelStepContainer.visibility = View.VISIBLE
        binding.intensityStepContainer.visibility = View.GONE
        binding.questionsStepContainer.visibility = View.GONE
        binding.reviewStepContainer.visibility = View.GONE
        binding.sectionTitle.text = getString(R.string.how_are_you_feeling)
    }

    private fun setupFeelingsGrid() {
        val feelings = FasterScaleProvider.getFeelings()
        val adapter = FeelingsAdapter(feelings) { feeling ->
            selectedFeeling = feeling
            transitionToIntensityStep()
        }
        binding.feelingsGrid.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.feelingsGrid.adapter = adapter

        binding.buttonAddFeeling.setOnClickListener {
            showAddFeelingDialog()
        }
    }

    private fun showAddFeelingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_feeling, null)
        val emojiEdit = dialogView.findViewById<EditText>(R.id.edit_emoji)
        val meaningEdit = dialogView.findViewById<EditText>(R.id.edit_meaning)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_custom_feeling)
            .setView(dialogView)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val emoji = emojiEdit.text.toString().trim()
                val meaning = meaningEdit.text.toString().trim()
                if (emoji.isNotEmpty() && meaning.isNotEmpty()) {
                    selectedFeeling = IntrospectionFeeling(emoji, meaning, listOf("restoration"))
                    transitionToIntensityStep()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun transitionToIntensityStep() {
        binding.wheelStepContainer.visibility = View.GONE
        binding.intensityStepContainer.visibility = View.VISIBLE
        binding.sectionTitle.text = getString(R.string.how_strongly_feeling)
        
        binding.selectedFeelingEmoji.text = selectedFeeling?.emoji
        binding.selectedFeelingLabel.text = selectedFeeling?.label
        
        binding.scrollView.smoothScrollTo(0, 0)
    }

    private fun setupIntensitySlider() {
        binding.intensitySlider.addOnChangeListener { _, value, _ ->
            selectedIntensity = value.toInt()
            binding.intensityValueDisplay.text = selectedIntensity.toString()
        }

        binding.buttonIntensityDone.setOnClickListener {
            val feeling = selectedFeeling ?: return@setOnClickListener
            if (feeling.potentialLevels.size > 1) {
                transitionToQuestionsStep()
            } else {
                val levels = FasterScaleProvider.getLevels(requireContext())
                determinedLevel = levels.find { it.id == feeling.potentialLevels.first() }
                transitionToReviewStep()
            }
        }
    }

    private fun transitionToQuestionsStep() {
        binding.intensityStepContainer.visibility = View.GONE
        binding.questionsStepContainer.visibility = View.VISIBLE
        binding.sectionTitle.text = getString(R.string.label_behaviors_header)
        
        binding.questionsList.removeAllViews()
        behaviorsTally.clear()

        val feeling = selectedFeeling ?: return
        val allLevels = FasterScaleProvider.getLevels(requireContext())
        val potentialLevels = allLevels.filter { feeling.potentialLevels.contains(it.id) }
        
        val allBehaviors = potentialLevels.flatMap { level -> 
            level.behaviors
        }.distinct()

        allBehaviors.forEach { behavior ->
            val checkBox = CheckBox(requireContext()).apply {
                text = behavior
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
                setOnCheckedChangeListener { _, isChecked ->
                    behaviorsTally[behavior] = isChecked
                }
            }
            binding.questionsList.addView(checkBox)
        }

        binding.scrollView.smoothScrollTo(0, 0)
    }

    private fun setupQuestionsButtons() {
        binding.buttonQuestionsDone.setOnClickListener {
            calculateDeterminedLevel()
            transitionToReviewStep()
        }
    }

    private fun calculateDeterminedLevel() {
        val feeling = selectedFeeling ?: return
        val allLevels = FasterScaleProvider.getLevels(requireContext())
        val potentialLevels = allLevels.filter { feeling.potentialLevels.contains(it.id) }
        
        var bestLevel: FasterScaleLevel? = null
        var maxChecked = -1

        potentialLevels.forEach { level ->
            val checkedCount = level.behaviors.count { behaviorsTally[it] == true }
            if (checkedCount > maxChecked) {
                maxChecked = checkedCount
                bestLevel = level
            }
        }
        
        determinedLevel = bestLevel ?: potentialLevels.firstOrNull() ?: allLevels.first()
    }

    private fun transitionToReviewStep() {
        binding.intensityStepContainer.visibility = View.GONE
        binding.questionsStepContainer.visibility = View.GONE
        binding.reviewStepContainer.visibility = View.VISIBLE
        binding.sectionTitle.text = getString(R.string.checkin_summary)
        
        binding.summaryFeelingText.text = "${selectedFeeling?.emoji} ${selectedFeeling?.label}"
        binding.summaryIntensityText.text = "$selectedIntensity / 5"
        binding.summaryScaleText.text = determinedLevel?.title ?: ""
        
        binding.scrollView.smoothScrollTo(0, 0)
    }

    private fun setupReviewButtons() {
        binding.buttonChangeFeeling.setOnClickListener {
            setupSteps()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class FeelingsAdapter(
        private val items: List<IntrospectionFeeling>,
        private val onItemClick: (IntrospectionFeeling) -> Unit
    ) : RecyclerView.Adapter<FeelingsAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemFeelingBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFeelingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.feelingEmoji.text = item.emoji
            holder.binding.feelingLabel.text = item.label
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
