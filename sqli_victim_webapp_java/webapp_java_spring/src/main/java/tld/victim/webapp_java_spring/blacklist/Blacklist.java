package tld.victim.webapp_java_spring.blacklist;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class to perform blacklisting.
 */
public class Blacklist {

    private BlacklistConfDataHelper blacklistConfDataHelper = BlacklistConfDataHelper.get();

    /**
     * Delivered blacklist configuration to apply.
     */
    private final String[] blacklistConfig;
    /**
     * Log output of this class.
     */
    private BlacklistLog blacklistLog;
    /**
     * Is the query to block because of blacklisting?
     */
    private boolean queryIsBlocked = false;
    /**
     * Blacklisted query to deliver at the end.
     */
    private String[] blacklistedQuery;
    /**
     * Position of predicate in sql query.
     */
    private int predicate_pos;

    /**
     * Position of table in sql query.
     */
    private int table_pos;

    public Blacklist(String[] blacklistConfig, String[] originalQuery, int table_pos, int predicate_pos)
    {
        this.blacklistConfig = blacklistConfig;
        this.predicate_pos = predicate_pos;
        this.table_pos = table_pos;
        blacklistLog = new BlacklistLog();
        apply(originalQuery);
    }

    /**
     * Apply the blacklist.
     * @param originalQuery original query to apply blacklist on.
     */
    public void apply(String[] originalQuery) {

        blacklistedQuery = originalQuery;
        if (blacklistConfig != null)
        {
            if (confContains(blacklistConfDataHelper.CONFSTRING_HELP))
            {
                blacklistLog.addHelpScreen();
            }
            else if (noMutualExclusionsInConfig())
            {
                if (confContains(blacklistConfDataHelper.CONFSTRING_ODD_SINGLE_QUOTES))
                    blacklistedQuery = replaceOddSingleQuote(blacklistedQuery);
                if (confContains(blacklistConfDataHelper.CONFSTRING_ALL_LOWERCASE))
                    queryIsBlocked = (isAllLowerCase(blacklistedQuery) == false);
                if (confContains(blacklistConfDataHelper.CONFSTRING_ALL_UPPERCASE))
                    queryIsBlocked = (isAllUppercase(blacklistedQuery) == false);
                if (confContains(blacklistConfDataHelper.CONFSTRING_KEYWORD_DETECTION))
                    queryIsBlocked = (hasBadKeywordCombo(blacklistedQuery) == true);

                blacklistLog.addNewline();
            }
        }
    }

    public boolean confContains(String confstring_all_uppercase) {
        return Arrays.stream(blacklistConfig).anyMatch(confstring_all_uppercase::equals);
    }

    /**
     * Check if there are any blacklist configuration parameters which are mutual exclusive.
     * @return boolean true, if no mutual exlusion has been found.
     */
    private boolean noMutualExclusionsInConfig() {
        if (confContains(blacklistConfDataHelper.CONFSTRING_ALL_UPPERCASE) &&
                confContains(blacklistConfDataHelper.CONFSTRING_ALL_LOWERCASE))
        {
            blacklistLog.add("Mutual exclusion in config: " + blacklistConfDataHelper.CONFSTRING_ALL_LOWERCASE + " & " + blacklistConfDataHelper.CONFSTRING_ALL_UPPERCASE + "\n");
            return false;
        }
        return true;
    }

    /**
     * Replace a single ' with ' ' if odd number of single quotes are in the predicate part of the sql query.
     * @param originalquery query to apply on.
     * @return new blacklisted query string[] array.
     */
    public String[] replaceOddSingleQuote(String[] originalquery)
    {
        blacklistLog.addStrong(blacklistConfDataHelper.CONFSTRING_ODD_SINGLE_QUOTES);

        // make a copy of original query
        String[] newquery = Arrays.stream(originalquery).map(String::new).toArray(String[]::new);
        long count = originalquery[predicate_pos].chars().filter(ch -> ch == '\'').count();
        if (count%2 != 0) // if odd
        {
            newquery[predicate_pos] = originalquery[predicate_pos].replaceFirst("'","' '");
            // log out changes
            blacklistLog.add("Replacing:");
            blacklistLog.add(String.join("", originalquery));
            blacklistLog.add("with:");
            blacklistLog.add(String.join("", newquery) + "\n");
        }
        else
            blacklistLog.addNothingToReplace(originalquery);

        return newquery;
    }

    /**
     * Check that all characters in the query are lower case.
     * @param originalquery original query to apply blacklist on.
     * @return true, if all characters are lower case.
     */
    public boolean isAllLowerCase(String[] originalquery) {

        blacklistLog.addStrong(blacklistConfDataHelper.CONFSTRING_ALL_LOWERCASE);

        AtomicInteger pos = new AtomicInteger(0);
        AtomicReference<Character> foundchar = new AtomicReference<>((char) 0);
        boolean result = Arrays.stream(originalquery).allMatch(querypart ->
        {
            for (char c : querypart.toCharArray()) {
                if (Character.isLetter(c) && (Character.isLowerCase(c) == false) && (pos.get() != table_pos)) {
                    foundchar.set(c);
                    return false;
                }
            }
            pos.getAndIncrement();
            return true;
        });
        if (result == false) {
            blacklistLog.add("Uppercase character found: " + foundchar);
            blacklistLog.addRefuseExecution();
        }
        else blacklistLog.addCheckOk(originalquery);

        return result;
    }

    /**
     * Check that all characters in the query are upper case.
     * @param originalquery original query to apply blacklist on.
     * @return true, if all characters are lower case.
     */
    public boolean isAllUppercase(String[] originalquery) {

        blacklistLog.addStrong(blacklistConfDataHelper.CONFSTRING_ALL_UPPERCASE);

        AtomicInteger pos = new AtomicInteger(0);
        AtomicReference<Character> foundchar = new AtomicReference<>((char) 0);
        boolean result = Arrays.stream(originalquery).allMatch(querypart ->
        {
            for (char c : querypart.toCharArray()) {
                if (Character.isLetter(c) && (Character.isUpperCase(c) == false) && (pos.get() != table_pos)) {
                    foundchar.set(c);
                    return false;
                }
            }
            pos.getAndIncrement();
            return true;
        });
        if (result == false) {
            blacklistLog.add("Lowercase character found: " + foundchar);
            blacklistLog.addRefuseExecution();
        }
        else blacklistLog.addCheckOk(originalquery);
        return result;
    }

    public boolean hasBadKeywordCombo(String[] originalquery) {
        blacklistLog.addStrong(blacklistConfDataHelper.CONFSTRING_KEYWORD_DETECTION);

        for (String badkeywordCombo : blacklistConfDataHelper.BADKEYWORD_COMBOS)
        {
            if (originalquery[predicate_pos].toUpperCase().contains(badkeywordCombo))
            {
                blacklistLog.add("Keyword combination found: "+ badkeywordCombo);
                blacklistLog.addRefuseExecution();
                return true;
            }
        }
        blacklistLog.addCheckOk(originalquery);
        return false;
    }

    public boolean isQueryBlocked() {
        return queryIsBlocked;
    }

    public String getBlacklistedQuery() {
        return String.join("", blacklistedQuery);
    }

    public String getLog() {
        return blacklistLog.log;
    }
}