package org.example;

/**
 * Console output shared by two threads: the keyboard loop and the MessageReader.
 * Every method locks on the same object so lines from both threads never get mixed.
 */
public class Console {
    private static final String PROMPT = "> ";
    private static final Object LOCK = new Object();

    /** Shows the prompt where the user types. */
    public static void prompt() {
        synchronized (LOCK) {
            System.out.print(PROMPT);
            System.out.flush();
        }
    }

    /** Prints a line from the main thread (local info or errors). */
    public static void info(String text) {
        synchronized (LOCK) {
            System.out.println(text);
        }
    }

    /**
     * Prints a message from the server at once, even while the user is typing:
     * clears the current prompt line, prints the message, and draws the prompt again.
     * (What the user already typed is still in the input buffer; Enter still sends it.)
     */
    public static void server(String text) {
        synchronized (LOCK) {
            System.out.print("\r\033[2K");          // go to line start and clear the line
            System.out.println("[server] " + text);
            System.out.print(PROMPT);
            System.out.flush();
        }
    }
}
