package pe.ayni.skills.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How alike two names of skills are, from 0 (nothing in common) to 1 (the same name).
 *
 * <p>A student who does not find a tool types the name they know it by, and the catalogue may hold
 * it under another spelling: "Node.js" and "NodeJS", "Postgre SQL" and "PostgreSQL", "Phyton" and
 * "Python", "Programación en Python" and "Python". The comparison ignores case, accents,
 * punctuation and spaces, forgives a typo, and sees one name inside another. It cannot know that two
 * unrelated words mean the same thing, so a real synonym is for the student to catch in the list.
 */
final class NameSimilarity {

  /** From this score on, two names are shown to the student as possibly the same skill. */
  static final double THRESHOLD = 0.7;

  /** Words that carry nothing in a name: "Diseño de interfaces" and "Diseño interfaces". */
  private static final Set<String> FILLER =
      Set.of("de", "del", "la", "el", "los", "las", "en", "con", "para", "y", "a", "of", "the", "and", "to", "in");

  /** What a word with a typo, or the stem of another, is worth next to an identical word. */
  private static final double LOOSE_WORD = 0.85;

  private NameSimilarity() {}

  static double between(String first, String second) {
    String a = normalise(first);
    String b = normalise(second);
    String compactA = a.replace(" ", "");
    String compactB = b.replace(" ", "");
    if (compactA.isEmpty() || compactB.isEmpty()) {
      return 0;
    }
    if (compactA.equals(compactB)) {
      return 1;
    }
    return Math.max(
        Math.max(containment(compactA, compactB), editSimilarity(compactA, compactB)),
        sharedWords(a, b));
  }

  static boolean areAlike(String first, String second) {
    return between(first, second) >= THRESHOLD;
  }

  /** Lower case, no accents, and symbols that mean something in a tool's name spelled out. */
  static String normalise(String name) {
    if (name == null) {
      return "";
    }
    String spelled = name.replace("++", "plusplus").replace("+", "plus").replace("#", "sharp");
    String plain =
        Normalizer.normalize(spelled, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .strip();
    return plain;
  }

  /** One name inside the other, like "Python" in "Programming in Python". Short names do not count. */
  private static double containment(String a, String b) {
    String shorter = a.length() <= b.length() ? a : b;
    String longer = shorter == a ? b : a;
    return shorter.length() >= 4 && longer.contains(shorter) ? 0.85 : 0;
  }

  /** One minus the share of letters that would have to change, so one typo in six is 0.83. */
  private static double editSimilarity(String a, String b) {
    int longest = Math.max(a.length(), b.length());
    if (longest < 4) {
      return 0;
    }
    return 1.0 - (double) distance(a, b) / longest;
  }

  /** The share of words the two names have in common, counting a word with a typo as the same. */
  private static double sharedWords(String a, String b) {
    List<String> wordsA = words(a);
    List<String> wordsB = words(b);
    if (wordsA.isEmpty() || wordsB.isEmpty()) {
      return 0;
    }
    List<String> remaining = new ArrayList<>(wordsB);
    double shared = 0;
    for (String word : wordsA) {
      for (int i = 0; i < remaining.size(); i++) {
        if (word.equals(remaining.get(i))) {
          remaining.remove(i);
          shared += 1;
          break;
        }
        if (sameWord(word, remaining.get(i))) {
          remaining.remove(i);
          shared += LOOSE_WORD;
          break;
        }
      }
    }
    return 2.0 * shared / (wordsA.size() + wordsB.size());
  }

  private static List<String> words(String name) {
    List<String> words = new ArrayList<>();
    for (String word : name.split(" ")) {
      if (!word.isEmpty() && !FILLER.contains(word)) {
        words.add(word);
      }
    }
    return words;
  }

  /** Equal, one typo apart in a long word, or one the stem of the other ("interfaz", "interfaces"). */
  private static boolean sameWord(String a, String b) {
    if (a.equals(b)) {
      return true;
    }
    int shorter = Math.min(a.length(), b.length());
    if (shorter < 5) {
      return false;
    }
    if (distance(a, b) <= 1) {
      return true;
    }
    int prefix = 0;
    while (prefix < shorter && a.charAt(prefix) == b.charAt(prefix)) {
      prefix++;
    }
    return prefix >= 5 && prefix >= 0.8 * shorter;
  }

  /** Edits to turn one into the other, where swapping two neighbouring letters is one edit. */
  private static int distance(String a, String b) {
    int[][] d = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) {
      d[i][0] = i;
    }
    for (int j = 0; j <= b.length(); j++) {
      d[0][j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
        if (i > 1
            && j > 1
            && a.charAt(i - 1) == b.charAt(j - 2)
            && a.charAt(i - 2) == b.charAt(j - 1)) {
          d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
      }
    }
    return d[a.length()][b.length()];
  }
}
