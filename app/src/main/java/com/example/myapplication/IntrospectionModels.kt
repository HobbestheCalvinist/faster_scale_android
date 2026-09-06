package com.fasterscale.app

import android.content.Context

/**
 * Metadata for a level on the FASTER scale.
 * Pulls strings from resources to ensure easy configuration.
 */
data class FasterScaleLevel(
    val id: String,
    val title: String,
    val letter: String,
    val description: String,
    val behaviors: List<String>
)

/**
 * Metadata for a feeling.
 * Maps an emoji to potential levels on the scale.
 */
data class IntrospectionFeeling(
    val emoji: String,
    val label: String,
    val potentialLevels: List<String> // IDs of FasterScaleLevel
)

object FasterScaleProvider {
    
    /**
     * Loads the scale levels and behaviors from strings.xml.
     */
    fun getLevels(context: Context): List<FasterScaleLevel> {
        return listOf(
            FasterScaleLevel("restoration", context.getString(R.string.scale_restoration), "R", context.getString(R.string.desc_restoration), context.resources.getStringArray(R.array.behaviors_restoration).toList()),
            FasterScaleLevel("forgetting_priorities", context.getString(R.string.scale_forgetting), "F", context.getString(R.string.desc_forgetting), context.resources.getStringArray(R.array.behaviors_forgetting).toList()),
            FasterScaleLevel("anxiety", context.getString(R.string.scale_anxiety), "A", context.getString(R.string.desc_anxiety), context.resources.getStringArray(R.array.behaviors_anxiety).toList()),
            FasterScaleLevel("speeding_up", context.getString(R.string.scale_speeding), "S", context.getString(R.string.desc_speeding), context.resources.getStringArray(R.array.behaviors_speeding).toList()),
            FasterScaleLevel("ticked_off", context.getString(R.string.scale_ticked_off), "T", context.getString(R.string.desc_ticked_off), context.resources.getStringArray(R.array.behaviors_ticked_off).toList()),
            FasterScaleLevel("exhausted", context.getString(R.string.scale_exhausted), "E", context.getString(R.string.desc_exhausted), context.resources.getStringArray(R.array.behaviors_exhausted).toList()),
            FasterScaleLevel("relapse", context.getString(R.string.scale_relapse), "R", context.getString(R.string.desc_relapse), context.resources.getStringArray(R.array.behaviors_relapse).toList())
        )
    }

    /**
     * Centralized feelings list. Easy to extend or move to a JSON/Remote config later.
     */
    fun getFeelings(): List<IntrospectionFeeling> {
        return listOf(
            // Restoration (Healthy)
            IntrospectionFeeling("😊", "Happy", listOf("restoration")),
            IntrospectionFeeling("😌", "Peaceful", listOf("restoration")),
            IntrospectionFeeling("🙏", "Grateful", listOf("restoration")),
            
            // Ambiguous (Can be multiple letters)
            IntrospectionFeeling("😎", "Confident", listOf("restoration", "forgetting_priorities")),
            IntrospectionFeeling("🥳", "Excited", listOf("restoration", "speeding_up")),
            IntrospectionFeeling("😴", "Tired", listOf("exhausted", "speeding_up")),
            IntrospectionFeeling("😢", "Sad", listOf("anxiety", "exhausted")),
            IntrospectionFeeling("🏃", "Busy", listOf("speeding_up", "forgetting_priorities")),
            IntrospectionFeeling("🤥", "Dishonest", listOf("forgetting_priorities", "relapse")),
            
            // Danger Zone
            IntrospectionFeeling("😰", "Scared", listOf("anxiety")),
            IntrospectionFeeling("😠", "Mad", listOf("ticked_off")),
            IntrospectionFeeling("🤢", "Sick", listOf("exhausted")),
            IntrospectionFeeling("😶", "Isolated", listOf("exhausted", "relapse")),
            IntrospectionFeeling("🔒", "Trapped", listOf("relapse"))
        )
    }
}
