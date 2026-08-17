package me.eccentric_nz.gamemodeinventories.JSON;

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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

// An ordered sequence of values whose external form is JSON text in square brackets.
// get methods throw if a value is missing; opt methods return a default instead.
public class JSONArray {

    private final ArrayList myArrayList;

    public JSONArray() {

        this.myArrayList = new ArrayList();

    }

    // Construct a JSONArray from a JSONTokener; throws on syntax error.
    public JSONArray(JSONTokener x) throws JSONException {

        this();
        if (x.nextClean() != '[') {

            throw x.syntaxError("A JSONArray text must start with '['");

        }

        if (x.nextClean() != ']') {

            x.back();
            for (;;) {

                if (x.nextClean() == ',') {

                    x.back();
                    this.myArrayList.add(JSONObject.NULL);

                } else {

                    x.back();
                    this.myArrayList.add(x.nextValue());

                }

                switch (x.nextClean()) {

                    case ',':
                        if (x.nextClean() == ']') {

                            return;

                        }
                        x.back();
                        break;
                    case ']':
                        return;
                    default:
                        throw x.syntaxError("Expected a ',' or ']'");

                }

            }

        }

    }

    // Construct a JSONArray from a source JSON text; throws on syntax error.
    public JSONArray(String source) throws JSONException {

        this(new JSONTokener(source));

    }

    public JSONArray(Collection collection) {

        this.myArrayList = new ArrayList();
        if (collection != null) {

            Iterator iter = collection.iterator();
            while (iter.hasNext()) {

                this.myArrayList.add(JSONObject.wrap(iter.next()));

            }

        }

    }

    // Construct a JSONArray from an array; throws if the argument is not an array.
    public JSONArray(Object array) throws JSONException {

        this();
        if (array.getClass().isArray()) {

            int length = Array.getLength(array);
            for (int i = 0; i < length; i += 1) {

                this.put(JSONObject.wrap(Array.get(array, i)));

            }

        } else {

            throw new JSONException("JSONArray initial value should be a string or collection or array.");

        }

    }

    // Get the object value at an index; throws if there is no value for the index.
    public Object get(int index) throws JSONException {

        Object object = this.opt(index);
        if (object == null) {

            throw new JSONException("JSONArray[" + index + "] not found.");

        }

        return object;

    }

    // Get the boolean value at an index. The strings "true" and "false"
    // (case insensitive) are converted; throws if not convertible to boolean.
    public boolean getBoolean(int index) throws JSONException {

        Object object = this.get(index);
        if (object.equals(Boolean.FALSE) || (object instanceof String string && string.equalsIgnoreCase("false"))) {

            return false;

        } else if (object.equals(Boolean.TRUE)
                || (object instanceof String string && string.equalsIgnoreCase("true")))
        {

            return true;

        }

        throw new JSONException("JSONArray[" + index + "] is not a boolean.");

    }

    // Get the double value at an index; throws if the value cannot be converted to
    // a number.
    public double getDouble(int index) throws JSONException {

        Object object = this.get(index);
        try {

            return object instanceof Number ? ((Number) object).doubleValue() : Double.parseDouble((String) object);

        } catch (NumberFormatException e) {

            throw new JSONException("JSONArray[" + index + "] is not a number.");

        }

    }

    // Get the int value at an index; throws if the value cannot be converted to a
    // number.
    public int getInt(int index) throws JSONException {

        Object object = this.get(index);
        try {

            return object instanceof Number ? ((Number) object).intValue() : Integer.parseInt((String) object);

        } catch (NumberFormatException e) {

            throw new JSONException("JSONArray[" + index + "] is not a number.");

        }

    }

    // Get the JSONArray at an index; throws if the value is not a JSONArray.
    public JSONArray getJSONArray(int index) throws JSONException {

        Object object = this.get(index);
        if (object instanceof JSONArray) {

            return (JSONArray) object;

        }

        throw new JSONException("JSONArray[" + index + "] is not a JSONArray.");

    }

    // Get the JSONObject at an index; throws if the value is not a JSONObject.
    public JSONObject getJSONObject(int index) throws JSONException {

        Object object = this.get(index);
        if (object instanceof JSONObject) {

            return (JSONObject) object;

        }

        throw new JSONException("JSONArray[" + index + "] is not a JSONObject.");

    }

    // Get the long value at an index; throws if the value cannot be converted to a
    // number.
    public long getLong(int index) throws JSONException {

        Object object = this.get(index);
        try {

            return object instanceof Number ? ((Number) object).longValue() : Long.parseLong((String) object);

        } catch (NumberFormatException e) {

            throw new JSONException("JSONArray[" + index + "] is not a number.");

        }

    }

    // Get the byte value at an index; throws if the value cannot be converted to a
    // number.
    public byte getByte(int index) throws JSONException {

        Object object = this.get(index);
        try {

            return object instanceof Number ? ((Number) object).byteValue() : Byte.parseByte((String) object);

        } catch (NumberFormatException e) {

            throw new JSONException("JSONArray[" + index + "] is not a number.");

        }

    }

    // Get the string at an index; throws if there is no string value.
    public String getString(int index) throws JSONException {

        Object object = this.get(index);
        if (object instanceof String) {

            return (String) object;

        }

        throw new JSONException("JSONArray[" + index + "] not a string.");

    }

    // Returns true if the value at the index is null, or if there is no value.
    public boolean isNull(int index) {

        return JSONObject.NULL.equals(this.opt(index));

    }

    // Join the elements into a string with separator between them.
    // Assumes the data structure is acyclical; throws on an invalid number.
    public String join(String separator) throws JSONException {

        int len = this.length();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < len; i += 1) {

            if (i > 0) {

                sb.append(separator);

            }

            sb.append(JSONObject.valueToString(this.myArrayList.get(i)));

        }

        return sb.toString();

    }

    // Get the number of elements in the JSONArray, including nulls.
    public int length() {

        return this.myArrayList.size();

    }

    // Get the object value at an index, or null if there is no object at that
    // index.
    public Object opt(int index) {

        return (index < 0 || index >= this.length()) ? null : this.myArrayList.get(index);

    }

    // Get the optional boolean at an index; false if missing or not convertible to
    // boolean.
    public boolean optBoolean(int index) {

        return this.optBoolean(index, false);

    }

    // Get the optional boolean at an index; defaultValue if missing or not
    // convertible.
    public boolean optBoolean(int index, boolean defaultValue) {

        try {

            return this.getBoolean(index);

        } catch (JSONException e) {

            return defaultValue;

        }

    }

    // Get the optional double at an index; NaN if missing or not convertible to a
    // number.
    public double optDouble(int index) {

        return this.optDouble(index, Double.NaN);

    }

    // Get the optional double at an index; defaultValue if missing or not
    // convertible.
    public double optDouble(int index, double defaultValue) {

        try {

            return this.getDouble(index);

        } catch (JSONException e) {

            return defaultValue;

        }

    }

    // Get the optional int at an index; zero if missing or not convertible to a
    // number.
    public int optInt(int index) {

        return this.optInt(index, 0);

    }

    // Get the optional int at an index; defaultValue if missing or not convertible.
    public int optInt(int index, int defaultValue) {

        try {

            return this.getInt(index);

        } catch (JSONException e) {

            return defaultValue;

        }

    }

    // Get the optional JSONArray at an index, or null if there is none.
    public JSONArray optJSONArray(int index) {

        Object o = this.opt(index);
        return o instanceof JSONArray ? (JSONArray) o : null;

    }

    // Get the optional JSONObject at an index, or null if there is none.
    public JSONObject optJSONObject(int index) {

        Object o = this.opt(index);
        return o instanceof JSONObject ? (JSONObject) o : null;

    }

    // Get the optional long at an index; zero if missing or not convertible to a
    // number.
    public long optLong(int index) {

        return this.optLong(index, 0);

    }

    // Get the optional long at an index; defaultValue if missing or not
    // convertible.
    public long optLong(int index, long defaultValue) {

        try {

            return this.getLong(index);

        } catch (JSONException e) {

            return defaultValue;

        }

    }

    // Get the optional string at an index; empty string if there is no value.
    // Non-null, non-string values are converted to a string.
    public String optString(int index) {

        return this.optString(index, "");

    }

    // Get the optional string at an index, or defaultValue if there is no value.
    public String optString(int index, String defaultValue) {

        Object object = this.opt(index);
        return JSONObject.NULL.equals(object) ? defaultValue : object.toString();

    }

    // Append a boolean value.
    public JSONArray put(boolean value) {

        this.put(value ? Boolean.TRUE : Boolean.FALSE);
        return this;

    }

    // Append a Collection value, which is wrapped as a JSONArray.
    public JSONArray put(Collection value) {

        this.put(new JSONArray(value));
        return this;

    }

    // Append a double value; throws if it is not finite.
    public JSONArray put(double value) throws JSONException {

        Double d = new Double(value);
        JSONObject.testValidity(d);
        this.put(d);
        return this;

    }

    // Append an int value.
    public JSONArray put(int value) {

        this.put(new Integer(value));
        return this;

    }

    // Append a long value.
    public JSONArray put(long value) {

        this.put(new Long(value));
        return this;

    }

    // Append a Map value, which is wrapped as a JSONObject.
    public JSONArray put(Map value) {

        this.put(new JSONObject(value));
        return this;

    }

    // Append an object value: a Boolean, Double, Integer, JSONArray, JSONObject,
    // Long, or String, or the JSONObject.NULL object.
    public JSONArray put(Object value) {

        this.myArrayList.add(value);
        return this;

    }

    // Put or replace a boolean at an index; pads with nulls if the index exceeds
    // the length.
    public JSONArray put(int index, boolean value) throws JSONException {

        this.put(index, value ? Boolean.TRUE : Boolean.FALSE);
        return this;

    }

    // Put a Collection at an index, wrapped as a JSONArray.
    public JSONArray put(int index, Collection value) throws JSONException {

        this.put(index, new JSONArray(value));
        return this;

    }

    // Put or replace a double at an index; pads with nulls if the index exceeds the
    // length.
    public JSONArray put(int index, double value) throws JSONException {

        this.put(index, new Double(value));
        return this;

    }

    // Put or replace an int at an index; pads with nulls if the index exceeds the
    // length.
    public JSONArray put(int index, int value) throws JSONException {

        this.put(index, new Integer(value));
        return this;

    }

    // Put or replace a long at an index; pads with nulls if the index exceeds the
    // length.
    public JSONArray put(int index, long value) throws JSONException {

        this.put(index, new Long(value));
        return this;

    }

    // Put a Map at an index, wrapped as a JSONObject.
    public JSONArray put(int index, Map value) throws JSONException {

        this.put(index, new JSONObject(value));
        return this;

    }

    // Put or replace an object value at an index. If the index exceeds the length,
    // null elements are added to pad it out; throws if the index is negative.
    public JSONArray put(int index, Object value) throws JSONException {

        JSONObject.testValidity(value);
        if (index < 0) {

            throw new JSONException("JSONArray[" + index + "] not found.");

        }

        if (index < this.length()) {

            this.myArrayList.set(index, value);

        } else {

            while (index != this.length()) {

                this.put(JSONObject.NULL);

            }

            this.put(value);

        }

        return this;

    }

    // Remove the element at an index and close the hole.
    // Returns the removed value, or null if there was none.
    public Object remove(int index) {

        Object o = this.opt(index);
        this.myArrayList.remove(index);
        return o;

    }

    // Produce a JSONObject pairing the given names with this array's values.
    // Returns null if either array is empty; throws if any name is null.
    public JSONObject toJSONObject(JSONArray names) throws JSONException {

        if (names == null || names.length() == 0 || this.length() == 0) {

            return null;

        }

        JSONObject jo = new JSONObject();
        for (int i = 0; i < names.length(); i += 1) {

            jo.put(names.getString(i), this.opt(i));

        }

        return jo;

    }

    // Make a compact JSON text of this JSONArray. Returns null if a syntactically
    // correct text cannot be produced. Assumes the data structure is acyclical.
    @Override
    public String toString() {

        try {

            return this.toString(0);

        } catch (JSONException e) {

            return null;

        }

    }

    // Make a prettyprinted JSON text of this JSONArray. Assumes the data structure
    // is acyclical.
    public String toString(int indentFactor) throws JSONException {

        StringWriter sw = new StringWriter();
        synchronized (sw.getBuffer()) {

            return this.write(sw, indentFactor, 0).toString();

        }

    }

    // Write the contents as compact JSON text to a writer. Assumes the data
    // structure is acyclical.
    public Writer write(Writer writer) throws JSONException {

        return this.write(writer, 0, 0);

    }

    // Write the contents as JSON text to a writer, with the given indentation.
    // Assumes the data structure is acyclical.
    Writer write(Writer writer, int indentFactor, int indent) throws JSONException {

        try {

            boolean commanate = false;
            int length = this.length();
            writer.write('[');

            if (length == 1) {

                JSONObject.writeValue(writer, this.myArrayList.get(0), indentFactor, indent);

            } else if (length != 0) {

                int newindent = indent + indentFactor;

                for (int i = 0; i < length; i += 1) {

                    if (commanate) {

                        writer.write(',');

                    }

                    if (indentFactor > 0) {

                        writer.write('\n');

                    }

                    JSONObject.indent(writer, newindent);
                    JSONObject.writeValue(writer, this.myArrayList.get(i), indentFactor, newindent);
                    commanate = true;

                }

                if (indentFactor > 0) {

                    writer.write('\n');

                }

                JSONObject.indent(writer, indent);

            }

            writer.write(']');
            return writer;

        } catch (IOException e) {

            throw new JSONException(e);

        }

    }

}
