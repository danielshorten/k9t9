package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.trie.Node
import com.shortendesign.k9keyboard.trie.T9Trie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ArrayBlockingQueue

class WordResolver {

    private lateinit var scope: CoroutineScope

    // Trie for loading and searching for words by key
    private val t9Trie = T9Trie()
    private var isTrieInitialized = false
    // current trie preloading job
    private var preloadJob: Job? = null
    // Keep track of recently preloaded keys to reduce unnecessary loading
    private val preloadsToCache = 3
    private val lastNPreloads = ArrayBlockingQueue<String>(preloadsToCache)
    private var retryCodeWordAfterPreload: String? = null

    fun init() {
        if (this.t9Trie.root == null) {
            preloadJob?.cancel()
            preloadJob = scope.launch {
                initT9Trie()
            }
        }

    }

    fun resolveCodeWord(codeWord: String, final: Boolean = false,
                        recomposingResult: KeyPressResult? = null) {
        val candidates = t9Trie.getCandidates(codeWord, maxLength = codeWord.length)
//        Log.d(LOG_TAG, "CANDIDATES for $codeWord: $candidates")
        // Special case: if the new codeword is one character shorter than the last-seen composing
        // text, assume we're deleting
        val composingText = lastComposingText
        val deleteResult = if (composingText?.length?.minus(codeWord.length) == 1)
            composingText.substring(0, composingText.length - 1)
        else null
        val resetToWord = when {
            // If we're beginning to recompose, reset to the initial word
            recomposingResult != null -> recomposingResult.word
            // If we're deleting, force the last text with one character truncated
            deleteResult != null -> deleteResult
            else -> null
        }
        val candidate = mode!!.resolveCodeWord(codeWord, candidates, final, resetToWord)
//        Log.d(LOG_TAG, "CANDIDATE: $candidate")

        // Initial pass
        if (!final) {
            // Ensure our T9 trie is primed with candidates for the current code word prefix
            // (it gets cleared periodically to free up memory, so needs to be reprimed).
            // This will also retry resolving the candidate if the above attempt returned null.
            preloadTrie(
                codeWord, 2,
                retryCandidates = candidate == null,
                recomposingResult = recomposingResult
            )
        }
        if (deleteResult != null) {
            setComposingText(deleteResult)
        }
        // If we can resolve a candidate, set the composing text
        // (except if we're beginning to recompose)
        else if (candidate != null && recomposingResult == null) {
            setComposingText(candidate)
        }
    }

    private fun preloadTrie(key: String, minKeyLength: Int = 0, retryCandidates: Boolean = false,
                            recomposingResult: KeyPressResult? = null) {
        // Only start a preload job if there isn't one active
        if (preloadJob == null || !preloadJob!!.isActive) {
            preloadJob = scope.launch {
                doPreloadTrie(key, minKeyLength, 200, retryCandidates = retryCandidates,
                    recomposingResult=recomposingResult)
            }
        } else {
            // If there's a job active but we wanted to retry, store that info
            retryCodeWordAfterPreload = key
        }
    }

    private suspend fun initT9Trie() {
        Node.setSupportedChars("123456789")
        t9Trie.clear()
        for (char in "123456789") {
            doPreloadTrie(char.toString())
        }
        isTrieInitialized = true
    }

    private suspend fun doPreloadTrie(
        key: String,
        minKeyLength: Int = 0,
        numCandidates: Int = 200,
        retryCandidates: Boolean = false,
        recomposingResult: KeyPressResult? = null
    ) {
        var skipLoad = false
        // Only preload if the key length meets the minimum threshold
        if (minKeyLength > 0 && key.length < minKeyLength) {
            skipLoad = true
        }
        // If we've preloaded for this key recently, skip
        if (lastNPreloads.contains(key)) {
            skipLoad = true
        }
        else {
            if (lastNPreloads.size == preloadsToCache) {
                lastNPreloads.remove()
            }
            lastNPreloads.add(key)
        }
        if (!skipLoad) {
            val words = wordDao.findCandidates(key, numCandidates)
            for (word in words) {
                //Log.d(LOG_TAG, "Preload trie ${word.code} => ${word.word}: ${word.frequency}")
                t9Trie.add(word.code, word.word, word.frequency)
            }
        }
        if (retryCandidates || retryCodeWordAfterPreload != null) {
//            Log.d(LOG_TAG, "Retrying resolveCodeWord")
            resolveCodeWord(retryCodeWordAfterPreload?: key, true, recomposingResult)
        }
        retryCodeWordAfterPreload = null
        //t9Trie.prune(key, depth = 3)
    }

}