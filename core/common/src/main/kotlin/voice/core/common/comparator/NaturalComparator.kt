/*
 * This file contains code copied or adapted from:
 * https://github.com/JetBrains/intellij-community/blob/fc5900ddec25d8b603f315ee92c7538f29b32a8f/platform/util/base/src/com/intellij/openapi/util/text/NaturalComparator.java#L16
 * Original License: Apache V2
 *
 * Modifications:
 * Copyright (c) 2026 Paul Woitaschek
 *
 * This file is distributed as part of Voice under the GNU GPL v3.
 */
package voice.core.common.comparator

import java.text.Collator
import java.util.Locale

internal object NaturalComparator : Comparator<String> {
  private val collatorThreadLocal = ThreadLocal.withInitial {
    Collator.getInstance(Locale.ROOT).apply {
      strength = Collator.PRIMARY
    }
  }

  override fun compare(
    s1: String,
    s2: String,
  ): Int {
    if (s1 == s2) {
      return 0
    }
    return with(collatorThreadLocal.get()!!) {
      naturalCompare(s1 = s1, s2 = s2, length1 = s1.length, length2 = s2.length, ignoreCase = true, likeFileNames = true)
    }
  }

  private fun isDecimalDigit(c: Char) = c in '0'..'9'

  context(collator: Collator)
  private fun naturalCompare(
    s1: String,
    s2: String,
    length1: Int,
    length2: Int,
    ignoreCase: Boolean,
    likeFileNames: Boolean,
  ): Int {
    var i = 0
    var j = 0
    while (i < length1 && j < length2) {
      val ch1 = s1[i]
      val ch2 = s2[j]
      if ((isDecimalDigit(ch1) || ch1 == ' ') && (isDecimalDigit(ch2) || ch2 == ' ')) {
        val start1 = skipChar(s1, skipChar(s1, i, length1, ' '), length1, '0')
        val start2 = skipChar(s2, skipChar(s2, j, length2, ' '), length2, '0')

        val end1 = skipDigits(s1, start1, length1)
        val end2 = skipDigits(s2, start2, length2)

        // numbers with more digits are always greater than shorter numbers
        val lengthDiff = (end1 - start1) - (end2 - start2)
        if (lengthDiff != 0) return lengthDiff

        // compare numbers with equal digit count
        val numberDiff = compareCharRange(s1, s2, start1, start2, end1)
        if (numberDiff != 0) return numberDiff

        // compare number length including leading spaces and zeroes
        val fullLengthDiff = (end1 - i) - (end2 - j)
        if (fullLengthDiff != 0) return fullLengthDiff

        // the numbers are the same; compare leading spaces and zeroes
        val leadingDiff = compareCharRange(s1, s2, i, j, start1)
        if (leadingDiff != 0) return leadingDiff

        i = end1 - 1
        j = end2 - 1
      } else {
        // only reached when the two sides are not both plain digits, so this is where
        // numbers written with chinese numerals (第一卷, 第二卷, …) are handled
        val number1 = readNumber(s1, i, length1)
        val number2 = readNumber(s2, j, length2)
        if (number1 != null && number2 != null) {
          val valueDiff = number1.value.compareTo(number2.value)
          if (valueDiff != 0) return valueDiff

          // same value, so the longer (more verbose) spelling wins
          val spellingDiff = number1.length - number2.length
          if (spellingDiff != 0) return spellingDiff

          i += number1.length
          j += number2.length
          continue
        }

        if (likeFileNames) {
          // for super natural comparison (IDEA-80435)
          if (ch1 != ch2) {
            val diff: Int = when {
              ch1 == '-' && ch2 != '_' -> {
                compareChars('_', ch2)
              }
              ch2 == '-' && ch1 != '_' -> {
                compareChars(ch1, '_')
              }
              else -> {
                compareChars(ch1, ch2)
              }
            }
            if (diff != 0) return diff
          }
        } else {
          val diff = compareChars(ch1, ch2)
          if (diff != 0) return diff
        }
      }
      i++
      j++
    }
    // After the loop, the end of one of the strings might not have been reached if the other
    // string ends with a number and the strings are equal until the end of that number.
    // When there are more characters in the string, then it is greater.
    if (i < length1) return +1
    if (j < length2) return -1
    if (length1 != length2) return length1 - length2

    // do case-sensitive compare if case-insensitive strings are equal
    return if (ignoreCase) naturalCompare(s1, s2, length1, length2, false, likeFileNames) else 0
  }

  /**
   * Reads the number that starts at [start]. Supports decimal digits as well as chinese
   * numerals so 第一卷 sorts before 第二卷 which sorts before 第十卷.
   */
  private fun readNumber(
    s: String,
    start: Int,
    length: Int,
  ): NumberToken? {
    if (start >= length) return null
    return if (isDecimalDigit(s[start])) {
      readDecimalNumber(s, start, length)
    } else {
      readChineseNumber(s, start, length)
    }
  }

  private fun readDecimalNumber(
    s: String,
    start: Int,
    length: Int,
  ): NumberToken? {
    var index = start
    // leading zeroes don't change the value
    while (index < length && s[index] == '0') index++
    val significantStart = index
    var value = 0L
    while (index < length && isDecimalDigit(s[index])) {
      value = value * 10 + (s[index] - '0')
      index++
    }
    // numbers that long are out of scope for file names, let the character comparison handle them
    if (index - significantStart > MAX_DECIMAL_DIGITS) return null
    return NumberToken(value, index - start)
  }

  private fun readChineseNumber(
    s: String,
    start: Int,
    length: Int,
  ): NumberToken? {
    val end = minOf(length, start + MAX_CHINESE_NUMERALS_LENGTH)
    var index = start
    var total = 0L
    var section = 0L
    var digit = -1L
    var readAny = false
    while (index < end) {
      val char = s[index]
      val value = chineseDigit(char)
      if (value >= 0) {
        digit = value
        index++
        readAny = true
        continue
      }
      val unit = chineseSmallUnit(char)
      if (unit > 0) {
        // 十五 reads as 15, so a missing digit defaults to one
        section = saturatingAdd(section, (if (digit < 0) 1L else digit) * unit)
        digit = -1L
        index++
        readAny = true
        continue
      }
      val largeUnit = chineseLargeUnit(char)
      if (largeUnit > 0) {
        section = saturatingAdd(section, if (digit < 0) 0L else digit)
        total = saturatingAdd(total, section * largeUnit)
        section = 0L
        digit = -1L
        index++
        readAny = true
        continue
      }
      break
    }
    if (!readAny) return null
    val value = saturatingAdd(total, saturatingAdd(section, if (digit < 0) 0L else digit))
    return NumberToken(value, index - start)
  }

  private fun saturatingAdd(
    lhs: Long,
    rhs: Long,
  ): Long {
    val sum = lhs + rhs
    return if (sum < lhs) Long.MAX_VALUE else sum
  }

  private fun chineseDigit(char: Char): Long = when (char) {
    '零', '〇' -> 0L
    '一', '壹' -> 1L
    '二', '两', '贰' -> 2L
    '三', '叁' -> 3L
    '四', '肆' -> 4L
    '五', '伍' -> 5L
    '六', '陆' -> 6L
    '七', '柒' -> 7L
    '八', '捌' -> 8L
    '九', '玖' -> 9L
    else -> -1L
  }

  private fun chineseSmallUnit(char: Char): Long = when (char) {
    '十', '拾' -> 10L
    '百', '佰' -> 100L
    '千', '仟' -> 1000L
    else -> -1L
  }

  private fun chineseLargeUnit(char: Char): Long = when (char) {
    '万', '萬' -> 10_000L
    '亿', '億' -> 100_000_000L
    else -> -1L
  }

  private class NumberToken(val value: Long, val length: Int)

  private fun compareCharRange(
    s1: String,
    s2: String,
    offset1: Int,
    offset2: Int,
    end1: Int,
  ): Int {
    var i = offset1
    var j = offset2
    while (i < end1) {
      val diff = s1[i].code - s2[j].code
      if (diff != 0) return diff
      i++
      j++
    }
    return 0
  }

  context(collator: Collator)
  private fun compareChars(
    lhs: Char,
    rhs: Char,
  ): Int {
    if (lhs.isLetter() && rhs.isLetter()) {
      val collatorDiff = collator.compare(lhs.toString(), rhs.toString())
      if (collatorDiff != 0) {
        return collatorDiff
      }
    }

    val diff1 = lhs.uppercaseChar() - rhs.uppercaseChar()
    if (diff1 != 0) {
      val diff2 = lhs.lowercaseChar() - rhs.lowercaseChar()
      if (diff2 != 0) {
        return diff2
      }
    }
    return 0
  }

  private fun skipDigits(
    s: String,
    start: Int,
    end: Int,
  ): Int {
    var start = start
    while (start < end && isDecimalDigit(s[start])) start++
    return start
  }

  private fun skipChar(
    s: String,
    start: Int,
    end: Int,
    c: Char,
  ): Int {
    var start = start
    while (start < end && s[start] == c) start++
    return start
  }

  private const val MAX_DECIMAL_DIGITS = 18
  private const val MAX_CHINESE_NUMERALS_LENGTH = 24
}
