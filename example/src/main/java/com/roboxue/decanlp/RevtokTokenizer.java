package com.roboxue.decanlp;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RevtokTokenizer {

    static final String SPACE = " ";
    static final int SPACE_INT = SPACE.codePointAt(0);
    static final char[] CAP = Character.toChars("\ue302".codePointAt(0));
    static final int CAP_INT = "\ue302".codePointAt(0);
    static final String CAP_SPACE = "\ue302 ";
    static final String SPACE_CAP = " \ue302";
    static final String EMPTY = "";

    private static void startNewToken(Deque<List<Integer>> tokens, int codePoint) {
        tokens.offer(Lists.newArrayList(codePoint));
    }

    private static void startNewTokenWithSpace(Deque<List<Integer>> tokens) {
        tokens.offer(Lists.newArrayList(SPACE_INT));
    }

    private static void appendToCurrentToken(Deque<List<Integer>> tokens, int codePoint) {
        tokens.getLast().add(codePoint);
    }

    private static void sealCurrentToken(Deque<List<Integer>> tokens) {
        tokens.getLast().add(SPACE_INT);
    }

    /**
     * Tokenize
     * @param message
     * @param deCapitalize
     * @param splitPunctuation
     * @return
     */
    public static List<String> tokenize(String message, boolean deCapitalize, boolean splitPunctuation) {
        Integer currentTokenPriority = 0;
        Deque<List<Integer>> toks = new LinkedList<>();
        toks.offer(new ArrayList<>());

        for (Integer unicodeChar : message.codePoints().toArray()) {
            int characterPriority = getCharacterPriority(unicodeChar);
            // split by space
            if (unicodeChar == SPACE_INT) {
                sealCurrentToken(toks);
                startNewTokenWithSpace(toks);
                currentTokenPriority = null;
                continue;
                // make sure the next non-space char starts a new token
            } else if (Objects.isNull(currentTokenPriority)) {
                appendToCurrentToken(toks, unicodeChar);
                // continuous symbols and numbers are treated as part of the token (category > 2) unless splitPunctuation
            } else if (characterPriority == currentTokenPriority && (characterPriority > 2 || !splitPunctuation)) {
                appendToCurrentToken(toks, unicodeChar);
                // control char and separators breaks the token down
            } else if (characterPriority <= 0 && currentTokenPriority <= 0) {
                startNewToken(toks, unicodeChar);
                // lower category char treated as the beginning of a new token
            } else if (characterPriority <= currentTokenPriority) {
                sealCurrentToken(toks);
                startNewToken(toks, unicodeChar);
            } else {
                startNewTokenWithSpace(toks);
                appendToCurrentToken(toks, unicodeChar);
            }
            currentTokenPriority = characterPriority;
        }
        if (toks.peekFirst().size() == 0) {
            toks.removeFirst();
        }
        if (Objects.nonNull(currentTokenPriority) && currentTokenPriority > 0) {
            toks.getLast().add(SPACE_INT);
        }
        if (deCapitalize) {
            return toks.stream().map(RevtokTokenizer::decapital).collect(Collectors.toList());
        } else {
            return toks.stream().map(token -> {
                StringBuilder result = new StringBuilder();
                token.forEach(c -> result.append(Character.toChars(c)));
                return result.toString().intern();
            }).collect(Collectors.toList());
        }
    }

    public static String detokenize(List<String> tokens) {
        int[] codePoints = String.join("", tokens).replace(CAP_SPACE, SPACE_CAP).codePoints().toArray();
        boolean spaceSkipped = false;
        boolean capSeen = false;
        StringBuilder sb = new StringBuilder();
        for (int codePoint : codePoints) {
            // PYTHON: re.sub(SPACE_INT + '+', lambda s: ' ' * (len(s.group(0)) // 2), text)
            if (codePoint == SPACE_INT) {
                if (spaceSkipped) {
                    sb.append(SPACE);
                }
                spaceSkipped = !spaceSkipped;
                continue;
            } else {
                spaceSkipped = false;
            }

            // PYTHON: re.sub(CAP + '.', lambda s: s.group(0)[-1].upper(), text, flags=re.S)
            if (capSeen) {
                sb.append(Character.toChars(Character.toUpperCase(codePoint)));
                capSeen = false;
            } else {
                if (codePoint == CAP_INT) {
                    capSeen = true;
                } else {
                    sb.append(Character.toChars(codePoint));
                }
            }
        }
        return sb.toString();
    }

    private static int getCharacterPriority(int character) {
        switch (Character.getType(character)) {
            //L
            case Character.UPPERCASE_LETTER: // Lu
            case Character.LOWERCASE_LETTER: // Ll
            case Character.TITLECASE_LETTER: // Lt
            case Character.MODIFIER_LETTER: // Lm
            case Character.OTHER_LETTER: // Lo
                return 7;
            //M
            case Character.NON_SPACING_MARK: // Mn
            case Character.ENCLOSING_MARK: // Me
            case Character.COMBINING_SPACING_MARK: // Mc
                return 7;
            // N
            case Character.DECIMAL_DIGIT_NUMBER: // Nd
            case Character.LETTER_NUMBER: // Nl
            case Character.OTHER_NUMBER: // No
                return 5;
            // S
            case Character.MATH_SYMBOL: // Sm
            case Character.CURRENCY_SYMBOL: // Sc
            case Character.MODIFIER_SYMBOL: // Sk
            case Character.OTHER_SYMBOL: // So
                return 3;
            // P
            case Character.DASH_PUNCTUATION: // Pd
            case Character.START_PUNCTUATION: // Ps
            case Character.END_PUNCTUATION: // Pe
            case Character.CONNECTOR_PUNCTUATION: // Pc
            case Character.OTHER_PUNCTUATION: // Po
            case Character.INITIAL_QUOTE_PUNCTUATION: // Pi
            case Character.FINAL_QUOTE_PUNCTUATION: // Pf
                return 1;
            // Z
            case Character.SPACE_SEPARATOR: // Zs
            case Character.LINE_SEPARATOR: // Zl
            case Character.PARAGRAPH_SEPARATOR: // Zp
                return -1;
            // C
            case Character.UNASSIGNED: // Cn
            case Character.CONTROL: // Cc
            case Character.FORMAT: // Cf
            case Character.PRIVATE_USE: // Co
            case Character.SURROGATE: // Cs
                return -3;
            default: // Should not reach here
                return -3;
        }
    }

    private static String decapital(List<Integer> token) {
        if (token.size() == 0) {
            return EMPTY;
        }
        StringBuilder result = new StringBuilder();
        boolean pre = false;
        if (token.get(0) == SPACE_INT) {
            pre = true;
            token.remove(0);
        }
        if (Character.isUpperCase(token.get(0)) &&
                (token.size() == 1 || !Character.isUpperCase(token.get(1)))) {
            result.append(CAP);
            if (pre) {
                result.append(SPACE);
            }
            result.append(Character.toChars(Character.toLowerCase(token.remove(0))));
            token.forEach(c -> result.append(Character.toChars(c)));
            return result.toString();
        }

        if (pre) {
            result.append(SPACE);
        }
        token.forEach(c -> result.append(Character.toChars(c)));
        return result.toString();
    }
}
