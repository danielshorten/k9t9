/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shortendesign.k9keyboard

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Construct a CandidateView for showing suggested words for completion.
 *
 */

class CandidateView
    (context: Context) : LinearLayout(context) {

    private var suggestions: List<String>? = null
    private var candidateView: View? = null

    init {
        candidateView = View.inflate(context, R.layout.layout_candidate, this)
    }

    /**
     * A connection back to the service to communicate with the text field
     * @param listener
     */
    fun setService(listener: K9InputMethodServiceImpl) {
    }

    fun setSuggestions(suggestions: List<String>) {
        clear()
        updatePredictions(suggestions)
        invalidate()
        requestLayout()
    }

    private fun clear() {
        suggestions = emptyList()
        invalidate()
    }

    // TODO Refactor this method
    private fun updatePredictions(prediction: List<String>) {
        val first_prediction: TextView = findViewById(R.id.first_prediction)
        first_prediction.text = if (prediction.isNotEmpty()) prediction[0] else ""

        val second_prediction: TextView = findViewById(R.id.second_prediction)
        second_prediction.text = if (prediction.size > 1) prediction[1] else ""

        val third_prediction: TextView = findViewById(R.id.third_prediction)
        third_prediction.text = if (prediction.size > 2) prediction[2] else ""
    }
}