package me.eccentric_nz.gamemodeinventories.JSON;

import java.io.*;

/*
Copyright (c) 2002 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

// Extracts characters and tokens from a source string. Used by the JSONObject
// and JSONArray constructors to parse JSON source strings.
public class JSONTokener {

    private final Reader reader;
    private long character;
    private boolean eof;
    private long index;
    private long line;
    private char previous;
    private boolean usePrevious;

    public JSONTokener(Reader reader) {

        this.reader = reader.markSupported() ? reader : new BufferedReader(reader);
        eof = false;
        usePrevious = false;
        previous = 0;
        index = 0;
        character = 1;
        line = 1;

    }

    public JSONTokener(InputStream inputStream) throws JSONException {

        this(new InputStreamReader(inputStream));

    }

    public JSONTokener(String s) {

        this(new StringReader(s));

    }

    // Get the hex value of a character (base16), or -1 if c is not a hex digit.
    public static int dehexchar(char c) {

        if (c >= '0' && c <= '9') {

            return c - '0';

        }

        if (c >= 'A' && c <= 'F') {

            return c - ('A' - 10);

        }

        if (c >= 'a' && c <= 'f') {

            return c - ('a' - 10);

        }

        return -1;

    }

    // Back up one character to provide lookahead; stepping back twice is not
    // supported.
    public void back() throws JSONException {

        if (usePrevious || index <= 0) {

            throw new JSONException("Stepping back two steps is not supported");

        }

        index -= 1;
        character -= 1;
        usePrevious = true;
        eof = false;

    }

    public boolean end() {

        return eof && !usePrevious;

    }

    // Returns true if the source string still contains characters that next() can
    // consume.
    public boolean more() throws JSONException {

        next();
        if (end()) {

            return false;

        }

        back();
        return true;

    }

    // Get the next character in the source string, or 0 if past the end.
    public char next() throws JSONException {

        int c;
        if (usePrevious) {

            usePrevious = false;
            c = previous;

        } else {

            try {

                c = reader.read();

            } catch (IOException exception) {

                throw new JSONException(exception);

            }

            if (c <= 0) { // End of stream

                eof = true;
                c = 0;

            }

        }

        index += 1;
        if (previous == '\r') {

            line += 1;
            character = c == '\n' ? 0 : 1;

        } else if (c == '\n') {

            line += 1;
            character = 0;

        } else {

            character += 1;

        }

        previous = (char) c;
        return previous;

    }

    // Consume the next character and check that it matches c; throws if it does
    // not.
    public char next(char c) throws JSONException {

        char n = next();
        if (n != c) {

            throw syntaxError("Expected '" + c + "' and instead saw '" + n + "'");

        }

        return n;

    }

    // Get the next n characters; throws if fewer than n characters remain in the
    // source.
    public String next(int n) throws JSONException {

        if (n == 0) {

            return "";

        }

        char[] chars = new char[n];
        int pos = 0;

        while (pos < n) {

            chars[pos] = next();
            if (end()) {

                throw syntaxError("Substring bounds error");

            }

            pos += 1;

        }

        return new String(chars);

    }

    // Get the next character, skipping whitespace; 0 if there are no more
    // characters.
    public char nextClean() throws JSONException {

        for (;;) {

            char c = next();
            if (c == 0 || c > ' ') {

                return c;

            }

        }

    }

    // Return the characters up to the next close quote, processing backslash
    // escapes. Single quotes are accepted even though formal JSON forbids them.
    public String nextString(char quote) throws JSONException {

        char c;
        StringBuilder sb = new StringBuilder();
        for (;;) {

            c = next();
            switch (c) {

                case 0:
                case '\n':
                case '\r':
                    throw syntaxError("Unterminated string");
                case '\\':
                    c = next();
                    switch (c) {

                        case 'b':
                            sb.append('\b');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 'u':
                            sb.append((char) Integer.parseInt(next(4), 16));
                            break;
                        case '"':
                        case '\'':
                        case '\\':
                        case '/':
                            sb.append(c);
                            break;
                        default:
                            throw syntaxError("Illegal escape.");

                    }
                    break;
                default:
                    if (c == quote) {

                        return sb.toString();

                    }
                    sb.append(c);

            }

        }

    }

    // Get the text up to but not including the delimiter or end of line, trimmed.
    public String nextTo(char delimiter) throws JSONException {

        StringBuilder sb = new StringBuilder();
        for (;;) {

            char c = next();
            if (c == delimiter || c == 0 || c == '\n' || c == '\r') {

                if (c != 0) {

                    back();

                }

                return sb.toString().trim();

            }

            sb.append(c);

        }

    }

    // Get the text up to but not including one of the delimiter characters
    // or the end of line, trimmed.
    public String nextTo(String delimiters) throws JSONException {

        char c;
        StringBuilder sb = new StringBuilder();
        for (;;) {

            c = next();
            if (delimiters.indexOf(c) >= 0 || c == 0 || c == '\n' || c == '\r') {

                if (c != 0) {

                    back();

                }

                return sb.toString().trim();

            }

            sb.append(c);

        }

    }

    // Get the next value: a Boolean, Double, Integer, JSONArray, JSONObject, Long,
    // or String, or the JSONObject.NULL object. Throws on syntax error.
    public Object nextValue() throws JSONException {

        char c = nextClean();
        String string;
        switch (c) {

            case '"':
            case '\'':
                return nextString(c);
            case '{':
                back();
                return new JSONObject(this);
            case '[':
                back();
                return new JSONArray(this);

        }

        StringBuilder sb = new StringBuilder();
        while (c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {

            sb.append(c);
            c = next();

        }

        back();
        string = sb.toString().trim();
        if ("".equals(string)) {

            throw syntaxError("Missing value");

        }

        return JSONObject.stringToValue(string);

    }

    // Skip characters until the next character is the requested character. Returns
    // that character, or 0 (with no characters skipped) if it is not found.
    public char skipTo(char to) throws JSONException {

        char c;
        try {

            long startIndex = index;
            long startCharacter = character;
            long startLine = line;
            reader.mark(1000000);
            do {

                c = next();
                if (c == 0) {

                    reader.reset();
                    index = startIndex;
                    character = startCharacter;
                    line = startLine;
                    return c;

                }

            } while (c != to);

        } catch (IOException exc) {

            throw new JSONException(exc);

        }

        back();
        return c;

    }

    // Make a JSONException to signal a syntax error, with the current position
    // appended.
    public JSONException syntaxError(String message) {

        return new JSONException(message + toString());

    }

    // Returns " at {index} [character {character} line {line}]".
    @Override
    public String toString() {

        return " at " + index + " [character " + character + " line " + line + "]";

    }

}
