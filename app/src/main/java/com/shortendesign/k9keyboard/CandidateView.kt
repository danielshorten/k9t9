package com.shortendesign.k9keyboard

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * This is a dummy, invisible candidate view
 */

class CandidateView
    (context: Context) : LinearLayout(context) {

    private var candidateView: View? = null

    init {
        candidateView = View.inflate(context, R.layout.invisible, this)
    }
}
