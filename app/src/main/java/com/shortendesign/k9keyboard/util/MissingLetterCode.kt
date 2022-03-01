package com.shortendesign.k9keyboard.util

import java.lang.Exception

/**
 * Exception to indicate that we were missing a code representation when trying to encode some word
 */
class MissingLetterCode(message: String): Exception(message) {
}