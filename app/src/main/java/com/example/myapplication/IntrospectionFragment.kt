package com.fasterscale.app

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterscale.app.databinding.FragmentIntrospectionBinding
import com.fasterscale.app.databinding.ItemFeelingBinding
import com.fasterscale.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class IntrospectionFragment : Fragment() {

    private var _binding: FragmentIntrospectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    private var selectedFeeling: IntrospectionFeeling? = null
    private var selectedIntensity: Int = 3
    private var determinedLevel: FasterScaleLevel? = null
    private val behaviorsTally = mutableMapOf<String, Boolean>()
    
    private var customFeelingsList = listOf<CustomFeeling>()

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
        db = AppDatabase.getDatabase(requireContext())

        setupMenu()
        setupSteps()
        setupFeelingsGrid()
        setupIntensitySlider()
        setupQuestionsButtons()
        setupReviewButtons()
        setupBackButtons()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_introspection, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_reset -> {
                        showResetConfirmation()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showResetConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_reset)
            .setMessage(R.string.reset_introspection_confirmation)
            .setPositiveButton(R.string.action_reset) { _, _ ->
                resetWork()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun resetWork() {
        selectedFeeling = null
        selectedIntensity = 3
        determinedLevel = null
        behaviorsTally.clear()
        
        // Reset slider to default
        binding.intensitySlider.value = 3f
        binding.intensityValueDisplay.text = "3"
        
        setupSteps()
    }

    private fun setupSteps() {
        binding.wheelStepContainer.visibility = View.VISIBLE
        binding.intensityStepContainer.visibility = View.GONE
        binding.questionsStepContainer.visibility = View.GONE
        binding.reviewStepContainer.visibility = View.GONE
        binding.sectionTitle.text = getString(R.string.how_are_you_feeling)
    }

    private fun setupFeelingsGrid() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.customFeelingDao().getAllCustomFeelings().collect { customFeelings ->
                customFeelingsList = customFeelings
                val defaultFeelings = FasterScaleProvider.getFeelings()
                val convertedCustom = customFeelings.map { 
                    IntrospectionFeeling(it.emoji, it.label, listOf(it.levelId))
                }
                
                val allFeelings = defaultFeelings + convertedCustom
                
                val adapter = FeelingsAdapter(allFeelings) { feeling ->
                    selectedFeeling = feeling
                    transitionToIntensityStep()
                }
                binding.feelingsGrid.layoutManager = GridLayoutManager(requireContext(), 4)
                binding.feelingsGrid.adapter = adapter
            }
        }

        binding.buttonAddFeeling.setOnClickListener {
            showAddFeelingDialog()
        }
    }

    private fun showAddFeelingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_feeling, null)
        val emojiEdit = dialogView.findViewById<EditText>(R.id.edit_emoji)
        val meaningEdit = dialogView.findViewById<EditText>(R.id.edit_meaning)
        val levelSpinner = dialogView.findViewById<Spinner>(R.id.spinner_level)

        val levels = FasterScaleProvider.getLevels(requireContext())
        val levelTitles = levels.map { it.title }
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, levelTitles)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        levelSpinner.adapter = spinnerAdapter

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_custom_feeling)
            .setView(dialogView)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val emoji = emojiEdit.text.toString().trim()
                val label = meaningEdit.text.toString().trim()
                val levelIndex = levelSpinner.selectedItemPosition
                
                if (emoji.isNotEmpty() && label.isNotEmpty() && levelIndex >= 0) {
                    val selectedLevelId = levels[levelIndex].id
                    val customFeeling = CustomFeeling(emoji = emoji, label = label, levelId = selectedLevelId)
                    
                    viewLifecycleOwner.lifecycleScope.launch {
                        db.customFeelingDao().insertCustomFeeling(customFeeling)
                        selectedFeeling = IntrospectionFeeling(emoji, label, listOf(selectedLevelId))
                        transitionToIntensityStep()
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun transitionToIntensityStep() {
        binding.wheelStepContainer.visibility = View.GONE
        binding.intensityStepContainer.visibility = View.VISIBLE
        binding.questionsStepContainer.visibility = View.GONE
        binding.reviewStepContainer.visibility = View.GONE
        binding.sectionTitle.text = getString(R.string.how_strongly_feeling)
        
        binding.selectedFeelingEmoji.text = selectedFeeling?.emoji
        binding.selectedFeelingLabel.text = selectedFeeling?.label
    }

    private fun setupIntensitySlider() {
        binding.intensitySlider.addOnChangeListener { _, value, _ ->
            selectedIntensity = value.toInt()
            binding.intensityValueDisplay.text = selectedIntensity.toString()
        }

        binding.buttonIntensityDone.setOnClickListener {
            val feeling = selectedFeeling ?: return@setOnClickListener
            // Transition to questions if multiple levels OR if there are custom behaviors for the level
            val allLevels = FasterScaleProvider.getLevels(requireContext())
            val hasCustomBehaviors = customFeelingsList.any { feeling.potentialLevels.contains(it.levelId) }

            if (feeling.potentialLevels.size > 1 || hasCustomBehaviors) {
                transitionToQuestionsStep()
            } else {
                determinedLevel = allLevels.find { it.id == feeling.potentialLevels.first() }
                transitionToReviewStep()
            }
        }
    }

    private fun transitionToQuestionsStep() {
        binding.wheelStepContainer.visibility = View.GONE
        binding.intensityStepContainer.visibility = View.GONE
        binding.questionsStepContainer.visibility = View.VISIBLE
        binding.reviewStepContainer.visibility = View.GONE
        binding.sectionTitle.text = getString(R.string.label_behaviors_header)
        
        binding.questionsList.removeAllViews()
        
        val feeling = selectedFeeling ?: return
        val allLevels = FasterScaleProvider.getLevels(requireContext())
        val potentialLevels = allLevels.filter { feeling.potentialLevels.contains(it.id) }
        
        // Base behaviors from strings.xml
        val baseBehaviors = potentialLevels.flatMap { it.behaviors }
        
        // Custom behaviors from user-added feelings associated with these levels
        val customBehaviors = customFeelingsList
            .filter { feeling.potentialLevels.contains(it.levelId) }
            .map { it.label }
            
        val allBehaviors = (baseBehaviors + customBehaviors).distinct()

        allBehaviors.forEach { behavior ->
            val checkBox = CheckBox(requireContext()).apply {
                text = behavior
                isChecked = behaviorsTally[behavior] == true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
                setOnCheckedChangeListener { _, isChecked ->
                    behaviorsTally[behavior] = isChecked
                    updateLevelCounters()
                }
            }
            binding.questionsList.addView(checkBox)
        }
        
        updateLevelCounters()
    }

    private fun updateLevelCounters() {
        val allLevels = FasterScaleProvider.getLevels(requireContext())
        val feeling = selectedFeeling ?: return
        val potentialLevels = allLevels.filter { feeling.potentialLevels.contains(it.id) }

        val summary = potentialLevels.mapNotNull { level ->
            val baseCount = level.behaviors.count { behaviorsTally[it] == true }
            val customCount = customFeelingsList
                .filter { it.levelId == level.id }
                .count { behaviorsTally[it.label] == true }
            
            val totalCount = baseCount + customCount
            if (totalCount > 0) "${level.title}: $totalCount" else null
        }.joinToString("   |   ")

        binding.textviewLevelCounters.text = if (summary.isNotEmpty()) summary else "No behaviors selected"
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
            val baseCount = level.behaviors.count { behaviorsTally[it] == true }
            val customCount = customFeelingsList
                .filter { it.levelId == level.id }
                .count { behaviorsTally[it.label] == true }
            val checkedCount = baseCount + customCount
            
            if (checkedCount > maxChecked) {
                maxChecked = checkedCount
                bestLevel = level
            }
        }
        
        determinedLevel = bestLevel ?: potentialLevels.firstOrNull() ?: allLevels.first()
    }

    private fun transitionToReviewStep() {
        binding.wheelStepContainer.visibility = View.GONE
        binding.intensityStepContainer.visibility = View.GONE
        binding.questionsStepContainer.visibility = View.GONE
        binding.reviewStepContainer.visibility = View.VISIBLE
        binding.sectionTitle.text = getString(R.string.checkin_summary)
        
        binding.summaryFeelingText.text = "${selectedFeeling?.emoji} ${selectedFeeling?.label}"
        binding.summaryIntensityText.text = "$selectedIntensity / 5"
        binding.summaryScaleText.text = determinedLevel?.title ?: ""
    }

    private fun setupReviewButtons() {
        binding.buttonChangeFeeling.setOnClickListener {
            resetWork()
        }
    }

    private fun setupBackButtons() {
        binding.buttonIntensityBack.setOnClickListener {
            setupSteps()
        }
        binding.buttonQuestionsBack.setOnClickListener {
            transitionToIntensityStep()
        }
        binding.buttonReviewBack.setOnClickListener {
            val feeling = selectedFeeling
            val hasCustomBehaviors = feeling?.let { f -> customFeelingsList.any { f.potentialLevels.contains(it.levelId) } } ?: false
            if (feeling != null && (feeling.potentialLevels.size > 1 || hasCustomBehaviors)) {
                transitionToQuestionsStep()
            } else {
                transitionToIntensityStep()
            }
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
